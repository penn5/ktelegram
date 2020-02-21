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

package tk.hack5.telekat.core.client

import com.github.aakira.napier.Napier
import com.soywiz.krypto.SecureRandom
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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
import tk.hack5.telekat.core.state.MTProtoState
import tk.hack5.telekat.core.state.MTProtoStateImpl
import tk.hack5.telekat.core.state.MemorySession
import tk.hack5.telekat.core.state.Session
import tk.hack5.telekat.core.tl.*

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
        phoneNumber: () -> String = { "9996627244" },
        signUpConsent: (Help_TermsOfServiceObject?) -> Pair<String, String>? = { null },
        phoneCode: () -> String = { "22222" },
        password: () -> String = { "" }
    ): UserType

    abstract suspend fun disconnect()
}

open class TelegramClientImpl(
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


    override suspend fun connect() {
        connectionConstructor(session.ipAddress, session.port).let {
            it.connect()
            this@TelegramClientImpl.connection = it
            if (session.state?.authKey == null)
                session = session.setState(
                    MTProtoStateImpl(
                        authenticate(
                            this@TelegramClientImpl,
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

            GlobalScope.launch {
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
        session.save()
        connection?.disconnect()
        connection = null
    }

    override suspend fun start(
        phoneNumber: () -> String,
        signUpConsent: (Help_TermsOfServiceObject?) -> Pair<String, String>?,
        phoneCode: () -> String,
        password: () -> String
    ): UserType {
        if (connection?.connected != true)
            connect()
        try {
            return getMe()
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
            /*
            val passwd = password()
            val remotePassword = this(Account_GetPasswordRequest()) as Account_PasswordObject
            when (remotePassword.currentAlgo) {
                is PasswordKdfAlgoSHA256SHA256PBKDF2HMACSHA512iter100000SHA256ModPowObject -> {
                    // TODO https://github.com/korlibs/krypto/issues/12
                    val g = remotePassword.currentAlgo.g.toByteArray(256)
                    val p = remotePassword.currentAlgo.p.pad(256)
                    val k = (p + g).sha256()
                    val a = getSecureNonce()
                    val u = ()
                }
                else -> error("Invalid password algo")
            }*/
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
                        return newAuth.user
                    }
                    else -> error("Signup failed")
                }
            }
            is Auth_AuthorizationObject -> {
                session.save()
                return auth.user
            }
            else -> error("Login failed")
        }
    }

    suspend fun getMe() = this(Users_GetUsersRequest(listOf(InputUserSelfObject()))).single()

    protected suspend fun startRecvLoop() = coroutineScope {
        val byteChannel = Channel<ByteArray>()
        launch {
            connection!!.recvLoop(byteChannel)
        }
        launch {
            unpacker!!.pump(byteChannel)
        }
    }

    override suspend fun <R : TLObject<*>> send(request: TLMethod<R>, encoder: MTProtoEncoder): R {
        connection!!.send(encoder.encode(request.toTlRepr().toByteArray()))
        val response = encoder.decode(connection!!.recv()).toIntArray()
        println(response.contentToString())
        return request.constructor.fromTlRepr(response)!!.second
    }

    override suspend fun <R : TLObject<*>> sendWrapped(request: TLMethod<R>, encoder: MTProtoEncoderWrapped): R {
        val ret = unpacker!!.sendAndRecv(request)
        if (ret is RpcErrorObject)
            throw RpcError(ret.errorCode, ret.errorMessage)
        @Suppress("UNCHECKED_CAST")
        return ret as R
    }

    override suspend operator fun <N, R : TLObject<N>> invoke(request: TLMethod<R>): N {
        return try {
            sendWrapped(request, encoder!!).native
        } catch (e: BadRequestError.FloodWaitError) {
            if (e.seconds > maxFloodWait) throw e
            delay(e.seconds.toLong())
            this(request)
        }
    }
}