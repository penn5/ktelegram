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

package tk.hack5.telekat.core.packer

import com.github.aakira.napier.Napier
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import tk.hack5.telekat.core.connection.Connection
import tk.hack5.telekat.core.encoder.EncryptedMTProtoEncoder
import tk.hack5.telekat.core.mtproto.*
import tk.hack5.telekat.core.mtproto.MessageObject
import tk.hack5.telekat.core.state.MTProtoState
import tk.hack5.telekat.core.tl.*
import tk.hack5.telekat.core.utils.GZIPImpl

private const val tag = "MessagePackerUnpacker"

class MessagePackerUnpacker(
    private val connection: Connection,
    private val encoder: EncryptedMTProtoEncoder,
    private val state: MTProtoState,
    val updatesChannel: Channel<UpdatesType>
) {
    private val pendingMessages: MutableMap<Long, CompletableDeferred<MessageUnpackAction>> = HashMap(5)

    suspend fun sendAndRecv(message: TLMethod<*>): TLObject<*> = coroutineScope {
        val encoded = encoder.wrapAndEncode(message)
        val deferred = CompletableDeferred<MessageUnpackAction>(coroutineContext[Job])
        pendingMessages[encoded.second] = deferred
        connection.send(encoded.first)
        when (val action = deferred.await()) {
            is MessageUnpackActionRetry -> sendAndRecv(message)
            is MessageUnpackActionReturn -> action.value
        }
    }

    suspend fun pump(input: Channel<ByteArray>) {
        while (true) {
            try {
                val b = input.receive()
                val d = encoder.decode(b)
                val m = MessageObject.fromTlRepr(d.toIntArray(), bare = true)!!.second
                state.updateSeqNo(m.seqno)
                unpackMessage(m)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                Napier.e("Dropped packet due to exception", e, tag = tag)
            }
        }
    }

    private suspend fun unpackMessage(message: TLObject<*>, msgId: Long? = null) {
        try {
            println("msg = $message")
            if (message is MessageObject) {
                return unpackMessage(message.body, message.msgId)
            } else
                msgId!!
            when (message) {
                is ObjectType -> {
                    unpackMessage(handleMaybeGzipped(message), msgId)
                }
                is BadServerSaltObject -> {
                    // Fix the salt and retry the message
                    Napier.d("Bad server salt, corrected to ${message.newServerSalt}", tag = tag)
                    state.salt = message.newServerSalt.asTlObject().toTlRepr().toByteArray()
                    pendingMessages[message.badMsgId]!!.complete(MessageUnpackActionRetry)
                }
                is NewSessionCreatedObject -> return // We don't care about new sessions, AFAIK
                is MsgContainerObject -> {
                    // Recurse the container
                    message.messages.sortedBy { it.seqno }.forEach { unpackMessage(it, msgId) }
                }
                is RpcResultObject -> {
                    pendingMessages[message.reqMsgId]!!.complete(
                        MessageUnpackActionReturn(
                            handleMaybeGzipped(message.result)
                        )
                    )
                }
                is PongObject -> pendingMessages[message.msgId]!!.complete(MessageUnpackActionReturn(message))
                is BadMsgNotificationObject -> {
                    Napier.e("Bad msg ${message.badMsgId}", tag = tag)
                    when (message.errorCode) {
                        in 16..17 -> {
                            TODO("sync time")
                        }
                        18 -> Napier.e("Server says invalid msgId")
                        19 -> Napier.e("Server says duped msgId")
                        20 -> {
                        } // Just re-send it
                        32 -> state.lastMsgId += 16
                        33 -> state.lastMsgId -= 16
                        in 34..35 -> error("Server says relevancy incorrect")
                        48 -> {
                            return
                        } // We will get a BadServerSalt and re-send then
                        64 -> Napier.e("Server says invalid container")
                        else -> Napier.e("Server sent invalid BadMsgNotification")
                    }
                    pendingMessages[message.badMsgId]?.complete(MessageUnpackActionRetry)
                }
                is MsgDetailedInfoObject -> {
                    Napier.e("Detailed msg info", tag = tag)
                    TODO("implement")
                }
                is MsgNewDetailedInfoObject -> {
                    Napier.e("New detailed msg info", tag = tag)
                    TODO("implement")
                }
                is MsgsAckObject -> {
                    message.msgIds.forEach {
                        //incomingMessages.send(MessageUnpackActionReturn(it, null))
                    }
                }
                is FutureSaltsObject -> {
                    // TODO store and handle future salts
                }
                is MsgsStateReqObject -> {
                    // TODO actually store some data so we can do retries properly
                    connection.send(
                        encoder.wrapAndEncode(
                            MsgsStateInfoObject(
                                msgId,
                                ByteArray(message.msgIds.size) { 1 })
                        ).first
                    )
                }
                is UpdatesType -> updatesChannel.send(message)
                else -> Napier.e("new message type - $message")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            Napier.e("Dropped message due to exception", e, tag = tag)
        }
    }

    private suspend fun handleMaybeGzipped(message: ObjectType): TLObject<*> {
        return when (message) {
            is ObjectObject -> {
                message.innerObject
            }
            is GzipPackedObject -> {
                handleMaybeGzipped(ObjectObject.fromTlRepr(GZIPImpl.decompress(message.packedData).toIntArray())!!.second)
            }
        }
    }
}

sealed class MessageUnpackAction

object MessageUnpackActionRetry : MessageUnpackAction()
class MessageUnpackActionReturn(val value: TLObject<*>) : MessageUnpackAction()
