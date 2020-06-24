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

import tk.hack5.telekat.core.client.TelegramClient
import tk.hack5.telekat.core.tl.*

interface ChatGetter {
    val chatPeer: PeerType
    val client: TelegramClient

    suspend fun getChat(): Peer = chatPeer.let {
        when (it) {
            is PeerUserObject -> PeerUser(client(Users_GetUsersRequest(
                listOf(InputUserObject(it.userId, it.toInputPeer(client)!!.accessHash))
            )).single() as UserObject)
            is PeerChatObject -> PeerChat((client(Messages_GetChatsRequest(
                listOf(it.chatId)
            )) as Messages_ChatsObject).chats.single() as ChatObject)
            is PeerChannelObject -> PeerChannel((client(Channels_GetChannelsRequest(
                listOf(InputChannelObject(it.channelId, it.toInputPeer(client)!!.accessHash))
            )) as Messages_ChatsObject).chats.single() as ChannelObject)
        }
    }
    suspend fun getInputChat(): InputPeerType = chatPeer.toInputPeer(client)!!
}