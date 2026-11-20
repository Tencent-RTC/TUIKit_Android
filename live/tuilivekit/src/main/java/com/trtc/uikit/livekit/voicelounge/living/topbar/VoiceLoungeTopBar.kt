package com.trtc.uikit.livekit.voicelounge.living.topbar

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.os.SystemClock
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.tencent.imsdk.v2.V2TIMFollowOperationResult
import com.tencent.imsdk.v2.V2TIMFollowTypeCheckResult
import com.tencent.imsdk.v2.V2TIMManager
import com.tencent.imsdk.v2.V2TIMValueCallback
import com.trtc.uikit.livekit.R
import com.trtc.uikit.livekit.common.log.LiveKitLog
import com.trtc.uikit.livekit.common.theme.LiveTheme
import com.trtc.uikit.livekit.player.living.audience.AudienceView
import com.trtc.uikit.livekit.voicelounge.MATCH
import com.trtc.uikit.livekit.voicelounge.VoiceLoungeBag
import com.trtc.uikit.livekit.voicelounge.VoiceLoungeMain
import com.trtc.uikit.livekit.voicelounge.WRAP
import com.trtc.uikit.livekit.voicelounge.addTo
import com.trtc.uikit.livekit.voicelounge.clearImage
import com.trtc.uikit.livekit.voicelounge.clipOval
import com.trtc.uikit.livekit.voicelounge.dp
import com.trtc.uikit.livekit.voicelounge.frameParams
import com.trtc.uikit.livekit.voicelounge.lightenColor
import com.trtc.uikit.livekit.voicelounge.linearParams
import com.trtc.uikit.livekit.voicelounge.living.VoiceLoungeLiveClock
import com.trtc.uikit.livekit.voicelounge.living.VoiceLoungeRole
import com.trtc.uikit.livekit.voicelounge.living.giftrank.VoiceLoungeGiftRankPanel
import com.trtc.uikit.livekit.voicelounge.living.giftrank.VoiceLoungeGiftRankStore
import com.trtc.uikit.livekit.voicelounge.living.status.VoiceLoungeStatusPanel
import com.trtc.uikit.livekit.voicelounge.loadUrl
import com.trtc.uikit.livekit.voicelounge.ovalDrawable
import com.trtc.uikit.livekit.voicelounge.roundedDrawable
import com.trtc.uikit.livekit.voicelounge.setPressAlpha
import com.trtc.uikit.livekit.voicelounge.settings.VoiceLoungeSettings
import com.trtc.uikit.livekit.voicelounge.settings.theme.VoiceLoungeColor
import com.trtc.uikit.livekit.voicelounge.settings.theme.VoiceLoungeSkin
import com.trtc.uikit.livekit.voicelounge.singleLineEnd
import com.trtc.uikit.livekit.voicelounge.tint
import com.trtc.uikit.livekit.voicelounge.vlString
import com.trtc.uikit.livekit.voicelounge.voiceLoungeText
import com.trtc.uikit.livekit.voicelounge.whiteAlpha
import com.trtc.uikit.livekit.voicelounge.withAlpha
import io.trtc.tuikit.atomicxcore.api.live.LikeStore
import io.trtc.tuikit.atomicxcore.api.live.LiveInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

internal class VoiceLoungeTopBar(
    context: Context,
    private val role: VoiceLoungeRole,
    private val liveID: String,
    private val settings: VoiceLoungeSettings,
    private val giftRankStore: VoiceLoungeGiftRankStore,
    private val clock: VoiceLoungeLiveClock,
) : LinearLayout(context) {
    var onClose: (() -> Unit)? = null

    private val giftRankPanel by lazy { VoiceLoungeGiftRankPanel(context, giftRankStore) }
    private val statusPanel by lazy { VoiceLoungeStatusPanel(context, liveID, clock) }
    private val bindings = VoiceLoungeBag()
    private var countScope: CoroutineScope? = null
    private var likeCount = 0

    private val hostCard = LinearLayout(context)
    private val hostAvatarView = ImageView(context)
    private val roomNameLabel = voiceLoungeText(context, LiveTheme.TextRole.ACTION)
    private val statLabel = RollingStatLabel(context)
    private val hifiBadge = TextView(context)
    private val audiencePresence = AudienceView(context)
    private val endButton = FrameLayout(context)
    private val followButton = TextView(context)
    private var isFollowed = false
    private var followCheckedUserID = ""
    private var lastFollowTapTime = 0L

    private val giftRankPill = PillButton(context, context.getString(R.string.livekit_voiceroom_room_gift_rank))
    private val plazaPill =
        PillButton(context, context.getString(R.string.livekit_voiceroom_room_plaza), showsChevron = true)
    private var skin = VoiceLoungeSkin.defaultSkin
    private val logger = LiveKitLog.getVoiceRoomLogger("VoiceLoungeTopBar")

    init {
        orientation = VERTICAL
        setupViews()
        setupStyles()
        setupBindings()
        renderRoom()
        renderCounts()
    }

    fun dismissPanels() {
        if (giftRankPanel.isShowing) giftRankPanel.dismiss()
        if (statusPanel.isShowing) statusPanel.dismiss()
    }

    private fun setupViews() {
        val row = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        addView(row, linearParams(MATCH, dp(CARD_HEIGHT)))
        val cardHolder = FrameLayout(context)
        row.addView(cardHolder, linearParams(0, MATCH, 1f, start = dp(12), end = dp(8)))
        cardHolder.addView(hostCard, frameParams(WRAP, MATCH, Gravity.START))
        row.addView(audiencePresence, linearParams(WRAP, dp(26)))
        row.addView(endButton, linearParams(dp(END_SIZE), dp(END_SIZE), start = dp(6), end = dp(12)))
        val endIcon = ImageView(context).apply {
            setImageResource(R.drawable.livekit_voiceprepare_close)
            scaleType = ImageView.ScaleType.FIT_CENTER
        }
        endButton.addView(endIcon, frameParams(dp(16), dp(16), Gravity.CENTER))

        hostCard.orientation = HORIZONTAL
        hostCard.gravity = Gravity.CENTER_VERTICAL
        hostCard.setPadding(dp(4), 0, dp(10), 0)
        hostCard.addView(hostAvatarView, linearParams(dp(AVATAR_SIZE), dp(AVATAR_SIZE)))
        val textStack = LinearLayout(context).apply { orientation = VERTICAL }
        hostCard.addView(textStack, linearParams(WRAP, WRAP, 1f, start = dp(7)))
        textStack.addView(roomNameLabel, linearParams(WRAP, WRAP))
        val line2 = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        textStack.addView(line2, linearParams(WRAP, WRAP, top = dp(2)))
        line2.addView(statLabel, linearParams(WRAP, dp(RollingStatLabel.LINE_HEIGHT), 1f))
        line2.addView(hifiBadge, linearParams(WRAP, WRAP, start = dp(6)))
        hostCard.addView(followButton, linearParams(WRAP, dp(FOLLOW_HEIGHT), start = dp(7)))

        val subBar = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        addView(subBar, linearParams(MATCH, dp(26), start = dp(14), top = dp(10), end = dp(14)))
        subBar.addView(giftRankPill, linearParams(WRAP, dp(26)))
        subBar.addView(View(context), linearParams(0, 1, 1f))
        subBar.addView(plazaPill, linearParams(WRAP, dp(26)))
    }

    private fun setupStyles() {
        hostCard.background = roundedDrawable(VoiceLoungeColor.card, dp(CARD_HEIGHT / 2).toFloat())
        endButton.background = ovalDrawable(VoiceLoungeColor.card)
        hostAvatarView.scaleType = ImageView.ScaleType.CENTER_CROP
        hostAvatarView.clipOval()
        roomNameLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
        roomNameLabel.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        roomNameLabel.singleLineEnd()
        hifiBadge.text = "HiFi"
        hifiBadge.setTextSize(TypedValue.COMPLEX_UNIT_SP, 8f)
        hifiBadge.typeface = Typeface.DEFAULT_BOLD
        hifiBadge.includeFontPadding = false
        hifiBadge.maxLines = 1
        hifiBadge.gravity = Gravity.CENTER
        hifiBadge.setPadding(dp(5), dp(1), dp(5), dp(1))
        hifiBadge.background = roundedDrawable(Color.TRANSPARENT, dp(7).toFloat(), dp(1), whiteAlpha(0.3f))
        followButton.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
        followButton.typeface = Typeface.DEFAULT_BOLD
        followButton.gravity = Gravity.CENTER
        followButton.includeFontPadding = false
        followButton.minWidth = dp(52)
        followButton.setPadding(dp(12), 0, dp(12), 0)
        followButton.visibility = if (role == VoiceLoungeRole.AUDIENCE) View.VISIBLE else View.GONE
        plazaPill.visibility = if (role == VoiceLoungeRole.HOST) View.VISIBLE else View.GONE
        endButton.setPressAlpha()
        renderFollowButton()
    }

    private fun setupBindings() {
        endButton.setOnClickListener { onClose?.invoke() }
        followButton.setOnClickListener { tapFollow() }
        giftRankPill.setOnClickListener { giftRankPanel.show() }
        plazaPill.setOnClickListener { if (role == VoiceLoungeRole.HOST) statusPanel.show() }
        settings.changes.subscribe { renderRoom() }.addTo(bindings)
        giftRankStore.changes.subscribe { renderCounts() }.addTo(bindings)
    }

    fun activate() {
        if (countScope != null) return
        audiencePresence.bind(LiveInfo().apply { liveID = this@VoiceLoungeTopBar.liveID })
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        countScope = scope
        scope.launch {
            LikeStore.create(liveID).likeState.totalLikeCount.map { it.toInt() }.distinctUntilChanged().collect {
                likeCount = it
                renderCounts()
            }
        }
    }

    fun deactivate() {
        countScope?.cancel()
        countScope = null
        likeCount = 0
        renderCounts()
    }

    fun release() {
        deactivate()
        bindings.clear()
        if (giftRankPanel.isShowing) giftRankPanel.dismiss()
        if (statusPanel.isShowing) statusPanel.dismiss()
    }

    private fun renderRoom() {
        roomNameLabel.text = settings.displayRoomName
        checkFollowIfNeeded()
        val avatarURL = settings.hostAvatarURL
        if (avatarURL.isEmpty()) {
            hostAvatarView.clearImage()
            hostAvatarView.background = ovalDrawable(VoiceLoungeColor.avatarPlaceholders[0])
        } else {
            hostAvatarView.background = null
            hostAvatarView.loadUrl(avatarURL, R.drawable.livekit_livelist_default_avatar)
        }
        skin = settings.skin
        hifiBadge.setTextColor(skin.light)
        giftRankPill.applySkin(skin.mid)
        plazaPill.applySkin(skin.mid)
    }

    private fun renderCounts() {
        statLabel.setItems(
            listOf(
                vlString(R.string.livekit_voiceroom_room_like_stat, likeCount.toString()),
                vlString(R.string.livekit_voiceroom_room_gift_stat, giftRankStore.giftCount.toString()),
            ),
        )
    }

    private fun renderFollowButton() {
        if (isFollowed) {
            followButton.text = context.getString(R.string.livekit_voiceroom_room_followed)
            followButton.background = roundedDrawable(VoiceLoungeColor.overlayMarked, dp(FOLLOW_HEIGHT / 2).toFloat())
            followButton.setTextColor(LiveTheme.Color.text(LiveTheme.TextTone.SECONDARY))
        } else {
            followButton.text = context.getString(R.string.livekit_voiceroom_room_follow)
            followButton.background = roundedDrawable(VoiceLoungeColor.rose, dp(FOLLOW_HEIGHT / 2).toFloat())
            followButton.setTextColor(Color.WHITE)
        }
    }

    private fun finishFollow(isFollowed: Boolean, hostUserID: String) {
        followButton.isEnabled = true
        if (hostUserID != settings.hostUserID) return
        this.isFollowed = isFollowed
        renderFollowButton()
    }

    private fun checkFollowIfNeeded() {
        val hostUserID = settings.hostUserID
        if (role != VoiceLoungeRole.AUDIENCE || hostUserID.isEmpty() || hostUserID == followCheckedUserID) return
        followCheckedUserID = hostUserID
        isFollowed = false
        renderFollowButton()
        V2TIMManager.getFriendshipManager().checkFollowType(
            listOf(hostUserID),
            object : V2TIMValueCallback<List<V2TIMFollowTypeCheckResult>> {
                override fun onSuccess(results: List<V2TIMFollowTypeCheckResult>?) {
                    VoiceLoungeMain.post {
                        if (hostUserID != settings.hostUserID) return@post
                        val type = results?.firstOrNull()?.followType ?: return@post
                        isFollowed =
                            type == V2TIMFollowTypeCheckResult.V2TIM_FOLLOW_TYPE_IN_MY_FOLLOWING_LIST || type == V2TIMFollowTypeCheckResult.V2TIM_FOLLOW_TYPE_IN_BOTH_FOLLOWERS_LIST
                        renderFollowButton()
                    }
                }

                override fun onError(code: Int, desc: String?) {
                    logger.error("checkFollowType failed code=$code message=${desc.orEmpty()}")
                }
            },
        )
    }

    private fun tapFollow() {
        val hostUserID = settings.hostUserID
        val now = SystemClock.elapsedRealtime()
        if (hostUserID.isEmpty() || now - lastFollowTapTime < FOLLOW_DEBOUNCE_MS) return
        lastFollowTapTime = now
        followButton.isEnabled = false
        val target = !isFollowed
        val callback = object : V2TIMValueCallback<List<V2TIMFollowOperationResult>> {
            override fun onSuccess(results: List<V2TIMFollowOperationResult>?) {
                VoiceLoungeMain.post { finishFollow(target, hostUserID) }
            }

            override fun onError(code: Int, desc: String?) {
                logger.error("follow failed target=$target code=$code message=${desc.orEmpty()}")
                VoiceLoungeMain.post { followButton.isEnabled = true }
            }
        }
        val manager = V2TIMManager.getFriendshipManager()
        if (target) manager.followUser(listOf(hostUserID), callback) else manager.unfollowUser(
            listOf(hostUserID), callback
        )
    }

    private class PillButton(context: Context, title: String, showsChevron: Boolean = false) : LinearLayout(context) {
        private val titleLabel = voiceLoungeText(context, LiveTheme.TextRole.CONTROL, emphasized = true, text = title)
        private val chevron = ImageView(context)

        init {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(4), dp(12), dp(4))
            addView(titleLabel, linearParams(WRAP, WRAP))
            chevron.setImageResource(R.drawable.livekit_living_status_chevron)
            chevron.scaleType = ImageView.ScaleType.FIT_CENTER
            chevron.visibility = if (showsChevron) View.VISIBLE else View.GONE
            addView(chevron, linearParams(dp(5), dp(9), start = dp(2)))
            setPressAlpha()
        }

        fun applySkin(color: Int) {
            background = roundedDrawable(color.withAlpha(0.26f), dp(13).toFloat())
            val text = lightenColor(color, 0.3f, 0.35f)
            titleLabel.setTextColor(text)
            chevron.tint(text)
        }
    }

    private class RollingStatLabel(context: Context) : FrameLayout(context) {
        private val frontLabel = voiceLoungeText(context, LiveTheme.TextRole.METADATA, LiveTheme.TextTone.SECONDARY)
        private val backLabel = voiceLoungeText(context, LiveTheme.TextRole.METADATA, LiveTheme.TextTone.SECONDARY)
        private var items: List<String> = emptyList()
        private var currentIndex = 0
        private val ticker = object : Runnable {
            override fun run() {
                advance()
                postDelayed(this, INTERVAL_MS)
            }
        }

        init {
            clipChildren = true
            clipToPadding = true
            addView(frontLabel, frameParams(MATCH, MATCH))
            addView(backLabel, frameParams(MATCH, MATCH))
            backLabel.alpha = 0f
            frontLabel.maxLines = 1
            backLabel.maxLines = 1
        }

        fun setItems(items: List<String>) {
            if (items == this.items) return
            val countChanged = items.size != this.items.size
            this.items = items
            if (currentIndex >= items.size) currentIndex = 0
            frontLabel.text = items.getOrNull(currentIndex)
            requestLayout()
            if (countChanged) updateTimer()
        }

        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            val textWidth = items.maxOfOrNull { frontLabel.paint.measureText(it) } ?: 0f
            val desired = kotlin.math.ceil(textWidth).toInt()
            val specSize = MeasureSpec.getSize(widthMeasureSpec)
            val width = when (MeasureSpec.getMode(widthMeasureSpec)) {
                MeasureSpec.EXACTLY -> specSize
                MeasureSpec.AT_MOST -> minOf(desired, specSize)
                else -> desired
            }
            super.onMeasure(
                MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(dp(LINE_HEIGHT), MeasureSpec.EXACTLY),
            )
        }

        override fun onAttachedToWindow() {
            super.onAttachedToWindow()
            updateTimer()
        }

        override fun onDetachedFromWindow() {
            removeCallbacks(ticker)
            super.onDetachedFromWindow()
        }

        private fun updateTimer() {
            removeCallbacks(ticker)
            if (!isAttachedToWindow || items.size <= 1) return
            postDelayed(ticker, INTERVAL_MS)
        }

        private fun advance() {
            if (items.size <= 1) return
            currentIndex = (currentIndex + 1) % items.size
            backLabel.text = items[currentIndex]
            val h = height.toFloat()
            backLabel.translationY = h
            backLabel.alpha = 1f
            frontLabel.animate().translationY(-h).alpha(0f).setDuration(ANIMATION_MS).start()
            backLabel.animate().translationY(0f).setDuration(ANIMATION_MS).withEndAction {
                frontLabel.text = items.getOrNull(currentIndex)
                frontLabel.translationY = 0f
                frontLabel.alpha = 1f
                backLabel.alpha = 0f
                backLabel.translationY = h
            }.start()
        }

        companion object {
            const val LINE_HEIGHT = 17
            private const val INTERVAL_MS = 3_000L
            private const val ANIMATION_MS = 350L
        }
    }

    private companion object {
        const val CARD_HEIGHT = 46
        const val AVATAR_SIZE = 38
        const val END_SIZE = 36
        const val FOLLOW_HEIGHT = 28
        const val FOLLOW_DEBOUNCE_MS = 1_000L
    }
}
