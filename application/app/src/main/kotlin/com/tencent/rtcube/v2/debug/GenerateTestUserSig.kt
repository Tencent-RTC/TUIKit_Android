package com.tencent.rtcube.v2.debug

import android.util.Base64
import org.json.JSONObject
import java.nio.charset.Charset
import java.util.zip.Deflater
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Open-source placeholder implementation: `SDKAPPID = 0`.
 *
 * Before using, please replace with your own `SDKAPPID` / `SECRETKEY` / License information.
 *
 * Usage:
 *   val userSig = GenerateTestUserSig.genTestUserSig(userId, sdkAppId, secretKey)
 *
 * Note: This solution is only applicable to debugging demos.
 * Before going online officially, please migrate the UserSig calculation code and keys
 * to your backend server to avoid traffic theft caused by encryption key leakage.
 */
object GenerateTestUserSig {

    /**
     * Tencent Cloud SDKAppId, which needs to be replaced with the SDKAppId under your own account.
     *
     * Enter Tencent Cloud IM to create an application, and you can see the SDKAppId,
     * which is the unique identifier used by Tencent Cloud to distinguish customers.
     */
    const val SDKAPPID: Int = 0

    /**
     * Encryption key used for calculating the signature, the steps to obtain it are as follows:
     *
     * step1. Enter Tencent Cloud IM, if you do not have an application yet, create one.
     * step2. Click "Application Configuration" to enter the basic configuration page,
     *        and further find the "Account System Integration" section.
     * step3. Click the "View Key" button, you can see the encryption key used to calculate UserSig,
     *        please copy and paste it into the following variable.
     */
    const val SECRETKEY: String = ""

    /** Debug SDKAppID (replace with your own) */
    const val DEBUG_SDKAPPID: Int = SDKAPPID

    /** Debug SecretKey (replace with your own) */
    const val DEBUG_SECRETKEY: String = SECRETKEY

    /**
     * Signature expiration time, it is recommended not to set it too short.
     *
     * Time unit: seconds.
     * Default time: 7 x 24 x 60 x 60 = 604800 = 7 days.
     */
    private const val EXPIRETIME = 604_800L

    /** Tencent Effect License (please replace with your applied License) */
    const val TENCENT_EFFECT_LICENSE_KEY = ""
    const val TENCENT_EFFECT_LICENSE_URL = ""

    /** Player License */
    const val PLAYER_LICENSE_KEY = ""
    const val PLAYER_LICENSE_URL = ""

    /** Live push License */
    const val LIVE_LICENSE_URL = ""
    const val LIVE_LICENSE_KEY = ""

    const val KARAOKE_LICENSE_KEY = ""
    const val KARAOKE_LICENSE_URL = ""

    /** Bugly crash reporting AppId (open-source version does not integrate Bugly) */
    const val BUGLY_APP_ID = ""

    /** Sensors Data server URL (open-source version does not integrate analytics) */
    const val SENSORS_SERVER_URL = ""

    /** Serverless test environment URL */
    const val BASE_URL_TEST_INTEL = ""
    const val BASE_URL_TEST: String = ""

    /** Production environment URL of the backend service */
    var BASE_URL: String = ""

    /** Multi-tenant parameter */
    var APAAS_APP_ID: String = ""

    /**
     * Generate a UserSig for the given SDKAppID and SecretKey.
     */
    fun genTestUserSig(userId: String, sdkAppId: Int, secretKey: String): String {
        if (sdkAppId == 0 || secretKey.isEmpty()) return ""
        return genTLSSignature(
            sdkAppId = sdkAppId.toLong(),
            userId = userId,
            expire = EXPIRETIME,
            key = secretKey
        )
    }

    private fun genTLSSignature(sdkAppId: Long, userId: String, expire: Long, key: String): String {
        val currentTime = System.currentTimeMillis() / 1000

        val jsonObject = JSONObject().apply {
            put("TLS.ver", "2.0")
            put("TLS.identifier", userId)
            put("TLS.sdkappid", sdkAppId)
            put("TLS.expire", expire)
            put("TLS.time", currentTime)
        }

        val signContent = "TLS.identifier:$userId\n" +
                "TLS.sdkappid:$sdkAppId\n" +
                "TLS.time:$currentTime\n" +
                "TLS.expire:$expire\n"

        val sig = hmacSha256(key, signContent)
        jsonObject.put("TLS.sig", sig)

        val compressor = Deflater()
        compressor.setInput(jsonObject.toString().toByteArray(Charset.forName("UTF-8")))
        compressor.finish()

        val compressedBytes = ByteArray(2048)
        val compressedLength = compressor.deflate(compressedBytes)
        compressor.end()

        return String(
            Base64.encode(
                compressedBytes.copyOfRange(0, compressedLength),
                Base64.NO_WRAP
            )
        ).replace('+', '*')
            .replace('/', '-')
            .replace('=', '_')
    }

    private fun hmacSha256(key: String, content: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        val secretKeySpec = SecretKeySpec(key.toByteArray(Charset.forName("UTF-8")), "HmacSHA256")
        mac.init(secretKeySpec)
        val hash = mac.doFinal(content.toByteArray(Charset.forName("UTF-8")))
        return String(Base64.encode(hash, Base64.NO_WRAP))
    }
}
