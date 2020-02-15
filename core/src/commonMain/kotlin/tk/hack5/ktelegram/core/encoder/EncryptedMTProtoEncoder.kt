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
import tk.hack5.ktelegram.core.LongObject
import tk.hack5.ktelegram.core.asTlObject
import tk.hack5.ktelegram.core.crypto.AES
import tk.hack5.ktelegram.core.crypto.AESMode
import tk.hack5.ktelegram.core.crypto.AuthKey
import tk.hack5.ktelegram.core.state.MTProtoState
import tk.hack5.ktelegram.core.toByteArray
import tk.hack5.ktelegram.core.toIntArray
import kotlin.random.Random

class EncryptedMTProtoEncoder(
    state: MTProtoState,
    val authKey: AuthKey,
    val aesConstructor: (AESMode, ByteArray) -> AES
) : MTProtoEncoder(state) {
    val authKeyId = authKey.keyId.asTlObject().toTlRepr().toByteArray()

    private fun calcKey(msgKey: ByteArray, client: Boolean): Pair<ByteArray, ByteArray> {
        val x = if (client) 0 else 8
        val sha256a = (msgKey + authKey.key.sliceArray(x until x + 36)).sha256()
        val sha256b = (authKey.key.sliceArray(x + 40 until x + 76) + msgKey).sha256()

        val key = sha256a.sliceArray(0 until 8) + sha256b.sliceArray(8 until 24) + sha256a.sliceArray(24 until 32)
        val iv = sha256b.sliceArray(0 until 8) + sha256a.sliceArray(8 until 24) + sha256b.sliceArray(24 until 32)
        return Pair(key, iv)
    }

    override fun encode(data: ByteArray): ByteArray {
        val internalHeader =
            state.salt + state.sessionId + state.getMsgId().asTlObject().toTlRepr().toByteArray() + (state.seq++ * 2).asTlObject().toTlRepr().toByteArray()
        val paddingLength = (-internalHeader.size - data.size - 12) % 16 + 12
        val fullData = internalHeader + data + Random.nextBytes(paddingLength)
        val msgKey = (authKey.key.sliceArray(88 until 120) + fullData).sha256().sliceArray(8 until 24)
        val aesKey = calcKey(msgKey, true)
        val encrypted = aesConstructor(AESMode.ENCRYPT, aesKey.first).doIGE(aesKey.second, fullData)
        return authKeyId + msgKey + encrypted
    }

    override fun decode(data: ByteArray): ByteArray {
        require(data.size >= 8) { "Data too small" }
        require(data.sliceArray(0 until 8).contentEquals(authKeyId))
        val msgKey = data.sliceArray(8 until 24)
        val aesKey = calcKey(msgKey, false)
        val decrypted =
            aesConstructor(AESMode.DECRYPT, aesKey.first).doIGE(aesKey.second, data.sliceArray(24 until data.size))
        require((authKey.key.sliceArray(88 until 120) + decrypted).sha256().sliceArray(8 until 24).contentEquals(msgKey)) { "Invalid msgKey" }
        require(decrypted.sliceArray(0 until 8).contentEquals(state.salt)) { "Invalid salt" }
        require(decrypted.sliceArray(8 until 16).contentEquals(state.sessionId)) { "Invalid sessionId" }
        require(state.validateMsgId(LongObject.fromTlRepr(decrypted.sliceArray(16 until 24).toIntArray())!!.second.native)) { "Invalid msgId" }
        require(decrypted.sliceArray(24 until 32)) // TODO

    }
}