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

package tk.hack5.telekat.core.crypto

import com.soywiz.krypto.sha1
import kotlinx.serialization.Serializable
import org.gciatto.kt.math.BigInteger
import tk.hack5.telekat.core.tl.BigIntegerSerializer
import tk.hack5.telekat.core.tl.LongObject
import tk.hack5.telekat.core.tl.toIntArray
import tk.hack5.telekat.core.utils.pad

@Serializable
data class AuthKey(@Serializable(with = BigIntegerSerializer::class) private val data: BigInteger) {
    val key = data.toByteArray().pad(256)
    val auxHash: Long
    val keyId: ByteArray

    init {
        println("key.size=${key.size}")
        val hash = key.sha1()
        println(hash)
        auxHash = LongObject.fromTlRepr(hash.toIntArray())!!.second.native // 64 high order bits
        keyId = hash.sliceArray(12 until 20) // 64 low order bits
    }
}