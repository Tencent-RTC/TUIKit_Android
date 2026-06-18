package com.tencent.rtcube.v2.privacy.service

import android.app.Activity
import android.content.Context
import android.util.Log

import androidx.annotation.StringRes
import com.tencent.qcloud.tuicore.interfaces.TUIServiceCallback
import com.tencent.qcloud.tuicore.util.SPUtils
import com.tencent.rtcube.v2.R
import com.tencent.rtcube.v2.component.ConfirmDialog
import com.tencent.rtcube.v2.component.ShowTipDialog
import com.tencent.rtcube.v2.component.showComposeDialog
import com.tencent.rtcube.v2.privacy.store.AUTH_ALLOW
import com.tencent.rtcube.v2.privacy.store.AUTH_DENY
import com.tencent.rtcube.v2.privacy.store.BEAUTY_AUTH_STATUS
import com.tencent.rtcube.v2.privacy.store.NOT_AUTH
import com.tencent.rtcube.v2.privacy.store.SP_BEAUTY_AUTH

internal object PrivacyBeautyAuthDialog {
    private const val TAG = "PrivacyBeautyAuthDialog"

    /**
     * Show the beauty-feature authorization dialog.
     * @return true if already in ALLOW state; otherwise false - the dialog is shown and the final
     *         result is delivered via [callback].
     */
    fun showBeautyAuthDialog(context: Context?, callback: TUIServiceCallback?): Boolean {
        return showAuthDialog(
            context = context,
            callback = callback,
            statusKey = BEAUTY_AUTH_STATUS,
            requestMsgRes = R.string.privacy_beauty_face_request,
            notAuthMsgRes = R.string.privacy_beauty_face_not_auth,
        )
    }

    private fun showAuthDialog(
        context: Context?,
        callback: TUIServiceCallback?,
        statusKey: String,
        @StringRes requestMsgRes: Int,
        @StringRes notAuthMsgRes: Int,
    ): Boolean {
        val sp = SPUtils.getInstance(SP_BEAUTY_AUTH)

        when (sp.getInt(statusKey, NOT_AUTH)) {
            AUTH_ALLOW -> {
                callback?.onServiceCallback(AUTH_ALLOW, "", null)
                return true
            }

            AUTH_DENY -> {
                if (context != null) showNoAuthDialog(context, notAuthMsgRes)
                callback?.onServiceCallback(AUTH_DENY, "", null)
                return false
            }
        }

        val activity = (context as? Activity)?.takeIf { !it.isFinishing && !it.isDestroyed }
        if (activity == null) {
            Log.w(TAG, "showAuthDialog: no valid Activity, fallback to AUTH_ALLOW")
            sp.put(statusKey, AUTH_ALLOW)
            callback?.onServiceCallback(AUTH_ALLOW, "", null)
            return true
        }

        activity.showComposeDialog { dialog ->
            ConfirmDialog(
                message = activity.getString(requestMsgRes),
                negativeText = activity.getString(R.string.privacy_deny),
                positiveText = activity.getString(R.string.privacy_allow),
                onNegative = {
                    sp.put(statusKey, AUTH_DENY)
                    dialog.dismiss()
                    callback?.onServiceCallback(AUTH_DENY, "", null)
                },
                onPositive = {
                    sp.put(statusKey, AUTH_ALLOW)
                    dialog.dismiss()
                    callback?.onServiceCallback(AUTH_ALLOW, "", null)
                },
            )
        }
        return false
    }

    private fun showNoAuthDialog(context: Context, @StringRes messageRes: Int) {
        val activity = (context as? Activity)?.takeIf { !it.isFinishing && !it.isDestroyed } ?: return
        activity.showComposeDialog { dialog ->
            ShowTipDialog(
                message = activity.getString(messageRes),
                confirmText = activity.getString(android.R.string.ok),
                onDismiss = { dialog.dismiss() },
            )
        }
    }
}
