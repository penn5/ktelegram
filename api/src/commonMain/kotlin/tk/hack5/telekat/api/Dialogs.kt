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
    offsetPeer: InputPeerType = InputPeerEmptyObject(),
    excludePinned: Boolean = false,
    folderId: Int? = null
) =
    iter<Dialog, Triple<Int, Int, InputPeerType>> { input ->
        when (val dialogs = this(
            Messages_GetDialogsRequest(
                excludePinned, folderId, input?.first ?: offsetDate, input?.second ?: offsetId,
                input?.third ?: offsetPeer, 128, 0
            )
        )) {
            is Messages_DialogsSliceObject -> {
                val nonFolders = dialogs.dialogs.dropLastWhile { it !is DialogObject }
                    .let { if (it.isEmpty()) dialogs.dialogs else it }
                val lastMessage = dialogs.messages.minBy { it.date!! }!!
                val lastMessageId = getChatId(lastMessage)!!
                val lastDialog =
                    dialogs.dialogs.find { lastMessageId == (it as? DialogObject)?.peer?.id } as DialogObject?
                lastDialog!!
                Pair(
                    nonFolders.map { Dialog(it, dialogs) },
                    Triple(lastMessage.date!!, lastMessage.id, dialogs.getInputPeer(lastDialog))
                )
            }
            is Messages_DialogsObject -> {
                val nonFolders = dialogs.dialogs.dropLastWhile { it !is DialogObject }
                    .let { if (it.isEmpty()) dialogs.dialogs else it }
                Pair(nonFolders.map { Dialog(it, dialogs) }, null)
            }
            is Messages_DialogsNotModifiedObject -> error("dialog hash NI")
        }
    }

sealed class Dialog {
    abstract val dialog: DialogType

    companion object {
        operator fun invoke(dialog: DialogType, dialogs: Messages_DialogsType) = when (dialog) {
            is DialogObject -> DialogChat(
                dialog,
                dialogs.getPeer(dialog),
                dialogs.getInputPeer(dialog)
            )
            is DialogFolderObject -> DialogFolder(
                dialog,
                dialog.folder as FolderObject
            )
        }
    }
}

data class DialogChat(override val dialog: DialogType, val peer: Peer, val inputPeer: InputPeerType) : Dialog()
data class DialogFolder(override val dialog: DialogType, val folder: FolderObject) : Dialog()