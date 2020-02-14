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

package tk.hack5.ktelegram.core.crypto

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals

class AESTests {
    @Test
    fun testIGE() {
        val key = byteArrayOf(53, -48, -52, -59, 127, -31, -60, 43, -44, -45, -119, 2, 40, 18, 77, 116, -76, -104, -98, 108, 49, 81, 1, 114, -20, -96, 107, 18, -41, 116, -50, -36)
        val iv = byteArrayOf(32, -72, -117, -11, -97, 33, -127, 108, 15, -84, 93, -31, -49, 32, 40, -38, -47, 72, 22, 29, -84, 19, 104, 68, 91, -21, 22, -25, 5, -31, -20, 105)

        val data = Random.Default.nextBytes(26)
        val encrypted = AESPlatformImpl(AESMode.ENCRYPT, key).doIGE(iv, data) { Random.nextBytes(it) }
        val decrypted = AESPlatformImpl(AESMode.DECRYPT, key).doIGE(iv, encrypted).slice(0 until 26)
        assertEquals(data.toList(), decrypted)
    }
}