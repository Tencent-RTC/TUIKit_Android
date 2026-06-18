package com.tencent.rtcube.v2.privacy.store

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.tencent.qcloud.tuicore.util.SPUtils
import com.tencent.rtcube.v2.login.LoginEntry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

const val SP_BEAUTY_AUTH = "beauty_auth"
const val BEAUTY_AUTH_STATUS = "beauty_status"
const val AVATAR_AUTH_STATUS = "avatar_status"
const val NOT_AUTH = 0
const val AUTH_ALLOW = 1
const val AUTH_DENY = 2

class PrivacyStore {

    private val _state = MutableStateFlow(PrivacyState())
    val state: StateFlow<PrivacyState> = _state.asStateFlow()

    fun loadUserInfo() {
        val user = LoginEntry.currentUser.value ?: return
        _state.update {
            it.copy(
                userId = user.userId,
                userName = user.name,
                userAvatar = user.avatar,
                phone = user.phone,
                email = user.email,
            )
        }
    }

    fun refreshPermissions(context: Context) {
        val cameraGranted =
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        val storageGranted = ContextCompat.checkSelfPermission(context,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        ) == PackageManager.PERMISSION_GRANTED
        val micGranted =
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        val beautyAllowed = SPUtils.getInstance(SP_BEAUTY_AUTH).getInt(BEAUTY_AUTH_STATUS, NOT_AUTH) == AUTH_ALLOW
        val avatarAllowed = SPUtils.getInstance(SP_BEAUTY_AUTH).getInt(AVATAR_AUTH_STATUS, NOT_AUTH) == AUTH_ALLOW
        _state.update {
            it.copy(
                cameraGranted = cameraGranted,
                storageGranted = storageGranted,
                micGranted = micGranted,
                beautyAllowed = beautyAllowed,
                avatarAllowed = avatarAllowed,
            )
        }
    }

    fun setBeautyAllowed(allowed: Boolean) {
        SPUtils.getInstance(SP_BEAUTY_AUTH).put(BEAUTY_AUTH_STATUS, if (allowed) AUTH_ALLOW else AUTH_DENY)
        _state.update { it.copy(beautyAllowed = allowed) }
    }

    fun setAvatarAllowed(allowed: Boolean) {
        SPUtils.getInstance(SP_BEAUTY_AUTH).put(AVATAR_AUTH_STATUS, if (allowed) AUTH_ALLOW else AUTH_DENY)
        _state.update { it.copy(avatarAllowed = allowed) }
    }

    fun launchAppSettings(context: Context) {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
        intent.setData(Uri.parse("package:" + context.packageName))
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (context.packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY).isNotEmpty()) {
            context.startActivity(intent)
        }
    }

    fun openUrl(context: Context, url: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        context.startActivity(intent)
    }
}
