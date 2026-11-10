package com.tencent.uikit.app.mine

import com.tencent.imsdk.v2.V2TIMCallback
import com.tencent.imsdk.v2.V2TIMManager
import com.tencent.imsdk.v2.V2TIMUserFullInfo
import com.tencent.imsdk.v2.V2TIMValueCallback

object UserManager {
    private const val ERR_INVALID_PARAMETERS = -1

    @Volatile
    private var cachedUserInfo: V2TIMUserFullInfo? = null

    @JvmStatic
    fun getInstance(): UserManager = this

    fun getSelfUserInfo(callback: V2TIMValueCallback<V2TIMUserFullInfo>?) {
        val loginUser = V2TIMManager.getInstance().loginUser
        if (loginUser.isNullOrEmpty()) {
            callback?.onError(ERR_INVALID_PARAMETERS, "login user is empty")
            return
        }
        V2TIMManager.getInstance().getUsersInfo(listOf(loginUser), object :
            V2TIMValueCallback<List<V2TIMUserFullInfo>> {
            override fun onSuccess(list: List<V2TIMUserFullInfo>?) {
                if (!list.isNullOrEmpty()) {
                    cachedUserInfo = list[0]
                    callback?.onSuccess(cachedUserInfo)
                } else {
                    callback?.onError(ERR_INVALID_PARAMETERS, "user info list is empty")
                }
            }

            override fun onError(code: Int, desc: String?) {
                callback?.onError(code, desc)
            }
        })
    }

    fun updateSelfUserInfo(info: V2TIMUserFullInfo?, callback: V2TIMCallback?) {
        if (info == null) {
            callback?.onError(ERR_INVALID_PARAMETERS, "user info is null")
            return
        }
        V2TIMManager.getInstance().setSelfInfo(info, object : V2TIMCallback {
            override fun onSuccess() {
                cachedUserInfo = info
                callback?.onSuccess()
            }

            override fun onError(code: Int, desc: String?) {
                callback?.onError(code, desc)
            }
        })
    }
} 