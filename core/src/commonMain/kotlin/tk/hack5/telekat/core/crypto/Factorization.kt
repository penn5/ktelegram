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

import org.gciatto.kt.math.BigInteger

// Reimplementation of https://github.com/habnabit/haberdasher/blob/master/clacks_crypto/src/asymm.rs#L114
// Thanks @habnabit for confirming stuff and helping out with learning Rust

// Would be inline but lint says its pointless
internal fun BigInteger.sqrtExact(): BigInteger {
    val ret = sqrt()
    if (ret.pow(2) < this)
        return ret + BigInteger.ONE
    return ret
}

fun factorize(pq: BigInteger): Pair<BigInteger, BigInteger> {
    var pqSqrt = pq.sqrtExact()
    while (true) {
        val pqSquareDiff = pqSqrt.pow(2) - pq
        if (pqSquareDiff == BigInteger.ZERO) error("pq must not be square")
        val pqSquareDiffRoot = pqSquareDiff.sqrtExact() // This could be made sqrt() but then its more effort to code and would probably end up slower anyway
        if (pqSquareDiffRoot + pqSqrt >= pq) error("the sqrt(ceil(sqrt(pq))^2-pq)+ceil(sqrt(pq)) must be < pq")
        if (pqSquareDiffRoot.pow(2) != pqSquareDiff) {
            // If pqSquareDiff was not a square number...
            pqSqrt += BigInteger.ONE
            continue // Make it bigger and try again
        }
        val p = pqSqrt + pqSquareDiffRoot
        val q = (pqSqrt - pqSquareDiffRoot).absoluteValue
        return if (p > q) Pair(q, p) else Pair(p, q)
    }
}