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

package tk.hack5.telekat.core.network

import io.ktor.network.selector.ActorSelectorManager
import io.ktor.network.sockets.*
import io.ktor.util.KtorExperimentalAPI
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.io.ByteReadChannel
import kotlinx.coroutines.io.ByteWriteChannel
import java.net.InetSocketAddress

actual class TCPClientImpl actual constructor(
    scope: CoroutineScope,
    private val targetAddress: String,
    private val targetPort: Int
) : TCPClient(targetAddress, targetPort) {
    private lateinit var socket: Socket

    override var readChannel: ByteReadChannel? = null
    override var writeChannel: ByteWriteChannel? = null

    override val address get() = (socket.remoteAddress as InetSocketAddress).hostString!!
    override val port get() = (socket.remoteAddress as InetSocketAddress).port

    @KtorExperimentalAPI
    override suspend fun connect() {
        socket = aSocket(actor).tcp().connect(targetAddress, targetPort)
        readChannel = socket.openReadChannel()
        writeChannel = socket.openWriteChannel()
    }

    @KtorExperimentalAPI
    override suspend fun close() {
        readChannel?.cancel(null)
        readChannel = null
        writeChannel?.close(null)
        writeChannel = null
        socket.awaitClosed()
        actor.close()
    }

    @KtorExperimentalAPI
    val actor = ActorSelectorManager(scope.coroutineContext + Dispatchers.IO)
}