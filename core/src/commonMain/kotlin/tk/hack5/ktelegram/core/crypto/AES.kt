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

import kotlin.experimental.xor
import kotlin.random.Random

interface AESInterface {
    val mode: AESMode
    val key: ByteArray

    fun doECB(data: ByteArray): ByteArray

    fun doIGE(iv: ByteArray, input: ByteArray, secureRandom: Random? = null): ByteArray

    val blockSize: Int
}

abstract class AES(override val mode: AESMode, override val key: ByteArray): AESInterface

/**
 * This class provides IGE abstractions over ECB, to simplify platform implementations where IGE is unavailable.
 * Platforms that don't want to use this less efficient implementation should override [doIGE]
 */
abstract class AESBaseImpl(mode: AESMode, key: ByteArray) : AES(mode, key) {
    override fun doIGE(iv: ByteArray, input: ByteArray, secureRandom: Random?): ByteArray {
        // https://github.com/LonamiWebs/Telethon/blob/7b4cd92/telethon/crypto/aes.py

        val data = if (mode == AESMode.ENCRYPT) {
            val padding = input.size % blockSize
            if (padding == 0) input else input + secureRandom!!.nextBytes(blockSize - padding)
        } else input

        val ivSplit = Pair(iv.sliceArray(0 until iv.size / 2), iv.sliceArray(iv.size / 2 until iv.size))

        var iv1: ByteArray
        var iv2: ByteArray
        when (mode) {
            AESMode.DECRYPT -> {
                iv1 = ivSplit.first
                iv2 = ivSplit.second
            }
            AESMode.ENCRYPT -> {
                iv1 = ivSplit.second
                iv2 = ivSplit.first
            }
        }

        val inputBlock = ByteArray(blockSize) { 0 }
        var outputBlock: ByteArray

        return (data.indices step blockSize).map { block ->
            inputBlock.indices.forEach { index -> inputBlock[index] = data[block + index].xor(iv2[index]) }

            outputBlock = doECB(inputBlock)

            outputBlock = outputBlock.mapIndexed { index, byte -> byte.xor(iv1[index]) }.toByteArray()

            iv1 = data.sliceArray(block until block + blockSize)
            iv2 = outputBlock

            outputBlock.toList()
        }.flatten().toByteArray()
    }
}

expect class AESPlatformImpl(mode: AESMode, key: ByteArray) : AESBaseImpl

enum class AESMode {
    ENCRYPT,
    DECRYPT
}