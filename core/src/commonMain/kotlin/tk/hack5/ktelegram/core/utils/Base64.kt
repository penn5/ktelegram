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

package tk.hack5.ktelegram.core.utils

/**
 * Decode the ByteArray from base64 into bytes
 * WARNING: this does not handle padding (=)
 * This is designed to run quickly, but won't work properly in a normal scenario
 * Only for use with RSA keys or other things that are already 4-byte aligned without padding
 */
val ByteArray.fromBase64: ByteArray
    get() {
        require(size % 4 == 0) { "Invalid input size" }
        return toList().chunked(4) {
            base64ToInt(it[0]).shl(18) +
                    base64ToInt(it[1]).shl(12) +
                    base64ToInt(it[2]).shl(6) +
                    base64ToInt(it[3])
        }.map {
            listOf(
                0xFF.and(it.shr(16)).toByte(),
                0xFF.and(it.shr(8)).toByte(),
                0xFF.and(it).toByte()
            )
        }.flatten().toByteArray()
    }

private fun base64ToInt(char: Byte) = when (char) {
    in 65..90 -> char - 65
    in 97..122 -> char - 71
    in 48..57 -> char + 4
    '+'.toByte() -> 62
    '/'.toByte() -> 63
    else -> error("Invalid character $char in input")
}