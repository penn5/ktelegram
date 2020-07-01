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

package tk.hack5.telekat.core.state

import com.github.aakira.napier.Napier
import com.soywiz.klock.DateTime
import com.soywiz.klock.seconds
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import tk.hack5.telekat.core.crypto.AuthKey
import tk.hack5.telekat.core.tl.asTlObject
import tk.hack5.telekat.core.tl.toByteArray
import kotlin.math.roundToInt
import kotlin.math.roundToLong
import kotlin.random.Random

private const val tag = "MTProtoState"

interface MTProtoState {
    val authKey: AuthKey?
    var timeOffset: Long
    var salt: ByteArray
    val sessionId: ByteArray
    var seq: Int
    var remoteContentRelatedSeq: Int
    var lastMsgId: Long


    suspend fun getMsgId(): Long
    fun validateMsgId(id: Long): Boolean

    suspend fun updateTimeOffset(seconds: Int)
    suspend fun updateTimeOffset(msgId: Long)

    fun updateMsgId(msgId: Long)
    suspend fun updateSeqNo(seq: Int)
}

@Serializable
data class MTProtoStateImpl(override val authKey: AuthKey? = null) : MTProtoState {
    @Transient
    override var timeOffset = 0L
    @Transient
    override var salt = 0L.asTlObject().toTlRepr().toByteArray()
    override val sessionId = Random.nextLong().asTlObject().toTlRepr().toByteArray()
    @Transient
    override var seq = 0
    @Transient
    override var remoteContentRelatedSeq = -1
    override var lastMsgId = 0L
    @Transient
    private val lock = Mutex()

    @ExperimentalUnsignedTypes
    override suspend fun getMsgId(): Long {
        val now = DateTime.now()
        val sinceEpoch = now - DateTime.EPOCH
        val secsSinceEpoch = sinceEpoch.seconds.toInt()
        val sinceSecond = sinceEpoch - secsSinceEpoch.seconds
        val nanoseconds = sinceSecond.nanoseconds.roundToInt()
        val secs = secsSinceEpoch + timeOffset
        var newMsgId = secs.shl(32).or(nanoseconds.toLong().shl(2))
        lock.withLock {
            while (newMsgId <= lastMsgId)
                newMsgId = lastMsgId + 4
            lastMsgId = newMsgId
        }
        Napier.d("Generated msg_id=$newMsgId", tag = tag)
        return newMsgId
    }

    @ExperimentalUnsignedTypes
    override fun validateMsgId(id: Long): Boolean {
        val now = DateTime.now()
        val sinceEpoch = now - DateTime.EPOCH
        val serverTime = id.toULong().shr(32)
        if (serverTime < (sinceEpoch - 300.seconds).seconds.toUInt()) return false
        if (serverTime > (sinceEpoch + 30.seconds).seconds.toUInt()) return false
        return true
    }

    override suspend fun updateSeqNo(seq: Int) {
        require(seq / 2 >= remoteContentRelatedSeq) { "seqno was reduced by the server ($seq < 2*$remoteContentRelatedSeq)" }
        if (seq.rem(2) == 1) {
            // Content related
            lock.withLock {
                remoteContentRelatedSeq++
            }
        }
    }

    override suspend fun updateTimeOffset(seconds: Int) {
        lock.withLock {
            val now = DateTime.now() - DateTime.EPOCH
            val oldOffset = timeOffset
            timeOffset = seconds - now.seconds.roundToLong()
            Napier.d("Updating timeOffset to $timeOffset (was $oldOffset, t=$now, c=$seconds)", tag = tag)
        }
    }

    override suspend fun updateTimeOffset(msgId: Long) {
        updateTimeOffset(msgId.ushr(32).toInt())
    }

    @ExperimentalUnsignedTypes
    override fun updateMsgId(msgId: Long) =
        require(validateMsgId(msgId)) { "msg_id was reduced by the server ($msgId)" }
}