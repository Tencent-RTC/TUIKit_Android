package com.trtc.uikit.roomkit.inroom.features.roominfo.leaveroombutton

import android.app.Activity
import android.content.Context
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.trtc.uikit.roomkit.R
import com.trtc.uikit.roomkit.base.popover.RoomPopover
import com.trtc.uikit.roomkit.base.util.dp

class LeaveRoomButton @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr) {

    private val trigger: LinearLayout
    private val icon: ImageView
    private val cancelLabel: TextView
    private var isMember = false
    private var isPromptOpen = false
    private var popover: RoomPopover? = null
    private var onLeaveClick: (() -> Unit)? = null
    private var onEndClick: (() -> Unit)? = null

    init {
        LayoutInflater.from(context).inflate(R.layout.roomkit_leave_room_button, this, true)
        trigger = findViewById(R.id.leave_trigger)
        icon = findViewById(R.id.leave_icon)
        cancelLabel = findViewById(R.id.leave_cancel_label)
        setOnClickListener {
            when {
                isMember -> onLeaveClick?.invoke()
                isPromptOpen -> closePrompt()
                else -> openPrompt()
            }
        }
        initIconAlpha()
    }

    private fun initIconAlpha() {
        icon.alpha = 1f
        cancelLabel.alpha = 0f
    }

    override fun setPressed(pressed: Boolean) {
        super.setPressed(pressed)
        val scale = if (pressed) PRESSED_SCALE else 1f
        trigger.animate()
            .scaleX(scale)
            .scaleY(scale)
            .setDuration(PRESSED_ANIM_DURATION)
            .start()
    }

    fun setMemberRole(member: Boolean) {
        isMember = member
        if (member) closePrompt()
    }

    fun setOnLeaveClickListener(listener: (() -> Unit)?) {
        onLeaveClick = listener
    }

    fun setOnEndClickListener(listener: (() -> Unit)?) {
        onEndClick = listener
    }

    private fun openPrompt() {
        if (popover != null) return
        val created = RoomPopover(this)
        created.align = RoomPopover.Align.END
        created.showsArrow = false
        created.gap = context.dp(POPOVER_GAP_DP)
        val menu = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 0, 0)
        }
        menu.addView(menuItem(R.string.roomkit_main_leave_end, R.drawable.roomkit_bg_menu_end, R.color.roomkit_fg_on_danger) {
            closePrompt()
            onEndClick?.invoke()
        })
        (menu.getChildAt(menu.childCount - 1).layoutParams as LinearLayout.LayoutParams).bottomMargin = context.dp(MENU_GAP_DP)
        menu.addView(menuItem(R.string.roomkit_main_leave_self, R.drawable.roomkit_bg_menu_leave, R.color.roomkit_fg_primary) {
            closePrompt()
            onLeaveClick?.invoke()
        })
        created.contentView.addView(
            menu,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT),
        )
        created.onClose = { closePrompt() }
        popover = created
        created.show(hostView())
        applyPromptState(true)
    }

    private fun closePrompt() {
        popover?.close()
        popover = null
        applyPromptState(false)
    }

    private fun applyPromptState(open: Boolean) {
        isPromptOpen = open
        trigger.setBackgroundResource(
            if (open) R.drawable.roomkit_bg_leave_pressed_pill else R.drawable.roomkit_bg_leave_danger_pill,
        )
        if (open) {
            cancelLabel.visibility = View.VISIBLE
            icon.animate().alpha(0f).setDuration(STATE_ANIM_DURATION).start()
            cancelLabel.animate().alpha(1f).setDuration(STATE_ANIM_DURATION).start()
        } else {
            icon.animate().alpha(1f).setDuration(STATE_ANIM_DURATION).start()
            cancelLabel.animate().alpha(0f).setDuration(STATE_ANIM_DURATION).withEndAction {
                if (!isPromptOpen) cancelLabel.visibility = View.GONE
            }.start()
        }
    }

    private fun hostView(): ViewGroup {
        val activity = context as? Activity ?: throw IllegalStateException("LeaveRoomButton requires an Activity context")
        return activity.findViewById(android.R.id.content)
    }

    private fun menuItem(
        titleRes: Int,
        backgroundRes: Int,
        textColorRes: Int,
        onTap: () -> Unit,
    ): View = Button(context).apply {
        text = context.getString(titleRes)
        setBackgroundResource(backgroundRes)
        setTextColor(context.getColor(textColorRes))
        textSize = MENU_TEXT_SP.toFloat()
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        minWidth = 0
        minHeight = 0
        minimumWidth = 0
        minimumHeight = 0
        setPadding(0, 0, 0, 0)
        gravity = Gravity.CENTER
        includeFontPadding = false
        stateListAnimator = null
        setAllCaps(false)
        setOnClickListener { onTap() }
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, context.dp(MENU_HEIGHT_DP))
    }

    private companion object {
        const val MENU_HEIGHT_DP = 44
        const val MENU_GAP_DP = 6
        const val POPOVER_GAP_DP = 8
        const val MENU_TEXT_SP = 15
        const val STATE_ANIM_DURATION = 200L
        const val PRESSED_SCALE = 0.97f
        const val PRESSED_ANIM_DURATION = 80L
    }
}
