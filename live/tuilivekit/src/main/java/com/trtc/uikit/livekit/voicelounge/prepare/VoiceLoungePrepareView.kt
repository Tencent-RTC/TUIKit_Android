package com.trtc.uikit.livekit.voicelounge.prepare

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.os.Build
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.annotation.DrawableRes
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.trtc.uikit.livekit.R
import com.trtc.uikit.livekit.common.theme.BottomSheetView
import com.trtc.uikit.livekit.common.theme.LiveTheme
import com.trtc.uikit.livekit.voicelounge.MATCH
import com.trtc.uikit.livekit.voicelounge.WRAP
import com.trtc.uikit.livekit.voicelounge.dp
import com.trtc.uikit.livekit.voicelounge.frameParams
import com.trtc.uikit.livekit.voicelounge.linearParams
import com.trtc.uikit.livekit.voicelounge.ovalDrawable
import com.trtc.uikit.livekit.voicelounge.rgb
import com.trtc.uikit.livekit.voicelounge.setPressAlpha
import com.trtc.uikit.livekit.voicelounge.settings.VoiceLoungeConfig
import com.trtc.uikit.livekit.voicelounge.settings.VoiceLoungeSeatLayout
import com.trtc.uikit.livekit.voicelounge.settings.aitranscriber.VoiceLoungeAITranscriberPanel
import com.trtc.uikit.livekit.voicelounge.settings.audioeffect.VoiceLoungeAudioEffectPanel
import com.trtc.uikit.livekit.voicelounge.settings.seatlayout.VoiceLoungeSeatLayoutPanel
import com.trtc.uikit.livekit.voicelounge.settings.theme.VoiceLoungeBackground
import com.trtc.uikit.livekit.voicelounge.settings.theme.VoiceLoungeBackgroundView
import com.trtc.uikit.livekit.voicelounge.settings.theme.VoiceLoungeSkin
import com.trtc.uikit.livekit.voicelounge.settings.theme.VoiceLoungeThemeSelectPanel
import com.trtc.uikit.livekit.voicelounge.settings.topic.VoiceLoungeTopicPanel
import com.trtc.uikit.livekit.voicelounge.voiceLoungeText

internal class VoiceLoungePrepareView(context: Context) : FrameLayout(context) {
    var onExit: (() -> Unit)? = null
    var onStart: ((VoiceLoungeConfig) -> Unit)? = null

    private val accessorySlot = FrameLayout(context)
    var accessoryView: View? = null
        set(value) {
            if (field === value) return
            field?.let { (it.parent as? android.view.ViewGroup)?.removeView(it) }
            field = value
            accessorySlot.visibility = if (value == null) GONE else VISIBLE
            value?.let {
                (it.parent as? android.view.ViewGroup)?.removeView(it)
                accessorySlot.addView(it, LayoutParams(-1, -1))
            }
            requestLayout()
        }
    var title: String
        get() = infoEditorView.roomName
        set(value) {
            infoEditorView.endRoomNameEditing()
            infoEditorView.setRoomName(value)
            input = input.copy(roomName = infoEditorView.roomName)
        }

    private val backgroundView = VoiceLoungeBackgroundView(context)
    private val backgroundDimView = View(context)
    private val exitButton = FrameLayout(context)
    private val infoEditorView = VoiceLoungeInfoEditorView(context)
    private val heroView = VoiceLoungeHostHeroView(context)
    private val seatPreviewView = VoiceLoungeSeatPreviewView(context)
    private val actionsRow = LinearLayout(context)
    private val startButton = StartLiveButton(context)
    private val createdPanels = mutableListOf<BottomSheetView>()
    private val themePanel by lazy { VoiceLoungeThemeSelectPanel(context).also(createdPanels::add) }
    private val morePanel by lazy { VoiceLoungePrepareMorePanel(context).also(createdPanels::add) }
    private val layoutPanel by lazy { VoiceLoungeSeatLayoutPanel(context).also(createdPanels::add) }
    private val transcriberPanel by lazy { VoiceLoungeAITranscriberPanel(context).also(createdPanels::add) }
    private val audioEffectsPanel by lazy { VoiceLoungeAudioEffectPanel(context).also(createdPanels::add) }
    private val topicPanel by lazy { VoiceLoungeTopicPanel(context).also(createdPanels::add) }
    private val coverPickerView by lazy {
        com.trtc.uikit.livekit.pusher.prepare.PusherCoverPickerView(context).also(createdPanels::add)
    }
    private var captureVolume = 100

    private var input = VoiceLoungeConfig()
    private var hostName = ""
    private var hostAvatarURL = ""
    private var statusTop = 0
    private var navigationBottom = 0

    init {
        setupViews()
        setupBindings()
        applyInsets()
        render()
        ViewCompat.setOnApplyWindowInsetsListener(this) { _, insets ->
            statusTop = insets.getInsets(
                WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.displayCutout()
            ).top
            navigationBottom = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
            applyInsets()
            insets
        }
    }

    fun bind(input: VoiceLoungeConfig) {
        this.input = input
        applyDefaultLayout()
        render()
    }

    fun updateHost(avatarURL: String, name: String) {
        val previousDefault = VoiceLoungeConfig.defaultRoomName(hostName)
        if (input.roomName.isEmpty() || input.roomName == previousDefault) input = input.copy(roomName = "")
        hostAvatarURL = avatarURL
        hostName = name
        applyDefaultLayout()
        render()
    }

    fun deactivate() = infoEditorView.endRoomNameEditing()

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        ViewCompat.requestApplyInsets(this)
    }

    override fun onDetachedFromWindow() {
        createdPanels.forEach { if (it.isShowing) it.dismiss() }
        super.onDetachedFromWindow()
    }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        if (changedView === this) heroView.setActive(visibility == VISIBLE)
    }

    private fun setupViews() {
        addView(backgroundView, frameParams(MATCH, MATCH))
        backgroundDimView.setBackgroundColor(rgb(0.051f, 0.055f, 0.078f, 0.42f))
        addView(backgroundDimView, frameParams(MATCH, MATCH))

        val exitIcon = ImageView(context).apply {
            setImageResource(R.drawable.livekit_prepare_close)
            scaleType = ImageView.ScaleType.FIT_CENTER
        }
        exitButton.addView(exitIcon, frameParams(dp(44), dp(44), Gravity.CENTER))
        exitButton.contentDescription = context.getString(R.string.livekit_common_close)
        exitButton.setPressAlpha()
        addView(exitButton, frameParams(dp(44), dp(44), Gravity.TOP or Gravity.START, start = dp(8)))

        accessorySlot.visibility = GONE
        addView(accessorySlot, frameParams(MATCH, dp(48), Gravity.TOP, start = dp(60), end = dp(60)))

        clipChildren = false
        clipToPadding = false
        addView(seatPreviewView, frameParams(WRAP, WRAP, Gravity.TOP))
        addView(heroView, frameParams(MATCH, WRAP, Gravity.TOP))
        addView(
            infoEditorView,
            frameParams(MATCH, WRAP, Gravity.TOP, start = dp(EDITOR_INSET), end = dp(EDITOR_INSET)),
        )

        actionsRow.orientation = LinearLayout.HORIZONTAL
        actionsRow.gravity = Gravity.TOP
        actionsRow.addView(
            toolButton(
                R.string.livekit_voiceroom_prepare_action_background, R.drawable.livekit_voiceprepare_background
            ) {
                showBackground()
            },
            linearParams(WRAP, WRAP),
        )
        actionsRow.addView(
            toolButton(R.string.livekit_common_more, R.drawable.livekit_voiceprepare_more) { showMoreSettings() },
            linearParams(WRAP, WRAP, start = dp(ACTION_SPACING)),
        )
        addView(actionsRow, frameParams(WRAP, WRAP, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL))

        addView(startButton, frameParams(MATCH, dp(52), Gravity.BOTTOM, start = dp(20), end = dp(20)))
    }

    private fun applyInsets() {
        (exitButton.layoutParams as LayoutParams).topMargin = statusTop + dp(4)
        (accessorySlot.layoutParams as LayoutParams).topMargin = statusTop + dp(4)
        val bottomInset = maxOf(navigationBottom, dp(MINIMUM_BOTTOM_INSET))
        (startButton.layoutParams as LayoutParams).bottomMargin = bottomInset + dp(7)
        (actionsRow.layoutParams as LayoutParams).bottomMargin = bottomInset + dp(7 + 52 + 41)
        requestLayout()
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        layoutContent()
    }

    private fun layoutContent() {
        val editorTop = statusTop + dp(if (accessoryView == null) HEADER_TOP else ACCESSORY_HEADER_TOP)
        infoEditorView.offsetTopAndBottom(editorTop - infoEditorView.top)
        val editorBottom = infoEditorView.bottom
        val actionsTop = actionsRow.top
        val heroHeight = heroView.measuredHeight
        val heroMinTop = editorBottom + dp(HERO_MINIMUM_GAP)
        val layout = input.seatLayout

        seatPreviewView.scaleX = 1f
        seatPreviewView.scaleY = 1f
        when (layout) {
            VoiceLoungeSeatLayout.ONE_V8X -> {
                val previewTop = editorBottom + dp(SEAT_PREVIEW_MINIMUM_GAP)
                val previewRight = width - dp(EDITOR_INSET)
                val previewHeight = seatPreviewView.measuredHeight
                val available = actionsTop - dp(SEAT_PREVIEW_MINIMUM_GAP) - previewTop
                val scale = if (previewHeight > 0 && available in 1 until previewHeight) {
                    available.toFloat() / previewHeight
                } else {
                    1f
                }
                seatPreviewView.pivotX = seatPreviewView.measuredWidth.toFloat()
                seatPreviewView.pivotY = 0f
                seatPreviewView.scaleX = scale
                seatPreviewView.scaleY = scale
                placeChild(seatPreviewView, previewRight - seatPreviewView.measuredWidth, previewTop)
                val previewCenter = previewTop + previewHeight * scale / 2f
                val heroTop = maxOf(heroMinTop, (previewCenter - heroHeight / 2f).toInt())
                placeChild(heroView, 0, heroTop)
            }

            VoiceLoungeSeatLayout.DUO -> {
                placeChild(heroView, 0, maxOf(editorTop + dp(HERO_OFFSET), heroMinTop))
            }

            else -> {
                var heroTop = maxOf(editorTop + dp(HERO_OFFSET), heroMinTop)
                var gap = dp(SEAT_PREVIEW_GAP)
                val previewHeight = seatPreviewView.measuredHeight
                val limit = actionsTop - dp(SEAT_PREVIEW_MINIMUM_GAP)
                var overflow = heroTop + heroHeight + gap + previewHeight - limit
                if (overflow > 0) {
                    val gapShrink = minOf(overflow, gap - dp(SEAT_PREVIEW_MINIMUM_GAP))
                    gap -= gapShrink
                    overflow -= gapShrink
                }
                if (overflow > 0) heroTop = maxOf(heroMinTop, heroTop - overflow)
                placeChild(heroView, 0, heroTop)
                val previewLeft = (width - seatPreviewView.measuredWidth) / 2
                placeChild(seatPreviewView, previewLeft, heroTop + heroHeight + gap)
            }
        }
    }

    private fun placeChild(child: View, x: Int, y: Int) {
        child.layout(x, y, x + child.measuredWidth, y + child.measuredHeight)
    }

    private fun setupBindings() {
        exitButton.setOnClickListener {
            infoEditorView.endRoomNameEditing()
            onExit?.invoke()
        }
        startButton.setOnClickListener {
            infoEditorView.endRoomNameEditing()
            input = input.copy(roomName = infoEditorView.roomName)
            onStart?.invoke(input)
        }
        infoEditorView.onRoomNameChanged = { name -> input = input.copy(roomName = name) }
        infoEditorView.onSelectTopic = {
            infoEditorView.endRoomNameEditing()
            topicPanel.onConfirm = { topic ->
                input = input.copy(topic = topic)
                render()
            }
            topicPanel.show(input.topic, input.skin)
        }
        infoEditorView.onChangeCover = {
            infoEditorView.endRoomNameEditing()
            coverPickerView.onSelect = { url ->
                input = input.copy(coverURL = url)
                render()
            }
            coverPickerView.bind(input.coverURL)
            coverPickerView.show()
        }
    }

    private fun showBackground() {
        infoEditorView.endRoomNameEditing()
        themePanel.onSelect = { backgroundID ->
            VoiceLoungeBackground.background(backgroundID)?.let {
                input = input.copy(background = it)
                render()
            }
        }
        themePanel.onSelectSkin = { skinID ->
            VoiceLoungeSkin.skin(skinID)?.let {
                input = input.copy(skin = it)
                render()
            }
        }
        themePanel.bind(input.background.id, input.skin.id)
        themePanel.show()
    }

    private fun showMoreSettings() {
        infoEditorView.endRoomNameEditing()
        morePanel.onLayout = { presentLayoutPanel() }
        morePanel.onTranscriber = { presentTranscriberPanel() }
        morePanel.onAudio = { presentAudioPanel() }
        morePanel.show()
    }

    private fun presentLayoutPanel() {
        layoutPanel.onConfirm = { layout ->
            input = input.copy(seatLayout = layout)
            render()
        }
        layoutPanel.show(input.seatLayout, true, input.skin)
    }

    private fun presentTranscriberPanel() {
        transcriberPanel.onToggle = { isOn -> input = input.copy(isTranscriberEnabled = isOn) }
        transcriberPanel.show(input.isTranscriberEnabled, input.skin)
    }

    private fun presentAudioPanel() {
        audioEffectsPanel.onSetCaptureVolume = { volume -> captureVolume = volume }
        audioEffectsPanel.show(captureVolume, input.skin)
    }

    private fun applyDefaultLayout() {
        val saved = VoiceLoungeSeatLayout.savedDefault(context) ?: return
        input = input.copy(seatLayout = saved)
    }

    private fun render() {
        backgroundView.show(input.background)
        startButton.applySkin(input.skin)
        startButton.setTitle(context.getString(R.string.livekit_voiceroom_prepare_start_voice))
        infoEditorView.bind(input, hostName)
        heroView.bind(hostName, hostAvatarURL, input.skin)
        heroView.applyLayout(input.seatLayout)
        seatPreviewView.visibility = if (input.seatLayout == VoiceLoungeSeatLayout.DUO) GONE else VISIBLE
        seatPreviewView.applyLayout(input.seatLayout)
        requestLayout()
    }

    private fun toolButton(titleRes: Int, @DrawableRes icon: Int, action: () -> Unit): View {
        val column = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
        }
        val iconBox = FrameLayout(context)
        val iconView = ImageView(context).apply {
            setImageResource(icon)
            scaleType = ImageView.ScaleType.FIT_CENTER
            imageTintList = ColorStateList.valueOf(rgb(1f, 1f, 1f, ACTION_ICON_ALPHA))
        }
        iconBox.addView(iconView, frameParams(dp(ACTION_ICON_SIZE), dp(ACTION_ICON_SIZE), Gravity.CENTER))
        column.addView(iconBox, linearParams(dp(ACTION_ICON_BOX), dp(ACTION_ICON_BOX)))
        val label = voiceLoungeText(
            context, LiveTheme.TextRole.CONTROL, LiveTheme.TextTone.SECONDARY,
            gravity = Gravity.CENTER, emphasized = true, text = context.getString(titleRes),
        )
        label.maxLines = 1
        label.ellipsize = android.text.TextUtils.TruncateAt.END
        label.maxWidth = dp(ACTION_LABEL_MAX_WIDTH)
        column.minimumWidth = dp(ACTION_ICON_BOX)
        column.addView(label, linearParams(WRAP, WRAP, top = dp(7)))
        column.setPressAlpha()
        column.setOnClickListener { action() }
        return column
    }

    private class StartLiveButton(context: Context) : FrameLayout(context) {
        private val dotView = View(context)
        private val titleLabel = voiceLoungeText(context, LiveTheme.TextRole.DIALOG_TITLE, gravity = Gravity.CENTER)
        private val border = GradientDrawable().apply { orientation = GradientDrawable.Orientation.BL_TR }
        private val inner = GradientDrawable().apply { orientation = GradientDrawable.Orientation.BL_TR }

        init {
            background = LayerDrawable(arrayOf(border, inner)).apply {
                val inset = context.dp(1)
                setLayerInset(1, inset, inset, inset, inset)
            }
            elevation = context.dp(14).toFloat()
            dotView.background = ovalDrawable(android.graphics.Color.WHITE)
            dotView.elevation = context.dp(4).toFloat()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                dotView.outlineSpotShadowColor = android.graphics.Color.WHITE
                dotView.outlineAmbientShadowColor = android.graphics.Color.WHITE
            }
            val content = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                clipChildren = false
            }
            content.addView(dotView, linearParams(context.dp(8), context.dp(8)))
            content.addView(titleLabel, linearParams(WRAP, WRAP, start = context.dp(8)))
            addView(content, frameParams(WRAP, WRAP, Gravity.CENTER, start = context.dp(16), end = context.dp(16)))
            setPressAlpha()
            applySkin(VoiceLoungeSkin.defaultSkin)
        }

        fun setTitle(title: String) {
            titleLabel.text = title
        }

        fun applySkin(skin: VoiceLoungeSkin) {
            border.colors = intArrayOf(skin.deep, skin.mid)
            inner.colors = intArrayOf(skin.deep, skin.mid, skin.light)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                outlineSpotShadowColor = skin.deep
                outlineAmbientShadowColor = skin.deep
            }
        }

        override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
            super.onSizeChanged(w, h, oldw, oldh)
            border.cornerRadius = h / 2f
            inner.cornerRadius = maxOf(0f, (h - context.dp(2)) / 2f)
        }
    }

    private companion object {
        const val ACTION_SPACING = 30
        const val ACTION_ICON_BOX = 30
        const val ACTION_ICON_SIZE = 26
        const val ACTION_ICON_ALPHA = 0.92f
        const val ACTION_LABEL_MAX_WIDTH = 72

        const val MINIMUM_BOTTOM_INSET = 34
        const val EDITOR_INSET = 24
        const val HEADER_TOP = 49
        const val ACCESSORY_HEADER_TOP = 85
        const val HERO_OFFSET = 250
        const val HERO_MINIMUM_GAP = 36
        const val SEAT_PREVIEW_GAP = 32
        const val SEAT_PREVIEW_MINIMUM_GAP = 16
    }
}
