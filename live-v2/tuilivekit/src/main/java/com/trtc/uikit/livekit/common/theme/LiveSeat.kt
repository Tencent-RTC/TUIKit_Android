package com.trtc.uikit.livekit.common.theme

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.appcompat.widget.AppCompatImageView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import com.trtc.uikit.livekit.R
import com.trtc.uikit.livekit.common.theme.LiveText
import com.trtc.uikit.livekit.common.theme.LiveTheme
import com.trtc.uikit.livekit.common.theme.avatar.AtomicAvatar
import com.trtc.uikit.livekit.common.theme.label.AtomicLabel
import kotlin.math.min

/** Shared seat visuals. Entry subclasses own SDK projection and role decisions. */
abstract class LiveSeat protected constructor(context: Context, emptyTitle: String) : FrameLayout(context) {
    private val noVideoView = LiveSeatNoVideoView(context)
    private val overlayView = LiveSeatOverlayView(context)
    private val emptyView = LiveSeatEmptyView(context, emptyTitle)

    init {
        isClickable = false
        isFocusable = false
        clipChildren = true
        listOf(noVideoView, overlayView, emptyView).forEach {
            addView(it, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        }
    }

    protected fun updateContent(name: String, avatarURL: String, microphoneOn: Boolean) {
        noVideoView.update(avatarURL)
        overlayView.update(name, microphoneOn)
    }

    protected fun updateVisibility(isEmpty: Boolean, showsNoVideo: Boolean, showsOverlay: Boolean) {
        emptyView.visibility = if (isEmpty) VISIBLE else INVISIBLE
        noVideoView.visibility = if (!isEmpty && showsNoVideo) VISIBLE else INVISIBLE
        overlayView.visibility = if (!isEmpty && showsOverlay) VISIBLE else INVISIBLE
    }
}

private class LiveSeatEmptyView(context: Context, title: String) : FrameLayout(context) {

    private val contentView = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
    }
    private val plusView = AtomicLabel(context).apply {
        text = "+"
        gravity = Gravity.CENTER
        setTextColor(Color.argb(117, 255, 255, 255))
        includeFontPadding = false
    }
    private val invitationView = LiveText(context).apply {
        role = LiveTheme.TextRole.METADATA
        tone = LiveTheme.TextTone.TERTIARY
        text = title
        gravity = Gravity.CENTER
        includeFontPadding = false
    }

    init {
        isClickable = false
        isFocusable = false
        setBackgroundColor(Color.TRANSPARENT)
        val inset = dp(1f).toInt()
        addView(
            contentView,
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT).apply {
                setMargins(inset, inset, inset, inset)
            },
        )
        contentView.addView(
            plusView,
            LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, dp(32f).toInt()),
        )
        contentView.addView(
            invitationView,
            LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, dp(14f).toInt()).apply {
                topMargin = dp(3f).toInt()
            },
        )
    }

    override fun onSizeChanged(width: Int, height: Int, oldWidth: Int, oldHeight: Int) {
        super.onSizeChanged(width, height, oldWidth, oldHeight)
        val contentWidth = (width - dp(2f)).coerceAtLeast(0f)
        val contentHeight = (height - dp(2f)).coerceAtLeast(0f)
        val compact = min(contentWidth, contentHeight) <= dp(110f)
        contentView.background = ColorDrawable(
            Color.argb(if (compact) 14 else 9, 255, 255, 255),
        )
        plusView.setTextSize(TypedValue.COMPLEX_UNIT_SP, if (compact) 29f else 26f)
        plusView.typeface = Typeface.create("sans-serif-thin", Typeface.NORMAL)

        val plusHeight = dp(if (compact) 32f else 30f).toInt()
        val spacing = dp(3f).toInt()
        val invitationHeight = dp(14f).toInt()
        val showsInvitation = contentHeight >= plusHeight + spacing + invitationHeight
        invitationView.visibility = if (showsInvitation) VISIBLE else GONE
        plusView.layoutParams = (plusView.layoutParams as LinearLayout.LayoutParams).apply {
            this.height = plusHeight
        }
        invitationView.layoutParams =
            (invitationView.layoutParams as LinearLayout.LayoutParams).apply {
                this.height = invitationHeight
                topMargin = spacing
            }
    }

    private fun dp(value: Float): Float = value * resources.displayMetrics.density
}

private class LiveSeatNoVideoView(
    context: Context,
) : FrameLayout(context) {

    private val avatarView = AtomicAvatar(context)
    private var avatarURL: String? = null

    init {
        clipChildren = true
        background = GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(ContextCompat.getColor(context, R.color.livekit_pusher_coguest_no_video_start), ContextCompat.getColor(context, R.color.livekit_pusher_coguest_no_video_end)),
        )
        avatarView.setShape(AtomicAvatar.AvatarShape.Round)
        addView(avatarView)
    }

    fun update(avatarURL: String) {
        val url = avatarURL.trim()
        if (this.avatarURL == url) return
        this.avatarURL = url
        avatarView.setContent(AtomicAvatar.AvatarContent.URL(url, R.drawable.livekit_livelist_default_avatar))
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        val size = avatarSize(measuredWidth, measuredHeight)
        val exactSize = MeasureSpec.makeMeasureSpec(size, MeasureSpec.EXACTLY)
        avatarView.measure(exactSize, exactSize)
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        val size = avatarSize(width, height)
        val avatarLeft = (width - size) / 2
        val avatarTop = (height - size) / 2
        avatarView.layout(avatarLeft, avatarTop, avatarLeft + size, avatarTop + size)
    }

    private fun avatarSize(containerWidth: Int, containerHeight: Int): Int {
        val shortSide = min(containerWidth, containerHeight) / resources.displayMetrics.density
        val avatarDp = when {
            shortSide <= 110f -> 42f
            shortSide <= 140f -> 30f
            shortSide > 250f -> 67f
            else -> 63f
        }
        return dp(avatarDp).toInt()
    }

    private fun dp(value: Float): Float = value * resources.displayMetrics.density
}

private class LiveSeatOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : ConstraintLayout(context, attrs, defStyleAttr) {
    private val microphoneImage: AppCompatImageView
    private val nameView: AtomicLabel

    init {
        LayoutInflater.from(context).inflate(R.layout.livekit_seat_overlay_view, this, true)
        microphoneImage = findViewById(R.id.livekit_seat_microphone_icon)
        nameView = findViewById(R.id.livekit_seat_name)
        isClickable = false
        isFocusable = false
    }

    fun update(name: String, microphoneOn: Boolean) {
        microphoneImage.setImageResource(
            if (microphoneOn) {
                R.drawable.livekit_coguest_seat_mic
            } else {
                R.drawable.livekit_coguest_seat_muted
            }
        )
        nameView.text = name
    }
}
