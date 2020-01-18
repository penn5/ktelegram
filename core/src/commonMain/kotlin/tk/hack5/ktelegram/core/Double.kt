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

package tk.hack5.ktelegram.core

fun Double.asTlObject() = DoubleObject(this, true)

class DoubleObject(private val double: Double, override val bare: Boolean) : TLObject<Double> {
    @ExperimentalUnsignedTypes
    override fun _toTlRepr(): IntArray {
        return LongObject(double.toRawBits(), bare)._toTlRepr()
    }

    override val native = double

    @ExperimentalUnsignedTypes
    override val _id = Companion._id

    companion object : TLConstructor<DoubleObject> {
        @ExperimentalUnsignedTypes
        override fun _fromTlRepr(data: IntArray): Pair<Int, DoubleObject>? {
            val ret = LongObject._fromTlRepr(data)
            return ret?.let {
                Pair(it.first, DoubleObject(Double.fromBits(it.second.native), true))
            }
        }

        @ExperimentalUnsignedTypes
        override val _id: UInt? = null
    }
}