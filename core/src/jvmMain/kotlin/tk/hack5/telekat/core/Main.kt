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

package tk.hack5.telekat.core

import com.github.aakira.napier.DebugAntilog
import com.github.aakira.napier.Napier
import kotlinx.coroutines.runBlocking
import tk.hack5.telekat.core.client.TelegramClientImpl
import tk.hack5.telekat.core.state.JsonSession
import tk.hack5.telekat.core.state.invoke
import tk.hack5.telekat.core.tl.*
import java.io.File
import kotlin.random.Random

private fun main() {
//    System.setProperty(DEBUG_PROPERTY_NAME, "on")
    Napier.base(DebugAntilog())
//    println(Messages_GetDialogsRequest(false, null, 0, 0, InputPeerEmptyObject(), 100, 0).toTlRepr().map { it.toUInt() }.joinToString())
    amain()
}

private fun amain() = runBlocking {
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
    val dialogs = client(
        Messages_GetDialogsRequest(
            false,
            null,
            0,
            0,
            InputPeerEmptyObject(),
            100,
            0
        )
    ) as Messages_DialogsSliceObject
    println("===================")
    println(dialogs.chats.first())
    println(dialogs)
    val dialog = dialogs.dialogs.mapNotNull { dialog ->
        when ((dialog as DialogObject).peer) {
            is PeerUserObject -> {
                null/*
                val userId = (dialog.peer as PeerUserObject).userId
                val user = dialogs.users.single { (it as? UserObject)?.id == userId } as UserObject
                InputPeerUserObject(user.id, user.accessHash!!)
            */
            }
            is PeerChannelObject -> {
                val channelId = (dialog.peer as PeerChannelObject).channelId
                val channel = dialogs.chats.single { (it as? ChannelObject)?.id == channelId } as ChannelObject
                if (channel.title != "MTProto Devs") return@mapNotNull null
                InputPeerChannelObject(channel.id, channel.accessHash!!)
            }
            is PeerChatObject -> {
                val chatId = (dialog.peer as PeerChatObject).chatId
                val chat = dialogs.chats.single { (it as? ChatObject)?.id == chatId } as ChatObject
                if (chat.title != "MTProto Devs") return@mapNotNull null
                InputPeerChatObject(chat.id)
            }
        }
    }
    println(dialog)
    val update = client(
        Messages_SendMessageRequest(
            false,
            false,
            false,
            false,
            dialog.first(),
            null,
            dialog.toString(),
            Random.nextLong()
        )
    )
    println(update)
    client.disconnect()
}