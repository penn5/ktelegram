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

package tk.hack5.ktelegram.core.state

interface Session<T : Session<T>> {
    val dcId: Int
    val ipAddress: String
    val port: Int
    val state: MTProtoState?
    val entities: Map<Long, Long>

    fun setDc(dcId: Int, ipAddress: String, port: Int): T
    fun setState(state: MTProtoState): T
}

data class MemorySession(
    override val dcId: Int = 2,
    override val ipAddress: String = "149.154.167.50",
    override val port: Int = 443,
    override var state: MTProtoState? = null,
    override val entities: MutableMap<Long, Long> = mutableMapOf()
) : Session<MemorySession> {
    override fun setDc(dcId: Int, ipAddress: String, port: Int): MemorySession =
        copy(dcId = dcId, ipAddress = ipAddress, port = port)

    override fun setState(state: MTProtoState): MemorySession = copy(state = state)
}