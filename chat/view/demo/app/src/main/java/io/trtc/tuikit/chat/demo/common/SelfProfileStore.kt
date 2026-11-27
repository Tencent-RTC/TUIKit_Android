package io.trtc.tuikit.chat.demo.common

import io.trtc.tuikit.atomicxcore.api.CompletionHandler
import io.trtc.tuikit.atomicxcore.api.login.LoginStore
import io.trtc.tuikit.atomicxcore.api.login.UserProfile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

object SelfProfileStore {

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val _profile = MutableStateFlow(readProfile())
    val profile: StateFlow<UserProfile?> = _profile.asStateFlow()

    init {
        scope.launch {
            LoginStore.shared.loginState.loginUserInfo.collect { userInfo ->
                _profile.value = userInfo?.copy()
            }
        }
    }

    fun setSelfInfo(profile: UserProfile, completion: CompletionHandler? = null) {
        LoginStore.shared.setSelfInfo(profile, object : CompletionHandler {
            override fun onSuccess() {
                mergeLocal(profile)
                completion?.onSuccess()
            }

            override fun onFailure(code: Int, desc: String) {
                completion?.onFailure(code, desc)
            }
        })
    }

    private fun mergeLocal(change: UserProfile) {
        val current = _profile.value ?: readProfile() ?: return
        _profile.value = current.copy(
            nickname = change.nickname ?: current.nickname,
            avatarURL = change.avatarURL ?: current.avatarURL,
            selfSignature = change.selfSignature ?: current.selfSignature,
            gender = change.gender ?: current.gender,
            role = change.role ?: current.role,
            level = change.level ?: current.level,
            birthday = change.birthday ?: current.birthday,
            allowType = change.allowType ?: current.allowType,
            customInfo = change.customInfo ?: current.customInfo
        )
    }

    private fun readProfile(): UserProfile? =
        LoginStore.shared.loginState.loginUserInfo.value?.copy()
}
