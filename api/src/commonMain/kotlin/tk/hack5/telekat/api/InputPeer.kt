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
import tk.hack5.telekat.core.updates.ObjectType

fun ChannelObject.toInputPeer(): InputPeerChannelObject = InputPeerChannelObject(id, accessHash!!)
suspend fun PeerChannelObject.toInputPeer(client: TelegramClient): InputPeerChannelObject? {
    return InputPeerChannelObject(id, client.getAccessHash(ObjectType.CHANNEL, id) ?: return null)
}

fun ChatObject.toInputPeer(): InputPeerChatObject = InputPeerChatObject(id)
fun PeerChatObject.toInputPeer(): InputPeerChatObject = InputPeerChatObject(chatId)
fun UserObject.toInputPeer(): InputPeerUserObject = InputPeerUserObject(id, accessHash!!)
suspend fun PeerUserObject.toInputPeer(client: TelegramClient): InputPeerUserObject? {
    return InputPeerUserObject(id, client.getAccessHash(ObjectType.USER, id) ?: return null)
}

suspend fun PeerType.toInputPeer(client: TelegramClient): InputPeerType? = when (this) {
    is PeerUserObject -> toInputPeer(client)
    is PeerChatObject -> toInputPeer()
    is PeerChannelObject -> toInputPeer(client)
}

fun Messages_DialogsType.getInputPeer(dialog: DialogObject): InputPeerType {
    val id = dialog.peer.id
    return when (this) {
        is Messages_DialogsObject -> when (dialog.peer) {
            is PeerUserObject -> (users.first { (it as? UserObject)?.id == id } as UserObject).toInputPeer()
            is PeerChannelObject -> (chats.first { (it as? ChannelObject)?.id == id } as ChannelObject).toInputPeer()
            is PeerChatObject -> InputPeerChatObject(id)
        }
        is Messages_DialogsSliceObject -> when (dialog.peer) {
            is PeerUserObject -> (users.first { (it as? UserObject)?.id == id } as UserObject).toInputPeer()
            is PeerChannelObject -> (chats.first { (it as? ChannelObject)?.id == id } as ChannelObject).toInputPeer()
            is PeerChatObject -> InputPeerChatObject(id)
        }
        is Messages_DialogsNotModifiedObject -> TODO("dialogs caching")
    }
}

fun InputPeerUserObject.toInputUser() = InputUserObject(userId, accessHash)