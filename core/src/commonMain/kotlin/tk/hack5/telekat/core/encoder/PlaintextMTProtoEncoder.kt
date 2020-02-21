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

package tk.hack5.telekat.core.encoder

import tk.hack5.telekat.core.state.MTProtoState
import tk.hack5.telekat.core.tl.*

class PlaintextMTProtoEncoder(state: MTProtoState) : MTProtoEncoder(state) {
    override fun encode(data: ByteArray): ByteArray = ByteArray(8) { 0 } +
            state.getMsgId().asTlObject().toTlRepr().toByteArray() + data.size.toByteArray() + data

    override fun decode(data: ByteArray): ByteArray {
        println(data.contentToString())
        if (data.sliceArray(0 until 8).any { it != 0.toByte() })
            error("Invalid authKeyId")
        val msgId = LongObject.fromTlRepr(data.sliceArray(8 until 16).toIntArray())!!.second.native
        state.updateMsgId(msgId)
        val len = data.sliceArray(16 until 20).toInt()
        if (len + 20 > data.size)
            error("Invalid len")
        return data.sliceArray(20 until data.size)
    }
}