package com.tencent.rtcube.v2.privacy

import android.content.Context
import android.content.Intent
import androidx.core.content.edit
import com.tencent.rtcube.assembly.AppTarget
import com.tencent.rtcube.v2.App
import com.tencent.rtcube.v2.AppTargetResolver
import com.tencent.rtcube.v2.privacy.service.FirstLaunchPrivacyAlertDialog

object PrivacyEntry {
    private val isOverseas: Boolean
        get() = AppTargetResolver.getAppTarget() == AppTarget.OVERSEAS

    const val userAgreementURL: String = "https://rule.tencent.com/rule/preview/2b809db2-e870-4f20-8443-d319fdfb6a12"
    const val privacySummaryURL: String = "https://privacy.qq.com/document/preview/55d060f5059945dcb49ac844edc93889"
    val privacyProtectURL: String get() = if (isOverseas) "https://trtc.io/demo/privacy" else "https://privacy.qq.com/document/preview/5f422b2fed97475dbfbb9a38aaf00fd9"
    const val dataCollectionListURL: String = "https://privacy.qq.com/document/preview/d1b6939dc49d4e83b084dbdfe1f2cdda"
    const val thirdShareURL: String = "https://privacy.qq.com/document/preview/10d30e3998d440629a8cdf5d95efae39"
    const val termsOfServiceURL: String = "https://trtc.io/app/service"

    private const val PREF_NAME = "rtcube_first_launch_privacy"
    private const val KEY_FIRST_LAUNCH_SHOWN = "per_user_first_open"

    fun pushPrivacyPage(type: PrivacyPageType, context: Context) {
        when (type) {
            PrivacyPageType.PrivacyCenter -> {
                val intent = Intent(context, PrivacyActivity::class.java)
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
            }

            PrivacyPageType.Privacy -> openUrl(context, privacyProtectURL)
            PrivacyPageType.PrivacySummary -> openUrl(context, privacySummaryURL)
            PrivacyPageType.Agreement -> openUrl(context, userAgreementURL)
            PrivacyPageType.DataCollection -> openUrl(context, dataCollectionListURL)
            PrivacyPageType.ThirdShare -> openUrl(context, thirdShareURL)
            PrivacyPageType.TermsOfService -> openUrl(context, termsOfServiceURL)
        }
    }

    private fun openUrl(context: Context, url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url))
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun showFirstLaunchPrivacyDialog(onAgree: () -> Unit = {}): Boolean {
        val activity = App.currentActivity ?: return false
        if (prefs(activity).getBoolean(KEY_FIRST_LAUNCH_SHOWN, false)) return false
        val shown = FirstLaunchPrivacyAlertDialog.show(activity = activity, onAgree = onAgree)
        if (shown) prefs(activity).edit { putBoolean(KEY_FIRST_LAUNCH_SHOWN, true) }
        return shown
    }

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
}
