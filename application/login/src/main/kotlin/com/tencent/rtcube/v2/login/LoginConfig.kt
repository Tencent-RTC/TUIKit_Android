package com.tencent.rtcube.v2.login

/**
 * Login module configuration.
 */
data class LoginConfig(
    /** Base URL of production server (required). */
    val httpBaseUrl: String = "",

    /** Base URL of test server (optional). */
    val testBaseUrl: String = "",

    /**
     * SDKAPPID (default 0).
     * - Production: injected from host app (`BuildConfig.SDKAPPID`).
     * - Debug: injected from host app (`BuildConfig.DEBUG_SDKAPPID`).
     */
    val sdkAppId: Int = 0,

    /** aPaaS application ID. */
    val apaasAppId: String = "",

    /** Production secret key, used to generate UserSig, injected from `BuildConfig.SECRETKEY`. */
    val secretKey: String = "",

    /** Debug SDKAPPID, injected from `BuildConfig.DEBUG_SDKAPPID`, paired with [debugSecretKey]. */
    val debugSdkAppId: Int = 0,

    /** Debug secret key, injected from `BuildConfig.DEBUG_SECRETKEY`, paired with [debugSdkAppId]. */
    val debugSecretKey: String = "",
) {
    companion object {
        val default = LoginConfig(
            httpBaseUrl = "",
            testBaseUrl = "",
            apaasAppId = "",
            sdkAppId = 0,
            secretKey = "",
            debugSdkAppId = 0,
            debugSecretKey = "",
        )
    }
}
