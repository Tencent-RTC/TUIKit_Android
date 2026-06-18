package com.tencent.rtcube.v2.login.tokenauth.utils

import android.content.Context
import com.tencent.mmkv.MMKV

object TokenCacheManager {

    private const val PREF_NAME = "login_token_cache"
    private const val KEY_TOKEN = "login_token"
    private const val KEY_USER_ID = "login_user_id"

    private var mmkv: MMKV? = null

    fun init(context: Context) {
        MMKV.initialize(context.applicationContext)
        mmkv = MMKV.mmkvWithID(PREF_NAME, MMKV.MULTI_PROCESS_MODE)
    }

    private fun checkInit(): MMKV {
        return checkNotNull(mmkv) {
            "TokenCacheManager has not been initialized. " +
            "Call TokenCacheManager.init(context) in Application.onCreate() before use."
        }
    }

    fun saveToken(token: String, userId: String) {
        checkInit().apply {
            encode(KEY_TOKEN, token)
            encode(KEY_USER_ID, userId)
        }
    }

    fun getToken(): String? {
        return checkInit().decodeString(KEY_TOKEN)
    }

    fun getUserId(): String? {
        return checkInit().decodeString(KEY_USER_ID)
    }

    fun hasToken(): Boolean {
        val kv = checkInit()
        val token = kv.decodeString(KEY_TOKEN)
        val userId = kv.decodeString(KEY_USER_ID)
        return !token.isNullOrEmpty() && !userId.isNullOrEmpty()
    }

    fun clear() {
        checkInit().clearAll()
    }
}
