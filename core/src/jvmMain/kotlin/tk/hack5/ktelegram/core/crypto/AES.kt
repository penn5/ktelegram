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