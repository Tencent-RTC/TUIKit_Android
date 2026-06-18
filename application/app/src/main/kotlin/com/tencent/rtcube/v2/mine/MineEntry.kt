package com.tencent.rtcube.v2.mine

import android.content.Context
import android.content.Intent

object MineEntry {
    fun startMineActivity(
        context: Context,
        onLogout: () -> Unit,
    ) {
        MineCallbacks.onLogout = onLogout

        val intent = Intent(context, MineActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }
}

internal object MineCallbacks {
    var onLogout: (() -> Unit)? = null

    fun clear() {
        onLogout = null
    }
}
