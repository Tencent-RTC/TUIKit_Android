package com.tencent.rtcube.v2.mine.store

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import com.tencent.imsdk.v2.V2TIMCallback
import com.tencent.imsdk.v2.V2TIMManager
import com.tencent.imsdk.v2.V2TIMSDKListener
import com.tencent.imsdk.v2.V2TIMUserFullInfo
import com.tencent.imsdk.v2.V2TIMValueCallback
import com.tencent.rtcube.v2.login.LoginEntry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

class MineStore {

    private val _state = MutableStateFlow(MineState())
    val state: StateFlow<MineState> = _state.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var userObserveJob: Job? = null
    private var selfInfoListener: V2TIMSDKListener? = null

    var onLogoutResult: ((Result<Unit>) -> Unit)? = null
    var onLogOffResult: ((Result<Unit>) -> Unit)? = null

    fun loadUserInfo() {
        if (userObserveJob?.isActive == true) return
        userObserveJob = scope.launch {
            LoginEntry.currentUser.collect { user ->
                _state.update {
                    it.copy(
                        userId = user?.userId.orEmpty(),
                        nickname = user?.name.orEmpty(),
                        avatarUrl = user?.avatar.orEmpty(),
                    )
                }
            }
        }
        registerSelfInfoListener()
    }

    fun showLogoutDialog() {
        _state.update { it.copy(isLogoutDialogVisible = true) }
    }

    fun hideLogoutDialog() {
        _state.update { it.copy(isLogoutDialogVisible = false) }
    }

    fun showStatementDialog() {
        _state.update { it.copy(isStatementDialogVisible = true) }
    }

    fun hideStatementDialog() {
        _state.update { it.copy(isStatementDialogVisible = false) }
    }

    fun showAbout() {
        _state.update { it.copy(isAboutVisible = true) }
    }

    fun hideAbout() {
        _state.update { it.copy(isAboutVisible = false) }
    }

    fun showExperience() {
        _state.update { it.copy(isExperienceVisible = true) }
    }

    fun hideExperience() {
        _state.update { it.copy(isExperienceVisible = false) }
    }

    fun showLogOff() {
        _state.update { it.copy(isLogOffVisible = true) }
    }

    fun hideLogOff() {
        _state.update { it.copy(isLogOffVisible = false) }
    }

    fun showLogOffDialog() {
        _state.update { it.copy(isLogOffDialogVisible = true) }
    }

    fun hideLogOffDialog() {
        _state.update { it.copy(isLogOffDialogVisible = false) }
    }

    fun getSDKVersion(): String {
        return try {
            com.tencent.rtmp.TXLiveBase.getSDKVersionStr()
        } catch (e: Exception) {
            ""
        }
    }

    fun getAppVersion(context: Context): String {
        return try {
            val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(context.packageName, 0)
            }
            info.versionName ?: ""
        } catch (e: PackageManager.NameNotFoundException) {
            ""
        }
    }

    fun logOff() {
        _state.update { it.copy(isLogOffLoading = true, isLogOffDialogVisible = false) }
        LoginEntry.logoff { result ->
            _state.update { it.copy(isLogOffLoading = false) }
            onLogOffResult?.invoke(result)
        }
    }

    fun openIcpUrl(context: Context) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://beian.miit.gov.cn/#/home"))
        context.startActivity(intent)
    }

    fun getCurrentUserId(): String {
        return LoginEntry.currentUser.value?.userId ?: ""
    }

    fun logout() {
        _state.update { it.copy(isLoggingOut = true, isLogoutDialogVisible = false) }
        LoginEntry.logout { result ->
            _state.update { it.copy(isLoggingOut = false) }
            onLogoutResult?.invoke(result)
        }
    }

    suspend fun loadSelfInfo(): V2TIMUserFullInfo? {
        val selfId = V2TIMManager.getInstance().loginUser ?: return null
        return fetchUserInfo(selfId)
    }

    fun commitSelfInfo(
        faceUrl: String,
        nickname: String,
        signature: String,
        gender: Int,
        birthday: Long,
    ) {
        val info = V2TIMUserFullInfo().apply {
            if (faceUrl.isNotEmpty()) setFaceUrl(faceUrl)
            setNickname(nickname)
            if (birthday != 0L) setBirthday(birthday)
            setSelfSignature(signature)
            setGender(gender)
        }
        scope.launch {
            val error = setSelfInfoSuspend(info)
            if (error != null) {
                _state.update { it.copy(toastMessage = error) }
            } else {
                _state.update {
                    it.copy(
                        nickname = nickname,
                        avatarUrl = faceUrl,
                    )
                }
            }
        }
    }

    private fun registerSelfInfoListener() {
        if (selfInfoListener != null) return
        val listener = object : V2TIMSDKListener() {
            override fun onSelfInfoUpdated(info: V2TIMUserFullInfo) {
                _state.update {
                    it.copy(
                        nickname = info.nickName.orEmpty(),
                        avatarUrl = info.faceUrl.orEmpty(),
                    )
                }
            }
        }
        selfInfoListener = listener
        V2TIMManager.getInstance().addIMSDKListener(listener)
    }

    private fun unregisterSelfInfoListener() {
        selfInfoListener?.let { V2TIMManager.getInstance().removeIMSDKListener(it) }
        selfInfoListener = null
    }

    private suspend fun fetchUserInfo(userId: String): V2TIMUserFullInfo? =
        suspendCoroutine { cont ->
            V2TIMManager.getInstance().getUsersInfo(
                listOf(userId),
                object : V2TIMValueCallback<List<V2TIMUserFullInfo>> {
                    override fun onSuccess(result: List<V2TIMUserFullInfo>) {
                        cont.resume(result.firstOrNull())
                    }

                    override fun onError(code: Int, desc: String?) {
                        cont.resume(null)
                    }
                },
            )
        }

    private suspend fun setSelfInfoSuspend(info: V2TIMUserFullInfo): String? =
        suspendCoroutine { cont ->
            V2TIMManager.getInstance().setSelfInfo(
                info,
                object : V2TIMCallback {
                    override fun onSuccess() {
                        cont.resume(null)
                    }

                    override fun onError(code: Int, desc: String?) {
                        cont.resume("code=$code, $desc")
                    }
                },
            )
        }

    fun clearToast() {
        _state.update { it.copy(toastMessage = "") }
    }

    fun destroy() {
        unregisterSelfInfoListener()
        scope.cancel()
    }
}
