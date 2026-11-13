package com.tencent.qcloud.tuikit.tuicallkit.call.callview.overlays

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Outline
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import com.tencent.qcloud.tuikit.tuicallkit.R
import com.tencent.qcloud.tuikit.tuicallkit.common.CallKitContactLoader
import com.tencent.qcloud.tuikit.tuicallkit.common.CallKitImageBlur
import com.tencent.qcloud.tuikit.tuicallkit.common.dp
import io.trtc.tuikit.atomicxcore.api.call.CallStore
import io.trtc.tuikit.atomicxcore.api.login.LoginStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

internal class VideoCallingOverlay @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr) {

    private val peerAvatarContainer: FrameLayout
    private val peerAvatarImageView: ImageView
    private val peerNameLabel: TextView
    private val peerColumn: LinearLayout
    private val callingLabel: TextView
    private val selfOffView: FrameLayout
    private val selfOffBackdrop: ImageView
    private val selfAvatarImageView: ImageView

    private val mainHandler = Handler(Looper.getMainLooper())
    private var dotCount = 0
    private var isDotRunning = false
    private var reloadToken = 0
    private var backdropToken = 0

    private val dotTask = object : Runnable {
        override fun run() {
            if (!isDotRunning) {
                return
            }
            dotCount = (dotCount + 1) % 4
            updateCallingText()
            mainHandler.postDelayed(this, 300)
        }
    }

    init {
        peerAvatarImageView = ImageView(context).apply {
            setImageResource(R.drawable.tuicallkit_default_avatar)
            scaleType = ImageView.ScaleType.CENTER_CROP
            clipToOutline = true
            outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) {
                    outline.setRoundRect(0, 0, view.width, view.height, 22f.dp(context).toFloat())
                }
            }
        }
        peerAvatarContainer = FrameLayout(context).apply {
            elevation = 15f.dp(context).toFloat()
            // 圆角 outline 让投影跟随头像形状（默认无 outline 时投影不生效）
            outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) {
                    outline.setRoundRect(0, 0, view.width, view.height, 22f.dp(context).toFloat())
                }
            }
            addView(peerAvatarImageView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        }
        peerNameLabel = TextView(context).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
            setTextColor(ContextCompat.getColor(context, android.R.color.white))
            typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
            gravity = Gravity.CENTER
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            maxWidth = resources.displayMetrics.widthPixels - 56.dp(context)
            setShadowLayer(2f, 0f, 1f.dp(context).toFloat(), 0x73000000)
        }
        peerColumn = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            val avatarSize = 108.dp(context)
            addView(peerAvatarContainer, LinearLayout.LayoutParams(avatarSize, avatarSize))
            val nameParams = LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
            nameParams.topMargin = 12.dp(context)
            addView(peerNameLabel, nameParams)
        }
        val columnParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL)
        addView(peerColumn, columnParams)

        callingLabel = TextView(context).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            setTextColor(ContextCompat.getColor(context, android.R.color.white))
            gravity = Gravity.CENTER
            setShadowLayer(2f, 0f, 1f.dp(context).toFloat(), 0x73000000)
        }
        val callingParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL)
        addView(callingLabel, callingParams)

        selfOffBackdrop = ImageView(context).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
        }
        selfAvatarImageView = ImageView(context).apply {
            setImageResource(R.drawable.tuicallkit_default_avatar)
            scaleType = ImageView.ScaleType.CENTER_CROP
            clipToOutline = true
            outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) {
                    outline.setRoundRect(0, 0, view.width, view.height, 60f.dp(context).toFloat())
                }
            }
        }
        val ring = View(context).apply {
            val circle = GradientDrawable()
            circle.shape = GradientDrawable.OVAL
            circle.setColor(0x0AFFFFFF)
            background = circle
        }
        selfOffView = FrameLayout(context).apply {
            isVisible = false
            setBackgroundResource(R.drawable.tuicallkit_bg_audio_gradient)
            addView(selfOffBackdrop, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
            val ringSize = 140.dp(context)
            addView(ring, LayoutParams(ringSize, ringSize, Gravity.CENTER))
            val avatarSize = 120.dp(context)
            addView(selfAvatarImageView, LayoutParams(avatarSize, avatarSize, Gravity.CENTER))
        }
        addView(selfOffView, 0, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        positionContent()
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        positionContent()
    }

    private fun positionContent() {
        if (height <= 0) {
            return
        }
        // 用 measuredHeight 且仅在值变化时写回，避免布局循环
        (peerColumn.layoutParams as? LayoutParams)?.let {
            val target = (height * 0.44f - peerColumn.measuredHeight / 2f).toInt().coerceAtLeast(0)
            if (it.topMargin != target) {
                it.topMargin = target
                peerColumn.layoutParams = it
            }
        }
        (callingLabel.layoutParams as? LayoutParams)?.let {
            val target = (height * 569f / 852f - callingLabel.measuredHeight / 2f).toInt().coerceAtLeast(0)
            if (it.topMargin != target) {
                it.topMargin = target
                callingLabel.layoutParams = it
            }
        }
    }

    override fun setVisibility(visibility: Int) {
        super.setVisibility(visibility)
        if (visibility == View.VISIBLE) {
            startDots()
        } else {
            stopDots()
        }
    }

    override fun onDetachedFromWindow() {
        stopDots()
        super.onDetachedFromWindow()
    }

    fun reload() {
        reloadToken += 1
        val token = reloadToken
        val state = CallStore.shared.observerState
        val selfID = LoginStore.shared.loginState.loginUserInfo.value?.userID.orEmpty()
        val peerID = state.allParticipants.value.firstOrNull { it.id != state.selfInfo.value.id }?.id
            ?: state.activeCall.value.inviteeIds.firstOrNull().orEmpty()
        peerNameLabel.text = peerID
        if (peerID.isNotEmpty()) {
            CallKitContactLoader.load(listOf(peerID)) { _, nickname, _, bitmap ->
                if (reloadToken != token) {
                    return@load
                }
                if (!nickname.isNullOrEmpty()) {
                    peerNameLabel.text = nickname
                }
                if (bitmap != null) {
                    peerAvatarImageView.setImageBitmap(bitmap)
                }
            }
        }
        if (selfID.isNotEmpty()) {
            CallKitContactLoader.load(listOf(selfID)) { _, _, _, bitmap ->
                if (reloadToken != token || bitmap == null) {
                    return@load
                }
                selfAvatarImageView.setImageBitmap(bitmap)
                updateSelfOffBackdrop(bitmap)
            }
        }
        startDots()
    }

    fun setCameraOff(off: Boolean) {
        selfOffView.isVisible = off
        peerAvatarContainer.isVisible = !off
        peerNameLabel.isVisible = !off
    }

    private fun updateSelfOffBackdrop(image: Bitmap) {
        backdropToken += 1
        val token = backdropToken
        val width = if (width > 0) width else resources.displayMetrics.widthPixels
        val height = if (height > 0) height else resources.displayMetrics.heightPixels
        CoroutineScope(Dispatchers.Default).launch {
            val blurred = CallKitImageBlur.blurred(image, width, height)
            launch(Dispatchers.Main) {
                if (backdropToken == token) {
                    selfOffBackdrop.setImageBitmap(blurred)
                }
            }
        }
    }

    private fun startDots() {
        if (isDotRunning) {
            return
        }
        isDotRunning = true
        dotCount = 0
        updateCallingText()
        mainHandler.postDelayed(dotTask, 300)
    }

    private fun stopDots() {
        isDotRunning = false
        mainHandler.removeCallbacks(dotTask)
    }

    private fun updateCallingText() {
        callingLabel.text = context.getString(R.string.tuicallkit_call_calling_video) + ".".repeat(dotCount)
    }
}
