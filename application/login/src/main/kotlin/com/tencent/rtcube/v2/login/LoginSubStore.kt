package com.tencent.rtcube.v2.login

import com.tencent.rtcube.v2.login.components.model.LoginResult
import kotlinx.coroutines.flow.SharedFlow

/**
 * Interface implemented by all login sub-module Stores.
 */
interface LoginSubStore {
    /** Login result flow — subscribed by Navigator and aggregated to a unified exit. */
    val resultFlow: SharedFlow<Result<LoginResult>>
}
