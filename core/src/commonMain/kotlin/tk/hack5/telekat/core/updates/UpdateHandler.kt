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

package tk.hack5.telekat.core.updates

import tk.hack5.telekat.core.tl.*

interface UpdateHandler {
    fun handleUpdate(update: UpdatesType): Map<Long, Long>
}

class UpdateHandlerImpl : UpdateHandler {
    override fun handleUpdate(update: UpdatesType): Map<Long, Long> {
        return when (update) {
            is UpdatesTooLongObject -> TODO()
            is UpdateShortMessageObject -> TODO()
            is UpdateShortChatMessageObject -> TODO()
            is UpdateShortObject -> TODO()
            is UpdatesCombinedObject -> TODO()
            is UpdatesObject -> TODO()
            is UpdateShortSentMessageObject -> TODO()
        }
    }
}