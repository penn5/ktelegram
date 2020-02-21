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

import tk.hack5.telekat.api.iter.iter
import tk.hack5.telekat.core.client.TelegramClient
import tk.hack5.telekat.core.tl.*

suspend fun TelegramClient.getDialogs(
    offsetId: Int = 0,
    offsetDate: Int = 0,
    excludePinned: Boolean = false,
    folderId: Int? = null
) =
    iter<DialogType, InputPeerType> { input ->
        when (val dialogs = this(
            Messages_GetDialogsRequest(
                excludePinned, folderId, offsetDate, offsetId,
                input ?: InputPeerEmptyObject(), 128, 0
            )
        )) {
            is Messages_DialogsSliceObject -> {
                val nonFolders = dialogs.dialogs.dropLastWhile { it !is DialogObject }
                    .let { if (it.isEmpty()) dialogs.dialogs else it }
                Pair(nonFolders, dialogs.getInputPeer(dialogs.dialogs.last { it is DialogObject } as DialogObject))
            }
            is Messages_DialogsObject -> {
                Pair(dialogs.dialogs, null)
            }
            is Messages_DialogsNotModifiedObject -> error("dialog hash NI")
        }
    }