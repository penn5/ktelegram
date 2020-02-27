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

package tk.hack5.telekat.core.utils

import tk.hack5.telekat.core.tl.TLObject

abstract class TLWalker<T> {
    open val result: T? = null

    fun walk(tlObject: TLObject<*>): T? {
        for (field in tlObject.fields) {
            if (handle(field.key, field.value))
                field.value?.let { walk(it) }
        }
        return result
    }

    protected open fun handle(key: String, value: TLObject<*>?): Boolean = true
}