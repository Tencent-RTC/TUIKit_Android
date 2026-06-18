package com.tencent.rtcube.modules

import android.content.Context
import android.util.Log
import com.tencent.rtcube.v2.login.LoginEntry
import com.tencent.rtcube.v2.login.components.model.UserModel
import io.trtc.tuikit.atomicxcore.api.CompletionHandler
import io.trtc.tuikit.atomicxcore.api.login.LoginStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

object AtomicXCoreLogin {
    private const val TAG = "AtomicXCoreLogin"
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var collectJob: Job? = null

    fun startAutoLogin(context: Context) {
        if (collectJob?.isActive == true) return
        val appContext = context.applicationContext
        collectJob = scope.launch {
            LoginEntry.currentUser
                .distinctUntilChanged { old, new -> old.isSameLoginCredential(new) }
                .collect { userModel ->
                    if (userModel != null) {
                        loginAtomicX(appContext, userModel)
                    } else {
                        logoutAtomicX()
                    }
                }
        }
    }

    fun stopAutoLogin() {
        collectJob?.cancel()
        collectJob = null
    }

    private fun loginAtomicX(context: Context, userModel: UserModel) {
        val sdkAppId = LoginEntry.config.sdkAppId
        if (sdkAppId == 0 || userModel.userId.isEmpty() || userModel.userSig.isEmpty()) {
            Log.w(TAG, "skip login: sdkAppId=$sdkAppId, userId=${userModel.userId}, userSigEmpty=${userModel.userSig.isEmpty()}")
            return
        }
        Log.i(TAG, "login: sdkAppId=$sdkAppId, userId=${userModel.userId}")
        LoginStore.shared.login(
            context,
            sdkAppId,
            userModel.userId,
            userModel.userSig,
            object : CompletionHandler {
                override fun onSuccess() {
                    Log.i(TAG, "AtomicXCore login success")
                }

                override fun onFailure(code: Int, desc: String) {
                    Log.w(TAG, "AtomicXCore login failed: code=$code desc=$desc")
                }
            }
        )
    }

    private fun logoutAtomicX() {
        Log.i(TAG, "logout")
        LoginStore.shared.logout(object : CompletionHandler {
            override fun onSuccess() {
                Log.i(TAG, "AtomicXCore logout success")
            }

            override fun onFailure(code: Int, desc: String) {
                Log.w(TAG, "AtomicXCore logout failed: code=$code desc=$desc")
            }
        })
    }

    private fun UserModel?.isSameLoginCredential(other: UserModel?): Boolean {
        if (this == null && other == null) return true
        if (this == null || other == null) return false
        return userId == other.userId && userSig == other.userSig && token == other.token
    }
}
