package com.tencent.rtcube.v2.login.ioaauth

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.activity.result.contract.ActivityResultContract
import com.tencent.rtcube.v2.login.components.model.LoginError
import com.tencent.rtcube.v2.login.components.model.LoginResult
import com.tencent.rtcube.v2.login.ioaauth.IOAAuthContract.Companion.EXTRA_ERROR_MESSAGE
import com.tencent.rtcube.v2.login.ioaauth.IOAAuthContract.Companion.EXTRA_LOGIN_RESULT

/**
 * Launches [IOAAuthActivity] and maps its raw result back to [Result] of [LoginResult].
 *
 * Result mapping:
 * - RESULT_OK + [EXTRA_LOGIN_RESULT]    -> [Result.success]
 * - RESULT_OK + [EXTRA_ERROR_MESSAGE]   -> [Result.failure] of [LoginError.LoginFailed]
 * - RESULT_CANCELED (or anything else)  -> [Result.failure] of [LoginError.Cancelled]
 */
internal class IOAAuthContract : ActivityResultContract<Unit, Result<LoginResult>>() {

    override fun createIntent(context: Context, input: Unit): Intent =
        Intent(context, IOAAuthActivity::class.java)

    override fun parseResult(resultCode: Int, intent: Intent?): Result<LoginResult> {
        if (resultCode != Activity.RESULT_OK) {
            return Result.failure(LoginError.Cancelled)
        }
        @Suppress("DEPRECATION")
        val loginResult = intent?.getSerializableExtra(EXTRA_LOGIN_RESULT) as? LoginResult
        if (loginResult != null) {
            return Result.success(loginResult)
        }
        val message = intent?.getStringExtra(EXTRA_ERROR_MESSAGE)
            ?: return Result.failure(LoginError.Cancelled)
        return Result.failure(LoginError.LoginFailed(-1, message))
    }

    companion object {
        const val EXTRA_LOGIN_RESULT = "extra_ioa_login_result"
        const val EXTRA_ERROR_MESSAGE = "extra_ioa_error_message"
    }
}
