package com.tencent.rtcube.v2.login

import android.content.Context
import android.util.Log

object AgreementNavigator {

    private const val TAG = "AgreementNavigator"

    private const val LINK_PRIVACY = "privacy"
    private const val LINK_PRIVACY_SUMMARY = "privacySummary"
    private const val LINK_AGREEMENT = "agreement"
    private const val LINK_TERMS_OF_SERVICE = "termsOfService"

    fun openUserAgreement(context: Context) {
        dispatch(context, LINK_AGREEMENT)
    }

    fun openPrivacySummary(context: Context) {
        dispatch(context, LINK_PRIVACY_SUMMARY)
    }

    fun openPrivacyPolicy(context: Context) {
        dispatch(context, LINK_PRIVACY)
    }

    fun openTermsOfService(context: Context) {
        dispatch(context, LINK_TERMS_OF_SERVICE)
    }

    private fun dispatch(context: Context, linkType: String) {
        val handler = LoginEntry.privacyLinkHandler
        if (handler == null) {
            Log.w(TAG, "privacyLinkHandler not set, linkType=$linkType")
            return
        }
        handler.invoke(linkType, context)
    }
}
