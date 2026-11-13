package com.tencent.qcloud.tuikit.tuicallkit.call

import android.app.Activity
import android.content.Intent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.tencent.qcloud.tuikit.tuicallkit.call.callview.CallActivity
import com.tencent.qcloud.tuikit.tuicallkit.call.incoming.IncomingBannerView
import com.tencent.qcloud.tuikit.tuicallkit.common.CallKitContext
import com.tencent.qcloud.tuikit.tuicallkit.common.dp
import java.lang.ref.WeakReference

internal object CallWindow {

    private const val BANNER_HEIGHT_DP = 72
    private const val BANNER_TOP_MARGIN_DP = 8
    private const val BANNER_SIDE_MARGIN_DP = 12
    private const val BANNER_SLIDE_DISTANCE_DP = 120

    private var callActivity: WeakReference<CallActivity>? = null
    private var bannerView: IncomingBannerView? = null
    private var bannerHost: WeakReference<ViewGroup>? = null
    private var bannerObserverRegistered = false

    val isShowing: Boolean
        get() = callActivity?.get() != null

    val isBannerShowing: Boolean
        get() = bannerView?.parent != null

    fun showFullScreen() {
        if (isShowing) {
            return
        }
        val context = CallKitContext.requireContext()
        val intent = Intent(context, CallActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    fun dismiss() {
        callActivity?.get()?.finish()
    }

    fun attach(activity: CallActivity) {
        callActivity = WeakReference(activity)
    }

    fun detach(activity: CallActivity) {
        if (callActivity?.get() === activity) {
            callActivity = null
        }
    }

    fun showBanner(banner: IncomingBannerView) {
        registerBannerObserver()
        removeBannerImmediately()
        bannerView = banner
        val activity = CallKitContext.topActivity() ?: return
        attachBannerTo(activity, banner)
        banner.translationY = -BANNER_SLIDE_DISTANCE_DP.dp(activity).toFloat()
        // 滑入 ease-out（对齐 iOS）
        banner.animate().translationY(0f).setDuration(300).setInterpolator(android.view.animation.DecelerateInterpolator()).start()
    }

    fun dismissBanner() {
        val banner = bannerView ?: return
        bannerView = null
        bannerHost = null
        banner.animate().cancel()
        banner.animate()
            .translationY(-BANNER_SLIDE_DISTANCE_DP.dp(banner.context).toFloat())
            .setDuration(250)
            .setInterpolator(android.view.animation.AccelerateInterpolator())
            .withEndAction {
                (banner.parent as? ViewGroup)?.removeView(banner)
            }
            .start()
    }

    private fun removeBannerImmediately() {
        val banner = bannerView ?: return
        bannerView = null
        bannerHost = null
        banner.animate().cancel()
        (banner.parent as? ViewGroup)?.removeView(banner)
    }

    private fun registerBannerObserver() {
        if (bannerObserverRegistered) {
            return
        }
        bannerObserverRegistered = true
        CallKitContext.addTopActivityObserver { activity ->
            val banner = bannerView ?: return@addTopActivityObserver
            if (activity == null || banner.parent != null) {
                if (activity == null) {
                    removeBannerImmediately()
                }
                return@addTopActivityObserver
            }
            attachBannerTo(activity, banner)
        }
    }

    private fun attachBannerTo(activity: Activity, banner: IncomingBannerView) {
        (banner.parent as? ViewGroup)?.removeView(banner)
        val decor = activity.window?.decorView as? ViewGroup ?: return
        val sideMargin = BANNER_SIDE_MARGIN_DP.dp(activity)
        val params = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, BANNER_HEIGHT_DP.dp(activity))
        params.marginStart = sideMargin
        params.marginEnd = sideMargin
        params.topMargin = BANNER_TOP_MARGIN_DP.dp(activity) + statusBarHeight(decor)
        decor.addView(banner, params)
        bannerHost = WeakReference(decor)
    }

    private fun statusBarHeight(host: View): Int {
        val insets = ViewCompat.getRootWindowInsets(host) ?: return 0
        return insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
    }
}
