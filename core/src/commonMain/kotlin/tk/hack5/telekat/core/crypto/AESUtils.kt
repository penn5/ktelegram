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
import org.gciatto.kt.math.BigInteger
import tk.hack5.telekat.core.tl.asTlObject128
import tk.hack5.telekat.core.tl.asTlObject256
import tk.hack5.telekat.core.tl.toByteArray

fun generateKeyFromNonce(serverNonce: BigInteger, newNonce: BigInteger): Pair<ByteArray, ByteArray> {
    val serverBytes = serverNonce.asTlObject128().toTlRepr().toByteArray()
    val newBytes = newNonce.asTlObject256().toTlRepr().toByteArray()
    val hashes = Triple((newBytes + serverBytes).sha1(),
        (serverBytes + newBytes).sha1(),
        (newBytes + newBytes).sha1())
    return Pair(hashes.first + hashes.second.sliceArray(0 until 12), hashes.second.sliceArray(12 until 20) + hashes.third + newBytes.sliceArray(0 until 4))
}