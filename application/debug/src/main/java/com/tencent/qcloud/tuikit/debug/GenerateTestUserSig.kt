package com.tencent.qcloud.tuikit.debug

import android.util.Base64
import org.json.JSONObject
import java.nio.charset.StandardCharsets
import java.util.zip.Deflater
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/*
 *
 *
 * Description: Generates UserSig for testing. UserSig is a security signature designed
 *           by Tencent Cloud for its cloud services.
 *           It is calculated based on `SDKAppID`, `UserID`,
 *           and `EXPIRETIME` using the HMAC-SHA256 encryption algorithm.
 *
 * Attention: For the following reasons, do not use the code below in your commercial application.
 *
 *            The code may be able to calculate UserSig correctly, b
 *            ut it is only for quick testing of the SDK’s basic features,
 *            not for commercial applications.
 *            `SECRETKEY` in client code can be easily decompiled and reversed, especially on web.
 *            Once your key is disclosed, attackers will be able to steal your Tencent Cloud traffic.
 *
 *            The correct method is to deploy the `UserSig` calculation code and encryption key on your server
 *            so that your application can request a `UserSig` from your server,
 *            which will calculate it whenever one is needed.
 *            Given that it is more difficult to hack a server than a client application,
 *            server-end calculation can better protect your key.
 *
 * Reference: https://cloud.tencent.com/document/product/269/32688#Server
 */
object GenerateTestUserSig {

    /**
     * Tencent Cloud SDKAppID. Set it to the SDKAppID of your account.
     * <p>
     * You can view your `SDKAppId` after creating an application in the [Tencent Cloud IM console](https://console.cloud.tencent.com/avc).
     * SDKAppID uniquely identifies a Tencent Cloud account.
     */
    const val SDKAPPID: Int = 0

    /**
     * Signature validity period, which should not be set too short
     * <p>
     * Unit: Second
     * Default value: 604800 (seven days)
     */
    private const val EXPIRETIME: Long = 604_800

    /**
     * Follow the steps below to obtain the key required for UserSig calculation.
     * <p>
     * Step 1. Log in to the [IM console](https://console.cloud.tencent.com/avc). If you don't have an application yet, create one.
     * Step 2. Click your application and find “Basic Information”.
     * Step 3. Click “Display Key” to view the key used for UserSig calculation.
     * Copy and paste the key to the variable below.
     * <p>
     * Note: This method is for testing only. Before commercial launch,
     * please migrate the UserSig calculation code and key to your backend server
     * to prevent key disclosure and traffic stealing.
     * Documentation: https://cloud.tencent.com/document/product/269/32688#Server
     */
    private const val SECRETKEY = ""

    const val TENCENT_EFFECT_LICENSE_URL = ""

    const val TENCENT_EFFECT_LICENSE_KEY = ""

    /**
     * Calculating UserSig
     * <p>
     * The asymmetric encryption algorithm HMAC-SHA256 is used in the
     * function to calculate UserSig based on `SDKAppID`, `UserID`, and `EXPIRETIME`.
     *
     * @note: For the following reasons, do not use the code below in your commercial application.
     * <p>
     * The code may be able to calculate UserSig correctly,
     * but it is only for quick testing of the SDK’s basic features,
     * not for commercial applications.
     * SECRETKEY in client code can be easily decompiled and reversed, especially on web.
     * Once your key is disclosed, attackers will be able to steal your Tencent Cloud traffic.
     * <p>
     * The correct method is to deploy the `UserSig` calculation code and encryption key on your server
     * so that your application can request a `UserSig` from your server,
     * which will calculate it whenever one is needed.
     * Given that it is more difficult to hack a server than a client application,
     * server-end calculation can better protect your key.
     * <p>
     * Documentation: https://cloud.tencent.com/document/product/269/32688#Server
     */
    @JvmStatic
    fun genTestUserSig(userId: String): String =
        genTLSSignature(SDKAPPID.toLong(), userId, EXPIRETIME, null, SECRETKEY)

    /**
     * Generating a TLS Ticket
     *
     * @param sdkappid      `appid` of your application
     * @param userId        User ID
     * @param expire        Validity period in seconds
     * @param userbuf       `null` by default
     * @param priKeyContent Private key required for generating a TLS ticket
     * @return If an error occurs, an empty string will be returned or exceptions printed.
     * If the operation succeeds, a valid ticket will be returned.
     */
    private fun genTLSSignature(
        sdkappid: Long,
        userId: String,
        expire: Long,
        userbuf: ByteArray?,
        priKeyContent: String,
    ): String {
        if (priKeyContent.isEmpty()) return ""

        val currTime = System.currentTimeMillis() / 1000
        val sigDoc = JSONObject().apply {
            put("TLS.ver", "2.0")
            put("TLS.identifier", userId)
            put("TLS.sdkappid", sdkappid)
            put("TLS.expire", expire)
            put("TLS.time", currTime)
        }
        val base64UserBuf = userbuf?.let {
            Base64.encodeToString(it, Base64.NO_WRAP).also { encoded ->
                sigDoc.put("TLS.userbuf", encoded)
            }
        }
        val sig = hmacsha256(sdkappid, userId, currTime, expire, priKeyContent, base64UserBuf)
        if (sig.isEmpty()) return ""
        sigDoc.put("TLS.sig", sig)

        val compressor = Deflater()
        compressor.setInput(sigDoc.toString().toByteArray(StandardCharsets.UTF_8))
        compressor.finish()
        val compressedBytes = ByteArray(2048)
        val compressedBytesLength = compressor.deflate(compressedBytes)
        compressor.end()
        return String(
            base64EncodeUrl(compressedBytes.copyOfRange(0, compressedBytesLength)),
            StandardCharsets.UTF_8,
        )
    }

    private fun hmacsha256(
        sdkappid: Long,
        userId: String,
        currTime: Long,
        expire: Long,
        priKeyContent: String,
        base64Userbuf: String?,
    ): String {
        val contentToBeSigned = buildString {
            append("TLS.identifier:$userId\n")
            append("TLS.sdkappid:$sdkappid\n")
            append("TLS.time:$currTime\n")
            append("TLS.expire:$expire\n")
            if (base64Userbuf != null) append("TLS.userbuf:$base64Userbuf\n")
        }
        return try {
            val byteKey = priKeyContent.toByteArray(StandardCharsets.UTF_8)
            val hmac = Mac.getInstance("HmacSHA256")
            hmac.init(SecretKeySpec(byteKey, "HmacSHA256"))
            val byteSig = hmac.doFinal(contentToBeSigned.toByteArray(StandardCharsets.UTF_8))
            Base64.encodeToString(byteSig, Base64.NO_WRAP)
        } catch (e: Exception) {
            e.printStackTrace()
            ""
        }
    }

    private fun base64EncodeUrl(input: ByteArray): ByteArray {
        val base64 = Base64.encodeToString(input, Base64.NO_WRAP).toByteArray()
        for (i in base64.indices) {
            when (base64[i].toInt().toChar()) {
                '+' -> base64[i] = '*'.code.toByte()
                '/' -> base64[i] = '-'.code.toByte()
                '=' -> base64[i] = '_'.code.toByte()
            }
        }
        return base64
    }
}
