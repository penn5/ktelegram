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

@file:Suppress("MemberVisibilityCanBePrivate")

package tk.hack5.telekat.core.client

import com.github.aakira.napier.Napier
import com.soywiz.krypto.SecureRandom
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import tk.hack5.telekat.core.auth.authenticate
import tk.hack5.telekat.core.connection.Connection
import tk.hack5.telekat.core.connection.TcpFullConnection
import tk.hack5.telekat.core.encoder.EncryptedMTProtoEncoder
import tk.hack5.telekat.core.encoder.MTProtoEncoder
import tk.hack5.telekat.core.encoder.MTProtoEncoderWrapped
import tk.hack5.telekat.core.encoder.PlaintextMTProtoEncoder
import tk.hack5.telekat.core.errors.BadRequestError
import tk.hack5.telekat.core.errors.RedirectedError
import tk.hack5.telekat.core.errors.RpcError
import tk.hack5.telekat.core.mtproto.RpcErrorObject
import tk.hack5.telekat.core.packer.MessagePackerUnpacker
import tk.hack5.telekat.core.state.*
import tk.hack5.telekat.core.tl.*
import tk.hack5.telekat.core.updates.ObjectType
import tk.hack5.telekat.core.updates.UpdateHandler
import tk.hack5.telekat.core.updates.UpdateHandlerImpl
import tk.hack5.telekat.core.updates.UpdateOrSkipped

private const val tag = "TelegramClient"

abstract class TelegramClient {
    internal abstract val secureRandom: SecureRandom
    abstract suspend fun connect()

    internal abstract suspend fun <R : TLObject<*>> send(request: TLMethod<R>, encoder: MTProtoEncoder): R
    protected abstract suspend fun <R : TLObject<*>> sendWrapped(
        request: TLMethod<R>,
        encoder: MTProtoEncoderWrapped
    ): R

    abstract suspend operator fun <N, R : TLObject<N>> invoke(request: TLMethod<R>): N
    abstract suspend fun start(
        phoneNumber: () -> String,
        signUpConsent: (Help_TermsOfServiceObject?) -> Pair<String, String>? = { null },
        phoneCode: () -> String,
        password: () -> String
    ): Pair<Boolean?, UserType>

    abstract suspend fun disconnect()

    abstract suspend fun getMe(): UserObject
    abstract suspend fun getInputMe(): InputPeerUserObject

    abstract suspend fun getAccessHash(constructor: ObjectType, peerId: Int): Long?
    abstract suspend fun getAccessHash(constructor: ObjectType, peerId: Long): Long?
    abstract var updateCallbacks: List<suspend (UpdateOrSkipped) -> Unit>
    abstract suspend fun catchUp()
}

open class TelegramClientCoreImpl(
    protected val apiId: String, protected val apiHash: String,
    protected val connectionConstructor: (String, Int) -> Connection = ::TcpFullConnection,
    protected val plaintextEncoder: MTProtoEncoder = PlaintextMTProtoEncoder(MTProtoStateImpl()),
    protected val encryptedEncoderConstructor: (MTProtoState) -> EncryptedMTProtoEncoder = { EncryptedMTProtoEncoder(it) },
    protected val deviceModel: String = "ktg",
    protected val systemVersion: String = "0.0.1",
    protected val appVersion: String = "0.0.1",
    protected val systemLangCode: String = "en",
    protected val langPack: String = "",
    protected val langCode: String = "en",
    protected var session: Session<*> = MemorySession(),
    protected val maxFloodWait: Int = 0
) : TelegramClient() {
    override var secureRandom = SecureRandom()
    protected var connection: Connection? = null
    protected var encoder: EncryptedMTProtoEncoder? = null
    protected var unpacker: MessagePackerUnpacker? = null
    protected var serverConfig: ConfigObject? = null
    protected val updatesChannel = Channel<UpdatesType>(Channel.UNLIMITED)
    protected lateinit var inputUserSelf: InputPeerUserObject
    protected var updatesHandler: UpdateHandler? = null
    protected val activeTasks = mutableListOf<Job>()

    override var updateCallbacks = listOf<suspend (UpdateOrSkipped) -> Unit>()

    override suspend fun connect() {
        session.updates?.let {
            updatesHandler = UpdateHandlerImpl(it, this)
        }
        connectionConstructor(session.ipAddress, session.port).let {
            it.connect()
            this@TelegramClientCoreImpl.connection = it
            if (session.state?.authKey == null)
                session = session.setState(
                    MTProtoStateImpl(
                        authenticate(
                            this@TelegramClientCoreImpl,
                            plaintextEncoder
                        )
                    ).also { state ->
                        encoder = encryptedEncoderConstructor(state)
                        unpacker = MessagePackerUnpacker(it, encoder!!, state, updatesChannel)
                    })
            else {
                encoder = encryptedEncoderConstructor(session.state!!)
                unpacker = MessagePackerUnpacker(it, encoder!!, session.state!!, updatesChannel)
            }

            activeTasks += GlobalScope.launch {
                startRecvLoop()
            }

            this(Help_GetNearestDcRequest()) // First request has to be an unchanged request from the first layer
            serverConfig = this(
                InvokeWithLayerRequest(
                    105,
                    InitConnectionRequest(
                        apiId.toInt(),
                        deviceModel,
                        systemVersion,
                        appVersion,
                        systemLangCode,
                        langPack,
                        langCode,
                        null,
                        Help_GetConfigRequest()
                    )
                )
            ) as ConfigObject
        }
    }

    override suspend fun disconnect() {
        activeTasks.forEach {
            it.cancel()
        }
        activeTasks.forEach {
            it.join()
        }
        activeTasks.clear()
        session.save()
        connection?.disconnect()
        connection = null
    }

    override suspend fun start(
        phoneNumber: () -> String,
        signUpConsent: (Help_TermsOfServiceObject?) -> Pair<String, String>?,
        phoneCode: () -> String,
        password: () -> String
    ): Pair<Boolean?, UserType> {
        val (loggedIn, ret) = logIn(phoneNumber, signUpConsent, phoneCode, password)
        val state = (this(Updates_GetStateRequest()) as Updates_StateObject)
        if (session.updates == null) {
            session =
                session.setUpdateState(UpdateState(state.seq, state.date, state.qts, mutableMapOf(null to state.pts)))
        }
        updatesHandler = UpdateHandlerImpl(session.updates!!, this)
        activeTasks += GlobalScope.launch {
            while (true) {
                coroutineScope {
                    val update = updatesHandler!!.updates.receive()
                    println(this@TelegramClientCoreImpl.updateCallbacks)
                    this@TelegramClientCoreImpl.updateCallbacks.forEach { launch { it(update) } }
                }
            }
        }
        return loggedIn to ret
    }

    protected suspend fun logIn(
        phoneNumber: () -> String,
        signUpConsent: (Help_TermsOfServiceObject?) -> Pair<String, String>?,
        phoneCode: () -> String,
        password: () -> String
    ): Pair<Boolean?, UserType> {
        if (connection?.connected != true)
            connect()
        try {
            return null to getMe()
        } catch (e: BadRequestError.AuthKeyUnregisteredError) {
            Napier.v("Beginning sign-in", e, tag = tag)
        }
        val phone = phoneNumber()
        val sentCode =
            try {
                this(
                    Auth_SendCodeRequest(
                        phone, apiId.toInt(), apiHash, CodeSettingsObject(
                            allowFlashcall = false,
                            currentNumber = false,
                            allowAppHash = false
                        )
                    )
                ) as Auth_SentCodeObject
            } catch (e: RedirectedError.PhoneMigrateError) {
                Napier.d("Phone migrated to ${e.dc}", tag = tag)
                disconnect()
                val newDc = serverConfig!!.dcOptions.map { it as DcOptionObject }.filter { it.id == e.dc }.random()
                session = session.setDc(e.dc, newDc.ipAddress, newDc.port).setState(null)
                return start({ phone }, signUpConsent, phoneCode, password)
            }
        val auth = try {
            this(Auth_SignInRequest(phone, sentCode.phoneCodeHash, phoneCode()))
        } catch (e: BadRequestError.SessionPasswordNeededError) {
            // TODO 2fa
            throw e
        }
        when (auth) {
            is Auth_AuthorizationSignUpRequiredObject -> {
                val name = signUpConsent(auth.termsOfService as Help_TermsOfServiceObject)
                require(name != null) { "Terms of Service were not accepted" }
                when (val newAuth = this(Auth_SignUpRequest(phone, sentCode.phoneCodeHash, name.first, name.second))) {
                    is Auth_AuthorizationObject -> {
                        session.save()
                        return true to newAuth.user
                    }
                    else -> error("Signup failed (unknown $newAuth)")
                }
            }
            is Auth_AuthorizationObject -> {
                session.save()
                return false to auth.user
            }
            else -> error("Login failed (unknown $auth)")
        }
    }

    override suspend fun getMe(): UserObject {
        return (this(Users_GetUsersRequest(listOf(InputUserSelfObject()))).single() as UserObject).also {
            inputUserSelf = InputPeerUserObject(it.id, it.accessHash!!)
        }
    }

    override suspend fun getInputMe(): InputPeerUserObject {
        if (::inputUserSelf.isInitialized)
            return inputUserSelf
        getMe()
        return inputUserSelf
    }

    override suspend fun getAccessHash(constructor: ObjectType, peerId: Int): Long? =
        getAccessHash(constructor, peerId.toLong())

    override suspend fun getAccessHash(constructor: ObjectType, peerId: Long): Long? =
        session.entities[constructor.name]?.get(peerId)

    override suspend fun catchUp() = updatesHandler!!.catchUp()

    protected suspend fun startRecvLoop() = coroutineScope {
        val byteChannel = Channel<ByteArray>()
        launch {
            connection!!.recvLoop(byteChannel)
        }
        launch {
            unpacker!!.pump(byteChannel)
        }
        launch {
            while (true)
                unpacker!!.updatesChannel.receive().let {
                    updatesHandler?.getEntities(it)?.let { entities ->
                        session.addEntities(entities)
                    }
                    updatesHandler?.handleUpdates(it)
                }
        }
    }

    override suspend fun <R : TLObject<*>> send(request: TLMethod<R>, encoder: MTProtoEncoder): R {
        Napier.d(request.toString(), tag = tag)
        connection!!.send(encoder.encode(request.toTlRepr().toByteArray()))
        val response = encoder.decode(connection!!.recv()).toIntArray()
        return request.constructor.fromTlRepr(response)!!.second
    }

    override suspend fun <R : TLObject<*>> sendWrapped(request: TLMethod<R>, encoder: MTProtoEncoderWrapped): R {
        Napier.d(request.toString(), tag = tag)
        val ret = unpacker!!.sendAndRecv(request)
        if (ret is RpcErrorObject)
            throw RpcError(ret.errorCode, ret.errorMessage, request)
        @Suppress("UNCHECKED_CAST")
        return ret as R
    }

    suspend fun <R : TLObject<*>> sendAndUnpack(request: TLMethod<R>): R {
        val ret: R = try {
            sendWrapped(request, encoder!!)
        } catch (e: BadRequestError.FloodWaitError) {
            if (e.seconds > maxFloodWait) throw e
            delay(e.seconds.toLong())
            sendAndUnpack(request)
        }
        updatesHandler?.getEntities(ret)?.let {
            session.addEntities(it)
        }
        return ret
    }

    override suspend operator fun <N, R : TLObject<N>> invoke(request: TLMethod<R>): N {
        try {
            return sendAndUnpack(request).native
        } catch (e: Throwable) {
            throw e // Needed to preserve stack traces on buggy coroutines impl
        }
    }
}