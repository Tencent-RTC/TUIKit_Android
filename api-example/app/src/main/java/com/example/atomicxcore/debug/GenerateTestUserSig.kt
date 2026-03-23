package com.example.atomicxcore.debug

import android.util.Base64
import org.json.JSONObject
import java.nio.charset.StandardCharsets
import java.util.zip.Deflater
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Tencent Cloud SDKAppId, which needs to be replaced with the SDKAppId under your own account.
 *
 * Enter Tencent Cloud IM to create an application, and you can see the SDKAppId,
 * which is the unique identifier used by Tencent Cloud to distinguish customers.
 */
const val SDKAPPID: Long = 0

/**
 * Encryption key used for calculating the signature.
 *
 * Note: This solution is only applicable to debugging demos.
 * Before going online officially, please migrate the UserSig calculation code and keys
 * to your backend server to avoid traffic theft caused by encryption key leakage.
 */
const val SECRETKEY = ""

/**
 * Signature expiration time, it is recommended not to set it too short
 *
 * Time unit: seconds
 * Default time: 7 x 24 x 60 x 60 = 604800 = 7 days
 */
const val EXPIRETIME: Long = 604_800

/**
 * Local UserSig generation utility
 * Uses HmacSHA256 + zlib compression + Base64URL encoding
 * Corresponds to iOS's GenerateTestUserSig.swift
 *
 * Note: For debugging only; UserSig must be generated server-side in production
 */
object GenerateTestUserSig {

    fun genTestUserSig(identifier: String): String {
        val currentTime = System.currentTimeMillis() / 1000

        val jsonObject = JSONObject().apply {
            put("TLS.ver", "2.0")
            put("TLS.identifier", identifier)
            put("TLS.sdkappid", SDKAPPID)
            put("TLS.expire", EXPIRETIME)
            put("TLS.time", currentTime)
        }

        // Concatenate the signature string in order
        val stringToSign = buildString {
            append("TLS.identifier:$identifier\n")
            append("TLS.sdkappid:$SDKAPPID\n")
            append("TLS.time:$currentTime\n")
            append("TLS.expire:$EXPIRETIME\n")
        }

        // HmacSHA256 signature
        val sig = hmacSHA256(stringToSign, SECRETKEY)
        jsonObject.put("TLS.sig", sig)

        // JSON → zlib compression → Base64URL encoding
        val jsonBytes = jsonObject.toString().toByteArray(StandardCharsets.UTF_8)
        val compressedBytes = compress(jsonBytes)
        return base64URL(compressedBytes)
    }

    /**
     * HmacSHA256 signature
     */
    private fun hmacSHA256(data: String, key: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        val secretKeySpec = SecretKeySpec(
            key.toByteArray(StandardCharsets.US_ASCII),
            "HmacSHA256"
        )
        mac.init(secretKeySpec)
        val signBytes = mac.doFinal(data.toByteArray(StandardCharsets.US_ASCII))
        return Base64.encodeToString(signBytes, Base64.NO_WRAP)
    }

    /**
     * zlib compression
     */
    private fun compress(data: ByteArray): ByteArray {
        val deflater = Deflater()
        deflater.setInput(data)
        deflater.finish()

        val buffer = ByteArray(data.size + 64)
        val compressedSize = deflater.deflate(buffer)
        deflater.end()

        return buffer.copyOf(compressedSize)
    }

    /**
     * Base64URL encoding (replace +/= with *-_)
     */
    private fun base64URL(data: ByteArray): String {
        val base64 = Base64.encodeToString(data, Base64.NO_WRAP)
        return base64
            .replace('+', '*')
            .replace('/', '-')
            .replace('=', '_')
    }
}
