package com.trtc.uikit.livekit.pusher.living

import android.content.Context
import android.graphics.Color
import android.graphics.PointF
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import com.trtc.uikit.livekit.common.theme.LiveToast
import androidx.appcompat.widget.AppCompatImageView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import com.trtc.uikit.livekit.R

class LiveBottomToolbarView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : ConstraintLayout(context, attrs, defStyleAttr) {

    private val pkAction: View
    private val flipAction: View
    private val musicAction: View
    private val actions: List<View>
    private val actionLabels: List<TextView>
    private val coGuestAction: View
    private val moreAction: View
    private val coGuestIcon: AppCompatImageView
    private val contentHeight = (68 * resources.displayMetrics.density).toInt()

    private var liveID = ""

    /** Raised when the host taps the co-guest action. Ignored until a broadcast is bound. */
    var onShowCoGuest: (() -> Unit)? = null

    /** Raised when the host taps the more action. Ignored until a broadcast is bound. */
    var onShowLivingSettings: (() -> Unit)? = null

    internal var onFlipCamera: (() -> Unit)? = null
    internal var onShowMusic: (() -> Unit)? = null

    init {
        // The icon glow extends beyond the action's bounds.
        // Allow the action subtree to draw outside those bounds as well.
        clipChildren = false
        LayoutInflater.from(context).inflate(R.layout.livekit_pusher_live_bottom_toolbar_view, this, true)
        pkAction = findViewById(R.id.livekit_pusher_living_toolbar_pk)
        flipAction = findViewById(R.id.livekit_pusher_living_toolbar_flip)
        musicAction = findViewById(R.id.livekit_pusher_living_toolbar_music)
        coGuestAction = findViewById(R.id.livekit_pusher_living_toolbar_coguest)
        moreAction = findViewById(R.id.livekit_pusher_living_toolbar_more)
        coGuestIcon = findViewById(R.id.livekit_pusher_living_toolbar_coguest_icon)

        actions = listOf(pkAction, coGuestAction, flipAction, musicAction, moreAction)
        actionLabels = listOf(
            R.id.livekit_pusher_living_toolbar_pk_label, R.id.livekit_pusher_living_toolbar_coguest_label,
            R.id.livekit_pusher_living_toolbar_flip_label, R.id.livekit_pusher_living_toolbar_music_label,
            R.id.livekit_pusher_living_toolbar_more_label,
        ).map { findViewById(it) }
        pkAction.setOnClickListener {
            if (liveID.isNotEmpty()) LiveToast.show(context, context.getString(R.string.livekit_pusher_pk_developing))
        }
        flipAction.setOnClickListener { if (liveID.isNotEmpty()) onFlipCamera?.invoke() }
        musicAction.setOnClickListener { if (liveID.isNotEmpty()) onShowMusic?.invoke() }
        coGuestAction.setOnClickListener { if (liveID.isNotEmpty()) onShowCoGuest?.invoke() }
        moreAction.setOnClickListener { if (liveID.isNotEmpty()) onShowLivingSettings?.invoke() }
        setCoGuestStageActive(false)
        ViewCompat.setOnApplyWindowInsetsListener(this) { _, insets ->
            val navigationBottom = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
            val bottomAreaHeight = contentHeight + navigationBottom
            layoutParams?.let { params ->
                if (params.height != bottomAreaHeight) {
                    params.height = bottomAreaHeight
                    layoutParams = params
                }
            }
            val actionTop = (16 * resources.displayMetrics.density).toInt()
            actions.forEach { action ->
                action.updateLayoutParams<MarginLayoutParams> {
                    topMargin = actionTop
                }
            }
            insets
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val minimum = resources.getDimension(R.dimen.livekit_pusher_living_toolbar_action_width)
        val fixedSpacing = resources.getDimensionPixelSize(
            R.dimen.livekit_pusher_living_toolbar_pk_inset
        ) + resources.getDimensionPixelSize(
            R.dimen.livekit_pusher_living_toolbar_pk_gap
        ) + 4 * resources.getDimensionPixelSize(R.dimen.livekit_pusher_living_toolbar_action_gap)
        val available =
            (MeasureSpec.getSize(widthMeasureSpec) - fixedSpacing).toFloat().coerceAtLeast(minimum * actions.size)
        val labelSize = resources.getDimension(R.dimen.livekit_pusher_living_toolbar_label_size)
        val preferred = actionLabels.map { label ->
            maxOf(
                minimum, kotlin.math.ceil(label.paint.measureText(label.text.toString()) * labelSize / label.textSize)
            )
        }
        val extra = preferred.sum() - minimum * actions.size
        val ratio = if (extra > 0) ((available - minimum * actions.size) / extra).coerceIn(0f, 1f) else 1f
        actions.forEachIndexed { index, action ->
            action.layoutParams.width = kotlin.math.floor(minimum + (preferred[index] - minimum) * ratio).toInt()
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }

    fun bind(liveID: String) {
        this.liveID = liveID
        setCoGuestUnread(false)
        setCoGuestStageActive(false)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        ViewCompat.requestApplyInsets(this)
    }

    fun setCoGuestStageActive(isActive: Boolean) {
        setBackgroundColor(
            if (isActive) {
                Color.TRANSPARENT
            } else {
                ContextCompat.getColor(context, R.color.livekit_control_base)
            },
        )
    }

    fun setCoGuestUnread(hasUnread: Boolean) {
        coGuestIcon.setImageResource(
            if (hasUnread) R.drawable.livekit_pusher_living_toolbar_coguest_unread
            else R.drawable.livekit_pusher_living_toolbar_coguest,
        )
    }

    internal fun likeAnimationOriginIn(target: View): PointF {
        val toolbarLocation = IntArray(2)
        val targetLocation = IntArray(2)
        getLocationInWindow(toolbarLocation)
        target.getLocationInWindow(targetLocation)
        return PointF(
            toolbarLocation[0] + moreAction.left + moreAction.width / 2f - targetLocation[0],
            toolbarLocation[1] + moreAction.top - resources.getDimension(
                R.dimen.livekit_pusher_living_toolbar_like_origin_offset
            ) - targetLocation[1],
        )
    }
}
