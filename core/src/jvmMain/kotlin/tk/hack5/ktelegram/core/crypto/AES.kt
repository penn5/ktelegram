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

package tk.hack5.ktelegram.core.crypto

import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec
import kotlin.experimental.xor

// https://stackoverflow.com/questions/17797582/java-aes-256-decrypt-with-ige

actual class AESPlatformImpl actual constructor(mode: AESMode, key: ByteArray) : AESBaseImpl(mode, key) {
    private val cipher = Cipher.getInstance("AES/ECB/NoPadding")!!

    init {
        val cipherMode = when (mode) {
            AESMode.DECRYPT -> Cipher.DECRYPT_MODE
            AESMode.ENCRYPT -> Cipher.ENCRYPT_MODE
        }
        cipher.init(cipherMode, SecretKeySpec(key, "AES"))
    }

    override fun doECB(data: ByteArray) = cipher.doFinal(data)!!

    override val blockSize = cipher.blockSize
}


fun decryptAesIge(key: ByteArray, iv: ByteArray, message: ByteArray): ByteArray {
    val cipher = Cipher.getInstance("AES/ECB/NoPadding")
    cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"))
    val blocksize = cipher.blockSize
    var xPrev = iv.copyOfRange(0, blocksize)
    var yPrev = iv.copyOfRange(blocksize, iv.size)
    var decrypted = ByteArray(0)
    var y: ByteArray
    var x: ByteArray
    var i = 0
    while (i < message.size) {
        x = message.copyOfRange(i, i + blocksize)
        y = xor(cipher.doFinal(xor(x, yPrev)), xPrev)
        xPrev = x
        yPrev = y
        decrypted += y
        i += blocksize
    }
    return decrypted
}

private fun xor(a: ByteArray, b: ByteArray): ByteArray {
    require (a.size == b.size) { "Invalid sizes" }
    return a.mapIndexed { index, byte -> byte.xor(b[index]) }.toByteArray()
}