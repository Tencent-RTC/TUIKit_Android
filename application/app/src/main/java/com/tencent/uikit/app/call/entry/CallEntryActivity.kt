package com.tencent.uikit.app.call.entry

import android.content.Intent
import android.os.Bundle
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import com.tencent.uikit.app.App
import com.tencent.uikit.app.R
import com.tencent.uikit.app.call.common.CallAccessToast
import com.tencent.uikit.app.call.common.WindowInsetsHelper
import com.tencent.uikit.app.call.settings.CallSettingsActivity
import com.tencent.uikit.app.main.BaseActivity
import com.tencent.qcloud.tuikit.tuicallkit.TUICallKit
import io.trtc.tuikit.atomicxcore.api.CompletionHandler
import io.trtc.tuikit.atomicxcore.api.call.CallMediaType
import io.trtc.tuikit.atomicxcore.api.call.CallParams
import io.trtc.tuikit.atomicxcore.api.device.DeviceStore
import io.trtc.tuikit.atomicxcore.api.device.VideoOrientation
import io.trtc.tuikit.atomicxcore.api.login.LoginStore

class CallEntryActivity : BaseActivity() {

    private lateinit var entryView: CallEntryView
    private lateinit var state: CallEntryState
    private var isCalling = false
    private var friendPicker: FriendPickerSheet? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.callaccess_entry_activity)
        WindowInsetsHelper.applyStatusBarInset(findViewById(R.id.header))
        findViewById<FrameLayout>(R.id.back_button).setOnClickListener { finish() }
        findViewById<ImageView>(R.id.back_icon).setColorFilter(ContextCompat.getColor(this, R.color.callaccess_brand))

        state = (application as App).callEntryState
        entryView = findViewById(R.id.entry_view)
        entryView.bind(state)
        WindowInsetsHelper.applyImeAndNavInset(entryView.findViewById(R.id.entry_scroll))
        WindowInsetsHelper.applyNavigationBarInset(entryView.findViewById(R.id.bottom_bar))
        applyFacadeSettings()
        bindActions()
    }

    override fun onResume() {
        super.onResume()
        entryView.reloadFromState()
    }

    private fun applyFacadeSettings() {
        val facade = TUICallKit.createInstance(this)
        facade.enableMuteMode(state.muteModeEnabled)
        facade.enableIncomingBanner(state.bannerOn)
        facade.enableFloatWindow(state.showFloatButton)
        facade.enableVirtualBackground(state.blurBackgroundOn)
        facade.enableAITranscriber(state.captionOn)
    }

    private fun bindActions() {
        entryView.onFriendsTap = { openFriendPicker() }
        entryView.onMoreSettingsTap = { startActivity(Intent(this, CallSettingsActivity::class.java)) }
        entryView.onStartCallTap = { startCall() }
    }

    private fun openFriendPicker() {
        entryView.commitPendingInput(isForCall = false)
        val picker = FriendPickerSheet()
        friendPicker = picker
        picker.present(this, findViewById(android.R.id.content)) {
            entryView.reloadFromState()
            friendPicker = null
        }
    }

    private fun startCall() {
        if (isCalling) {
            return
        }
        if (!entryView.commitPendingInput(isForCall = true)) {
            return
        }
        val selfID = LoginStore.shared.loginState.loginUserInfo.value?.userID
        val calleeIDs = state.userIDs.filter { it != selfID }
        if (calleeIDs.isEmpty()) {
            val messageRes = if (state.userIDs.isEmpty()) R.string.callaccess_call_empty_callee else R.string.callaccess_call_self_only
            CallAccessToast.show(this, getString(messageRes))
            return
        }
        if (state.media == CallEntryState.Media.VIDEO) {
            val quality = state.resolution.videoQuality
            if (quality == null) {
                CallAccessToast.show(this, getString(R.string.callaccess_call_2k_unsupported))
                return
            }
            DeviceStore.shared().updateVideoQuality(quality, VideoOrientation.PORTRAIT)
        }

        val params = CallParams()
        params.roomId = state.roomID
        params.timeout = state.timeoutSeconds
        params.userData = state.extendedInfo

        isCalling = true
        entryView.setCallButtonEnabled(false)
        val mediaType = if (state.media == CallEntryState.Media.VIDEO) CallMediaType.Video else CallMediaType.Audio
        TUICallKit.createInstance(this).calls(calleeIDs, mediaType, params, object : CompletionHandler {
            override fun onSuccess() {
                runOnUiThread {
                    isCalling = false
                    entryView.setCallButtonEnabled(true)
                    CallAccessToast.show(this@CallEntryActivity, getString(R.string.callaccess_call_calling))
                }
            }

            override fun onFailure(code: Int, desc: String) {
                runOnUiThread {
                    isCalling = false
                    entryView.setCallButtonEnabled(true)
                    CallAccessToast.show(this@CallEntryActivity, getString(R.string.callaccess_call_failed, "$code $desc"))
                }
            }
        })
    }
}
