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

import tk.hack5.telekat.core.tl.*

val PeerType.id
    get() = when (this) {
        is PeerUserObject -> userId
        is PeerChatObject -> chatId
        is PeerChannelObject -> channelId
    }

sealed class Peer(val id: Int, val inputPeer: InputPeerType)

data class PeerUser(val user: UserObject) :
    Peer(user.id, user.toInputPeer())

data class PeerChat(val chat: ChatObject) : Peer(chat.id, chat.toInputPeer())
data class PeerChannel(val channel: ChannelObject) : Peer(channel.id, channel.toInputPeer())

fun UserObject.toPeer() = PeerUser(this)
fun ChatObject.toPeer() = PeerChat(this)
fun ChannelObject.toPeer() = PeerChannel(this)

fun ChatType.toPeer() = when (this) {
    is ChatObject -> this.toPeer()
    is ChannelObject -> this.toPeer()
    is ChatEmptyObject -> null
    is ChatForbiddenObject -> null
    is ChannelForbiddenObject -> null
}

fun Messages_DialogsType.getPeer(dialog: DialogObject): Peer {
    val id = dialog.peer.id
    return when (this) {
        is Messages_DialogsObject -> when (dialog.peer) {
            is PeerUserObject -> (users.first { (it as? UserObject)?.id == id } as UserObject).toPeer()
            is PeerChannelObject -> (chats.first { (it as? ChannelObject)?.id == id } as ChannelObject).toPeer()
            is PeerChatObject -> (chats.first { (it as? ChatObject)?.id == id } as ChatObject).toPeer()
        }
        is Messages_DialogsSliceObject -> when (dialog.peer) {
            is PeerUserObject -> (users.first { (it as? UserObject)?.id == id } as UserObject).toPeer()
            is PeerChannelObject -> (chats.first { (it as? ChannelObject)?.id == id } as ChannelObject).toPeer()
            is PeerChatObject -> (chats.first { (it as? ChatObject)?.id == id } as ChatObject).toPeer()
        }
        is Messages_DialogsNotModifiedObject -> TODO("dialogs caching")
    }
}