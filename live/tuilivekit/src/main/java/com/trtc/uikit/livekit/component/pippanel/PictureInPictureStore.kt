package com.trtc.uikit.livekit.component.pippanel

import android.app.AppOpsManager
import android.content.Context
import android.os.Build
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

data class PictureInPictureState(
    val isPictureInPictureMode: StateFlow<Boolean>,
)

class PictureInPictureStore private constructor() {

    companion object {
        val shared: PictureInPictureStore by lazy { PictureInPictureStore() }
    }

    private val _isPictureInPictureMode = MutableStateFlow(false)

    val state = PictureInPictureState(isPictureInPictureMode = _isPictureInPictureMode)

    fun updateIsPictureInPictureMode(isPictureInPictureMode: Boolean) {
        _isPictureInPictureMode.update { isPictureInPictureMode }
    }

    fun hasPipPermission(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return false
        }

        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager

        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_PICTURE_IN_PICTURE,
                android.os.Process.myUid(),
                context.packageName
            )
        } else {
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_PICTURE_IN_PICTURE,
                android.os.Process.myUid(),
                context.packageName
            )
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }
}
