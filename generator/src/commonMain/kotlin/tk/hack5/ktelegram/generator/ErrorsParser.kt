/*
 *     KTelegram (Telegram MTProto client library)
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

package tk.hack5.ktelegram.generator

data class Error(val name: String, val codes: Collection<Int>, val description: String) {
    constructor(name: String, code: String, description: String) : this(
        name,
        code.split(" ").filter { it.isNotEmpty() }.map { it.toInt() },
        description
    )

    companion object {
        operator fun invoke(line: String): Error {
            val split = line.split(",", limit = 3)
            return Error(
                split.component1().replace("\\", "\\\\"),
                split.component2(),
                split.component3().removeSurrounding("\"").replace("\\", "\\\\").replace("\"\"", "\\\"")
            )
        }
    }
}

data class Method(val name: String, val usability: MethodUsability, val errors: Collection<Error>) {
    constructor(name: String, usability: String, methodErrors: String, knownErrors: Map<String, Error>)
            : this(
        name,
        MethodUsability.valueOf(usability.toUpperCase()),
        methodErrors.split(" ").map { knownErrors.getValue(it) })

    companion object {
        operator fun invoke(line: String, knownErrors: Map<String, Error>): Method {
            val split = line.split(",")
            return Method(split.component1(), split.component2(), split.component3(), knownErrors)
        }
    }
}

enum class MethodUsability {
    USER,
    BOT,
    BOTH
}

