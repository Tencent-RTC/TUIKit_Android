package com.tencent.rtcube.modules.room

import android.content.Context
import android.content.Intent
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.Toast
import com.trtc.uikit.roomkit.R
import com.trtc.uikit.roomkit.base.avatar.RoomAvatar
import androidx.appcompat.widget.AppCompatTextView
import com.trtc.uikit.roomkit.base.dialog.RoomDialog
import com.trtc.uikit.roomkit.base.error.ErrorLocalized
import com.trtc.uikit.roomkit.base.util.getDisplayName
import io.trtc.tuikit.atomicxcore.api.CompletionHandler
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
    private var profileSheet: RoomHomeLogoutSheet? = null

    var onLoggedOut: (() -> Unit)? = null

    var onOpenSettings: (() -> Unit)? = null

    init {
        LayoutInflater.from(context).inflate(R.layout.roomkit_home_view, this, true)
        avatar = findViewById(R.id.avatar)
        titleText = findViewById(R.id.home_title)
        findViewById<View>(R.id.quick_meeting_button).setOnClickListener {
            context.startActivity(Intent(context, RoomQuickStartActivity::class.java))
        }
        findViewById<View>(R.id.join_button).setOnClickListener {
            context.startActivity(Intent(context, RoomJoinActivity::class.java))
        }
        findViewById<View>(R.id.schedule_button).setOnClickListener {
            Toast.makeText(context, R.string.roomkit_home_coming_soon, Toast.LENGTH_SHORT).show()
        }
        avatar.setOnClickListener { showProfileSheet() }
    }

    private fun showProfileSheet() {
        if (profileSheet != null) return
        val sheet = RoomHomeLogoutSheet(context)
        sheet.onSettings = {
            closeProfileSheet()
            openSettings()
        }
        sheet.onLogout = {
            closeProfileSheet()
            showLogoutDialog()
        }
        sheet.onDismiss = { profileSheet = null }
        profileSheet = sheet
        sheet.show()
    }

    private fun closeProfileSheet() {
        profileSheet?.dismiss()
        profileSheet = null
    }

    private fun openSettings() {
        val hook = onOpenSettings
        if (hook != null) {
            hook.invoke()
            return
        }
        Toast.makeText(context, R.string.roomkit_settings_open, Toast.LENGTH_SHORT).show()
    }

    private fun showLogoutDialog() {
        var dialog: RoomDialog? = null
        dialog = RoomDialog.builder(context)
            .title(context.getString(R.string.roomkit_pre_room_logout))
            .message(context.getString(R.string.roomkit_pre_room_logout_confirm))
            .closable(false)
            .cancelable(false)
            .secondaryAction(context.getString(R.string.roomkit_room_common_cancel)) {}
            .primaryAction(context.getString(R.string.roomkit_pre_room_logout)) {
                dialog?.dismiss()
                logout()
            }
            .show()
    }

    private fun logout() {
        LoginStore.shared.logout(object : CompletionHandler {
            override fun onSuccess() {
                post {
                    if (!isAttachedToWindow) return@post
                    profileSheet?.dismiss()
                    profileSheet = null
                    Toast.makeText(context, R.string.roomkit_pre_room_logged_out, Toast.LENGTH_SHORT).show()
                    onLoggedOut?.invoke()
                }
            }

            override fun onFailure(code: Int, desc: String) {
                post {
                    if (!isAttachedToWindow) return@post
                    ErrorLocalized.show(context, code)
                }
            }
        })
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
