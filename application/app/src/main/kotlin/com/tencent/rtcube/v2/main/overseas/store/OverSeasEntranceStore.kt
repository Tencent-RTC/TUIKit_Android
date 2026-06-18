package com.tencent.rtcube.v2.main.overseas.store

import android.content.Context
import android.content.Intent
import com.tencent.rtcube.v2.login.LoginEntry
import com.tencent.rtcube.v2.main.ModuleRegistry
import com.tencent.rtcube.v2.main.model.ResolvedModule
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

class OverSeasEntranceStore(private val onTrackAnalytics: ((String) -> Unit)? = null) {

    private val _state = MutableStateFlow(OverSeasEntranceState())
    val state: StateFlow<OverSeasEntranceState> = _state.asStateFlow()

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val dynamicJobs = mutableListOf<Job>()
    private var userJob: Job? = null

    companion object {
        private const val TAG = "OverseasEntranceStore"
        private val DISCOVERY_IDENTIFIERS = setOf("player", "ugsv")
    }

    fun loadModules() {
        val allModules = ModuleRegistry.resolvedModules()

        val products = allModules.filter { it.config.identifier !in DISCOVERY_IDENTIFIERS }
        val discovery = allModules.filter { it.config.identifier in DISCOVERY_IDENTIFIERS }

        _state.update {
            it.copy(
                productsModules = products,
                discoveryModules = discovery,
            )
        }
        subscribeDynamicUpdates()
        subscribeUserUpdates()
    }

    fun selectModule(context: Context, module: ResolvedModule): Intent? {
        onTrackAnalytics?.invoke(module.config.analyticsEvent)
        return runCatching { module.config.targetProvider(context) }.getOrNull()
    }

    fun selectTab(index: Int) {
        _state.update { it.copy(selectedTabIndex = index) }
    }

    private fun subscribeUserUpdates() {
        if (userJob?.isActive == true) return
        userJob = scope.launch {
            LoginEntry.currentUser.collect { user ->
                _state.update { it.copy(userAvatarUrl = user?.avatar.orEmpty()) }
            }
        }
    }

    private fun updateBadgeCount(identifier: String, count: Long) {
        _state.update { state ->
            state.copy(
                productsModules = state.productsModules.map {
                    if (it.config.identifier == identifier) it.copy(badgeCount = count) else it
                },
                discoveryModules = state.discoveryModules.map {
                    if (it.config.identifier == identifier) it.copy(badgeCount = count) else it
                },
            )
        }
    }

    private fun subscribeDynamicUpdates() {
        dynamicJobs.forEach { it.cancel() }
        dynamicJobs.clear()

        val allModules = _state.value.productsModules + _state.value.discoveryModules
        for (module in allModules) {
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
                            productsModules = state.productsModules.map {
                                if (it.config.identifier == module.config.identifier) {
                                    it.copy(isVisible = visible)
                                } else it
                            },
                            discoveryModules = state.discoveryModules.map {
                                if (it.config.identifier == module.config.identifier) {
                                    it.copy(isVisible = visible)
                                } else it
                            },
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
