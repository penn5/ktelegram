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

package tk.hack5.telekat.core.auth

import com.github.aakira.napier.Napier
import com.soywiz.krypto.sha1
import org.gciatto.kt.math.BigInteger
import tk.hack5.telekat.core.client.TelegramClient
import tk.hack5.telekat.core.crypto.*
import tk.hack5.telekat.core.encoder.MTProtoEncoder
import tk.hack5.telekat.core.mtproto.*
import tk.hack5.telekat.core.tl.*
import kotlin.random.Random

private const val tag = "Authenticator"

internal fun step1(nonce: BigInteger): ReqPqMultiRequest = ReqPqMultiRequest(nonce)

internal fun step2(nonce: BigInteger, pqRes: ResPQObject): BigInteger {
    if (pqRes.nonce != nonce)
        error("Invalid nonce (got ${pqRes.nonce} but expected $nonce)")
    val pq = BigInteger(pqRes.pq) // pq is big endian. how handy!
    Napier.d("pq=$pq", tag = tag)
    return pq
}

internal fun step3(pq: BigInteger) = factorize(pq)

internal fun step4(
    newNonce: BigInteger, factors: Pair<BigInteger, BigInteger>, pqRes: ResPQObject,
    padding: (Int) -> ByteArray
): ReqDHParamsRequest {
    val pqInnerData = PQInnerDataObject(
        pqRes.pq, factors.first.toByteArray(),
        factors.second.toByteArray(), pqRes.nonce, pqRes.serverNonce, newNonce
    )
    Napier.d("pqInnerData=$pqInnerData", tag = tag)
    val pqInnerDataRepr = pqInnerData.toTlRepr().toByteArray()
    val randomData = padding(235 - pqInnerDataRepr.size) // SHA1 is 20 bytes, and we must total 255
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
    encryptedData ?: error("No server fingerprints are trusted")

    return ReqDHParamsRequest(
        pqRes.nonce, pqRes.serverNonce, factors.first.toByteArray(),
        factors.second.toByteArray(), usedFingerprint!!, encryptedData
    )
}

internal fun step5(
    serverOuterDHParams: ServerDHParamsType,
    nonce: BigInteger,
    newNonce: BigInteger,
    serverNonce: BigInteger
):
        Triple<Triple<BigInteger, BigInteger, BigInteger>, Pair<ByteArray, ByteArray>, Int> {
    when (serverOuterDHParams) {
        is ServerDHParamsOkObject -> Napier.d("Got DH params OK", tag = tag)
        is ServerDHParamsFailObject -> {
            require(serverOuterDHParams.nonce == nonce) { "Invalid nonce" }
            require(serverOuterDHParams.serverNonce == serverNonce) { "Invalid server nonce" }
            val newNonceHash = Int128Object.fromTlRepr(
                newNonce.asTlObject128().toTlRepr()
                    .toByteArray().sha1().sliceArray(4 until 20).toIntArray()
            )!!.second.native
            require(serverOuterDHParams.newNonceHash == newNonceHash) { "Invalid fail hash" }
            error("DH negotiation failed")
        }
    }

    require(serverOuterDHParams.nonce == nonce) { "Invalid nonce" }
    require(serverOuterDHParams.serverNonce == serverNonce) { "Invalid server nonce" }
    val key = generateKeyFromNonce(serverNonce, newNonce)
    Napier.d("key=${key.first.contentToString()}, iv=${key.second.contentToString()}", tag = tag)
    val decryptedAnswer =
        AESPlatformImpl(AESMode.DECRYPT, key.first).doIGE(key.second, serverOuterDHParams.encryptedAnswer)
    val answer = ServerDHInnerDataObject.fromTlRepr(
        decryptedAnswer
            .sliceArray(20 until decryptedAnswer.size).toIntArray()
    )
    val checksum = decryptedAnswer.sliceArray(20 until answer!!.first * Int.SIZE_BYTES + 20).sha1()
    require(checksum.contentEquals(decryptedAnswer.sliceArray(0 until 20))) { "Corrupt DH params" }

    val serverDHParams = answer.second
    Napier.d("serverDHParams=$serverDHParams", tag = tag)
    require(serverDHParams.nonce == nonce) { "Invalid nonce" }
    require(serverDHParams.serverNonce == serverNonce) { "Invalid server nonce" }

    // Maths-y stuff https://github.com/LonamiWebs/Telethon/blob/cd4b915522a7b3d1bd9f392228812a531b58ff7e/telethon/network/authenticator.py#L120
    val dhPrime = BigInteger(byteArrayOf(0) + serverDHParams.dhPrime)
    // Must be 1-billionth error probability
    require(dhPrime.isProbablePrime(30)) { "Server sent invalid dhPrime" }
    require(((dhPrime - BigInteger.ONE) / BigInteger.TWO).isProbablePrime(30)) { "Server sent invalid dhPrime" }
    require(dhPrime.bitLength == 2048) { "Server sent invalid dhPrime" }
    require(
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
    return Triple(Triple(g, ga, dhPrime), key, serverDHParams.serverTime)
}

internal fun step6(
    random: (Int) -> ByteArray,
    g: BigInteger,
    ga: BigInteger,
    dhPrime: BigInteger,
    nonce: BigInteger,
    serverNonce: BigInteger,
    key: Pair<ByteArray, ByteArray>,
    retryId: Long = 0
): Pair<SetClientDHParamsRequest, AuthKey> {
    val b = BigInteger(byteArrayOf(0) + random(256)) // Unsigned
    val gb = g.modPow(b, dhPrime)!!
    if (gb <= BigInteger.ONE || gb >= dhPrime - BigInteger.ONE) error("Invalid gb")
    val gab = ga.modPow(b, dhPrime)!!
    val authKey = AuthKey(gab)
    val clientDHParams = ClientDHInnerDataObject(nonce, serverNonce, retryId, gb.toByteArray(256))
    Napier.d("clientDHParams=$clientDHParams")
    val clientDHParamsRepr = clientDHParams.toTlRepr().toByteArray()
    val clientDHParamsHashed = clientDHParamsRepr.sha1() + clientDHParamsRepr
    val clientDHParamsEncrypted = AESPlatformImpl(AESMode.ENCRYPT, key.first)
        .doIGE(key.second, clientDHParamsHashed, random)
    return Pair(SetClientDHParamsRequest(nonce, serverNonce, clientDHParamsEncrypted), authKey)
}

internal fun step9(newNonce: BigInteger, dhGen: SetClientDHParamsAnswerType, authKey: AuthKey): Long? {
    Napier.d("dhGen=$dhGen")

    when (dhGen) {
        is DhGenOkObject -> {
            validateNewNonceHash(dhGen.newNonceHash1, 1, newNonce, authKey)
            return null
        }
        is DhGenRetryObject -> {
            validateNewNonceHash(dhGen.newNonceHash2, 2, newNonce, authKey)
            Napier.d("Retrying DH Gen")
            return authKey.auxHash
        }
        is DhGenFailObject -> {
            validateNewNonceHash(dhGen.newNonceHash3, 3, newNonce, authKey)
            error("Server failure in DH Gen")
        }
        else -> error("Unknown DH Gen answer")
    }
}

internal suspend fun authenticate(client: TelegramClient, plaintextEncoder: MTProtoEncoder): AuthKey {
    val nonce = getSecureNonce(secureRandom = client.secureRandom)
    val pqRes = client.send(step1(nonce), plaintextEncoder) as ResPQObject
    val pq = step2(nonce, pqRes)
    val factors = step3(pq)
    val newNonce = getSecureNonce(256, client.secureRandom)
    val serverOuterDHParams = client.send(step4(newNonce, factors, pqRes) { Random.nextBytes(it) }, plaintextEncoder)
    Napier.d("serverDhParams=$serverOuterDHParams", tag = tag)
    val secrets = step5(serverOuterDHParams, nonce, newNonce, pqRes.serverNonce)
    plaintextEncoder.state.updateTimeOffset(secrets.third)
    var retryId = 0L
    while (true) {
        val step6ret = step6(
            { Random.nextBytes(it) },
            secrets.first.first,
            secrets.first.second,
            secrets.first.third,
            nonce,
            pqRes.serverNonce,
            secrets.second,
            retryId
        )
        val dhGen = client.send(step6ret.first, plaintextEncoder)
        val step9ret = step9(newNonce, dhGen, step6ret.second)
        if (step9ret == null)
            return step6ret.second
        else
            retryId = step9ret
    }
}

internal fun validateNewNonceHash(newNonceHash: BigInteger, i: Int, newNonce: BigInteger, authKey: AuthKey) {
    val data =
        newNonce.asTlObject256().toTlRepr().toByteArray() + i.toByte() + authKey.auxHash.asTlObject().toTlRepr().toByteArray()
    val hash = data.sha1()
    require(hash.sliceArray(4 until 20).reversedArray().contentEquals(newNonceHash.toByteArray())) { "Invalid new nonce hash" }
}

internal fun getSecureNonce(bits: Int = 128, secureRandom: Random): BigInteger {
    var nonce = BigInteger(bits - 1, secureRandom)
    if (secureRandom.nextBoolean())
        nonce = -nonce
    return nonce
}