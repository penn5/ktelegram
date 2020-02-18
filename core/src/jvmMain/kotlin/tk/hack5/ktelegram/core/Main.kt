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

import com.github.aakira.napier.DebugAntilog
import com.github.aakira.napier.Napier
import kotlinx.coroutines.debug.DebugProbes
import kotlinx.coroutines.runBlocking
import tk.hack5.ktelegram.core.client.TelegramClientImpl
import tk.hack5.ktelegram.core.tl.InputPeerEmptyObject
import tk.hack5.ktelegram.core.tl.Messages_GetDialogsRequest

fun main() {
    DebugProbes.install()
    Napier.base(DebugAntilog())
    println(Messages_GetDialogsRequest(false, null, 0, 0, InputPeerEmptyObject(), 100, 0).toTlRepr().map { it.toUInt() }.joinToString())
    amain()
}

fun amain() = runBlocking {
    val client = TelegramClientImpl("596386", "e142e0a65a50b707fa539ac91db2de16")
    /*thread(start=true) {
        sleep(7500)
//        DebugProbes.dumpCoroutines()
        DebugProbes.dumpCoroutines()
    }*/
    client.connect()
}