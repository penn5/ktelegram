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

import tk.hack5.telekat.core.mtproto.MessageObject
import tk.hack5.telekat.core.state.MTProtoState
import tk.hack5.telekat.core.tl.TLObject

abstract class MTProtoEncoder(val state: MTProtoState) {
    abstract fun encode(data: ByteArray): ByteArray
    abstract fun decode(data: ByteArray): ByteArray
}

abstract class MTProtoEncoderWrapped(state: MTProtoState) : MTProtoEncoder(state) {
    abstract fun encodeMessage(data: MessageObject): ByteArray
    abstract fun decodeMessage(data: ByteArray): MessageObject

    abstract fun wrapAndEncode(data: TLObject<*>, isContentRelated: Boolean = true): Pair<ByteArray, Long>
}