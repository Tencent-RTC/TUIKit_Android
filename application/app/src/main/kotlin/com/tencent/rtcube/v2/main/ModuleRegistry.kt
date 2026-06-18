package com.tencent.rtcube.v2.main

import com.tencent.rtcube.assembly.ModuleProvider
import com.tencent.rtcube.v2.main.ModuleRegistry.register
import com.tencent.rtcube.v2.main.model.ResolvedModule

object ModuleRegistry {

    private val _providers = mutableListOf<ModuleProvider>()

    val providers: List<ModuleProvider>
        get() = _providers.toList()

    fun register(provider: ModuleProvider) {
        if (_providers.any { it.config.identifier == provider.config.identifier }) return
        _providers.add(provider)
    }

    fun resolvedModules(): List<ResolvedModule> {
        return _providers.map { ResolvedModule(config = it.config, provider = it) }
    }

    fun reset() {
        _providers.clear()
    }
}
