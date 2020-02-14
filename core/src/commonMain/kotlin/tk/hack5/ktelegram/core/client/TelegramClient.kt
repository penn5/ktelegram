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
import tk.hack5.ktelegram.core.TLMethod
import tk.hack5.ktelegram.core.TLObject
import tk.hack5.ktelegram.core.auth.authenticate
import tk.hack5.ktelegram.core.connection.Connection
import tk.hack5.ktelegram.core.connection.TcpFullConnection
import tk.hack5.ktelegram.core.crypto.AuthKey
import tk.hack5.ktelegram.core.crypto.RSAEncoder
import tk.hack5.ktelegram.core.crypto.RSAEncoderImpl
import tk.hack5.ktelegram.core.encoder.EncryptedMTProtoEncoder
import tk.hack5.ktelegram.core.encoder.MTProtoEncoder
import tk.hack5.ktelegram.core.encoder.PlaintextMTProtoEncoder
import tk.hack5.ktelegram.core.state.MTProtoStateImpl
import tk.hack5.ktelegram.core.toByteArray
import tk.hack5.ktelegram.core.toIntArray

private const val tag = "TelegramClient"

abstract class TelegramClient {
    internal abstract val secureRandom: SecureRandom
    abstract val apiId: String
    abstract val apiHash: String
    abstract suspend fun connect(
        connection: (String, Int) -> Connection = ::TcpFullConnection,
        plaintextEncoder: MTProtoEncoder = PlaintextMTProtoEncoder(MTProtoStateImpl()),
        encryptedEncoder: MTProtoEncoder = EncryptedMTProtoEncoder(plaintextEncoder.state),
        rsaEncoder: RSAEncoder = RSAEncoderImpl
    )

    internal abstract suspend fun <R : TLObject<*>> send(request: TLMethod<R>, encoder: MTProtoEncoder): R
}

open class TelegramClientImpl(override val apiId: String, override val apiHash: String) : TelegramClient() {
    override var secureRandom = SecureRandom()
    protected var connection: Connection? = null
    protected var encoder: MTProtoEncoder? = null
    protected var rsaEncoder: RSAEncoder? = null
    protected var authKey: AuthKey? = null

    override suspend fun connect(
        connection: (String, Int) -> Connection,
        plaintextEncoder: MTProtoEncoder,
        encryptedEncoder: MTProtoEncoder,
        rsaEncoder: RSAEncoder
    ) {
        connection("149.154.167.51", 80).let {
            this.connection = it
            it.connect()
            authKey = authenticate(this, plaintextEncoder)
        }
    }

    override suspend fun <R : TLObject<*>> send(request: TLMethod<R>, encoder: MTProtoEncoder): R {
        connection!!.send(encoder.encode(request.toTlRepr().toByteArray()))
        val response = encoder.decode(connection!!.recv()).toIntArray()
        println(response.contentToString())
        return request.constructor.fromTlRepr(response)!!.second
    }
}