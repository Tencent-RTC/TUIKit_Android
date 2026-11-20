package com.trtc.uikit.livekit.voicelounge.prepare

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.animation.PathInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import com.trtc.uikit.livekit.R
import com.trtc.uikit.livekit.voicelounge.MATCH
import com.trtc.uikit.livekit.voicelounge.VoiceLoungeFont
import com.trtc.uikit.livekit.voicelounge.WRAP
import com.trtc.uikit.livekit.voicelounge.clipOval
import com.trtc.uikit.livekit.voicelounge.dp
import com.trtc.uikit.livekit.voicelounge.frameParams
import com.trtc.uikit.livekit.voicelounge.living.seat.VoiceLoungeSeatView
import com.trtc.uikit.livekit.voicelounge.loadUrl
import com.trtc.uikit.livekit.voicelounge.rgb
import com.trtc.uikit.livekit.voicelounge.settings.VoiceLoungeSeatLayout
import com.trtc.uikit.livekit.voicelounge.settings.theme.VoiceLoungeSkin
import com.trtc.uikit.livekit.voicelounge.voiceLoungeText
import com.trtc.uikit.livekit.voicelounge.whiteAlpha
import com.trtc.uikit.livekit.voicelounge.withAlpha

internal class VoiceLoungeHostHeroView(context: Context) : FrameLayout(context) {
    private val hostContainer = FrameLayout(context).apply { clipChildren = false; clipToPadding = false }

    private val rippleView = VoiceLoungeSeatView.WaveRingsView(context, AVATAR_SIZE.toFloat())
    private val avatarView = HostAvatarView(context)
    private val nameLabel = voiceLoungeText(context, com.trtc.uikit.livekit.common.theme.LiveTheme.TextRole.CAPTION)
    private val secondaryContainer = FrameLayout(context).apply { clipChildren = false; clipToPadding = false }
    private val secondaryRingView = PlaceholderRingView(context)
    private val secondaryLabel =
        voiceLoungeText(context, com.trtc.uikit.livekit.common.theme.LiveTheme.TextRole.CAPTION)

    private var currentLayout: VoiceLoungeSeatLayout = VoiceLoungeSeatLayout.ONE_V8
    private var representedAvatarURL = ""
    private var isActive = true

    init {
        isClickable = false
        isFocusable = false
        clipChildren = false
        clipToPadding = false

        addView(
            secondaryContainer,
            frameParams(dp(SECONDARY_SIZE + 40), WRAP, Gravity.TOP or Gravity.CENTER_HORIZONTAL),
        )
        secondaryContainer.addView(
            secondaryRingView,
            frameParams(dp(SECONDARY_SIZE), dp(SECONDARY_SIZE), Gravity.TOP or Gravity.CENTER_HORIZONTAL),
        )
        secondaryContainer.addView(
            secondaryLabel,
            frameParams(
                MATCH, WRAP, Gravity.TOP or Gravity.CENTER_HORIZONTAL,
                top = dp(SECONDARY_SIZE) + dp(14), start = dp(2), end = dp(2),
            ),
        )

        addView(hostContainer, frameParams(dp(AVATAR_SIZE + 56), WRAP, Gravity.TOP or Gravity.CENTER_HORIZONTAL))
        val avatarPx = dp(AVATAR_SIZE)
        val ripplePx = rippleView.sidePx
        hostContainer.addView(
            rippleView,
            frameParams(ripplePx, ripplePx, Gravity.TOP or Gravity.CENTER_HORIZONTAL, top = -(ripplePx - avatarPx) / 2),
        )
        hostContainer.addView(
            avatarView,
            frameParams(avatarPx, avatarPx, Gravity.TOP or Gravity.CENTER_HORIZONTAL),
        )
        hostContainer.addView(
            nameLabel,
            frameParams(
                MATCH, WRAP, Gravity.TOP or Gravity.CENTER_HORIZONTAL,
                top = avatarPx + dp(NAME_TOP), start = dp(4), end = dp(4),
            ),
        )

        nameLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, VoiceLoungeFont.size(13f))
        nameLabel.setTextColor(whiteAlpha(0.92f))
        nameLabel.gravity = Gravity.CENTER
        nameLabel.maxLines = 1
        nameLabel.ellipsize = TextUtils.TruncateAt.END
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            nameLabel.setShadowLayer(dp(4).toFloat(), 0f, dp(2).toFloat(), Color.BLACK.withAlpha(0.6f))
        }

        secondaryLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, VoiceLoungeFont.size(11f))
        secondaryLabel.setTextColor(whiteAlpha(0.55f))
        secondaryLabel.gravity = Gravity.CENTER
        secondaryLabel.maxLines = 1

        applyLayout(VoiceLoungeSeatLayout.ONE_V8, animated = false)
    }

    fun bind(hostName: String, avatarURL: String, skin: VoiceLoungeSkin) {
        nameLabel.text = hostName
        if (representedAvatarURL == avatarURL) return
        representedAvatarURL = avatarURL
        avatarView.setAvatar(avatarURL)
    }

    fun applyLayout(layout: VoiceLoungeSeatLayout, animated: Boolean = true) {
        currentLayout = layout
        val spec = SecondarySpec.of(layout, context)
        val avatarSize = if (spec == null) AVATAR_SIZE else SECONDARY_SIZE
        val avatarPx = dp(avatarSize)
        rippleView.setAvatarSize(avatarSize.toFloat())
        val ripplePx = rippleView.sidePx

        secondaryContainer.visibility = if (spec == null) GONE else VISIBLE
        secondaryLabel.visibility = if (spec?.title.isNullOrEmpty()) GONE else VISIBLE
        spec?.let { secondaryLabel.text = it.title }

        val hostTx = (spec?.hostOffsetDp ?: 0).let { dp(it) }.toFloat()
        val secondaryTx = (spec?.secondaryOffsetDp ?: 0).let { dp(it) }.toFloat()
        val topOffset = dp(spec?.topOffsetDp ?: 0)

        resizeAvatar(avatarPx, ripplePx)
        val nameMargin = avatarPx + dp(if (spec == null) NAME_TOP else NAME_TOP_COMPACT)
        (nameLabel.layoutParams as LayoutParams).topMargin = nameMargin
        nameLabel.requestLayout()

        val hostParams = hostContainer.layoutParams as LayoutParams
        val previousTop = hostParams.topMargin
        hostParams.topMargin = topOffset
        (secondaryContainer.layoutParams as LayoutParams).topMargin = topOffset
        hostContainer.requestLayout()
        secondaryContainer.requestLayout()
        val deltaY = (previousTop - topOffset).toFloat()

        if (animated && isAttachedToWindow) {
            hostContainer.translationY = deltaY
            secondaryContainer.translationY = deltaY
            animateTranslation(hostContainer, hostTx, 0f)
            animateTranslation(secondaryContainer, secondaryTx, 0f)
        } else {
            hostContainer.translationX = hostTx
            hostContainer.translationY = 0f
            secondaryContainer.translationX = secondaryTx
            secondaryContainer.translationY = 0f
        }
    }

    fun setActive(active: Boolean) {
        if (isActive == active) return
        isActive = active
        updateRipple()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        updateRipple()
    }

    override fun onDetachedFromWindow() {
        rippleView.setSpeaking(false)
        super.onDetachedFromWindow()
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        updateRipple()
    }

    private fun resizeAvatar(avatarPx: Int, ripplePx: Int) {
        (avatarView.layoutParams as LayoutParams).apply {
            width = avatarPx
            height = avatarPx
        }
        (rippleView.layoutParams as LayoutParams).apply {
            width = ripplePx
            height = ripplePx
            topMargin = -(ripplePx - avatarPx) / 2
        }
        avatarView.requestLayout()
        rippleView.requestLayout()
    }

    private fun animateTranslation(target: View, tx: Float, ty: Float) {
        val animator = AnimatorSet()
        animator.playTogether(
            ObjectAnimator.ofFloat(target, TRANSLATION_X, target.translationX, tx),
            ObjectAnimator.ofFloat(target, TRANSLATION_Y, target.translationY, ty),
        )
        animator.duration = 220
        animator.interpolator = PathInterpolator(0.3f, 0.1f, 0.3f, 1f)
        animator.start()
    }

    private fun updateRipple() {
        if (isActive && isAttachedToWindow && windowVisibility == VISIBLE) {
            rippleView.setSpeaking(true)
        } else {
            rippleView.setSpeaking(false)
        }
    }

    private data class SecondarySpec(
        val title: String,
        val hostOffsetDp: Int,
        val secondaryOffsetDp: Int,
        val topOffsetDp: Int,
    ) {
        companion object {
            fun of(layout: VoiceLoungeSeatLayout, context: Context): SecondarySpec? = when (layout) {
                VoiceLoungeSeatLayout.ONE_V8, VoiceLoungeSeatLayout.ONE_V8X -> null
                VoiceLoungeSeatLayout.TWO_V8 -> SecondarySpec(
                    context.getString(R.string.livekit_voiceroom_layout_cohost), -72, 72, 10,
                )

                VoiceLoungeSeatLayout.DUO -> SecondarySpec("", -60, 60, 40)
            }
        }
    }

    private companion object {
        const val AVATAR_SIZE = 96
        const val SECONDARY_SIZE = 76
        const val NAME_TOP = 20
        const val NAME_TOP_COMPACT = 12
    }
}

private class HostAvatarView(context: Context) : FrameLayout(context) {
    private val imageView = ImageView(context)

    init {
        background = GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(rgb(0.118f, 0.125f, 0.161f), rgb(0.051f, 0.055f, 0.078f)),
        ).apply { shape = GradientDrawable.OVAL }
        clipOval()
        elevation = context.dp(14).toFloat()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            outlineSpotShadowColor = Color.BLACK.withAlpha(0.45f)
            outlineAmbientShadowColor = Color.BLACK.withAlpha(0.45f)
        }
        imageView.scaleType = ImageView.ScaleType.CENTER_CROP
        imageView.clipOval()
        addView(imageView, FrameLayout.LayoutParams(MATCH, MATCH))
    }

    fun setAvatar(url: String) {
        if (url.isEmpty()) {
            imageView.setImageResource(R.drawable.livekit_livelist_default_avatar)
            return
        }
        imageView.loadUrl(url, R.drawable.livekit_livelist_default_avatar)
    }
}

private class PlaceholderRingView(context: Context) : FrameLayout(context) {
    init {
        background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(whiteAlpha(0.05f))
            setStroke(context.dp(1), whiteAlpha(0.10f))
        }
        val icon = ImageView(context).apply {
            setImageResource(R.drawable.livekit_voiceprepare_seat_mic)
            scaleType = ImageView.ScaleType.FIT_CENTER
            imageTintList = android.content.res.ColorStateList.valueOf(whiteAlpha(0.5f))
        }
        addView(
            icon,
            frameParams(context.dp(22), context.dp(22), Gravity.CENTER),
        )
    }
}
