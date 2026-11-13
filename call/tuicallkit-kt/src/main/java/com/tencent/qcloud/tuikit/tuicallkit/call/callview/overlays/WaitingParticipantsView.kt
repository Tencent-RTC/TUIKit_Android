package com.tencent.qcloud.tuikit.tuicallkit.call.callview.overlays

import android.content.Context
import android.graphics.Outline
import android.graphics.drawable.GradientDrawable
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
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import com.tencent.qcloud.tuikit.tuicallkit.R
import com.tencent.qcloud.tuikit.tuicallkit.call.CallKitConfig
import com.tencent.qcloud.tuikit.tuicallkit.common.CallKitContactLoader
import com.tencent.qcloud.tuikit.tuicallkit.common.CallKitLogger
import com.tencent.qcloud.tuikit.tuicallkit.common.dp
import io.trtc.tuikit.atomicxcore.api.call.CallMediaType
import io.trtc.tuikit.atomicxcore.api.call.CallStore
import io.trtc.tuikit.atomicxcore.api.login.LoginStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

internal class WaitingParticipantsView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr) {

    var onAccept: (() -> Unit)? = null
    var onReject: (() -> Unit)? = null
    var onContentLayout: (() -> Unit)? = null

    private val avatarPlaceholder = View(context)
    private val namePlaceholder = View(context)
    private val kickerLabel: TextView
    private val othersTitleLabel: TextView
    private val othersStack: LinearLayout
    private val othersColumn: LinearLayout
    private val centerColumn: LinearLayout
    private var bottomInset = 0
    private var subscribeJob: Job? = null
    private var othersLoadToken = 0
    private val logger = CallKitLogger.getLogger("WaitingParticipantsView")

    init {
        kickerLabel = TextView(context).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setTextColor(ContextCompat.getColor(context, R.color.tuicallkit_text_on_dark_secondary))
            gravity = Gravity.CENTER
        }

        othersTitleLabel = TextView(context).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setTextColor(ContextCompat.getColor(context, R.color.tuicallkit_text_on_dark_secondary))
            gravity = Gravity.CENTER
            maxLines = 1
            text = context.getString(R.string.tuicallkit_incoming_others_title)
        }
        othersStack = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        othersColumn = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            isVisible = false
            addView(othersTitleLabel, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
            val stackParams = LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
            stackParams.topMargin = 12.dp(context)
            addView(othersStack, stackParams)
        }

        val avatarSize = 96.dp(context)
        centerColumn = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            addView(avatarPlaceholder, LinearLayout.LayoutParams(avatarSize, avatarSize))
            val nameParams = LinearLayout.LayoutParams(1.dp(context), 24.dp(context))
            nameParams.topMargin = 12.dp(context)
            addView(namePlaceholder, nameParams)
            val kickerParams = LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
            kickerParams.topMargin = 12.dp(context)
            addView(kickerLabel, kickerParams)
            val othersParams = LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
            othersParams.topMargin = 14.dp(context)
            addView(othersColumn, othersParams)
        }
        val centerParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL)
        centerParams.marginStart = 28.dp(context)
        centerParams.marginEnd = 28.dp(context)
        addView(centerColumn, centerParams)
        centerColumn.post { centerCenterColumn() }

        val mediaType = CallStore.shared.observerState.activeCall.value.mediaType

        val rejectButton = FrameLayout(context).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(ContextCompat.getColor(context, R.color.tuicallkit_call_red))
            }
            clipToOutline = true
            val icon = ImageView(context).apply {
                setImageResource(R.drawable.tuicallkit_icon_reject)
                scaleType = ImageView.ScaleType.FIT_CENTER
            }
            addView(icon, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
            setOnClickListener { onReject?.invoke() }
        }

        val acceptButton = FrameLayout(context).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(ContextCompat.getColor(context, R.color.tuicallkit_call_green))
            }
            clipToOutline = true
            val icon = ImageView(context).apply {
                scaleType = ImageView.ScaleType.FIT_CENTER
            }
            if (mediaType == CallMediaType.Video) {
                icon.setImageResource(R.drawable.tuicallkit_icon_video_glyph)
                icon.setColorFilter(ContextCompat.getColor(context, android.R.color.white))
                addView(icon, LayoutParams(24.dp(context), 24.dp(context), Gravity.CENTER))
            } else {
                icon.setImageResource(R.drawable.tuicallkit_icon_accept)
                addView(icon, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
            }
            setOnClickListener { onAccept?.invoke() }
        }

        val rejectColumn = makeActionColumn(rejectButton, context.getString(R.string.tuicallkit_incoming_reject))
        val acceptColumn = makeActionColumn(acceptButton, context.getString(R.string.tuicallkit_incoming_accept))

        addView(rejectColumn, actionColumnParams())
        addView(acceptColumn, actionColumnParams())
        rejectColumn.tag = -88
        acceptColumn.tag = 88

        ViewCompat.setOnApplyWindowInsetsListener(this) { _, insets ->
            bottomInset = insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom
            requestLayout()
            insets
        }
    }

    private fun centerCenterColumn() {
        if (height <= 0) {
            return
        }
        val lp = centerColumn.layoutParams as LayoutParams
        val target = (height * 0.84f / 2f - centerColumn.measuredHeight / 2f).toInt().coerceAtLeast(0)
        if (lp.topMargin != target) {
            lp.topMargin = target
            centerColumn.layoutParams = lp
        }
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        // 实际布局完成后再通知外部同步头像槽位（getLocationOnScreen 读到的才是新坐标）
        onContentLayout?.invoke()
    }

    fun avatarSlotCenterYIn(target: View): Float {
        val slotLoc = IntArray(2)
        val targetLoc = IntArray(2)
        avatarPlaceholder.getLocationOnScreen(slotLoc)
        target.getLocationOnScreen(targetLoc)
        return slotLoc[1] + avatarPlaceholder.height / 2f - targetLoc[1]
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        subscribeJob?.cancel()
        subscribeJob = CoroutineScope(Dispatchers.Main + SupervisorJob()).launch {
            CallStore.shared.observerState.allParticipants
                .map { list -> list.map { it.id } }
                .distinctUntilChanged()
                .collect { reload() }
        }
    }

    override fun onDetachedFromWindow() {
        subscribeJob?.cancel()
        subscribeJob = null
        othersLoadToken++
        super.onDetachedFromWindow()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        centerCenterColumn()
        for (i in 0 until childCount) {
            val child = getChildAt(i)
            val offsetDp = (child.tag as? Int) ?: continue
            val params = child.layoutParams as? LayoutParams ?: continue
            params.bottomMargin = 28.dp(context) + bottomInset
            child.post {
                params.marginStart = (w / 2f + offsetDp.dp(context) - child.width / 2f).toInt().coerceAtLeast(0)
                child.layoutParams = params
            }
        }
    }

    private fun makeActionColumn(button: View, label: String): LinearLayout {
        val column = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
        }
        val buttonSize = 60.dp(context)
        column.addView(button, LinearLayout.LayoutParams(buttonSize, buttonSize))
        val textView = TextView(context).apply {
            text = label
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTextColor(ContextCompat.getColor(context, R.color.tuicallkit_incoming_action_text))
            gravity = Gravity.CENTER
        }
        val labelParams = LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
        labelParams.topMargin = 8.dp(context)
        column.addView(textView, labelParams)
        return column
    }

    private fun actionColumnParams(): LayoutParams {
        val params = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.BOTTOM or Gravity.START)
        params.bottomMargin = 28.dp(context) + bottomInset
        return params
    }

    fun reload() {
        val state = CallStore.shared.observerState
        val call = state.activeCall.value
        val inviterID = call.inviterId
        if (inviterID.isEmpty()) {
            return
        }
        val base = context.getString(
            if (call.mediaType == CallMediaType.Video) R.string.tuicallkit_incoming_video
            else R.string.tuicallkit_incoming_audio
        )
        kickerLabel.text = if (CallKitConfig.shared.enableMuteMode) {
            base + context.getString(R.string.tuicallkit_incoming_muted_suffix)
        } else {
            base
        }

        val selfID = LoginStore.shared.loginState.loginUserInfo.value?.userID.orEmpty()
            .ifEmpty { state.selfInfo.value.id }
        val participantIds = LinkedHashSet<String>().apply {
            state.allParticipants.value.forEach { add(it.id) }
            addAll(call.inviteeIds)
        }
        val others = participantIds.filter { it.isNotEmpty() && it != selfID && it != inviterID }
        val isMulti = call.chatGroupId.isNotEmpty() || call.inviteeIds.size > 1
        val showOthers = isMulti && others.isNotEmpty()
        logger.info("incoming others: multi=$isMulti count=${others.size} show=$showOthers selfReady=${selfID.isNotEmpty()}")
        kickerLabel.isVisible = !showOthers
        othersColumn.isVisible = showOthers
        val loadToken = ++othersLoadToken
        if (showOthers) {
            loadOthers(others, loadToken)
        }
        // 内容显隐改变中心组高度后重新居中，并通知外部同步头像槽位
        centerColumn.post {
            centerCenterColumn()
            onContentLayout?.invoke()
        }
    }

    private fun loadOthers(userIDs: List<String>, loadToken: Int) {
        othersStack.removeAllViews()
        for ((index, userID) in userIDs.withIndex()) {
            val imageView = ImageView(context).apply {
                setImageResource(R.drawable.tuicallkit_default_avatar)
                scaleType = ImageView.ScaleType.CENTER_CROP
                clipToOutline = true
                outlineProvider = object : ViewOutlineProvider() {
                    override fun getOutline(view: View, outline: Outline) {
                        outline.setRoundRect(0, 0, view.width, view.height, 10f.dp(context).toFloat())
                    }
                }
            }
            val container = FrameLayout(context).apply {
                elevation = 5f.dp(context).toFloat()
            }
            container.addView(imageView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
            val size = 36.dp(context)
            val params = LinearLayout.LayoutParams(size, size)
            params.marginEnd = if (index < userIDs.size - 1) 8.dp(context) else 0
            othersStack.addView(container, params)
        }
        CallKitContactLoader.load(userIDs) { userID, _, _, bitmap ->
            if (loadToken != othersLoadToken) {
                return@load
            }
            val index = userIDs.indexOf(userID)
            if (bitmap == null || index < 0 || index >= othersStack.childCount) {
                return@load
            }
            val container = othersStack.getChildAt(index) as? FrameLayout
            (container?.getChildAt(0) as? ImageView)?.setImageBitmap(bitmap)
        }
    }
}
