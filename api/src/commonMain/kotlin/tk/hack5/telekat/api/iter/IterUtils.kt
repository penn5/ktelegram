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

package tk.hack5.telekat.api.iter

import kotlinx.coroutines.flow.flow

suspend inline fun <R, O> iter(crossinline function: suspend (O?) -> Pair<Collection<R>, O?>) = flow {
    var lastOutput: Pair<Collection<R>, O?>? = null
    while (lastOutput?.second != null || lastOutput == null) {
        lastOutput = (function(lastOutput?.second).let {
            if (it.first.isEmpty() || it.second == null) null else it
        } ?: break)
        lastOutput.first.forEach { emit(it) }
    }
}