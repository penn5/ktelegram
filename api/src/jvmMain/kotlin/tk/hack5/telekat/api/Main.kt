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

import com.github.aakira.napier.DebugAntilog
import com.github.aakira.napier.Napier
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import tk.hack5.telekat.core.client.TelegramClientImpl
import tk.hack5.telekat.core.state.JsonSession
import tk.hack5.telekat.core.state.invoke
import tk.hack5.telekat.core.tl.MessageObject
import tk.hack5.telekat.core.tl.PeerChannelObject
import tk.hack5.telekat.core.tl.UpdateNewChannelMessageObject
import java.io.File

@ExperimentalCoroutinesApi
fun main(): Unit = runBlocking {
    //DebugProbes.install()
    Napier.base(DebugAntilog())
    val client =
        TelegramClientImpl(
            "596386",
            "e142e0a65a50b707fa539ac91db2de16",
            session = JsonSession(File("telekat.json")),
            maxFloodWait = 15
        )
    println(client.updateCallbacks)
    client.updateCallbacks += { or ->
        or.update?.let {
            println(it)
            val peer = (((it as? UpdateNewChannelMessageObject)?.message as? MessageObject)?.toId as? PeerChannelObject)
            if (peer?.channelId == 1330858993 && (it.message as? MessageObject)?.fromId != client.getInputMe().userId)
                client.sendMessage(
                    peer.toInputPeer(client)!!,
                    ((it.message as MessageObject).message.toInt() + 1).toString()
                )
            if (peer?.channelId == 1392066769)
                client.sendMessage(
                    peer.toInputPeer(client)!!,
                    "testing! sorry for any spam i do, its automated and i can't stop it for 60 seconds"
                )
        }
    }
    println(client.updateCallbacks)
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
    println("catching up")
    client.catchUp()
    println("catch up complete")
    delay(30000)
    //DebugProbes.dumpCoroutines()
    //val dialogs = client.getDialogs()
    //println(dialogs.first())
    //client.sendMessage((dialogs.filter { (it as? DialogChat)?.peer?.fullName?.contains("Programmers") == true }.first() as DialogChat).peer, "hello from my new kotlin mtproto library")
    client.disconnect()
    Unit
}