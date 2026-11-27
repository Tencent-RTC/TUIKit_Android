package com.trtc.uikit.livekit.player.floating

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import com.trtc.uikit.livekit.R
import com.trtc.uikit.livekit.common.theme.LiveToast
import com.trtc.uikit.livekit.common.theme.ScreenUtil
import io.trtc.tuikit.atomicxcore.api.live.AtomicPlayer
import androidx.core.net.toUri

object FloatingPlayerManager {

    private const val WINDOW_WIDTH_DP = 120f
    private const val WINDOW_HEIGHT_DP = 180f
    private const val WINDOW_MARGIN_DP = 12f

    private var session: FloatingSession? = null
    private var floatingView: FloatingPlayerView? = null
    private var windowManager: WindowManager? = null
    private var windowParams: WindowManager.LayoutParams? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private val dismissListeners = mutableSetOf<(String) -> Unit>()

    fun enterFloatingView(
        context: Context,
        player: AtomicPlayer,
        liveID: String,
        isPlaying: Boolean,
        isJoinedChannel: Boolean,
        onHandedOver: () -> Unit,
    ): Boolean {
        if (liveID.isEmpty() || floatingView != null) return false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(context)) {
            LiveToast.show(context, context.getString(R.string.common_float_permission_hint))
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                "package:${context.packageName}".toUri(),
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            runCatching { context.startActivity(intent) }
            return false
        }

        releaseSession(false, false)
        val appContext = context.applicationContext
        val manager = appContext.getSystemService(Context.WINDOW_SERVICE) as? WindowManager ?: return false
        val width = ScreenUtil.dip2px(WINDOW_WIDTH_DP)
        val height = ScreenUtil.dip2px(WINDOW_HEIGHT_DP)
        val margin = ScreenUtil.dip2px(WINDOW_MARGIN_DP)

        @Suppress("DEPRECATION") val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            WindowManager.LayoutParams.TYPE_PHONE
        }
        val params = WindowManager.LayoutParams(
            width,
            height,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = (ScreenUtil.getRealScreenWidth(appContext) - width - margin).coerceAtLeast(0)
            y = ((ScreenUtil.getRealScreenHeight(appContext) - height) / 2).coerceAtLeast(0)
        }

        val view = FloatingPlayerView(appContext).apply {
            onTap = { enterPlayerPage(appContext, liveID) }
            onClose = { releaseSession() }
            onDrag = { dx, dy -> updatePosition(appContext, dx, dy) }
        }

        val entered = try {
            manager.addView(view, params)
            player.setView(view.coreView)
            session = FloatingSession(player, liveID, isPlaying, isJoinedChannel)
            floatingView = view
            windowManager = manager
            windowParams = params
            true
        } catch (e: Exception) {
            runCatching { manager.removeViewImmediate(view) }
            false
        }
        if (entered) {
            onHandedOver()
            runCatching { context.finishActivity() }
        }
        return entered
    }

    fun isFloating(liveID: String = ""): Boolean =
        floatingView != null && (liveID.isEmpty() || session?.liveID == liveID)

    fun addFloatingDismissListener(listener: (String) -> Unit): () -> Unit {
        dismissListeners.add(listener)
        return { dismissListeners.remove(listener) }
    }

    private fun notifyFloatingDismissed(liveID: String) {
        if (liveID.isEmpty()) return
        mainHandler.post { dismissListeners.toList().forEach { it(liveID) } }
    }

    private fun enterPlayerPage(context: Context, liveID: String) {
        val intent = Intent(context, PlayerActivity::class.java).putExtra(PlayerActivity.EXTRA_LIVE_ID, liveID)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        if (runCatching { context.startActivity(intent) }.isFailure) return
        releaseWindow()
    }

    fun getAtomicPlayer(liveID: String, onRestored: (Boolean, Boolean) -> Unit): AtomicPlayer? {
        val current = session ?: return null
        if (floatingView != null || current.liveID != liveID) return null
        session = null
        onRestored(current.isPlaying, current.isJoinedChannel)
        return current.player
    }

    fun releaseSession(onlyUnclaimed: Boolean = false) {
        releaseSession(onlyUnclaimed, true)
    }

    private fun releaseSession(onlyUnclaimed: Boolean, notifyDismiss: Boolean) {
        if (onlyUnclaimed && floatingView != null) return
        val dismissedLiveID = if (floatingView != null) session?.liveID.orEmpty() else ""
        releaseWindow()
        releasePlayback()
        session = null
        if (notifyDismiss && dismissedLiveID.isNotEmpty()) notifyFloatingDismissed(dismissedLiveID)
    }

    private fun Context.finishActivity() {
        var current: Context? = this
        while (current is ContextWrapper) {
            if (current is Activity) {
                current.finish()
                return
            }
            current = current.baseContext
        }
    }

    private fun updatePosition(context: Context, dx: Int, dy: Int) {
        val view = floatingView ?: return
        val params = windowParams ?: return
        val manager = windowManager ?: return
        val maxX = (ScreenUtil.getRealScreenWidth(context) - params.width).coerceAtLeast(0)
        val maxY = (ScreenUtil.getRealScreenHeight(context) - params.height).coerceAtLeast(0)
        params.x = (params.x + dx).coerceIn(0, maxX)
        params.y = (params.y + dy).coerceIn(0, maxY)
        runCatching { manager.updateViewLayout(view, params) }
    }

    private fun releaseWindow() {
        val view = floatingView ?: return
        runCatching { windowManager?.removeViewImmediate(view) }
        floatingView = null
        windowManager = null
        windowParams = null
    }

    private fun releasePlayback() {
        val current = session ?: return
        if (current.isJoinedChannel) current.player.leaveChannel()
        if (current.isPlaying) current.player.stopPlay()
        session = current.copy(isPlaying = false, isJoinedChannel = false)
    }
}

private data class FloatingSession(
    val player: AtomicPlayer,
    val liveID: String,
    val isPlaying: Boolean,
    val isJoinedChannel: Boolean,
)
