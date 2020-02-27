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

package tk.hack5.telekat.generator

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

@ExperimentalUnsignedTypes
@Serializable
data class TLData(val constructors: List<TLConstructor>, val methods: List<TLMethod>) {
    val types: List<String> = (methods.map { listOf(it.type, *it.params.map { param -> param.type }.toTypedArray()) }
        .flatten() + constructors.map { listOf(it.type, *it.params.map { param -> param.type }.toTypedArray()) }
        .flatten()).map { it.split("?").last() }.filter { it.substringAfterLast(".").first().let {
            target -> target.toLowerCase() != target }}.map { it.replace("<.*>".toRegex(), "<T>") }
}

enum class EntryType {
    CONSTRUCTOR,
    METHOD,
}

@ExperimentalUnsignedTypes
interface TLEntry {
    val id: Int
    val name: String
    val params: List<TLParam>
    val type: String
    val entryType: EntryType
}

@ExperimentalUnsignedTypes
@Serializable
data class TLConstructor(
    override val id: Int,
    private val predicate: String,
    override val params: List<TLParam>, @SerialName("type") private val _type: String
) : TLEntry {
    override val entryType = EntryType.CONSTRUCTOR
    override val name = fixName(predicate)
    @Transient // kotlinx.serialization.Transient, not kotlin.jvm.Transient
    override val type = fixName(_type)
}

@ExperimentalUnsignedTypes
@Serializable
data class TLMethod(
    override val id: Int,
    private val method: String,
    override val params: List<TLParam>, @SerialName("type") private val _type: String
) : TLEntry {
    override val entryType = EntryType.METHOD
    override val name = fixName(method)
    @Transient // kotlinx.serialization.Transient, not kotlin.jvm.Transient
    override val type = fixName(_type)
}

@Serializable
data class TLParam(@SerialName("name") private val _name: String, @SerialName("type") private val _type: String) {
    @Transient // kotlinx.serialization.Transient, not kotlin.jvm.Transient
    val name = fixName(_name)
    @Transient // kotlinx.serialization.Transient, not kotlin.jvm.Transient
    val type = fixName(_type)
}

fun fixName(name: String): String {
    return name.first() + name.zipWithNext { prev, current ->
        when {
            prev == '_' -> current.toUpperCase().toString()
            current == '_' -> ""
            else -> current.toString()
        }
    }.joinToString("")
}