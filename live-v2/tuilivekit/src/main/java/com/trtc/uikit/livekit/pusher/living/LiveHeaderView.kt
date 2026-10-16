package com.trtc.uikit.livekit.pusher.living

import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import com.trtc.uikit.livekit.R
import com.trtc.uikit.livekit.pusher.living.audience.LiveAudiencePresenceView
import com.trtc.uikit.livekit.pusher.living.audience.PusherAudienceStore
import com.trtc.uikit.livekit.common.theme.avatar.AtomicAvatar
import com.trtc.uikit.livekit.common.theme.label.AtomicLabel
import io.trtc.tuikit.atomicxcore.api.device.NetworkQuality
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.roundToInt

class LiveHeaderView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : ConstraintLayout(context, attrs, defStyleAttr) {

    companion object {
        private const val SECONDS_PER_HOUR = 3600
        private const val SECONDS_PER_MINUTE = 60
    }

    private val avatarView: AtomicAvatar
    private val nameView: AtomicLabel
    private val likeView: AtomicLabel
    private val audiencePresenceView: LiveAudiencePresenceView
    private val statusView: View
    private val statusIconView: ImageView
    private val statusLabel: AtomicLabel
    private val statusChevronView: ImageView

    private var realNetworkQuality = NetworkQuality.UNKNOWN
    private var elapsedDuration = "00:00:00"
    private var renderedHostName: String? = null
    private var renderedAvatarURL: String? = null
    private var renderedLikeCount: Int? = null
    private var renderedElapsedSeconds: Int? = null
    private var renderedNetworkStatus: NetworkStatusPresentation? = null

    /** Raised when the host taps the close button and has to confirm ending the broadcast. */
    var onRequestEnd: (() -> Unit)? = null

    /** Raised when the host taps the duration capsule to open the live status panel. */
    var onShowStatus: (() -> Unit)? = null

    /** Raised when the host taps the audience presence pill. */
    var onShowAudience: (() -> Unit)? = null

    init {
        LayoutInflater.from(context).inflate(R.layout.livekit_pusher_live_header_view, this, true)
        avatarView = findViewById(R.id.livekit_pusher_header_avatar)
        nameView = findViewById(R.id.livekit_pusher_header_name)
        likeView = findViewById(R.id.livekit_pusher_header_like_count)
        audiencePresenceView = findViewById(R.id.livekit_pusher_header_audience_presence)
        statusView = findViewById(R.id.livekit_pusher_header_status)
        statusIconView = findViewById(R.id.livekit_pusher_header_status_icon)
        statusLabel = findViewById(R.id.livekit_pusher_header_status_label)
        statusChevronView = findViewById(R.id.livekit_pusher_header_status_chevron)

        findViewById<View>(R.id.livekit_pusher_header_close).setOnClickListener {
            onRequestEnd?.invoke()
        }
        statusView.setOnClickListener { onShowStatus?.invoke() }
        audiencePresenceView.onShowAudience = { onShowAudience?.invoke() }
        updateLikeCount(0)
        updateHost("", "")
        renderNetworkStatus()
    }

    internal fun bind(
        hostName: String,
        hostAvatarURL: String,
        elapsedSeconds: Int,
        audience: List<PusherAudienceStore.User>,
        viewerCount: Int,
        likeCount: Int,
        networkQuality: NetworkQuality,
    ) {
        updateHost(hostName, hostAvatarURL)
        updateAudience(audience, viewerCount)
        updateLikeCount(likeCount)
        updateNetworkQuality(networkQuality)
        updateElapsedSeconds(elapsedSeconds)
    }

    internal fun updateHost(name: String, avatarURL: String) {
        if (renderedHostName != name) {
            renderedHostName = name
            nameView.text = name.ifEmpty { context.getString(R.string.live_barrage_anchor) }
        }
        val url = avatarURL.trim()
        if (renderedAvatarURL == url) return
        renderedAvatarURL = url
        avatarView.setContent(
            AtomicAvatar.AvatarContent.URL(
                url,
                R.drawable.livekit_livelist_default_avatar,
            )
        )
    }

    internal fun updateAudience(users: List<PusherAudienceStore.User>, count: Int) {
        audiencePresenceView.bind(users, count)
    }

    internal fun updateLikeCount(count: Int) {
        val value = count.coerceAtLeast(0)
        if (renderedLikeCount == value) return
        renderedLikeCount = value
        likeView.text = context.getString(
            R.string.livekit_pusher_header_like_count_format,
            value.toString(),
        )
    }

    internal fun updateElapsedSeconds(elapsedSeconds: Int) {
        val safeSeconds = elapsedSeconds.coerceAtLeast(0)
        if (renderedElapsedSeconds == safeSeconds) return
        renderedElapsedSeconds = safeSeconds
        elapsedDuration = String.format(
            Locale.US,
            "%02d:%02d:%02d",
            safeSeconds / SECONDS_PER_HOUR,
            safeSeconds % SECONDS_PER_HOUR / SECONDS_PER_MINUTE,
            safeSeconds % SECONDS_PER_MINUTE,
        )
        updateStatusContentDescription()
    }

    internal fun updateNetworkQuality(networkQuality: NetworkQuality) {
        realNetworkQuality = networkQuality
        renderNetworkStatus()
    }

    private fun updateStatusContentDescription() {
        statusView.contentDescription = context.getString(
            R.string.livekit_pusher_header_duration_accessibility,
            statusLabel.text,
            elapsedDuration,
        )
    }

    private fun renderNetworkStatus() {
        val presentation = NetworkStatusPresentation.resolve(realNetworkQuality)
        if (renderedNetworkStatus == presentation) return
        renderedNetworkStatus = presentation
        val title = context.getString(presentation.titleRes)
        statusLabel.text = title
        statusLabel.setTextColor(ContextCompat.getColor(context, presentation.colorRes))
        statusIconView.isVisible = presentation.iconRes != null
        presentation.iconRes?.let(statusIconView::setImageResource)
        statusChevronView.setImageResource(presentation.chevronRes)

        val iconWidth = resources.getDimension(presentation.iconWidthRes).roundToInt()
        val labelStart = resources.getDimension(presentation.labelStartRes).roundToInt()
        val chevronWidth = resources.getDimension(presentation.chevronWidthRes).roundToInt()
        val chevronEnd = resources.getDimension(presentation.chevronEndRes).roundToInt()
        val labelChevronGap = resources.getDimension(R.dimen.livekit_pusher_header_status_label_chevron_gap)
        val minimumWidth = resources.getDimension(presentation.minimumWidthRes).roundToInt()

        statusIconView.layoutParams = statusIconView.layoutParams.apply { width = iconWidth }
        (statusLabel.layoutParams as ConstraintLayout.LayoutParams).apply {
            marginStart = labelStart
            statusLabel.layoutParams = this
        }
        (statusChevronView.layoutParams as ConstraintLayout.LayoutParams).apply {
            width = chevronWidth
            marginEnd = chevronEnd
            statusChevronView.layoutParams = this
        }
        val contentWidth = labelStart + ceil(statusLabel.paint.measureText(title)).toInt() +
            labelChevronGap.roundToInt() + chevronWidth + chevronEnd
        statusView.layoutParams = statusView.layoutParams.apply {
            width = max(minimumWidth, contentWidth)
        }
        updateStatusContentDescription()
    }
}

private enum class NetworkStatusPresentation(
    val titleRes: Int,
    val iconRes: Int?,
    val chevronRes: Int,
    val colorRes: Int,
    val minimumWidthRes: Int,
    val iconWidthRes: Int,
    val labelStartRes: Int,
    val chevronWidthRes: Int,
    val chevronEndRes: Int,
) {
    NORMAL(
        R.string.livekit_pusher_status_title,
        null,
        R.drawable.livekit_pusher_living_status_chevron,
        R.color.livekit_pusher_header_status_text,
        R.dimen.livekit_pusher_header_status_normal_width,
        R.dimen.livekit_pusher_header_status_icon_normal_width,
        R.dimen.livekit_pusher_header_status_normal_label_start,
        R.dimen.livekit_pusher_header_status_normal_chevron_width,
        R.dimen.livekit_pusher_header_status_normal_chevron_end,
    ),
    POOR(
        R.string.livekit_pusher_header_network_poor,
        R.drawable.livekit_pusher_living_network_poor,
        R.drawable.livekit_pusher_living_network_poor_chevron,
        R.color.livekit_pusher_header_network_poor,
        R.dimen.livekit_pusher_header_status_poor_width,
        R.dimen.livekit_pusher_header_status_icon_poor_width,
        R.dimen.livekit_pusher_header_status_poor_label_start,
        R.dimen.livekit_pusher_header_status_poor_chevron_width,
        R.dimen.livekit_pusher_header_status_poor_chevron_end,
    ),
    VERY_POOR(
        R.string.livekit_pusher_header_network_very_poor,
        R.drawable.livekit_pusher_living_network_very_poor,
        R.drawable.livekit_pusher_living_network_very_poor_chevron,
        R.color.livekit_pusher_header_network_very_poor,
        R.dimen.livekit_pusher_header_status_very_poor_width,
        R.dimen.livekit_pusher_header_status_icon_very_poor_width,
        R.dimen.livekit_pusher_header_status_very_poor_label_start,
        R.dimen.livekit_pusher_header_status_very_poor_chevron_width,
        R.dimen.livekit_pusher_header_status_very_poor_chevron_end,
    ),
    DISCONNECTED(
        R.string.livekit_pusher_header_network_disconnected,
        R.drawable.livekit_pusher_living_network_disconnected,
        R.drawable.livekit_pusher_living_network_disconnected_chevron,
        R.color.livekit_pusher_header_network_disconnected,
        R.dimen.livekit_pusher_header_status_disconnected_width,
        R.dimen.livekit_pusher_header_status_icon_disconnected_width,
        R.dimen.livekit_pusher_header_status_disconnected_label_start,
        R.dimen.livekit_pusher_header_status_disconnected_chevron_width,
        R.dimen.livekit_pusher_header_status_disconnected_chevron_end,
    );

    companion object {
        fun resolve(quality: NetworkQuality): NetworkStatusPresentation {

            return when (quality) {
                NetworkQuality.POOR -> POOR
                NetworkQuality.BAD, NetworkQuality.VERY_BAD -> VERY_POOR
                NetworkQuality.DOWN -> DISCONNECTED
                NetworkQuality.UNKNOWN, NetworkQuality.EXCELLENT, NetworkQuality.GOOD -> NORMAL
            }
        }
    }
}

internal class LiveHeaderCapsuleView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : ConstraintLayout(context, attrs, defStyleAttr) {

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = resources.displayMetrics.density
        color = ContextCompat.getColor(context, R.color.livekit_pusher_header_capsule_border)
    }
    private val shape = Path()
    private val bounds = RectF()

    init {
        setWillNotDraw(false)
    }

    override fun onSizeChanged(width: Int, height: Int, oldWidth: Int, oldHeight: Int) {
        super.onSizeChanged(width, height, oldWidth, oldHeight)
        bounds.set(0f, 0f, width.toFloat(), height.toFloat())
        val radius = height / 2f
        shape.reset()
        shape.addRoundRect(bounds, radius, radius, Path.Direction.CW)
        fillPaint.shader = LinearGradient(
            0f,
            0f,
            width.toFloat(),
            0f,
            intArrayOf(
                ContextCompat.getColor(context, R.color.livekit_pusher_header_capsule_start),
                ContextCompat.getColor(context, R.color.livekit_pusher_header_capsule_middle_dark),
                ContextCompat.getColor(context, R.color.livekit_pusher_header_capsule_middle_accent),
                ContextCompat.getColor(context, R.color.livekit_pusher_header_capsule_end),
            ),
            floatArrayOf(0f, 0.52f, 0.82f, 1f),
            Shader.TileMode.CLAMP,
        )
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawPath(shape, fillPaint)
        super.onDraw(canvas)
    }

    override fun dispatchDraw(canvas: Canvas) {
        val saveCount = canvas.save()
        canvas.clipPath(shape)
        super.dispatchDraw(canvas)
        canvas.restoreToCount(saveCount)
        val inset = borderPaint.strokeWidth / 2f
        val radius = (height - borderPaint.strokeWidth) / 2f
        canvas.drawRoundRect(
            inset,
            inset,
            width - inset,
            height - inset,
            radius,
            radius,
            borderPaint,
        )
    }
}
