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

import org.gciatto.kt.math.BigInteger
import tk.hack5.ktelegram.core.tl.VectorObject
import tk.hack5.ktelegram.core.tl.asTlObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@ExperimentalUnsignedTypes
class ReprStringTest {
    private fun testTlString(text: String, correct: IntArray? = null) {
        val input = text.asTlObject().toTlRepr()
        if (correct != null)
            assertTrue(correct.contentEquals(input), "serialization ${input.contentToString()} didn't match correct")
        println(input.contentToString())
        val ret = StringObject.fromTlRepr(input, true)
        println(ret?.second?.native)
        assertEquals(text, ret!!.second.native, "deserialized to ${input.contentToString()}")
    }

    @Test
    fun tlStringsTest() {
        testTlString("hi.")
        testTlString("hello world")
    }

    @Test
    fun conversionTest() {
        val input = "abc".asTlObject().toTlRepr()
        assertTrue(input.contentEquals(intArrayOf(1667391747)), input.contentToString())
        val output = BytesObject.fromTlRepr(intArrayOf(1667391747))!!.second.native
        assertTrue(output.contentEquals(byteArrayOf(97, 98, 99)), output.contentToString())
    }
/*
    @Test
    fun stringPaddingTest() {
        val ret = "hi.".toByteArray().pad()
        assertEquals(ret.size, 4, ret.contentToString())
        assertTrue(ret.contentEquals(byteArrayOf(104, 105, 46, 0)), ret.contentToString())
    }
*/
    @Test
    fun longStringTest() {
        testTlString("a".repeat(200))
        val correct = intArrayOf(82430, 875770417, 943142453, 875770417, 943142453, 875770417, 943142453, 875770417, 943142453, 875770417, 943142453, 875770417, 943142453, 875770417, 943142453, 875770417, 943142453, 875770417, 943142453, 875770417, 943142453, 875770417, 943142453, 875770417, 943142453, 875770417, 943142453, 875770417, 943142453, 875770417, 943142453, 875770417, 943142453, 875770417, 943142453, 875770417, 943142453, 875770417, 943142453, 875770417, 943142453, 875770417, 943142453, 875770417, 943142453, 875770417, 943142453, 875770417, 943142453, 875770417, 943142453, 875770417, 943142453, 875770417, 943142453, 875770417, 943142453, 875770417, 943142453, 875770417, 943142453, 875770417, 943142453, 875770417, 943142453, 875770417, 943142453, 875770417, 943142453, 875770417, 943142453, 875770417, 943142453, 875770417, 943142453, 875770417, 943142453, 875770417, 943142453, 875770417, 943142453, 65)
    testTlString("12345678".repeat(40) + "A", correct)
    }
}

@ExperimentalUnsignedTypes
class ReprDoubleTest {
    private fun testDouble(double: Double) {
        val input = double.asTlObject().toTlRepr()
        val ret = DoubleObject.fromTlRepr(input)
        assertEquals(double, ret!!.second.native, "deserialized to $input")
    }

    @Test
    fun bareDoubleTest() {
        testDouble(123.4)
        testDouble(0.0)
        testDouble(Double.NaN)
        testDouble(-12.3)
        testDouble(Double.MAX_VALUE)
        testDouble(Double.MIN_VALUE)
        testDouble(Double.MAX_VALUE / 2)
        testDouble(Double.MIN_VALUE / 2)
    }
}

@ExperimentalUnsignedTypes
class ReprLongTest {
    private fun testLong(long: Long, expected: IntArray? = null) {
        val bytes = long.asTlObject().toTlRepr()
        println(bytes.contentToString())
        assertTrue(expected?.contentEquals(bytes) ?: true, "serialization ${bytes.contentToString()} didn't match correct")
        val ret = LongObject.fromTlRepr(bytes)
        assertEquals(ret!!.second.native, long)
    }

    @Test
    fun bareLongTest() {
        testLong(0L, intArrayOf(0, 0))
        testLong(255L, intArrayOf(255, 0))
        testLong(Long.MAX_VALUE / 2, intArrayOf(-1, 1073741823))
        println(Long.MIN_VALUE)
        testLong(Long.MIN_VALUE / 2, intArrayOf(0, -1073741824))
        testLong(Long.MAX_VALUE, intArrayOf(-1, 2147483647))
        testLong(Long.MIN_VALUE, intArrayOf(0, -2147483648))
    }
}

@ExperimentalUnsignedTypes
class VectorTest {
    private fun <G : TLObject<*>>testVector(input: Collection<G>, bare: Boolean, constructor: TLConstructor<G>?, expected: IntArray? = null) {
        val bytes = input.asTlObject(bare).toTlRepr()
        assertTrue(expected?.contentEquals(bytes) ?: true, "serialization ${bytes.contentToString()} didn't match correct")
        val new = VectorObject.fromTlRepr(bytes, bare, constructor)
        assertEquals(input.map { it.native }.toList(), new?.second?.native?.map { it.native }?.toList()!!, "deserialization of ${bytes.contentToString()} didn't match")
    }

    @Test
    fun testEmptyVector() {
        testVector(listOf(), true, IntObject, intArrayOf(0))
        testVector(listOf(0.asTlObject()), false, IntObject, intArrayOf(VectorObject._id.toInt(), 1, 0))
    }
}

class BigIntTest {
    private fun test128(input: BigInteger, expected: IntArray? = null) {
        val bytes = input.asTlObject128().toTlRepr()
        assertTrue(expected?.contentEquals(bytes) ?: true, "serialization ${bytes.contentToString()} didn't match correct")
        print("bytes=")
        println(bytes.contentToString())
        val new = Int128Object.fromTlRepr(bytes)
        assertEquals(input, new!!.second.native, "deserialized from ${bytes.contentToString()}")
    }

    private fun test256(input: BigInteger, expected: IntArray? = null) {
        val bytes = input.asTlObject256().toTlRepr()
        assertTrue(expected?.contentEquals(bytes) ?: true, "serialization ${bytes.contentToString()} didn't match correct")
        println(bytes.contentToString())
        val new = Int256Object.fromTlRepr(bytes)
        assertEquals(input, new!!.second.native, "deserialized from ${bytes.contentToString()}")
    }

    @Test
    fun testInt128() {
        test128(BigInteger.of(-0xfff0), intArrayOf(-65520, -1, -1, -1))
        test128(-BigInteger.TEN, intArrayOf(-10, -1, -1, -1))
        test128(BigInteger.ZERO, intArrayOf(0, 0, 0, 0))
        test128(BigInteger.TEN, intArrayOf(10, 0, 0, 0))
    }

    @Test
    fun testInt256() {
        test256(BigInteger.of(-0xfff0))
        test256(-BigInteger.TEN, intArrayOf(-10, -1, -1, -1, -1, -1, -1, -1))
        test256(BigInteger.ZERO, intArrayOf(0, 0, 0, 0, 0, 0, 0, 0))
        test256(BigInteger.TEN, intArrayOf(10, 0, 0, 0, 0, 0, 0, 0))
    }
}