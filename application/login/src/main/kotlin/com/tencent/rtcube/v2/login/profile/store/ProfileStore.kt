package com.tencent.rtcube.v2.login.profile.store

import android.util.Log
import com.tencent.rtcube.v2.login.LoginEntry
import com.tencent.rtcube.v2.login.R
import com.tencent.rtcube.v2.login.components.model.UserModel
import com.tencent.rtcube.v2.login.components.service.LoginManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.random.Random

class ProfileStore(private val userModel: UserModel) {

    companion object {
        private const val TAG = "ProfileStore"
        private val NICKNAME_REGEX = Regex("^[a-z0-9A-Z\\u4e00-\\u9fa5_]{2,20}$")

        private val CUSTOM_NAME_RES_IDS = intArrayOf(
            R.string.login_profile_custom_name_1, R.string.login_profile_custom_name_2,
            R.string.login_profile_custom_name_3, R.string.login_profile_custom_name_4,
            R.string.login_profile_custom_name_5, R.string.login_profile_custom_name_6,
            R.string.login_profile_custom_name_7, R.string.login_profile_custom_name_8,
            R.string.login_profile_custom_name_9, R.string.login_profile_custom_name_10,
        )
    }

    private val _state = MutableStateFlow(
        ProfileState(
            userId = userModel.userId,
            nickname = userModel.name.ifEmpty {
                val resId = CUSTOM_NAME_RES_IDS[Random.nextInt(CUSTOM_NAME_RES_IDS.size)]
                LoginEntry.appContext.getString(resId)
            },
            avatarUrl = userModel.avatar.ifEmpty {
                val list = AvatarConstants.USER_AVATAR_ARRAY
                list[Random.nextInt(list.size)]
            },
        )
    )
    val state: StateFlow<ProfileState> = _state.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    var onProfileCompleted: ((UserModel) -> Unit)? = null

    fun updateNickname(nickname: String) {
        if (nickname.length > 20) return
        _state.update {
            it.copy(
                nickname = nickname,
                isNicknameValid = NICKNAME_REGEX.matches(nickname),
            )
        }
    }

    fun showAvatarDialog() {
        _state.update { it.copy(isAvatarDialogVisible = true) }
    }

    fun dismissAvatarDialog() {
        _state.update { it.copy(isAvatarDialogVisible = false) }
    }

    fun selectAvatarInDialog(avatarUrl: String) {
        _state.update { it.copy(avatarUrl = avatarUrl) }
    }

    fun clearToast() {
        _state.update { it.copy(toastMessage = "") }
    }

    fun updateProfile() {
        val currentState = state.value
        val nickname = currentState.nickname.trim()
        if (nickname.isEmpty()) {
            _state.update {
                it.copy(toastMessage = LoginEntry.appContext.getString(R.string.login_profile_toast_set_username))
            }
            return
        }
        if (!NICKNAME_REGEX.matches(nickname)) {
            _state.update { it.copy(isNicknameValid = false) }
            return
        }

        _state.update { it.copy(isNicknameValid = true, isLoading = true) }

        Log.i(TAG, "updateProfile, nickname: $nickname, avatarUrl: ${currentState.avatarUrl}")
        scope.launch {
            LoginManager.updateProfile(
                nickname = nickname,
                avatarUrl = currentState.avatarUrl,
            ).onSuccess { updatedUser ->
                _state.update {
                    it.copy(
                        isLoading = false,
                        toastMessage = LoginEntry.appContext.getString(
                            R.string.login_profile_toast_register_success
                        )
                    )
                }
                val completeUser = userModel.copy(
                    name = updatedUser.name,
                    avatar = updatedUser.avatar,
                )
                onProfileCompleted?.invoke(completeUser)
            }.onFailure { error ->
                _state.update {
                    it.copy(
                        isLoading = false,
                        toastMessage = LoginEntry.appContext.getString(
                            R.string.login_profile_toast_failed_to_set,
                            error.message.orEmpty()
                        )
                    )
                }
            }
        }
    }

    fun destroy() {
        scope.cancel()
    }
}
