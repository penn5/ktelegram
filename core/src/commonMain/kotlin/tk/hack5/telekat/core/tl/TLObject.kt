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

@file:Suppress("PropertyName", "FunctionName")

package tk.hack5.telekat.core.tl

interface TLObject<N> {
    val bare: Boolean
    fun _toTlRepr(): IntArray
    @Suppress("EXPERIMENTAL_API_USAGE") // @UseExperimental is experimental itself
                                                // and the -Xuse-experimental doesn't work properly
    fun toTlRepr(): IntArray = if (!bare) intArrayOf(_id?.toInt() ?:
        error("Boxed serialization not possible on bare types")) + _toTlRepr() else _toTlRepr()
    fun asTlObject() = this
    val native: N

    @ExperimentalUnsignedTypes
    val _id: UInt?
    /*
    Sample implementation:
    @ExperimentalUnsignedTypes
    override val _id: UInt?
        get() = Companion._id
    If you don't use a getter, it crashes with an obscure ClassCastException. No idea why.
     */
}

interface TLConstructor<T : TLObject<*>> {
    fun _fromTlRepr(data: IntArray): Pair<Int, T>?
    @Suppress("EXPERIMENTAL_API_USAGE") // @UseExperimental is experimental itself
                                                // and the -Xuse-experimental doesn't work properly
    fun fromTlRepr(data: IntArray, bare: Boolean = false): Pair<Int, T>? {
        // the Int is the count of bytes consumed, for Vectors
        if (!bare && _id != null) {
            if (data[0] != _id?.toInt())
                return null
            return _fromTlRepr(data.sliceArray(1 until data.size))?.let {
                Pair(it.first + 1, it.second)
            }
        } else return _fromTlRepr(data)
    }
    @ExperimentalUnsignedTypes
    val _id: UInt?
}

interface TLMethod<R : TLObject<*>> :
    TLObject<TLMethod<R>> {
    @ExperimentalUnsignedTypes
    override val _id: UInt

    val constructor: TLConstructor<R>

    @Suppress("UNCHECKED_CAST")
    fun castResult(result: TLObject<Any?>) = result as R
}