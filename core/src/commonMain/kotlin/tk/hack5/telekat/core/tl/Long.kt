/*
 *     TeleKat (Telegram MTProto client library)
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

package tk.hack5.telekat.core.tl

fun Long.asTlObject() = LongObject(this, true)

class LongObject(private val long: Long, override val bare: Boolean) :
    TLObject<Long> {
    @ExperimentalUnsignedTypes
    override fun _toTlRepr(): IntArray {
        val firstByte = long.toULong().toInt()
        val secondByte = long.toULong().shr(UInt.SIZE_BITS).toUInt().toInt()
        return intArrayOf(firstByte, secondByte)
    }

    override val native = long

    override val _id = id

    override val fields by lazy { mapOf<String, TLObject<*>>() }

    companion object :
        TLConstructor<LongObject> {
        @ExperimentalUnsignedTypes
        override fun _fromTlRepr(data: IntArray): Pair<Int, LongObject>? {
            return Pair(
                2,
                LongObject(
                    (data[1].toUInt().toULong().shl(UInt.SIZE_BITS) + data[0].toUInt().toULong()).toLong(),
                    true
                )
            )
        }

        override val id: Int? = null
    }
}