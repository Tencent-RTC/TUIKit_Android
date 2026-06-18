package com.tencent.rtcube.v2.main.domestic.service

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.tencent.rtcube.assembly.EntranceCardStyle
import com.tencent.rtcube.v2.login.LoginEntry
import com.tencent.rtcube.v2.main.model.ResolvedModule

object ModulePermissionService {
    private val TAG = "ModulePermissionService"
    private const val SP_NAME = "rtcube_module_permission"
    private const val KEY_BANNED_FEATURE_IDS = "bannedFeatureIds"
    private var modulePermissionPre: SharedPreferences? = null

    /** banned module identifier (from server) */
    @Volatile
    private var bannedModuleIds: Set<String> = emptySet()

    fun loadUserBlackList(context: Context) {
        modulePermissionPre = context.getSharedPreferences(SP_NAME, Context.MODE_PRIVATE)
        LoginEntry.getUserModuleBlackList { result ->
            result.fold(
                onSuccess = { blackList ->
                    bannedModuleIds = blackList.module.filter { it.value }.keys.toSet()
                    val bannedFeatureIds = blackList.feature.filter { it.value }.keys.toSet()
                    modulePermissionPre?.edit()?.putStringSet(KEY_BANNED_FEATURE_IDS, bannedFeatureIds)?.apply()
                    Log.d(TAG, "loadUserBlackList success, module=${blackList.module}, feature=${blackList.feature}")
                },
                onFailure = { error ->
                    clearUserBlackList()
                    Log.e(TAG, "loadUserBlackList failed", error)
                }
            )
        }
    }

    fun isHighRiskUser(): Boolean = LoginEntry.currentUser.value?.isHighRiskUser == true

    fun isModuleBanned(module: ResolvedModule): Boolean {
        if (module.config.cardStyle == EntranceCardStyle.BANNER) return false
        return module.config.identifier in bannedModuleIds
    }

    fun filter(modules: List<ResolvedModule>): List<ResolvedModule> = modules.filter { it.isVisible }

    fun clearUserBlackList() {
        bannedModuleIds = emptySet()
        modulePermissionPre?.edit()?.remove(KEY_BANNED_FEATURE_IDS)?.apply()
    }
}
