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
import kotlinx.coroutines.debug.DebugProbes
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import tk.hack5.telekat.core.state.JsonSession
import tk.hack5.telekat.core.state.invoke
import tk.hack5.telekat.core.tl.Messages_EditMessageRequest
import java.io.File

@ExperimentalCoroutinesApi
fun main(): Unit = runBlocking {
    DebugProbes.install()
    System.setProperty("java.util.logging.SimpleFormatter.format", "[%1\$tT.%1\$tL] [%4$-7s] %5\$s %n")
    Napier.base(DebugAntilog())
    val client =
        TelegramClientApiImpl(
            "596386",
            "e142e0a65a50b707fa539ac91db2de16",
            session = JsonSession(File("telekat.json")),
            maxFloodWait = 15,
            parentScope = this
        )
    /*client.updateCallbacks += { or ->
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
    }*/
    client.eventCallbacks += {
        println(it)
        when (it) {
            is NewMessage.NewMessageEvent -> {
                if (it.out) {
                    if (it.message == ".ping") {
                        val update = client(Messages_EditMessageRequest(false, it.getInputChat(), it.id, "Pong"))
                        client.sendUpdate(update)
                    }
                }
            }
            is EditMessage.EditMessageEvent -> println(it)
            is RawUpdate.RawUpdateEvent -> println(it)
        }
    }
    println(client.start(
        phoneNumber = {
            print("Phone Number: ")
            readLine()!!
        },
        signUpConsent = { Pair("test", "account") },
        phoneCode = {
            print("Enter the code you received: ")
            readLine()!!
        }, password = {
            print("Enter your password: ")
            System.console().readPassword().joinToString("")
        }
    ))
    client.catchUp()
    delay(30000)
    //DebugProbes.dumpCoroutines()
    //val dialogs = client.getDialogs()
    //println(dialogs.first())
    //client.sendMessage((dialogs.filter { (it as? DialogChat)?.peer?.fullName?.contains("Programmers") == true }.first() as DialogChat).peer, "hello from my new kotlin mtproto library")
    //println(client(Channels_GetChannelsRequest(listOf(InputChannelObject(channelId=1173753783, accessHash=-3214895137574953081)))))
    client.disconnect()
    Unit
}