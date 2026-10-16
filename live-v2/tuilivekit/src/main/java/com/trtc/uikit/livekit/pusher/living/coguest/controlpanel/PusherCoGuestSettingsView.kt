package com.trtc.uikit.livekit.pusher.living.coguest.controlpanel

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.core.widget.NestedScrollView
import com.trtc.uikit.livekit.R
import com.trtc.uikit.livekit.common.theme.BottomSheetView
import com.trtc.uikit.livekit.common.theme.LiveText
import com.trtc.uikit.livekit.pusher.living.coguest.PusherCoGuestActionHandling
import com.trtc.uikit.livekit.pusher.living.coguest.PusherCoGuestStore
import com.trtc.uikit.livekit.common.theme.button.AtomicButton
import java.lang.ref.WeakReference

internal class PusherCoGuestSettingsView(context: Context) : BottomSheetView(context) {
    var actionHandler: PusherCoGuestActionHandling?
        get() = actionHandlerReference?.get()
        set(value) {
            actionHandlerReference = value?.let(::WeakReference)
        }

    private val content: View
    private val scrollView: NestedScrollView
    private val connectedView: PusherCoGuestUserSectionView
    private val applicantsView: PusherCoGuestUserSectionView
    private val candidatesView: PusherCoGuestUserSectionView
    private val endAllButton: AtomicButton
    private val titleView: LiveText
    private val layoutControls: Map<PusherCoGuestStore.Layout, PusherCoGuestLayoutControl>

    private var state = PusherCoGuestStore.State()
    private var actionHandlerReference: WeakReference<PusherCoGuestActionHandling>? = null
    private var navigationBottom = 0
    private var targetPanelHeight = dp(MAXIMUM_HEIGHT_DP)

    override val sheetHeight: Int
        get() = targetPanelHeight

    init {
        LayoutInflater.from(context).inflate(R.layout.livekit_pusher_coguest_settings_view, contentView, true)
        content = contentView
        scrollView = content.findViewById(R.id.livekit_pusher_coguest_scroll)
        connectedView = content.findViewById(R.id.livekit_pusher_coguest_connected)
        applicantsView = content.findViewById(R.id.livekit_pusher_coguest_applicants)
        candidatesView = content.findViewById(R.id.livekit_pusher_coguest_candidates)
        endAllButton = content.findViewById(R.id.livekit_pusher_coguest_end_all)
        titleView = content.findViewById(R.id.livekit_pusher_coguest_settings_title)
        endAllButton.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            updateTitleInsets()
        }
        layoutControls = mapOf(
            PusherCoGuestStore.Layout.FLOATING to content.findViewById(
                R.id.livekit_pusher_coguest_layout_floating,
            ),
            PusherCoGuestStore.Layout.GRID_2 to content.findViewById(
                R.id.livekit_pusher_coguest_layout_grid2,
            ),
            PusherCoGuestStore.Layout.FIXED to content.findViewById(
                R.id.livekit_pusher_coguest_layout_fixed,
            ),
            PusherCoGuestStore.Layout.GRID_9 to content.findViewById(
                R.id.livekit_pusher_coguest_layout_grid9,
            ),
        )
        layoutControls.forEach { (layout, control) ->
            control.bind(layout)
            control.onSelect = { actionHandler?.selectLayout(it) }
        }
        connectedView.onAction = { actionHandler?.disconnect(it.user.userID) }
        applicantsView.onAction = { actionHandler?.acceptApplication(it.user.userID) }
        candidatesView.onAction = {
            if (it.action == PusherCoGuestAction.CANCEL_INVITATION) {
                actionHandler?.cancelInvitation(it.user.userID)
            } else {
                actionHandler?.invite(it.user.userID)
            }
        }
        endAllButton.setOnClickListener { actionHandler?.endAllConnections() }
        ViewCompat.setOnApplyWindowInsetsListener(contentView) { _, insets ->
            navigationBottom = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
            scrollView.updateLayoutParams<FrameLayout.LayoutParams> {
                bottomMargin = dp(SCROLL_BOTTOM_DP) + navigationBottom
            }
            render(state)
            insets
        }
    }

    override fun show() {
        render(state)
        scrollView.scrollTo(0, 0)
        super.show()
    }

    fun dismissIfShowing(): Boolean {
        if (!isShowing) return false
        dismiss()
        return true
    }

    fun bind(state: PusherCoGuestStore.State) {
        this.state = state
        render(state)
    }

    private fun render(state: PusherCoGuestStore.State) {
        layoutControls.forEach { (layout, control) ->
            control.update(layout == state.layout, !state.isUpdatingLayout)
        }
        val pending = state.pendingUserIDs
        connectedView.bind(
            PusherCoGuestSection.CONNECTED,
            state.connected.map { PusherCoGuestRow(it, PusherCoGuestAction.END, it.userID in pending, true) },
        )
        applicantsView.bind(
            PusherCoGuestSection.APPLICANTS,
            state.applicants.map {
                PusherCoGuestRow(it, PusherCoGuestAction.ACCEPT, it.userID in pending, !state.isFull)
            },
        )
        val invitees = state.invitees.map {
            PusherCoGuestRow(it, PusherCoGuestAction.CANCEL_INVITATION, it.userID in pending, true)
        }
        val candidates = state.candidates.map {
            PusherCoGuestRow(it, PusherCoGuestAction.INVITE, it.userID in pending, !state.isFull)
        }
        candidatesView.bind(PusherCoGuestSection.CANDIDATES, invitees + candidates)
        applicantsView.isVisible = state.applicants.isNotEmpty()
        candidatesView.isVisible = invitees.isNotEmpty() || candidates.isNotEmpty()
        endAllButton.isVisible = state.connected.isNotEmpty()
        updateTitleInsets()
        endAllButton.isEnabled = state.connected.any { it.userID !in pending }
        endAllButton.alpha = if (endAllButton.isEnabled) 1f else 0.46f
        updateSectionHeight(connectedView)
        updateSectionHeight(applicantsView)
        updateSectionHeight(candidatesView)
        updateSheetHeight(state)
    }

    private fun updateTitleInsets() {
        // Reserve the action's actual width on both sides so the title stays centered.
        val inset = if (endAllButton.isVisible) endAllButton.width + dp(8) else 0
        val params = titleView.layoutParams as FrameLayout.LayoutParams
        if (params.marginStart == inset && params.marginEnd == inset) return
        titleView.updateLayoutParams<FrameLayout.LayoutParams> {
            marginStart = inset
            marginEnd = inset
        }
    }

    private fun updateSectionHeight(view: PusherCoGuestUserSectionView) {
        if (view.layoutParams.height != view.preferredHeight) {
            view.updateLayoutParams<ViewGroup.LayoutParams> { height = view.preferredHeight }
        }
    }

    private fun updateSheetHeight(state: PusherCoGuestStore.State) {
        var contentHeight = LAYOUT_SECTION_DP + SECTION_SPACING_DP + pixelsToDp(connectedView.preferredHeight)
        if (state.applicants.isNotEmpty()) {
            contentHeight += SECTION_SPACING_DP + pixelsToDp(applicantsView.preferredHeight)
        }
        if (state.invitees.isNotEmpty() || state.candidates.isNotEmpty()) {
            contentHeight += SECTION_SPACING_DP + pixelsToDp(candidatesView.preferredHeight)
        }
        val target = dp(SCROLL_TOP_DP + contentHeight + SCROLL_BOTTOM_DP) + navigationBottom
        val maximum = minOf(dp(MAXIMUM_HEIGHT_DP), resourcesHeight() - dp(MINIMUM_TOP_GAP_DP))
        val nextHeight = minOf(target, maximum)
        if (nextHeight == targetPanelHeight) return
        targetPanelHeight = nextHeight
        updateSheetHeight(animated = isShowing)
    }

    private fun resourcesHeight(): Int = content.resources.displayMetrics.heightPixels

    private fun pixelsToDp(value: Int): Int =
        (value / content.resources.displayMetrics.density).toInt()

    private fun dp(value: Int): Int =
        (value * context.resources.displayMetrics.density).toInt()

    private companion object {
        const val SCROLL_TOP_DP = 78
        const val SCROLL_BOTTOM_DP = 16
        const val SECTION_SPACING_DP = 12
        const val LAYOUT_SECTION_DP = 162
        const val MAXIMUM_HEIGHT_DP = 654
        const val MINIMUM_TOP_GAP_DP = 80
    }
}

internal class PusherCoGuestLayoutControl @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : ConstraintLayout(context, attrs, defStyleAttr) {
    var onSelect: ((PusherCoGuestStore.Layout) -> Unit)? = null

    private val iconView: ImageView
    private val titleView: LiveText
    private lateinit var layout: PusherCoGuestStore.Layout
    private val density = resources.displayMetrics.density
    private val shapeBounds = RectF()
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private var isSelectedLayout = false

    init {
        LayoutInflater.from(context).inflate(R.layout.livekit_pusher_coguest_layout_control, this, true)
        iconView = findViewById(R.id.livekit_pusher_coguest_layout_icon)
        titleView = findViewById(R.id.livekit_pusher_coguest_layout_title)
        setWillNotDraw(false)
        background = null
        setOnClickListener { if (isEnabled) onSelect?.invoke(layout) }
    }

    fun bind(layout: PusherCoGuestStore.Layout) {
        this.layout = layout
        iconView.setImageResource(layout.icon)
        titleView.setText(layout.title)
    }

    fun update(selected: Boolean, enabled: Boolean) {
        isEnabled = enabled
        isSelectedLayout = selected
        invalidate()
        val color = ContextCompat.getColor(
            context,
            if (selected) R.color.livekit_pusher_coguest_layout_selected
            else R.color.livekit_pusher_coguest_layout_normal,
        )
        iconView.imageTintList = ColorStateList.valueOf(color)
        titleView.setTextColor(color)
        titleView.isEmphasized = selected
    }

    override fun onDraw(canvas: Canvas) {
        val borderWidth = (if (isSelectedLayout) 1.5f else 1f) * density
        val inset = borderWidth / 2f
        shapeBounds.set(inset, inset, width - inset, height - inset)
        val radius = 12f * density

        fillPaint.shader = if (isSelectedLayout) {
            LinearGradient(
                0f,
                0f,
                0f,
                height.toFloat(),
                intArrayOf(ContextCompat.getColor(context, R.color.livekit_pusher_coguest_layout_active_start), ContextCompat.getColor(context, R.color.livekit_pusher_coguest_layout_active_end)),
                null,
                Shader.TileMode.CLAMP,
            )
        } else {
            LinearGradient(
                0f,
                0f,
                0f,
                height.toFloat(),
                intArrayOf(ContextCompat.getColor(context, R.color.livekit_pusher_coguest_layout_idle_top), ContextCompat.getColor(context, R.color.livekit_pusher_coguest_layout_idle_bottom)),
                null,
                Shader.TileMode.CLAMP,
            )
        }
        canvas.drawRoundRect(shapeBounds, radius, radius, fillPaint)

        borderPaint.strokeWidth = borderWidth
        borderPaint.shader = if (isSelectedLayout) {
            LinearGradient(
                0f,
                height.toFloat(),
                width.toFloat(),
                0f,
                intArrayOf(ContextCompat.getColor(context, R.color.livekit_brand_deep_blue), ContextCompat.getColor(context, R.color.livekit_brand_blue), ContextCompat.getColor(context, R.color.livekit_brand_cyan)),
                floatArrayOf(0f, 0.55f, 1f),
                Shader.TileMode.CLAMP,
            )
        } else {
            LinearGradient(
                0f,
                0f,
                width.toFloat(),
                height.toFloat(),
                ContextCompat.getColor(context, R.color.livekit_pusher_coguest_layout_inactive_border),
                ContextCompat.getColor(context, R.color.livekit_pusher_coguest_layout_inactive_border),
                Shader.TileMode.CLAMP,
            )
        }
        canvas.drawRoundRect(shapeBounds, radius, radius, borderPaint)
        super.onDraw(canvas)
    }
}
