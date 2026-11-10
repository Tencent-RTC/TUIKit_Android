package com.tencent.uikit.app.room

import com.tencent.uikit.app.R
import android.content.Context
import android.content.Intent
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.Toast
import com.trtc.uikit.roomkit.base.avatar.RoomAvatar
import androidx.appcompat.widget.AppCompatTextView
import com.trtc.uikit.roomkit.base.util.getDisplayName
import io.trtc.tuikit.atomicxcore.api.login.LoginStore
import io.trtc.tuikit.atomicxcore.api.login.UserProfile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class RoomHomeView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr) {

    private val avatar: RoomAvatar
    private val titleText: AppCompatTextView
    private var loginJob: Job? = null
    private var profilePopup: RoomHomeLogoutPopup? = null

    var onOpenSettings: (() -> Unit)? = null

    init {
        LayoutInflater.from(context).inflate(R.layout.app_home_view, this, true)
        avatar = findViewById(R.id.avatar)
        titleText = findViewById(R.id.home_title)
        findViewById<View>(R.id.quick_meeting_button).setOnClickListener {
            context.startActivity(Intent(context, RoomQuickStartActivity::class.java))
        }
        findViewById<View>(R.id.join_button).setOnClickListener {
            context.startActivity(Intent(context, RoomJoinActivity::class.java))
        }
        findViewById<View>(R.id.schedule_button).setOnClickListener {
            Toast.makeText(context, R.string.app_home_coming_soon, Toast.LENGTH_SHORT).show()
        }
        avatar.setOnClickListener { showProfilePopup() }
    }

    private fun showProfilePopup() {
        if (profilePopup != null) return
        val popup = RoomHomeLogoutPopup(context)
        popup.onSettings = {
            closeProfilePopup()
            openSettings()
        }
        popup.onDismiss = { profilePopup = null }
        profilePopup = popup
        popup.show()
    }

    private fun closeProfilePopup() {
        profilePopup?.dismiss()
        profilePopup = null
    }

    private fun openSettings() {
        val hook = onOpenSettings
        if (hook != null) {
            hook.invoke()
            return
        }
        Toast.makeText(context, R.string.app_settings_open, Toast.LENGTH_SHORT).show()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        observeUserInfo()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        loginJob?.cancel()
        loginJob = null
    }

    private fun observeUserInfo() {
        loginJob?.cancel()
        loginJob = CoroutineScope(Dispatchers.Main).launch {
            LoginStore.shared.loginState.loginUserInfo.collect { userInfo ->
                updateUserInfo(userInfo)
            }
        }
    }

    private fun updateUserInfo(userInfo: UserProfile?) {
        if (userInfo == null) return
        val displayName = userInfo.getDisplayName()
        titleText.text = displayName
        avatar.text = displayName
        avatar.url = userInfo.avatarURL
    }
}
