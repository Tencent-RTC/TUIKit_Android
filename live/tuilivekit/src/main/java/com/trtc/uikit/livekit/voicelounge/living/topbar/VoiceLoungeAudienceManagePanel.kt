package com.trtc.uikit.livekit.voicelounge.living.topbar

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.tencent.imsdk.v2.V2TIMFollowOperationResult
import com.tencent.imsdk.v2.V2TIMFollowTypeCheckResult
import com.tencent.imsdk.v2.V2TIMManager
import com.tencent.imsdk.v2.V2TIMValueCallback
import com.trtc.uikit.livekit.R
import com.trtc.uikit.livekit.common.log.LiveKitLog
import com.trtc.uikit.livekit.common.theme.BottomSheetView
import com.trtc.uikit.livekit.common.theme.LiveTheme
import com.trtc.uikit.livekit.common.theme.LiveToast
import com.trtc.uikit.livekit.voicelounge.MATCH
import com.trtc.uikit.livekit.voicelounge.VoiceLoungeMain
import com.trtc.uikit.livekit.voicelounge.WRAP
import com.trtc.uikit.livekit.voicelounge.dp
import com.trtc.uikit.livekit.voicelounge.frameParams
import com.trtc.uikit.livekit.voicelounge.linearParams
import com.trtc.uikit.livekit.voicelounge.ovalDrawable
import com.trtc.uikit.livekit.voicelounge.rgb
import com.trtc.uikit.livekit.voicelounge.settings.theme.VoiceLoungeColor
import com.trtc.uikit.livekit.voicelounge.singleLineEnd
import com.trtc.uikit.livekit.voicelounge.vlString
import io.trtc.tuikit.atomicxcore.api.CompletionHandler
import io.trtc.tuikit.atomicxcore.api.live.LiveAudienceStore
import io.trtc.tuikit.atomicxcore.api.live.LiveUserInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

internal class VoiceLoungeAudienceManagePanel(
    context: Context,
    private val liveID: String,
    private val user: LiveUserInfo,
) : BottomSheetView(context) {

    private val store = LiveAudienceStore.create(liveID)
    private val logger = LiveKitLog.getVoiceRoomLogger("VoiceLoungeAudienceManagePanel")
    private var scope: CoroutineScope? = null
    private var isMuted = false
    private var isFollowing = false

    private val nameLabel = TextView(context)
    private val row = LinearLayout(context)
    private val followButton = IconButton(context, PanelGlyph.FOLLOW_ADD)
    private val muteButton = IconButton(context, PanelGlyph.COMMENT_OFF)
    private val kickButton = IconButton(context, PanelGlyph.PERSON_BLOCK, destructive = true)

    override val sheetHeight: Int
        get() = context.dp(TOP_INSET + NAME_HEIGHT + GAP + BUTTON_HEIGHT + BOTTOM_PADDING) + navigationBarHeight()

    init {
        setupViews()
        setupStyles()
        setupBindings()
        render()
    }

    override fun sheetWillPresent() {
        val newScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        scope = newScope
        val userID = user.userID
        newScope.launch {
            store.liveAudienceState.messageBannedUserList.collect { banned ->
                isMuted = banned.any { it.userID == userID }
                renderMute()
            }
        }
        checkFollowType()
    }

    override fun sheetDidDismiss() {
        scope?.cancel()
        scope = null
    }

    private fun setupViews() {
        contentView.addView(
            nameLabel,
            frameParams(MATCH, context.dp(NAME_HEIGHT), Gravity.TOP, start = context.dp(24), end = context.dp(24), top = context.dp(TOP_INSET)),
        )
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER
        contentView.addView(
            row,
            frameParams(WRAP, context.dp(BUTTON_HEIGHT), Gravity.TOP or Gravity.CENTER_HORIZONTAL, top = context.dp(TOP_INSET + NAME_HEIGHT + GAP)),
        )
        val spacing = context.dp(BUTTON_SPACING)
        row.addView(followButton, linearParams(context.dp(BUTTON_WIDTH), MATCH))
        row.addView(muteButton, linearParams(context.dp(BUTTON_WIDTH), MATCH, start = spacing))
        row.addView(kickButton, linearParams(context.dp(BUTTON_WIDTH), MATCH, start = spacing))
    }

    private fun setupStyles() {
        nameLabel.setTextColor(Color.WHITE)
        nameLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
        nameLabel.typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
        nameLabel.gravity = Gravity.CENTER
        nameLabel.singleLineEnd()
    }

    private fun setupBindings() {
        followButton.setOnClickListener { toggleFollow() }
        muteButton.setOnClickListener { toggleMute() }
        kickButton.setOnClickListener {
            store.kickUserOutOfRoom(user.userID, noop())
            dismiss()
        }
    }

    private fun toggleMute() {
        val target = !isMuted
        val name = displayName()
        dismiss()
        store.disableSendMessage(user.userID, target, object : CompletionHandler {
            override fun onSuccess() {
                VoiceLoungeMain.post {
                    LiveToast.show(
                        context,
                        vlString(
                            if (target) R.string.livekit_voicelounge_audience_muted_toast
                            else R.string.livekit_voicelounge_audience_unmuted_toast,
                            name,
                        ),
                    )
                }
            }

            override fun onFailure(code: Int, desc: String) = Unit
        })
    }

    private fun displayName(): String {
        val raw = user.userName.takeIf { it.isNotEmpty() } ?: user.userID
        return if (raw.length > 15) raw.take(15) + "…" else raw
    }

    private fun render() {
        nameLabel.text = displayName()
        kickButton.title = vlString(R.string.livekit_voicelounge_audience_kick)
        renderMute()
        renderFollow()
    }

    private fun renderMute() {
        muteButton.title = vlString(
            if (isMuted) R.string.livekit_voicelounge_audience_unmute else R.string.livekit_voicelounge_audience_mute,
        )
    }

    private fun renderFollow() {
        followButton.title = context.getString(
            if (isFollowing) R.string.common_unfollow_anchor else R.string.common_follow_anchor,
        )
        followButton.glyph = if (isFollowing) PanelGlyph.FOLLOWED_CHECK else PanelGlyph.FOLLOW_ADD
    }

    private fun toggleFollow() {
        val target = !isFollowing
        val callback = object : V2TIMValueCallback<List<V2TIMFollowOperationResult>> {
            override fun onSuccess(results: List<V2TIMFollowOperationResult>?) {
                VoiceLoungeMain.post {
                    isFollowing = target
                    renderFollow()
                }
            }

            override fun onError(code: Int, desc: String?) {
                logger.error("follow failed target=$target code=$code message=${desc.orEmpty()}")
            }
        }
        val manager = V2TIMManager.getFriendshipManager()
        if (target) manager.followUser(listOf(user.userID), callback) else manager.unfollowUser(listOf(user.userID), callback)
    }

    private fun checkFollowType() {
        V2TIMManager.getFriendshipManager().checkFollowType(
            listOf(user.userID),
            object : V2TIMValueCallback<List<V2TIMFollowTypeCheckResult>> {
                override fun onSuccess(results: List<V2TIMFollowTypeCheckResult>?) {
                    VoiceLoungeMain.post {
                        val type = results?.firstOrNull()?.followType ?: return@post
                        isFollowing =
                            type == V2TIMFollowTypeCheckResult.V2TIM_FOLLOW_TYPE_IN_MY_FOLLOWING_LIST || type == V2TIMFollowTypeCheckResult.V2TIM_FOLLOW_TYPE_IN_BOTH_FOLLOWERS_LIST
                        renderFollow()
                    }
                }

                override fun onError(code: Int, desc: String?) {
                    logger.error("checkFollowType failed code=$code message=${desc.orEmpty()}")
                }
            },
        )
    }

    private fun noop(): CompletionHandler = object : CompletionHandler {
        override fun onSuccess() = Unit
        override fun onFailure(code: Int, desc: String) = Unit
    }

    private fun navigationBarHeight(): Int {
        val insets = (context as? android.app.Activity)?.window?.decorView?.rootWindowInsets ?: return 0
        return androidx.core.view.WindowInsetsCompat.toWindowInsetsCompat(insets)
            .getInsets(androidx.core.view.WindowInsetsCompat.Type.navigationBars()).bottom
    }

    private companion object {
        const val TOP_INSET = 24
        const val NAME_HEIGHT = 24
        const val GAP = 26
        const val BUTTON_HEIGHT = 82
        const val BUTTON_WIDTH = 64
        const val BUTTON_SPACING = 54
        const val CIRCLE_SIZE = 56
        const val GLYPH_SIZE = 24
        const val BOTTOM_PADDING = 20
    }

    private enum class PanelGlyph { FOLLOW_ADD, FOLLOWED_CHECK, COMMENT_OFF, PERSON_BLOCK }

    private class IconButton(
        context: Context,
        glyph: PanelGlyph,
        destructive: Boolean = false,
    ) : LinearLayout(context) {
        var title: CharSequence?
            get() = titleLabel.text
            set(value) {
                titleLabel.text = value
            }

        private val titleLabel = TextView(context)
        private val iconView = GlyphView(context, glyph)

        var glyph: PanelGlyph
            get() = iconView.glyph
            set(value) {
                iconView.glyph = value
            }

        init {
            orientation = VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            val tint = if (destructive) DESTRUCTIVE_TINT else NORMAL_TINT
            val circle = FrameLayout(context).apply {
                background = ovalDrawable(VoiceLoungeColor.overlaySoft)
            }
            iconView.strokeColor = tint
            circle.addView(iconView, frameParams(context.dp(GLYPH_SIZE), context.dp(GLYPH_SIZE), Gravity.CENTER))
            addView(circle, linearParams(context.dp(CIRCLE_SIZE), context.dp(CIRCLE_SIZE)))
            titleLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            titleLabel.setTextColor(tint)
            titleLabel.gravity = Gravity.CENTER
            titleLabel.includeFontPadding = false
            titleLabel.maxLines = 1
            addView(titleLabel, linearParams(WRAP, WRAP, top = context.dp(8)))
            setOnTouchListener { _, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> alpha = 0.6f
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> alpha = 1f
                }
                false
            }
        }

        private companion object {
            val DESTRUCTIVE_TINT = rgb(1.0f, 0.36f, 0.42f)
            val NORMAL_TINT = LiveTheme.Color.text(LiveTheme.TextTone.PRIMARY)
        }
    }

    private class GlyphView(context: Context, glyph: PanelGlyph) : View(context) {
        var glyph: PanelGlyph = glyph
            set(value) {
                field = value
                invalidate()
            }

        var strokeColor: Int = Color.WHITE
            set(value) {
                field = value
                paint.color = value
                invalidate()
            }

        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            color = Color.WHITE
        }

        override fun onDraw(canvas: Canvas) {
            val scale = minOf(width, height) / 24f
            canvas.save()
            canvas.scale(scale, scale)
            paint.strokeWidth = 1.6f
            canvas.drawPath(path(glyph), paint)
            canvas.restore()
        }

        private fun path(glyph: PanelGlyph): Path = Path().apply {
            when (glyph) {
                PanelGlyph.FOLLOW_ADD -> {
                    addOval(RectF(6.1f, 4.6f, 12.9f, 11.4f), Path.Direction.CW)
                    moveTo(3.5f, 19.5f)
                    quadTo(9.25f, 12f, 15f, 19.5f)
                    moveTo(19f, 5.5f)
                    lineTo(19f, 11.5f)
                    moveTo(16f, 8.5f)
                    lineTo(22f, 8.5f)
                }

                PanelGlyph.FOLLOWED_CHECK -> {
                    addOval(RectF(6.1f, 4.6f, 12.9f, 11.4f), Path.Direction.CW)
                    moveTo(3.5f, 19.5f)
                    quadTo(9.25f, 12f, 15f, 19.5f)
                    moveTo(15.5f, 8.5f)
                    lineTo(18f, 11f)
                    lineTo(22.5f, 6.5f)
                }

                PanelGlyph.COMMENT_OFF -> {
                    addRoundRect(RectF(3.5f, 4.5f, 18.5f, 14.5f), 3f, 3f, Path.Direction.CW)
                    moveTo(8f, 14.5f)
                    lineTo(8f, 18f)
                    lineTo(11.5f, 14.5f)
                    moveTo(4f, 4f)
                    lineTo(19f, 19f)
                }

                PanelGlyph.PERSON_BLOCK -> {
                    addOval(RectF(5.5f, 4.6f, 11.9f, 11.0f), Path.Direction.CW)
                    moveTo(3f, 19.5f)
                    quadTo(7.75f, 12.5f, 12.5f, 19.5f)
                    addOval(RectF(13f, 12.5f, 21.4f, 20.9f), Path.Direction.CW)
                    moveTo(14.4f, 13.9f)
                    lineTo(20f, 19.5f)
                }
            }
        }
    }
}
