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

package tk.hack5.ktelegram.core.encoder

import com.soywiz.krypto.sha256
import tk.hack5.ktelegram.core.crypto.AES
import tk.hack5.ktelegram.core.crypto.AESMode
import tk.hack5.ktelegram.core.crypto.AESPlatformImpl
import tk.hack5.ktelegram.core.mtproto.MessageObject
import tk.hack5.ktelegram.core.mtproto.ObjectObject
import tk.hack5.ktelegram.core.state.MTProtoState
import tk.hack5.ktelegram.core.tl.TLObject
import tk.hack5.ktelegram.core.tl.toByteArray
import tk.hack5.ktelegram.core.tl.toIntArray
import kotlin.random.Random

open class EncryptedMTProtoEncoder(
    state: MTProtoState,
    val aesConstructor: (AESMode, ByteArray) -> AES = ::AESPlatformImpl
) : MTProtoEncoderWrapped(state) {
    private val authKeyId = state.authKey!!.keyId

    private fun calcKey(msgKey: ByteArray, client: Boolean): Pair<ByteArray, ByteArray> {
        val x = if (client) 0 else 8
        val sha256a = (msgKey + state.authKey!!.key.sliceArray(x until x + 36)).sha256()
        val sha256b = (state.authKey!!.key.sliceArray(x + 40 until x + 76) + msgKey).sha256()

        val key = sha256a.sliceArray(0 until 8) + sha256b.sliceArray(8 until 24) + sha256a.sliceArray(24 until 32)
        val iv = sha256b.sliceArray(0 until 8) + sha256a.sliceArray(8 until 24) + sha256b.sliceArray(24 until 32)
        return Pair(key, iv)
    }

    override fun encode(data: ByteArray): ByteArray {
        val internalHeader =
            state.salt + state.sessionId
        val paddingLength = (-internalHeader.size - data.size - 12) % 16 + 12
        val fullData = internalHeader + data + Random.nextBytes(paddingLength)
        val msgKey = (state.authKey!!.key.sliceArray(88 until 120) + fullData).sha256().sliceArray(8 until 24)
        val aesKey = calcKey(msgKey, true)
        val encrypted = aesConstructor(AESMode.ENCRYPT, aesKey.first).doIGE(aesKey.second, fullData)
        return authKeyId + msgKey + encrypted
    }

    override fun encodeMessage(data: MessageObject): ByteArray = encode(data.toTlRepr().toByteArray())
    override fun wrapAndEncode(data: TLObject<*>, isContentRelated: Boolean): ByteArray {
        val seq = (if (isContentRelated) state.seq++ else state.seq) * 2 + if (isContentRelated) 1 else 0
        val encoded = data.toTlRepr().toByteArray()
        return encodeMessage(MessageObject(state.getMsgId(), seq, encoded.size, ObjectObject(data), bare = true))
    }

    override fun decode(data: ByteArray): ByteArray {
        require(data.size >= 8) { "Data too small" }
        require(data.sliceArray(0 until 8).contentEquals(authKeyId)) { "Invalid authKeyId" }
        val msgKey = data.sliceArray(8 until 24)
        val aesKey = calcKey(msgKey, false)
        val decrypted =
            aesConstructor(AESMode.DECRYPT, aesKey.first).doIGE(aesKey.second, data.sliceArray(24 until data.size))
        println(decrypted.contentToString())
        require(
            (state.authKey!!.key.sliceArray(96 until 128) + decrypted).sha256().sliceArray(8 until 24).contentEquals(
                msgKey
            )
        ) { "Invalid msgKey" }
        require(decrypted.sliceArray(0 until 8).contentEquals(state.salt) || state.salt.all { it == 0.toByte() }) { "Invalid salt" }
        require(decrypted.sliceArray(8 until 16).contentEquals(state.sessionId)) { "Invalid sessionId" }
        // We cannot validate the msgId yet if there's a container because the container contents will be lower
        println(decrypted.slice(16 until decrypted.size))
        return decrypted.sliceArray(16 until decrypted.size)
    }

    override fun decodeMessage(data: ByteArray): MessageObject =
        MessageObject.fromTlRepr(decode(data).toIntArray(), bare = true)!!.second
}