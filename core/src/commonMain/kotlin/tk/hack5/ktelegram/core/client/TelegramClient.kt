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

import com.github.aakira.napier.Napier
import com.soywiz.krypto.SecureRandom
import com.soywiz.krypto.sha1
import org.gciatto.kt.math.BigInteger
import tk.hack5.ktelegram.core.*
import tk.hack5.ktelegram.core.connection.Connection
import tk.hack5.ktelegram.core.connection.TcpFullConnection
import tk.hack5.ktelegram.core.crypto.*
import tk.hack5.ktelegram.core.encoder.EncryptedMTProtoEncoder
import tk.hack5.ktelegram.core.encoder.MTProtoEncoder
import tk.hack5.ktelegram.core.encoder.PlaintextMTProtoEncoder
import tk.hack5.ktelegram.core.mtproto.*
import tk.hack5.ktelegram.core.state.MTProtoStateImpl
import tk.hack5.ktelegram.core.toByteArray
import kotlin.random.Random

private const val tag = "TelegramClient"

interface TelegramClient {
    val apiId: String
    val apiHash: String
    suspend fun connect(connection: (String, Int) -> Connection = ::TcpFullConnection,
                        plaintextEncoder: MTProtoEncoder = PlaintextMTProtoEncoder(MTProtoStateImpl()),
                        encryptedEncoder: MTProtoEncoder = EncryptedMTProtoEncoder(plaintextEncoder.state),
                        rsaEncoder: RSAEncoder = RSAEncoderImpl)
}

open class TelegramClientImpl(override val apiId: String, override val apiHash: String) : TelegramClient {
    protected var secureRandom = SecureRandom()
    protected var connection: Connection? = null
    protected var encoder: MTProtoEncoder? = null
    protected var rsaEncoder: RSAEncoder? = null
    protected var authKey: AuthKey? = null

    override suspend fun connect(connection: (String, Int) -> Connection, plaintextEncoder: MTProtoEncoder, encryptedEncoder: MTProtoEncoder, rsaEncoder: RSAEncoder) {
        connection("149.154.167.51", 80).let {
            this.connection = it
            it.connect()
            authKey = authenticate(plaintextEncoder)
        }
    }

    protected suspend fun authenticate(plaintextEncoder: MTProtoEncoder): AuthKey {
        val nonce = getSecureNonce()
        val pqRes = send(ReqPqRequest(nonce), plaintextEncoder) as ResPQObject
        println(nonce)
        println(pqRes)
        if (pqRes.nonce != nonce)
            error("Invalid nonce (got ${pqRes.nonce} but expected $nonce)")
        val pq = BigInteger(pqRes.pq) // pq is big endian. how handy!
        Napier.d("pq=$pq", tag=tag)
        val factors = factorize(pq)
        val pqInnerData = PQInnerDataObject(pqRes.pq, factors.first.toByteArray(),
            factors.second.toByteArray(), nonce, pqRes.serverNonce, getSecureNonce(256))
        Napier.d("pqInnerData=$pqInnerData", tag=tag)

        val pqInnerDataRepr = pqInnerData.toTlRepr().toByteArray()
        val randomData = Random.nextBytes(235 - pqInnerDataRepr.size) // SHA1 is 20 bytes, and we must total 255
        val dataWithHash = pqInnerDataRepr.sha1() + pqInnerDataRepr + randomData
        var encryptedData: ByteArray? = null
        var usedFingerprint: Long? = null
        for (fingerprint in pqRes.serverPublicKeyFingerprints) {
            encryptedData = RSAEncoderImpl.encrypt(dataWithHash, fingerprint)
            if (encryptedData != null) {
                usedFingerprint = fingerprint
                break
            }
        }
        println(encryptedData?.contentToString())
        println(encryptedData?.size)
        encryptedData ?: error("No server fingerprints are trusted")

        val dhParamsRequest = ReqDHParamsRequest(nonce, pqRes.serverNonce, factors.first.toByteArray(),
            factors.second.toByteArray(), usedFingerprint!!, encryptedData)

        val serverOuterDHParams = send(dhParamsRequest, plaintextEncoder)
        Napier.d("serverDhParams=$serverOuterDHParams", tag=tag)
        when (serverOuterDHParams) {
            is ServerDHParamsOkObject -> Napier.d("Got DH params OK", tag=tag)
            is ServerDHParamsFailObject -> {
                require(serverOuterDHParams.nonce == nonce) { "Invalid nonce" }
                require(serverOuterDHParams.serverNonce == pqRes.serverNonce) { "Invalid server nonce" }
                val newNonceHash = Int128Object.fromTlRepr(pqInnerData.newNonce.asTlObject128().toTlRepr()
                    .toByteArray().sha1().sliceArray(4 until 20).toIntArray())!!.second.native
                require(serverOuterDHParams.newNonceHash == newNonceHash) { "Invalid fail hash" }
                error("DH negotiation failed")
            }
            else -> error("Unknown type of ServerDHParams $serverOuterDHParams")
        }

        require(serverOuterDHParams.nonce == nonce) { "Invalid nonce" }
        require(serverOuterDHParams.serverNonce == pqRes.serverNonce) { "Invalid server nonce" }
        val key = generateKeyFromNonce(serverOuterDHParams.serverNonce, pqInnerData.newNonce)
        Napier.d("key=${key.first.contentToString()}, iv=${key.second.contentToString()}", tag=tag)
        val decryptedAnswer = AESPlatformImpl(AESMode.DECRYPT, key.first).doIGE(key.second, serverOuterDHParams.encryptedAnswer)
        println(decryptedAnswer.contentToString())
        val answer = ServerDHInnerDataObject.fromTlRepr(decryptedAnswer
            .sliceArray(20 until decryptedAnswer.size).toIntArray())
        val checksum = decryptedAnswer.sliceArray(20 until decryptedAnswer.size).sha1()
        println("checksum")
        println(checksum.contentToString())
        println(decryptedAnswer.sliceArray(0 until 20).contentToString())
        println(answer!!.first)
        // TODO this doesn't work
        // require(checksum.contentEquals(decryptedAnswer.sliceArray(0 until 20))) { "Corrupt DH params" }

        val serverDHParams = answer.second
        Napier.d("serverDHParams=$serverDHParams", tag=tag)
        require(serverDHParams.nonce == nonce) { "Invalid nonce" }
        require(serverDHParams.serverNonce == pqRes.serverNonce) { "Invalid server nonce" }
        // TODO this doesn't work too
        // plaintextEncoder.state.updateTimeOffset(serverDHParams.serverTime)

        // Maths-y stuff https://github.com/LonamiWebs/Telethon/blob/cd4b915522a7b3d1bd9f392228812a531b58ff7e/telethon/network/authenticator.py#L120
        val dhPrime = BigInteger(byteArrayOf(0) + serverDHParams.dhPrime)
        // Must be 1-billionth error probability
        require(dhPrime.isProbablePrime(30)) { "Server sent invalid dhPrime" }
        require(((dhPrime - BigInteger.ONE) / BigInteger.TWO).isProbablePrime(30)) { "Server sent invalid dhPrime" }
        require(dhPrime.bitLength == 2048) { "Server sent invalid dhPrime" }
        require (
            when (serverDHParams.g) {
                2 -> dhPrime.rem(BigInteger.of(8)) == BigInteger.of(7)
                3 -> dhPrime.rem(BigInteger.of(3)) == BigInteger.of(2)
                4 -> true
                5 -> dhPrime.rem(BigInteger.of(5)) in listOf(BigInteger.ONE, BigInteger.of(4))
                6 -> dhPrime.rem(BigInteger.of(24)) in listOf(BigInteger.of(19), BigInteger.of(23))
                7 -> dhPrime.rem(BigInteger.of(7)) in listOf(BigInteger.of(3), BigInteger.of(5), BigInteger.of(6))
                else -> false
            }
        ) { "Server sent invalid dhPrime or g" }

        val ga = BigInteger(byteArrayOf(0) + serverDHParams.gA)
        val g = BigInteger.of(serverDHParams.g)

        val dhPrimeMinusOne = dhPrime - BigInteger.ONE
        require(g > BigInteger.ONE && g < dhPrimeMinusOne) { "Server sent invalid g" }
        require(ga > BigInteger.ONE && ga < dhPrimeMinusOne) { "Server sent invalid ga" }
        println("g=$g, ga=$ga")
        println("dh_prime=$dhPrime")
        var retryId = 0L
        while (true) {
            val b = BigInteger(256, secureRandom) // Unsigned, intentionally
            println("b=$b")
            val gb = g.modPow(b, dhPrime)!!
            if (gb <= BigInteger.ONE || gb >= dhPrimeMinusOne) continue
            val gab = ga.modPow(b, dhPrime)!!
            val authKey = AuthKey(gab)
            println(gb.toByteArray())
            println(gb.toByteArray(256))
            val clientDHParams = ClientDHInnerDataObject(nonce, pqRes.serverNonce, retryId, gb.toByteArray(256))
            Napier.d("clientDHParams=$clientDHParams")
            val clientDHParamsRepr = clientDHParams.toTlRepr().toByteArray()
            val clientDHParamsHashed = clientDHParamsRepr.sha1() + clientDHParamsRepr
            val clientDHParamsEncrypted = AESPlatformImpl(AESMode.ENCRYPT, key.first)
                .doIGE(key.second, clientDHParamsHashed, secureRandom)
            val dhGen =
                send(SetClientDHParamsRequest(nonce, pqRes.serverNonce, clientDHParamsEncrypted), plaintextEncoder)
            Napier.d("dhGen=$dhGen")

            when (dhGen) {
                is DhGenOkObject -> {
                    validateNewNonceHash(dhGen.newNonceHash1, 1, pqInnerData.newNonce, authKey)
                    return authKey
                }
                is DhGenRetryObject -> {
                    validateNewNonceHash(dhGen.newNonceHash2, 2, pqInnerData.newNonce, authKey)
                    retryId = authKey.auxHash
                    Napier.d("Retrying DH")
                }
                is DhGenFailObject -> {
                    validateNewNonceHash(dhGen.newNonceHash3, 3, pqInnerData.newNonce, authKey)
                    error("Server failure")
                }
            }
        }
    }

    protected fun validateNewNonceHash(newNonceHash: BigInteger, i: Int, newNonce: BigInteger, authKey: AuthKey) {
        println(authKey)
        println(newNonce)
        println(authKey.auxHash)
        val data = newNonce.asTlObject256().toTlRepr().toByteArray() + i.toByte() + authKey.auxHash.asTlObject().toTlRepr().toByteArray()
        println(newNonce.toByteArray(32).contentToString())
        println(data.contentToString())
        val hash = data.sha1()
        println("hashes")
        println(hash.contentToString())
        println(newNonceHash)
        println(newNonceHash.toByteArray().contentToString())
        require(hash.sliceArray(4 until 20).reversedArray().contentEquals(newNonceHash.toByteArray())) { "Invalid new nonce hash" }
    }

    protected suspend fun <R : TLObject<*>>send(request: TLMethod<R>, encoder: MTProtoEncoder): R {
        connection!!.send(encoder.encode(request.toTlRepr().toByteArray()))
        val response = encoder.decode(connection!!.recv()).toIntArray()
        println(response.contentToString())
        return request.constructor.fromTlRepr(response)!!.second
    }

    protected fun getSecureNonce(bits: Int = 128): BigInteger {
        var nonce = BigInteger(bits - 1, secureRandom)
        if (secureRandom.nextBoolean())
            nonce = -nonce
        return nonce
    }
}