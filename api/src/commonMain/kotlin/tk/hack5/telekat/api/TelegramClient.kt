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

package tk.hack5.telekat.api

import com.github.aakira.napier.Napier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.GlobalScope
import tk.hack5.telekat.core.client.TelegramClientCoreImpl
import tk.hack5.telekat.core.connection.Connection
import tk.hack5.telekat.core.connection.TcpFullConnection
import tk.hack5.telekat.core.encoder.EncryptedMTProtoEncoder
import tk.hack5.telekat.core.encoder.MTProtoEncoder
import tk.hack5.telekat.core.encoder.PlaintextMTProtoEncoder
import tk.hack5.telekat.core.state.MTProtoState
import tk.hack5.telekat.core.state.MTProtoStateImpl
import tk.hack5.telekat.core.state.MemorySession
import tk.hack5.telekat.core.state.Session
import tk.hack5.telekat.core.updates.UpdateOrSkipped

class TelegramClientApiImpl(
    apiId: String,
    apiHash: String,
    connectionConstructor: (CoroutineScope, String, Int) -> Connection = ::TcpFullConnection,
    plaintextEncoder: MTProtoEncoder = PlaintextMTProtoEncoder(MTProtoStateImpl()),
    encryptedEncoderConstructor: (MTProtoState) -> EncryptedMTProtoEncoder = {
        EncryptedMTProtoEncoder(it)
    },
    deviceModel: String = "ktg",
    systemVersion: String = "0.0.1",
    appVersion: String = "0.0.1",
    systemLangCode: String = "en",
    langPack: String = "",
    langCode: String = "en",
    session: Session<*> = MemorySession(),
    maxFloodWait: Int = 0,
    scope: CoroutineScope = GlobalScope
) : TelegramClientCoreImpl(
    apiId,
    apiHash,
    connectionConstructor,
    plaintextEncoder,
    encryptedEncoderConstructor,
    deviceModel,
    systemVersion,
    appVersion,
    systemLangCode,
    langPack,
    langCode,
    session,
    maxFloodWait,
    scope
) {

    protected val handleUpdate: suspend (UpdateOrSkipped) -> Unit = handleUpdate@{
        for (handler in EventHandler.defaultHandlers) {
            handler.constructEvent(this, it)?.let { event ->
                eventCallbacks.forEach { callback ->
                    callback(event)
                }
                Napier.d("dispatched!")
                return@handleUpdate
            }
        }
    }

    override var updateCallbacks: List<suspend (UpdateOrSkipped) -> Unit> = emptyList()
        set(value) {
            field = if (value.contains(handleUpdate)) value else value + handleUpdate
        }

    init {
        updateCallbacks = listOf()
    }

    var eventCallbacks: List<suspend (Event) -> Unit> = listOf()
}