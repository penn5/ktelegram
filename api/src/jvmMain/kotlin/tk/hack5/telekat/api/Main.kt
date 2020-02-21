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

package tk.hack5.telekat.api

import kotlinx.coroutines.runBlocking
import tk.hack5.telekat.core.client.TelegramClientImpl
import tk.hack5.telekat.core.state.JsonSession
import tk.hack5.telekat.core.state.invoke
import java.io.File

fun main() = runBlocking {
    val client =
        TelegramClientImpl("596386", "e142e0a65a50b707fa539ac91db2de16", session = JsonSession(File("telekat.json")))
    println(client.start(
        phoneNumber = {
            print("Phone Number: ")
            readLine()!!
        }, signUpConsent = { Pair("test", "account") },
        phoneCode = {
            print("Enter the code you received: ")
            readLine()!!
        }, password = {
            print("Enter your password: ")
            System.console().readPassword().joinToString("")
        }
    ))
    println(client.getDialogs())
}