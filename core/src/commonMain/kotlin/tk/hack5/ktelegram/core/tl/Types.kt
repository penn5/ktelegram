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

package tk.hack5.ktelegram.core.tl

internal fun ByteArray.toIntArray(): IntArray {
    if (size % Int.SIZE_BYTES != 0)
        error("Bytes must be padded")
    return (0 until size / Int.SIZE_BYTES).map { sliceArray(it * Int.SIZE_BYTES until (it + 1) * Int.SIZE_BYTES) }
        .map { it.toInt() }.toIntArray()
}

internal fun IntArray.toByteArray(): ByteArray {
    return map { it.toByteArray().toList() }.flatten().toByteArray()
}

internal fun ByteArray.toInt(): Int {
    var ret = 0
    for (i in indices) {
        ret += this[i].toInt().and(0xFF).shl(i * Byte.SIZE_BITS)
    }
    return ret
}

internal fun Int.toByteArray(size: Int = Int.SIZE_BYTES): ByteArray {
    return (0 until size).map { ushr((it) * Byte.SIZE_BITS)
        .and(0xFF).toByte() }.toByteArray()
}