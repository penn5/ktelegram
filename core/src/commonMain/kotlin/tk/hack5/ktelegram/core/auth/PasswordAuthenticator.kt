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

package tk.hack5.ktelegram.core.auth

/*
import com.soywiz.krypto.SecureRandom
import com.soywiz.krypto.sha256
import org.gciatto.kt.math.BigInteger
import tk.hack5.ktelegram.core.tl.Account_PasswordObject
import tk.hack5.ktelegram.core.tl.PasswordKdfAlgoSHA256SHA256PBKDF2HMACSHA512iter100000SHA256ModPowObject
import tk.hack5.ktelegram.core.tl.toByteArray
import tk.hack5.ktelegram.core.utils.pad

fun hashPasswordKdfAlgoSHA256SHA256PBKDF2HMACSHA512iter100000SHA256ModPow(password: String, serverParams: Account_PasswordObject, algo: PasswordKdfAlgoSHA256SHA256PBKDF2HMACSHA512iter100000SHA256ModPowObject, secureRandom: SecureRandom) {
    // TODO https://github.com/korlibs/krypto/issues/12
    val g = algo.g
    val p = algo.p
    val k = (p.pad(256) + g.toByteArray(256)).sha256()
    val a = getSecureNonce(256, secureRandom)
    val gA = BigInteger.of(g).modPow(a, BigInteger(p))
    val x =
}

internal fun saltingHash(salt: ByteArray, data: ByteArray) {

}
TODO 2fa
*/