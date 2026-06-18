package com.tencent.rtcube.assembly

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Module provider interface.
 */
interface ModuleProvider {
    /** Entry config. */
    val config: ModuleConfig

    /** Badge count (optional, default 0). Home screen subscribes via Flow. */
    val badgeCountFlow: StateFlow<Long>
        get() = DEFAULT_BADGE_COUNT_FLOW

    /** Whether this module is visible (optional, default true). Used for conditional display. */
    val isVisibleFlow: StateFlow<Boolean>
        get() = DEFAULT_IS_VISIBLE_FLOW

    /**
     * Inject runtime environment (optional, default no-op).
     *
     * Called from ModuleAssembler.registerModules() to inject
     * context, flavor, License, etc. into the module.
     */
    fun setup(environment: ModuleEnvironment) {}

    companion object {
        /** Shared default Flow instances to avoid creating a new MutableStateFlow on each get() which would break subscriptions. */
        private val DEFAULT_BADGE_COUNT_FLOW: StateFlow<Long> = MutableStateFlow(0L)
        private val DEFAULT_IS_VISIBLE_FLOW: StateFlow<Boolean> = MutableStateFlow(true)
    }
}
