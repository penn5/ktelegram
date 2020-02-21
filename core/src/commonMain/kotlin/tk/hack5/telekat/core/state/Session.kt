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

package tk.hack5.telekat.core.state

import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonConfiguration

abstract class Session<T : Session<T>> {
    abstract val dcId: Int
    abstract val ipAddress: String
    abstract val port: Int
    abstract val state: MTProtoStateImpl?
    abstract val entities: Map<Long, Long>

    abstract fun setDc(dcId: Int, ipAddress: String, port: Int): T
    abstract fun setState(state: MTProtoStateImpl?): T

    abstract suspend fun save()
}

data class MemorySession(
    override val dcId: Int = 2,
    override val ipAddress: String = "149.154.167.50",
    override val port: Int = 443,
    override var state: MTProtoStateImpl? = null,
    override val entities: MutableMap<Long, Long> = mutableMapOf()
) : Session<MemorySession>() {
    override fun setDc(dcId: Int, ipAddress: String, port: Int): MemorySession =
        copy(dcId = dcId, ipAddress = ipAddress, port = port)

    override fun setState(state: MTProtoStateImpl?): MemorySession = copy(state = state)

    override suspend fun save() {}

    companion object {
        fun from(session: Session<*>): MemorySession = session.run {
            MemorySession(dcId, ipAddress, port, state, entities.toMutableMap())
        }
    }
}

@Serializable
data class JsonSession(
    @Transient val output: ((String) -> Unit)? = null,
    override val dcId: Int = 2,
    override val ipAddress: String = "149.154.167.50",
    override val port: Int = 443,
    override var state: MTProtoStateImpl? = null,
    override val entities: MutableMap<Long, Long> = mutableMapOf()
) : Session<JsonSession>() {
    override fun setDc(dcId: Int, ipAddress: String, port: Int): JsonSession =
        copy(dcId = dcId, ipAddress = ipAddress, port = port)

    override fun setState(state: MTProtoStateImpl?): JsonSession = copy(state = state)

    override suspend fun save() {
        output?.let {
            it(json.stringify(serializer(), this))
        }
    }

    companion object {
        private val json = Json(JsonConfiguration.Stable)

        fun load(input: String, output: ((String) -> Unit)?): JsonSession {
            return json.parse(serializer(), input).let {
                if (output != null)
                    it.copy(output = output)
                else
                    it
            }
        }

        fun from(session: Session<*>, output: ((String) -> Unit)? = null) = session.run {
            JsonSession(output, dcId, ipAddress, port, state, entities.toMutableMap())
        }
    }
}