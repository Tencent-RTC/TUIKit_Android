package com.tencent.rtcube.v2.main.domestic.store

import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.Toast
import com.tencent.rtcube.v2.R
import com.tencent.rtcube.v2.login.LoginEntry
import com.tencent.rtcube.v2.main.ModuleRegistry
import com.tencent.rtcube.v2.main.domestic.service.ModulePermissionService
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

class EntranceStore(private val onTrackAnalytics: ((String) -> Unit)? = null) {
    private val TAG = "EntranceStore"

    private val _state = MutableStateFlow(EntranceState())

    val state: StateFlow<EntranceState> = _state.asStateFlow()

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val dynamicJobs = mutableListOf<Job>()
    private var userJob: Job? = null

    // Whether the face-id verify dialog has already been shown in this session.
    private var isFaceAuthDialogShowed: Boolean = false

    fun loadModules() {
        var resolved = ModuleRegistry.resolvedModules()
        resolved = ModulePermissionService.filter(resolved)
        _state.update { it.copy(modules = resolved) }
        subscribeDynamicUpdates()
        subscribeUserUpdates()
    }

    fun selectModule(
        context: Context,
        index: Int,
        onTriggerFaceAuthDialog: ((onFaceAuthDialogShowed: (Boolean) -> Unit) -> Unit)? = null,
    ): Intent? {
        val visibleModules = _state.value.modules.filter { it.isVisible }
        if (index >= visibleModules.size) return null

        if (ModulePermissionService.isHighRiskUser()) {
            Log.w(TAG, "selectModule blocked: isHighRiskUser, isFaceAuthDialogShowed=$isFaceAuthDialogShowed")
            if (!isFaceAuthDialogShowed) {
                onTriggerFaceAuthDialog?.invoke { showed ->
                    isFaceAuthDialogShowed = showed
                }
            } else {
                val tip = context.getString(R.string.root_toast_module_maintenance)
                Toast.makeText(context, tip, Toast.LENGTH_SHORT).show()
            }
            return null
        }

        val module = visibleModules[index]
        if (ModulePermissionService.isModuleBanned(module)) {
            Log.w(TAG, "selectModule blocked: module banned, identifier=${module.config.identifier}")
            Toast.makeText(context, context.getString(R.string.root_toast_module_maintenance), Toast.LENGTH_SHORT).show()
            return null
        }

        onTrackAnalytics?.invoke(module.config.analyticsEvent)

        return runCatching { module.config.targetProvider(context) }.getOrNull()
    }

    fun badgeCount(index: Int): Long {
        val visibleModules = _state.value.modules.filter { it.isVisible }
        if (index >= visibleModules.size) return 0L
        return visibleModules[index].badgeCount
    }

    fun updateBadgeCount(identifier: String, count: Long) {
        _state.update { state ->
            state.copy(
                modules = state.modules.map {
                    if (it.config.identifier == identifier) it.copy(badgeCount = count) else it
                }
            )
        }
    }

    private fun subscribeUserUpdates() {
        if (userJob?.isActive == true) return
        userJob = scope.launch {
            LoginEntry.currentUser.collect { user ->
                _state.update { it.copy(userAvatarUrl = user?.avatar.orEmpty()) }
            }
        }
    }

    fun updateReportViewVisible(visible: Boolean) {
        _state.update { it.copy(isReportViewVisible = visible) }
    }

    private fun subscribeDynamicUpdates() {
        dynamicJobs.forEach { it.cancel() }
        dynamicJobs.clear()

        val modules = _state.value.modules
        for (module in modules) {
            val provider = module.provider ?: continue

            dynamicJobs += scope.launch {
                provider.badgeCountFlow.collect { count ->
                    updateBadgeCount(module.config.identifier, count)
                }
            }

            dynamicJobs += scope.launch {
                provider.isVisibleFlow.collect { visible ->
                    _state.update { state ->
                        state.copy(
                            modules = state.modules.map {
                                if (it.config.identifier == module.config.identifier) {
                                    it.copy(isVisible = visible)
                                } else {
                                    it
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    fun destroy() {
        dynamicJobs.forEach { it.cancel() }
        dynamicJobs.clear()
        userJob?.cancel()
        userJob = null
        scope.cancel()
    }
}
