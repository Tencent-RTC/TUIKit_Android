package com.tencent.rtcube.assembly

import android.content.Context
import com.tencent.rtcube.v2.login.components.model.UserModel

/**
 * External environment and parameters required by modules.
 *
 * Built and injected by the shell app at startup; modules receive it via [ModuleProvider.setup].
 *
 * @property context Android ApplicationContext.
 * @property flavor Current build variant ("rtcube" domestic / "tencentrtc" overseas).
 * @property liveLicenseUrl Beauty License URL.
 * @property liveLicenseKey Beauty License Key.
 * @property karaokeLicenseKey Online karaoke (copyrighted music) License Key.
 * @property karaokeLicenseUrl Online karaoke (copyrighted music) License URL.
 * @property getCurrentUserModel Closure that dynamically returns the current logged-in user.
 * @property generateUserSig External UserSig generator; takes userId and returns userSig.
 */
data class ModuleEnvironment(
    val context: Context,
    val appTarget: AppTarget,
    val liveLicenseKey: String = "",
    val liveLicenseUrl: String = "",
    val karaokeLicenseKey: String = "",
    val karaokeLicenseUrl: String = "",
    val getCurrentUserModel: () -> UserModel? = { null },
) {
    companion object {
        fun default(context: Context): ModuleEnvironment {
            return ModuleEnvironment(
                context = context.applicationContext,
                appTarget = AppTarget.DOMESTIC,
            )
        }
    }
}
