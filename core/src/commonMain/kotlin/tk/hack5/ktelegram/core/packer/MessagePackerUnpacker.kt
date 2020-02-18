/*
 *     KTelegram (Telegram MTProto client library)
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

package tk.hack5.ktelegram.core.packer

import com.github.aakira.napier.Napier
import kotlinx.coroutines.channels.Channel
import tk.hack5.ktelegram.core.connection.Connection
import tk.hack5.ktelegram.core.encoder.EncryptedMTProtoEncoder
import tk.hack5.ktelegram.core.mtproto.*
import tk.hack5.ktelegram.core.mtproto.MessageObject
import tk.hack5.ktelegram.core.state.MTProtoState
import tk.hack5.ktelegram.core.tl.*

private const val tag = "MessagePackerUnpacker"

class MessagePackerUnpacker(val connection: Connection, val encoder: EncryptedMTProtoEncoder, val state: MTProtoState) {
    private val incomingMessages = Channel<MessageUnpackAction>()

    suspend fun sendAndRecv(message: TLMethod<*>): TLObject<*> {
        connection.send(encoder.wrapAndEncode(message))
        return when (val action = incomingMessages.receive()) {
            is MessageUnpackActionRetry -> sendAndRecv(message)
            is MessageUnpackActionReturn -> {
                println(action)
                action.value
            }
        }
    }

    suspend fun pump(input: Channel<ByteArray>) {
        while (true) {
            try {
                val b = input.receive()
                val d = encoder.decode(b)
                println("raw data = d")
                val m = MessageObject.fromTlRepr(d.toIntArray(), bare = true)!!.second
                unpackMessage(m)
            } catch (e: Exception) {
                Napier.e("Dropped message due to exception", e, tag = tag)
            }
        }
    }

    suspend fun unpackMessage(message: TLObject<*>, msgId: Long? = null) {
        println("msg = $message")
        if (message is MessageObject)
            return unpackMessage(message.body, message.msgId)
        else
            msgId!!
        when (message) {
            is ObjectType -> {
                unpackMessage(handleMaybeGzipped(message), msgId)
            }
            is BadServerSaltObject -> {
                // Fix the salt and retry the message
                Napier.d("Bad server salt, corrected to ${message.newServerSalt}", tag = tag)
                state.salt = message.newServerSalt.asTlObject().toTlRepr().toByteArray()
                incomingMessages.send(MessageUnpackActionRetry(message.badMsgId))
            }
            is NewSessionCreatedObject -> return // We don't care about new sessions, AFAIK
            is MsgContainerObject -> {
                // Recurse the container
                message.messages.sortedBy { it.seqno }.forEach { unpackMessage(it, msgId) }
            }
            is RpcResultObject -> {
                incomingMessages.send(MessageUnpackActionReturn(message.reqMsgId, handleMaybeGzipped(message.result)))
            }
            is PongObject -> incomingMessages.send(MessageUnpackActionReturn(message.msgId, message))
            is BadMsgNotificationObject -> {
                Napier.e("Bad msg ${message.badMsgId}", tag = tag)
                TODO("implement")
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
                connection.send(encoder.wrapAndEncode(MsgsStateInfoObject(msgId, ByteArray(message.msgIds.size) { 1 })))
            }
        }
    }

    suspend fun handleMaybeGzipped(message: ObjectType): TLObject<*> {
        return when (message) {
            is ObjectObject -> {
                message.innerObject
            }
            is GzipPackedObject -> {
                error("NI:GZIP")
            }
            else -> error("Unexpected ObjectType")
        }
    }
}

sealed class MessageUnpackAction(val req_msg_id: Long)

class MessageUnpackActionRetry(req_msg_id: Long) : MessageUnpackAction(req_msg_id)
class MessageUnpackActionReturn(req_msg_id: Long, val value: TLObject<*>) : MessageUnpackAction(req_msg_id)
