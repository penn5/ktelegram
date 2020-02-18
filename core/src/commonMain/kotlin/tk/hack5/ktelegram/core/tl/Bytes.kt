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

fun ByteArray.asTlObject() = BytesObject(this, true)

class BytesObject(private val bytes: ByteArray, override val bare: Boolean) :
    TLObject<ByteArray> {
    @ExperimentalUnsignedTypes
    override fun _toTlRepr(): IntArray {
        return if (bytes.size >= 254) {
            val len = bytes.size.toByteArray(3)
            byteArrayOf(0xFE.toByte(), *len, *bytes)
        } else {
            byteArrayOf(bytes.size.toUByte().toByte(), *bytes)
        }.pad().toIntArray()
    }

    override val native = bytes

    @ExperimentalUnsignedTypes
    override val _id = Companion._id

    companion object :
        TLConstructor<BytesObject> {
        @ExperimentalUnsignedTypes
        override fun _fromTlRepr(data: IntArray): Pair<Int, BytesObject>? {
            val arr = data.toByteArray()
            val off: Int
            val len = if (arr[0] != 0xFE.toByte()) {
                off = 1
                arr[0].toUByte().toInt()
            } else {
                off = 4
                byteArrayOf(arr[1], arr[2], arr[3]).toInt()
            }
            return Pair(
                (off + len + 3) / 4,
                BytesObject(arr.sliceArray(off until off + len), true)
            )
        }

        @ExperimentalUnsignedTypes
        override val _id: UInt? = null
    }
}

private fun ByteArray.pad(multiple: Int = 4, padding: Byte = 0): ByteArray = this +
        ByteArray((multiple - (size % multiple)) % multiple) { padding }
