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

package tk.hack5.telekat.core.connection

import com.github.aakira.napier.Napier
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.io.readIntLittleEndian
import kotlinx.coroutines.io.writeFully
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import tk.hack5.telekat.core.network.TCPClient
import tk.hack5.telekat.core.network.TCPClientImpl
import tk.hack5.telekat.core.tl.toByteArray
import tk.hack5.telekat.core.utils.calculateCRC32

private const val tag = "Connection"

class ConnectionClosedError(message: String? = null, cause: Exception? = null) : Exception(message, cause)
class AlreadyConnectedError(message: String? = null, cause: Exception? = null) : Exception(message, cause)

abstract class Connection(protected val host: String, protected val port: Int) {
    var connected: Boolean? = false // null when in progress
        private set
    private val connectedChannel = Channel<Boolean?>()
    val connectedChangeChannel = connectedChannel as ReceiveChannel<Boolean?>
    private val sendLock = Mutex()
    private val recvLock = Mutex()
    private var recvLoopTask: Job? = null

    private fun notifyConnectionStatus(status: Boolean?) {
        Napier.i("New connection state for $this: $status", tag = tag)
        connected = status
        connectedChannel.offer(status)
    }

    suspend fun connect() {
        if (connected != false)
            throw AlreadyConnectedError("Still connected to the sever. Please wait for `connected == false`")
        notifyConnectionStatus(null)
        connectInternal()
        notifyConnectionStatus(true)
    }

    suspend fun disconnect() {
        connected = null
        disconnectInternal()
        connected = false
        connectedChannel.offer(false)
    }

    suspend fun send(data: ByteArray) {
        sendLock.withLock {
            sendInternal(data)
        }
    }

    suspend fun recvLoop(output: Channel<ByteArray>) {
        recvLock.withLock {
            while (connected == true) {
                println("connected")
                val a = recvInternal()
                output.send(a)
//               output.send(recvInternal())
                println("connected2")
            }
        }
    }

    suspend fun recv(): ByteArray {
        return recvLock.withLock {
            return@withLock recvInternal()
        }
    }

    protected abstract suspend fun sendInternal(data: ByteArray)
    protected abstract suspend fun recvInternal(): ByteArray
    protected abstract suspend fun connectInternal()
    protected abstract suspend fun disconnectInternal()
}

abstract class TcpConnection(host: String, port: Int, private val network: (String, Int) -> TCPClient) : Connection(host, port) {
    private var socket: TCPClient? = null
    protected val readChannel get() = socket?.readChannel!!
    protected val writeChannel get() = socket?.writeChannel!!

    override suspend fun connectInternal() {
        network(host, port).let {
            it.connect()
            socket = it
        }
    }

    override suspend fun disconnectInternal() {
        socket?.close()
    }
}

class TcpFullConnection(host: String, port: Int, network: (String, Int) -> TCPClient = ::TCPClientImpl) : TcpConnection(host, port, network) {
    constructor(host: String, port: Int) : this(host, port, ::TCPClientImpl)
    private var counter = 0
    override suspend fun sendInternal(data: ByteArray) {
        val len = data.size + 12
        val ret = byteArrayOf(*len.toByteArray(), *counter++.toByteArray(), *data)
        val crc = calculateCRC32(ret)
        writeChannel.writeFully(ret + crc.toByteArray())
        writeChannel.flush()
        println("sent")
    }

    override suspend fun recvInternal(): ByteArray {
        // The ugly try-catch statements here are because the exception is missing some line-number metadata
        // To recover this metadata we add it by throwing the exception from a different line
        val len: Int
        try {
            len = readChannel.readIntLittleEndian()
        } catch (e: ClosedReceiveChannelException) {
            throw ConnectionClosedError(cause=e)
        }
        println("len = $len")
        val seq: Int
        try {
            seq = readChannel.readIntLittleEndian()
        } catch (e: ClosedReceiveChannelException) {
            throw ConnectionClosedError(cause=e)
        }
        println("seq = $seq")
        val ret = ByteArray(len - 12)
        try {
            readChannel.readFully(ret, 0, ret.size)
        } catch (e: ClosedReceiveChannelException) {
            throw ConnectionClosedError(cause=e)
        }
        val crc: Int
        try {
            crc = readChannel.readIntLittleEndian()
        } catch (e: ClosedReceiveChannelException) {
            throw ConnectionClosedError(cause=e)
        }
        val full = byteArrayOf(*len.toByteArray(), *seq.toByteArray(), *ret)
        val calculatedCrc = calculateCRC32(full)
        if (crc != calculatedCrc)
            error("Invalid CRC in recv! $crc != $calculatedCrc")
        println(ret.contentToString())
        return ret // We don't care that there is extra data (the CRC) at the end, the fromTlRepr will ignore it.
    }
}