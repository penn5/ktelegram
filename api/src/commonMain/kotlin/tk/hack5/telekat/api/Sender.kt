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
import tk.hack5.telekat.core.tl.InputPeerUserObject
import tk.hack5.telekat.core.tl.PeerUserObject
import tk.hack5.telekat.core.tl.UserObject
import tk.hack5.telekat.core.tl.Users_GetUsersRequest

interface SenderGetter {
    val senderId: Int?
    val client: TelegramClient

    suspend fun getSender(): UserObject? = getInputSender()?.let {
        client(Users_GetUsersRequest(listOf(it.toInputUser()))).single() as UserObject
    }

    suspend fun getInputSender(): InputPeerUserObject? = senderId?.let {
        PeerUserObject(it).toInputPeer(client)
    }
}