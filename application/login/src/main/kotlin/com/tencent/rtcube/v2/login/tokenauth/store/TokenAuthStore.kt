package com.tencent.rtcube.v2.login.tokenauth.store

import android.util.Log
import com.tencent.rtcube.v2.login.LoginSubStore
import com.tencent.rtcube.v2.login.components.model.LoginError
import com.tencent.rtcube.v2.login.components.model.LoginResult
import com.tencent.rtcube.v2.login.components.service.LoginManager
import com.tencent.rtcube.v2.login.tokenauth.utils.TokenCacheManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

/**
 * Token-based auto-login store (no UI).
 */
class TokenAuthStore(private val scope: CoroutineScope) : LoginSubStore {
    private val TAG = "TokenAuthStore"
    private val _resultFlow = MutableSharedFlow<Result<LoginResult>>(replay = 1)
    override val resultFlow: SharedFlow<Result<LoginResult>> = _resultFlow.asSharedFlow()

    fun performAutoLogin() {
        if (!TokenCacheManager.hasToken()) {
            _resultFlow.tryEmit(Result.failure(LoginError.TokenExpired))
            return
        }

        scope.launch {
            val result = LoginManager.loginByToken()
            Log.i(TAG, "performAutoLogin: success=${result.isSuccess}, userId=${result.getOrNull()?.userModel?.userId}")
            _resultFlow.tryEmit(result)
        }
    }
}
