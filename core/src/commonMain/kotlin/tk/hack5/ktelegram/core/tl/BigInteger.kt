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

import kotlinx.serialization.Decoder
import kotlinx.serialization.Encoder
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializer
import kotlinx.serialization.internal.ByteArraySerializer
import kotlinx.serialization.internal.StringDescriptor
import org.gciatto.kt.math.BigInteger

fun BigInteger.asTlObject128(): Int128Object =
    Int128Object(this, true)

fun BigInteger.asTlObject256(): Int256Object =
    Int256Object(this, true)

class Int128Object(private val int128: BigInteger, override val bare: Boolean) :
    TLObject<BigInteger> {
    init {
        if (int128.bitLength >= 128)
            error("Cannot serialize integers with more than 128 bits (including sign) as an int128")
    }

    override fun _toTlRepr(): IntArray {
        val bigByteMask = BigInteger.of(0xFF)
        return ByteArray(16) {
            int128.shr(it * Byte.SIZE_BITS)
                .and(bigByteMask).toByte()
        }.toIntArray()
    }

    override val native = int128

    @ExperimentalUnsignedTypes
    override val _id = Companion._id

    companion object :
        TLConstructor<Int128Object> {
        @ExperimentalUnsignedTypes
        override val _id: UInt? = null

        override fun _fromTlRepr(data: IntArray): Pair<Int, Int128Object>? {
            if (data.size < 4)
                return null
            return Pair(
                4,
                Int128Object(
                    BigInteger(data.sliceArray(0 until 4).toByteArray().reversedArray()),
                    true
                )
            )
        }
    }

}

class Int256Object(private val int256: BigInteger, override val bare: Boolean) :
    TLObject<BigInteger> {
    init {
        if (int256.bitLength >= 256)
            error("Cannot serialize integers with more than 256 bits (including sign) as an int256")
    }

    override fun _toTlRepr(): IntArray {
        val bigByteMask = BigInteger.of(0xFF)
        return ByteArray(32) {
            int256.shr(it * Byte.SIZE_BITS)
                .and(bigByteMask).toByte()
        }.toIntArray()
    }

    override val native = int256

    @ExperimentalUnsignedTypes
    override val _id = Companion._id

    companion object :
        TLConstructor<Int128Object> {
        @ExperimentalUnsignedTypes
        override val _id: UInt? = null

        override fun _fromTlRepr(data: IntArray): Pair<Int, Int128Object>? {
            if (data.size < 8)
                return null
            return Pair(
                8,
                Int128Object(
                    BigInteger(data.sliceArray(0 until 4).toByteArray().reversedArray()),
                    true
                )
            )
        }
    }
}

fun BigInteger.toByteArray(size: Int): ByteArray {
    val ret = toByteArray()
    if (ret.size == size)
        return ret
    if (ret.size == size + 1) {
        require(ret[0] == 0.toByte())
        return ret.drop(1).toByteArray()
    }
    require(ret.size < size) { "Size $size is larger than ${ret.size} ($this=${ret.contentToString()})" }
    return ByteArray(size - ret.size) { 0 } + ret
}

@Serializer(forClass = BigInteger::class)
object BigIntegerSerializer : KSerializer<BigInteger> {
    override val descriptor = StringDescriptor
    override fun deserialize(decoder: Decoder): BigInteger {
        return BigInteger(ByteArraySerializer.deserialize(decoder))
    }

    override fun serialize(encoder: Encoder, obj: BigInteger) {
        ByteArraySerializer.serialize(encoder, obj.toByteArray())
    }
}