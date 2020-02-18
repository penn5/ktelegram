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

package tk.hack5.ktelegram.core.client

import com.soywiz.krypto.SecureRandom
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import tk.hack5.ktelegram.core.auth.authenticate
import tk.hack5.ktelegram.core.connection.Connection
import tk.hack5.ktelegram.core.connection.TcpFullConnection
import tk.hack5.ktelegram.core.crypto.AuthKey
import tk.hack5.ktelegram.core.encoder.EncryptedMTProtoEncoder
import tk.hack5.ktelegram.core.encoder.MTProtoEncoder
import tk.hack5.ktelegram.core.encoder.MTProtoEncoderWrapped
import tk.hack5.ktelegram.core.encoder.PlaintextMTProtoEncoder
import tk.hack5.ktelegram.core.errors.BadRequestError
import tk.hack5.ktelegram.core.errors.RedirectedError
import tk.hack5.ktelegram.core.errors.RpcError
import tk.hack5.ktelegram.core.mtproto.RpcErrorObject
import tk.hack5.ktelegram.core.packer.MessagePackerUnpacker
import tk.hack5.ktelegram.core.state.MTProtoState
import tk.hack5.ktelegram.core.state.MTProtoStateImpl
import tk.hack5.ktelegram.core.state.MemorySession
import tk.hack5.ktelegram.core.state.Session
import tk.hack5.ktelegram.core.tl.*

private const val tag = "TelegramClient"

abstract class TelegramClient {
    internal abstract val secureRandom: SecureRandom
    abstract suspend fun connect()

    internal abstract suspend fun <R : TLObject<*>> send(request: TLMethod<R>, encoder: MTProtoEncoder): R
    protected abstract suspend fun <R : TLObject<*>> sendWrapped(
        request: TLMethod<R>,
        encoder: MTProtoEncoderWrapped
    ): R

    abstract suspend operator fun <R : TLObject<*>> invoke(request: TLMethod<R>): R
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
    protected var session: Session<*> = MemorySession()
) : TelegramClient() {
    override var secureRandom = SecureRandom()
    protected var connection: Connection? = null
    protected var encoder: EncryptedMTProtoEncoder? = null
    protected var authKey: AuthKey? = null
    protected var unpacker: MessagePackerUnpacker? = null
    protected var serverConfig: ConfigObject? = null


    override suspend fun connect() {
        connectionConstructor(session.ipAddress, session.port).let {
            this@TelegramClientImpl.connection = it
            it.connect()
            authKey = authenticate(this@TelegramClientImpl, plaintextEncoder)
            MTProtoStateImpl(authKey).let { state ->
                encoder = encryptedEncoderConstructor(state)
                unpacker = MessagePackerUnpacker(it, encoder!!, state)
            }
            GlobalScope.launch {
                startRecvLoop()
            }
            this(Help_GetNearestDcRequest()) // First request has to be an unchanged request from the first layer
            serverConfig = this(
                InvokeWithLayerRequest(
                    108,
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
        connection?.disconnect()
        connection = null
    }

    override suspend fun start(
        phoneNumber: () -> String,
        signUpConsent: (Help_TermsOfServiceObject?) -> Pair<String, String>?,
        phoneCode: () -> String,
        password: () -> String
    ): UserType {
        connect()
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
                disconnect()
                val newDc = serverConfig!!.dcOptions.map { it as DcOptionObject }.filter { it.id == e.dc }.first()
                session = session.setDc(e.dc, newDc.ipAddress, newDc.port)
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
                        return newAuth.user
                    }
                    else -> error("Signup failed")
                }
            }
            is Auth_AuthorizationObject -> {
                return auth.user
            }
            else -> error("Login failed")
        }
    }

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

    override suspend operator fun <R : TLObject<*>> invoke(request: TLMethod<R>): R = sendWrapped(request, encoder!!)
}