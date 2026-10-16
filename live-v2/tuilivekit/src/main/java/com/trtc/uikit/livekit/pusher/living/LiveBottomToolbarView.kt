package com.trtc.uikit.livekit.pusher.living

import android.content.Context
import android.graphics.Color
import android.graphics.PointF
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
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

    private val coGuestAction: View
    private val moreAction: View
    private val coGuestIcon: AppCompatImageView
    private val contentHeight = (68 * resources.displayMetrics.density).toInt()

    private var liveID = ""

    /** Raised when the host taps the co-guest action. Ignored until a broadcast is bound. */
    var onShowCoGuest: (() -> Unit)? = null

    /** Raised when the host taps the more action. Ignored until a broadcast is bound. */
    var onShowLivingSettings: (() -> Unit)? = null

    init {
        // The icon glow extends beyond the action's bounds.
        // Allow the action subtree to draw outside those bounds as well.
        clipChildren = false
        LayoutInflater.from(context).inflate(R.layout.livekit_pusher_live_bottom_toolbar_view, this, true)
        coGuestAction = findViewById(R.id.livekit_pusher_living_toolbar_coguest)
        moreAction = findViewById(R.id.livekit_pusher_living_toolbar_more)
        coGuestIcon = findViewById(R.id.livekit_pusher_living_toolbar_coguest_icon)

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
            listOf(coGuestAction, moreAction).forEach { action ->
                action.updateLayoutParams<MarginLayoutParams> {
                    topMargin = actionTop
                }
            }
            insets
        }
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
            toolbarLocation[1] + moreAction.top -
                resources.getDimension(R.dimen.livekit_pusher_living_toolbar_like_origin_offset) -
                targetLocation[1],
        )
    }
}
