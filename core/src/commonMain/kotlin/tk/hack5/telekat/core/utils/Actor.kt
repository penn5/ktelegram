/*
 *     TeleKat (Telegram MTProto client library)
 *     Copyright (C) 2020 Hackintosh Five
 *
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU Affero General Public License as
 *     published by the Free Software Foundation, either version 3 of the
 *     License, or (at your option) any later version.
 *
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU Affero General Public License for more details.
 *
 *     You should have received a copy of the GNU Affero General Public License
 *     along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package tk.hack5.telekat.core.utils

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * An Actor executes functions in a series of coroutines.
 * Internally, this is implemented with a [Channel], to which tasks are sent. Each task is simply a Kotlin Lambda.
 */
interface Actor {
    /**
     * The current state of this Actor
     */
    val state: ActorState

    /**
     * Run the provided lambda in the actor's Job
     *
     * @param block the code to be executed in the actor
     * @return the result of [block]
     */
    suspend fun <R> act(block: suspend () -> R): R

    /**
     * Start the actor's jobs
     * @param skipLocking not for public use
     */
    suspend fun start(skipLocking: Boolean = false)

    /**
     * Stop all the actor's jobs
     *
     * If [cause] is null,
     * 1. Stop accepting new jobs
     * 2. Complete execution of the backlog, returning results as usual
     * 3. Cancel all actor jobs
     * If [cause] is not null, cancel all actor jobs with [cause] as the cause
     */
    suspend fun cancel(cause: CancellationException? = null)
}

/**
 * This represents the possible states of an [Actor]
 */
enum class ActorState {
    /**
     * The [Actor] is not running
     */
    STOPPED,

    /**
     * The Actor is in the process of starting.
     * It accepts tasks but they will not begin to be executed until the actor is [STARTED]
     */
    STARTING,

    /**
     * The [Actor] is running and executing tasks.
     */
    STARTED,

    /**
     * The [Actor] is no longer accepting tasks, but is still executing a backlog of tasks from when it was [STARTED]
     */
    STOPPING
}

/**
 * An Actor executes functions in a series of coroutines.
 * Internally, this is implemented with a [Channel], to which tasks are sent. Each task is simply a Kotlin Lambda.
 *
 * @param scope The scope in which to run the workers that execute the tasks enqueued upon the Actor
 * @param start The starting mode, which can be [CoroutineStart.LAZY] or [CoroutineStart.DEFAULT].
 * @param concurrency The number of workers, defaulting to one worker (which should be used when data is mutable
 */
abstract class BaseActor(
    private val scope: CoroutineScope = GlobalScope,
    private val start: CoroutineStart? = CoroutineStart.LAZY,
    private val concurrency: Int = 1
) : Actor {
    /**
     * Internal communication mechanism between the actor and the user
     */
    private val tasks = Channel<RemoteFunction<*>>(Channel.UNLIMITED)

    /**
     * The Job executing tasks
     * This Job has at least one child. Each child of this Job is used to execute tasks
     */
    private var job: Job? = null

    /**
     * Lock held when creating or destroying jobs, or changing [acceptingTasks]
     */
    private val startStopLock = Mutex()

    /**
     * Variable to set whether new tasks should be accepted
     * Set to false during [ActorState.STOPPING] or [ActorState.STOPPED]
     */
    private var acceptingTasks = false

    override val state: ActorState
        get() = when {
            job == null && !acceptingTasks -> ActorState.STOPPED
            job == null && acceptingTasks -> ActorState.STARTING // We should only be in this state for a few ms, but it saves us the need to lock
            job != null && acceptingTasks -> ActorState.STARTED
            else -> ActorState.STOPPING // job != null && acceptingTasks
        }

    override suspend fun <R> act(block: suspend () -> R): R {
        if (state == ActorState.STOPPED && start == CoroutineStart.LAZY) {
            start()
        }
        require(acceptingTasks) { "The Actor must be accepting tasks" }
        val result = CompletableDeferred<R>()
        tasks.send(RemoteFunction(block, result))
        return result.await()
    }

    /**
     * The code running inside the actor that takes tasks from [tasks] and executes them
     */
    private suspend fun loop() = coroutineScope {
        for (task in tasks) {
            task.runAndComplete()
        }
    }

    private suspend fun start() {
        when (state) {
            ActorState.STOPPED -> {
                acceptingTasks = true
                job = scope.launch {
                    repeat(concurrency) {
                        launch {
                            loop()
                        }
                    }
                }
            }
            else -> return
        }
    }

    override suspend fun start(skipLocking: Boolean) {
        if (skipLocking)
            start()
        else
            startStopLock.withLock {
                start()
            }
    }

    override suspend fun cancel(cause: CancellationException?) = startStopLock.withLock {
        tasks.cancel(cause)
        job?.cancelAndJoin()
        job = null
    }
}

/**
 * An actor that can be used without any subclassing or inheritance
 */
class GenericActor(
    scope: CoroutineScope = GlobalScope,
    start: CoroutineStart = CoroutineStart.LAZY,
    concurrency: Int = 1
) : BaseActor(scope, start, concurrency) {
    suspend operator fun <R> invoke(block: suspend () -> R): R = act(block)
}

/**
 * Class used to represent the equivalent of an RPC for Actors - running a procedure in another Job
 *
 * @param function The code to be executed remotely
 * @param result The output of the code
 */
private class RemoteFunction<R>(private val function: suspend () -> R, private val result: CompletableDeferred<R>) {
    /**
     * Execute the [function] and complete the [result], whether exceptionally or successfully
     */
    suspend fun runAndComplete() {
        try {
            result.complete(function())
        } catch (e: Throwable) {
            result.completeExceptionally(e)
        }
    }
}
