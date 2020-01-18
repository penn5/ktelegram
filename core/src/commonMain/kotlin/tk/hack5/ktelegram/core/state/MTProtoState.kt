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

import com.github.aakira.napier.Napier
import com.soywiz.klock.DateTime
import com.soywiz.klock.nanoseconds
import com.soywiz.klock.seconds
import kotlinx.serialization.Serializable
import kotlin.math.roundToInt
import kotlin.math.roundToLong
import kotlin.random.Random

private const val tag = "MTProtoState"

interface MTProtoState {
    var authKey: Long
    var timeOffset: Long
    var salt: Int
    val sessionId: Long
    var seq: Int
    var lastMsgId: Long

    fun getMsgId(): Long
    fun validateMsgId(id: Long): Boolean
    fun updateTimeOffset(msgId: Long)
    fun updateMsgId(msgId: Long)
    fun updateTimeOffset(seconds: Int)
}

@Serializable
class MTProtoStateImpl(override var authKey: Long = 0) : MTProtoState {
    override var timeOffset = 0L
    override var salt = 0
    override val sessionId = Random.nextLong()
    override var seq = 0
    override var lastMsgId = 0L

    @ExperimentalUnsignedTypes
    override fun getMsgId(): Long {
        val now = DateTime.now()
        val sinceEpoch = now - DateTime.EPOCH
        val secsSinceEpoch = sinceEpoch.seconds.toInt()
        val sinceSecond = sinceEpoch - secsSinceEpoch.seconds
        val nanoseconds = sinceSecond.nanoseconds.roundToInt()
        val secs = secsSinceEpoch + timeOffset
        var newMsgId = secs.shl(32).or(nanoseconds.toLong().shl(2))
        if (!validateMsgId(newMsgId))
            newMsgId = lastMsgId
        lastMsgId = newMsgId
        Napier.d("Generated msg_id=$newMsgId", tag=tag)
        return newMsgId
    }

    @ExperimentalUnsignedTypes
    override fun validateMsgId(id: Long): Boolean = id.toULong() > lastMsgId.toULong()

    override fun updateTimeOffset(seconds: Int) {
        val now = DateTime.now() - DateTime.EPOCH
        val oldOffset = timeOffset
        timeOffset = seconds - now.seconds.roundToLong()
        Napier.d("Updating timeOffset to $$timeOffset (was $oldOffset, t=$now, c=$seconds)", tag=tag)
    }

    override fun updateTimeOffset(msgId: Long) {
        updateTimeOffset(msgId.ushr(32).toInt())
    }

    @ExperimentalUnsignedTypes
    override fun updateMsgId(msgId: Long) {
        require(validateMsgId(msgId)) { "msg_id was reduced by the server" }
        lastMsgId = msgId
    }
}