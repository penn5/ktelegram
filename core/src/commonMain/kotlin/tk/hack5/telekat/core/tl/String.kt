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

package tk.hack5.telekat.core.tl

fun String.asTlObject() = StringObject(this, true)

class StringObject(private val string: String, override val bare: Boolean) :
    TLObject<String> {
    override fun _toTlRepr() = string.asByteArray().asTlObject().toTlRepr()

    override val native = string

    override val _id = id

    override val fields by lazy { mapOf<String, TLObject<*>>() }

    companion object :
        TLConstructor<StringObject> {
        override fun _fromTlRepr(data: IntArray): Pair<Int, StringObject>? = BytesObject.fromTlRepr(data)?.let {
            Pair(it.first, it.second.native.asString().asTlObject())
        }

        override val id: Int? = null
    }
}

internal expect fun String.asByteArray(): ByteArray

internal expect fun ByteArray.asString(): String