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
import com.trtc.uikit.roomkit.base.error.ErrorLocalized
import com.trtc.uikit.roomkit.base.popover.RoomPopover
import com.trtc.uikit.roomkit.base.toast.RoomToast
import com.trtc.uikit.roomkit.base.util.dp
import io.trtc.tuikit.atomicxcore.api.CompletionHandler
import io.trtc.tuikit.atomicxcore.api.room.ParticipantRole
import io.trtc.tuikit.atomicxcore.api.room.RoomParticipantStore
import io.trtc.tuikit.atomicxcore.api.room.RoomStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

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
    private var roomID = ""
    private var roleJob: Job? = null

    init {
        LayoutInflater.from(context).inflate(R.layout.roomkit_leave_room_button, this, true)
        trigger = findViewById(R.id.leave_trigger)
        icon = findViewById(R.id.leave_icon)
        cancelLabel = findViewById(R.id.leave_cancel_label)
        setOnClickListener {
            when {
                isMember -> leaveRoom(endAll = false)
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

    fun init(roomID: String) {
        this.roomID = roomID
        observeRole()
    }

    override fun onDetachedFromWindow() {
        roleJob?.cancel()
        roleJob = null
        super.onDetachedFromWindow()
    }

    private fun observeRole() {
        roleJob?.cancel()
        roleJob = CoroutineScope(Dispatchers.Main).launch {
            RoomParticipantStore.create(roomID).state.localParticipant.collect { local ->
                isMember = local?.role != ParticipantRole.OWNER
                if (isMember) closePrompt()
            }
        }
    }

    private fun leaveRoom(endAll: Boolean) {
        val completion = object : CompletionHandler {
            override fun onSuccess() {
                RoomToast.show(
                    context,
                    context.getString(if (endAll) R.string.roomkit_main_ended else R.string.roomkit_main_left),
                )
                (context as? Activity)?.finish()
            }

            override fun onFailure(code: Int, desc: String) {
                ErrorLocalized.showError(context, code, desc)
                (context as? Activity)?.finish()
            }
        }
        if (endAll) {
            RoomStore.shared().endRoom(completion)
        } else {
            RoomStore.shared().leaveRoom(completion)
        }
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
            leaveRoom(endAll = true)
        })
        (menu.getChildAt(menu.childCount - 1).layoutParams as LinearLayout.LayoutParams).bottomMargin = context.dp(MENU_GAP_DP)
        menu.addView(menuItem(R.string.roomkit_main_leave_self, R.drawable.roomkit_bg_menu_leave, R.color.roomkit_fg_primary) {
            closePrompt()
            leaveRoom(endAll = false)
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
