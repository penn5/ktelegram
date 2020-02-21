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

fun ChannelObject.toInputPeer(): InputPeerChannelObject = InputPeerChannelObject(this.id, this.accessHash!!)

fun ChatObject.toInputPeer(): InputPeerChatObject = InputPeerChatObject(this.id)

fun UserObject.toInputPeer(): InputPeerUserObject = InputPeerUserObject(this.id, this.accessHash!!)

fun Messages_DialogsType.getInputPeer(dialog: DialogObject): InputPeerType {
    val id = dialog.peer.getId()
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