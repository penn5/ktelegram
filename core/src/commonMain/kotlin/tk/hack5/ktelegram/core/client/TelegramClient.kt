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

package tk.hack5.ktelegram.core.client

import com.soywiz.krypto.SecureRandom
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import tk.hack5.ktelegram.core.auth.authenticate
import tk.hack5.ktelegram.core.connection.Connection
import tk.hack5.ktelegram.core.connection.TcpFullConnection
import tk.hack5.ktelegram.core.crypto.AuthKey
import tk.hack5.ktelegram.core.crypto.RSAEncoder
import tk.hack5.ktelegram.core.crypto.RSAEncoderImpl
import tk.hack5.ktelegram.core.encoder.EncryptedMTProtoEncoder
import tk.hack5.ktelegram.core.encoder.MTProtoEncoder
import tk.hack5.ktelegram.core.encoder.MTProtoEncoderWrapped
import tk.hack5.ktelegram.core.encoder.PlaintextMTProtoEncoder
import tk.hack5.ktelegram.core.packer.MessagePackerUnpacker
import tk.hack5.ktelegram.core.state.MTProtoState
import tk.hack5.ktelegram.core.state.MTProtoStateImpl
import tk.hack5.ktelegram.core.tl.*

private const val tag = "TelegramClient"

abstract class TelegramClient {
    internal abstract val secureRandom: SecureRandom
    abstract val apiId: String
    abstract val apiHash: String
    abstract suspend fun connect(
        connection: (String, Int) -> Connection = ::TcpFullConnection,
        plaintextEncoder: MTProtoEncoder = PlaintextMTProtoEncoder(MTProtoStateImpl()),
        encryptedEncoderConstructor: (MTProtoState) -> EncryptedMTProtoEncoder = {
            EncryptedMTProtoEncoder(it)
        },
        rsaEncoder: RSAEncoder = RSAEncoderImpl
    )

    internal abstract suspend fun <R : TLObject<*>> send(request: TLMethod<R>, encoder: MTProtoEncoder): R
    abstract suspend fun <R : TLObject<*>> sendWrapped(request: TLMethod<R>, encoder: MTProtoEncoderWrapped): R
}

open class TelegramClientImpl(override val apiId: String, override val apiHash: String) : TelegramClient() {
    override var secureRandom = SecureRandom()
    protected var connection: Connection? = null
    protected var encoder: EncryptedMTProtoEncoder? = null
    protected var authKey: AuthKey? = null
    protected var recvChannel: Channel<TLObject<*>>? = null
    protected var unpacker: MessagePackerUnpacker? = null


    override suspend fun connect(
        connection: (String, Int) -> Connection,
        plaintextEncoder: MTProtoEncoder,
        encryptedEncoderConstructor: (MTProtoState) -> EncryptedMTProtoEncoder,
        rsaEncoder: RSAEncoder
    ) = coroutineScope {
        connection("149.154.167.51", 80).let {
            this@TelegramClientImpl.connection = it
            it.connect()
            authKey = authenticate(this@TelegramClientImpl, plaintextEncoder)
            MTProtoStateImpl(authKey).let { state ->
                encoder = encryptedEncoderConstructor(state)
                unpacker = MessagePackerUnpacker(it, encoder!!, state)
            }
            GlobalScope.launch {
                startRecvLoop()
            }
            launch {
                delay(1000)
                sendWrapped(Help_GetConfigRequest(), encoder!!)
            }
            sendWrapped(
                InvokeWithLayerRequest(
                    105,
                    InitConnectionRequest(
                        apiId.toInt(),
                        "urmom",
                        "1.2.3",
                        "1.2.3",
                        "en",
                        "",
                        "en",
                        null,
                        Help_GetConfigRequest()
                    )
                ), encoder!!
            )
            //sendWrapped(Help_GetConfigRequest(), encoder!!)
//            sendWrapped(InvokeWithLayerRequest(100, InitConnectionRequest(apiId.toInt(), apiHash, "urmom", "1.2.3", "en", "", "en", null, Help_GetConfigRequest())), encoder!!)
//            sendWrapped(InvokeWithLayerRequest(105, Help_GetConfigRequest()), encoder!!)
            Unit
        }
    }

    protected suspend fun startRecvLoop() = coroutineScope {
        val byteChannel = Channel<ByteArray>()
        launch {
            connection!!.recvLoop(byteChannel)
        }
        launch {
            unpacker!!.pump(byteChannel)
        }
    }

    override suspend fun <R : TLObject<*>> send(request: TLMethod<R>, encoder: MTProtoEncoder): R {
        connection!!.send(encoder.encode(request.toTlRepr().toByteArray()))
        val response = encoder.decode(connection!!.recv()).toIntArray()
        println(response.contentToString())
        return request.constructor.fromTlRepr(response)!!.second
    }

    override suspend fun <R : TLObject<*>> sendWrapped(request: TLMethod<R>, encoder: MTProtoEncoderWrapped): R {
        return unpacker!!.sendAndRecv(request) as R
        /*
        connection!!.send(encoder.wrapAndEncode(request))
        val response = encoder.decodeAndUnwrap(recvChannel)
        println(response)
        when(response) {
            is BadServerSaltObject -> {
                encoder.state.salt = response.newServerSalt.asTlObject().toTlRepr().toByteArray()
                return sendWrapped(request, encoder)
            }
        }
        return response as R
        */
    }
}