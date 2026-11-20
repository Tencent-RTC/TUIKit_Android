package com.trtc.uikit.livekit.voicelounge.settings.seatlayout

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import com.trtc.uikit.livekit.R
import com.trtc.uikit.livekit.common.theme.BottomSheetView
import com.trtc.uikit.livekit.common.theme.LiveTheme
import com.trtc.uikit.livekit.voicelounge.MATCH
import com.trtc.uikit.livekit.voicelounge.WRAP
import com.trtc.uikit.livekit.voicelounge.dp
import com.trtc.uikit.livekit.voicelounge.frameParams
import com.trtc.uikit.livekit.voicelounge.linearParams
import com.trtc.uikit.livekit.voicelounge.onClick
import com.trtc.uikit.livekit.voicelounge.roundedDrawable
import com.trtc.uikit.livekit.voicelounge.settings.VoiceLoungeSeatLayout
import com.trtc.uikit.livekit.voicelounge.settings.theme.VoiceLoungeColor
import com.trtc.uikit.livekit.voicelounge.settings.theme.VoiceLoungeSkin
import com.trtc.uikit.livekit.voicelounge.voiceLoungeText
import com.trtc.uikit.livekit.voicelounge.whiteAlpha

internal class VoiceLoungeSeatLayoutPanel(context: Context) : BottomSheetView(context) {
    var onConfirm: ((VoiceLoungeSeatLayout) -> Unit)? = null

    private val cardRow = LinearLayout(context)
    private val defaultCheckbox = CheckboxRow(context)

    private var layouts: List<VoiceLoungeSeatLayout> = emptyList()
    private val cards = LinkedHashMap<VoiceLoungeSeatLayout, LayoutCard>()
    private var selected = VoiceLoungeSeatLayout.ONE_V8
    private var skin = VoiceLoungeSkin.defaultSkin

    override val sheetHeight: Int get() = context.dp(SHEET_HEIGHT)

    init {
        setupViews()
        setupStyles()
        defaultCheckbox.onChange = { saveDefault() }
    }

    fun show(selected: VoiceLoungeSeatLayout, setAsDefault: Boolean, skin: VoiceLoungeSkin) {
        layouts = VoiceLoungeSeatLayout.values().toList()
        this.selected = if (selected in layouts) selected else layouts.firstOrNull() ?: VoiceLoungeSeatLayout.ONE_V8
        this.skin = skin
        defaultCheckbox.isOn = setAsDefault
        defaultCheckbox.apply(skin)
        rebuildCards()
        syncSelection()
        show()
    }

    private fun setupViews() {
        contentView.addView(
            cardRow,
            frameParams(MATCH, context.dp(128), start = context.dp(16), end = context.dp(16), top = context.dp(64)),
        )
        contentView.addView(
            defaultCheckbox,
            frameParams(
                WRAP, context.dp(20), Gravity.TOP or Gravity.CENTER_HORIZONTAL, top = context.dp(64 + 128 + 14)
            ),
        )
    }

    private fun setupStyles() {
        sheetTitle = context.getString(R.string.livekit_voiceroom_layout_title)
        cardRow.orientation = LinearLayout.HORIZONTAL
        defaultCheckbox.setTitle(context.getString(R.string.livekit_voiceroom_layout_default_hint))
    }

    private fun rebuildCards() {
        cardRow.removeAllViews()
        cards.clear()
        layouts.forEachIndexed { index, layout ->
            val card = LayoutCard(context, layout)
            card.onTap = { select(it) }
            cards[layout] = card
            cardRow.addView(card, linearParams(0, MATCH, weight = 1f, start = if (index == 0) 0 else context.dp(10)))
        }
    }

    private fun select(layout: VoiceLoungeSeatLayout) {
        if (selected == layout) return
        selected = layout
        syncSelection()
        saveDefault()
        onConfirm?.invoke(layout)
    }

    private fun saveDefault() = VoiceLoungeSeatLayout.saveDefault(context, selected.takeIf { defaultCheckbox.isOn })

    private fun syncSelection() {
        cards.forEach { (layout, card) -> card.apply(layout == selected, skin) }
    }

    private companion object {
        const val SHEET_HEIGHT = 270
    }
}

private class LayoutCard(context: Context, private val layout: VoiceLoungeSeatLayout) : FrameLayout(context) {
    var onTap: ((VoiceLoungeSeatLayout) -> Unit)? = null

    private val previewView = LayoutPreviewView(context)
    private val titleLabel = voiceLoungeText(
        context, LiveTheme.TextRole.CAPTION, LiveTheme.TextTone.SECONDARY, Gravity.CENTER, emphasized = true,
    )

    init {
        background = roundedDrawable(VoiceLoungeColor.card, context.dp(12).toFloat())
        previewView.layout = layout
        addView(
            previewView,
            frameParams(context.dp(72), context.dp(72), Gravity.TOP or Gravity.CENTER_HORIZONTAL, top = context.dp(14)),
        )
        titleLabel.text = layout.displayName
        addView(
            titleLabel,
            frameParams(
                MATCH, WRAP, Gravity.TOP, start = context.dp(4), end = context.dp(4), top = context.dp(14 + 72 + 8)
            ),
        )
        onClick { onTap?.invoke(layout) }
    }

    fun apply(isSelected: Boolean, skin: VoiceLoungeSkin) {
        background = roundedDrawable(
            VoiceLoungeColor.card, context.dp(12).toFloat(), context.dp(1.5f),
            if (isSelected) skin.mid else Color.TRANSPARENT,
        )
        titleLabel.tone = if (isSelected) LiveTheme.TextTone.PRIMARY else LiveTheme.TextTone.SECONDARY
        previewView.accentColor = skin.mid
        previewView.isSelectedState = isSelected
        previewView.invalidate()
    }
}

private class LayoutPreviewView(context: Context) : View(context) {
    var layout = VoiceLoungeSeatLayout.ONE_V8
    var accentColor = VoiceLoungeSkin.defaultSkin.mid
    var isSelectedState = false

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val path = Path()

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val density = resources.displayMetrics.density
        canvas.save()
        canvas.scale(density, density)
        val w = width / density
        val h = height / density
        val hostColor = if (isSelectedState) accentColor else whiteAlpha(0.7f)
        val guestColor = whiteAlpha(0.55f)
        val r = 4f

        fun dot(x: Float, y: Float, color: Int) {
            fillPaint.color = color
            canvas.drawOval(x - r / 2, y - r / 2, x + r / 2, y + r / 2, fillPaint)
        }

        fun guestGrid() {
            for (row in 0 until 2) {
                for (col in 0 until 4) dot(w / 2 - 18 + col * 12, 28f + row * 12, guestColor)
            }
        }

        when (layout) {
            VoiceLoungeSeatLayout.ONE_V8 -> {
                dot(w / 2, 10f, hostColor)
                guestGrid()
            }

            VoiceLoungeSeatLayout.ONE_V8X -> {
                dot(w * 0.25f, h / 2, hostColor)
                for (i in 0 until 8) dot(w * 0.7f, 8 + i * (h - 16) / 7, guestColor)
            }

            VoiceLoungeSeatLayout.TWO_V8 -> {
                dot(w / 2 - 6, 10f, hostColor)
                dot(w / 2 + 6, 10f, hostColor)
                guestGrid()
            }

            VoiceLoungeSeatLayout.DUO -> {
                dot(w * 0.22f, h / 2, hostColor)
                dot(w * 0.78f, h / 2, guestColor)
                strokePaint.color = hostColor
                strokePaint.strokeWidth = 1.4f
                val mid = h / 2
                path.reset()
                path.moveTo(w * 0.3f, mid)
                path.lineTo(w * 0.4f, mid)
                path.lineTo(w * 0.45f, mid - 6)
                path.lineTo(w * 0.5f, mid + 6)
                path.lineTo(w * 0.55f, mid - 4)
                path.lineTo(w * 0.6f, mid)
                path.lineTo(w * 0.72f, mid)
                canvas.drawPath(path, strokePaint)
            }
        }
        canvas.restore()
    }
}

private class CheckboxRow(context: Context) : LinearLayout(context) {
    var onChange: ((Boolean) -> Unit)? = null
    var isOn = true
        set(value) {
            field = value
            applyColors()
        }

    private val box = CheckBoxView(context)
    private val titleLabel = voiceLoungeText(context, LiveTheme.TextRole.CAPTION, LiveTheme.TextTone.TERTIARY)
    private var skinMid = VoiceLoungeSkin.defaultSkin.mid

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        addView(box, linearParams(context.dp(16), context.dp(16)))
        addView(titleLabel, linearParams(WRAP, WRAP, start = context.dp(8)))
        onClick {
            isOn = !isOn
            onChange?.invoke(isOn)
        }
        applyColors()
    }

    fun setTitle(text: String) {
        titleLabel.text = text
    }

    fun apply(skin: VoiceLoungeSkin) {
        skinMid = skin.mid
        applyColors()
    }

    private fun applyColors() {
        box.boxColor = if (isOn) skinMid else VoiceLoungeColor.overlaySoft
        box.showsCheck = isOn
        box.invalidate()
    }
}

private class CheckBoxView(context: Context) : View(context) {
    var boxColor = VoiceLoungeColor.overlaySoft
    var showsCheck = true

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val check = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.WHITE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val path = Path()

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val d = resources.displayMetrics.density
        fill.color = boxColor
        canvas.drawRoundRect(0f, 0f, width.toFloat(), height.toFloat(), 8 * d, 8 * d, fill)
        if (!showsCheck) return
        check.strokeWidth = 1.5f * d
        path.reset()
        path.moveTo(4 * d, 8 * d)
        path.lineTo(7 * d, 11 * d)
        path.lineTo(12 * d, 5 * d)
        canvas.drawPath(path, check)
    }
}
