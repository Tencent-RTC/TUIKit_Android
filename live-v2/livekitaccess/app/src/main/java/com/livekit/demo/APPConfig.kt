package com.livekit.demo

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

object APPConfig {
    private const val PREF_NAME = "livekit_demo_pref"
    private const val KEY_LOGIN_USER_ID = "login_user_id"
    const val DEMO_LOGIN_SUCCESS = 1302
    const val DEMO_CLICK_LIVE = 1119

    private lateinit var sp: SharedPreferences

    fun init(context: Context) {
        sp = context.applicationContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    }

    var LOGIN_USER_ID: String
        get() = sp.getString(KEY_LOGIN_USER_ID, "") ?: ""
        set(value) {
            sp.edit { putString(KEY_LOGIN_USER_ID, value) }
        }
}