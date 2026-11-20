package com.trtc.uikit.livekit.voicelounge.settings.theme

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import com.trtc.uikit.livekit.R
import com.trtc.uikit.livekit.common.theme.BottomSheetView
import com.trtc.uikit.livekit.common.theme.LiveText
import com.trtc.uikit.livekit.common.theme.LiveTheme
import com.trtc.uikit.livekit.voicelounge.MATCH
import com.trtc.uikit.livekit.voicelounge.WRAP
import com.trtc.uikit.livekit.voicelounge.clipRounded
import com.trtc.uikit.livekit.voicelounge.dp
import com.trtc.uikit.livekit.voicelounge.frameParams
import com.trtc.uikit.livekit.voicelounge.linearParams
import com.trtc.uikit.livekit.voicelounge.onClick
import com.trtc.uikit.livekit.voicelounge.roundedDrawable
import com.trtc.uikit.livekit.voicelounge.singleLineEnd
import com.trtc.uikit.livekit.voicelounge.voiceLoungeText

internal class VoiceLoungeThemeSelectPanel(context: Context) : BottomSheetView(context) {
    var onSelect: ((String) -> Unit)? = null
    var onSelectSkin: ((String) -> Unit)? = null

    private val skinSectionLabel = voiceLoungeText(context, LiveTheme.TextRole.METADATA, LiveTheme.TextTone.SECONDARY)
    private val skinRow = LinearLayout(context)
    private val bgSectionLabel = voiceLoungeText(context, LiveTheme.TextRole.METADATA, LiveTheme.TextTone.SECONDARY)
    private val scrollView = HorizontalScrollView(context)
    private val cardRow = LinearLayout(context)

    private val skins = VoiceLoungeSkin.all
    private val backgrounds = VoiceLoungeBackground.all
    private val skinSwatches = mutableListOf<SkinSwatch>()
    private val backgroundCells = mutableListOf<BackgroundCell>()

    private var selectedID = VoiceLoungeBackground.DEFAULT_ID
    private var selectedSkinID = VoiceLoungeSkin.DEFAULT_ID

    override val sheetHeight: Int get() = context.dp(ThemePanelStyle.SHEET_HEIGHT)

    init {
        setupViews()
        setupStyles()
    }

    fun bind(selectedID: String, skinID: String) {
        this.selectedID = selectedID
        selectedSkinID = skinID
        reloadBackgrounds()
        syncSkinSelection()
    }

    private fun setupViews() {
        val inset = context.dp(ThemePanelStyle.SECTION_INSET)
        contentView.addView(
            skinSectionLabel,
            frameParams(MATCH, WRAP, start = inset, end = inset, top = context.dp(ThemePanelStyle.SKIN_LABEL_TOP)),
        )
        skins.forEach { skin ->
            val swatch = SkinSwatch(context, skin)
            swatch.onTap = { id -> selectSkin(id) }
            skinRow.addView(swatch, linearParams(0, MATCH, weight = 1f))
            skinSwatches.add(swatch)
        }
        contentView.addView(
            skinRow,
            frameParams(
                MATCH, context.dp(ThemePanelStyle.SKIN_ROW_HEIGHT), start = inset, end = inset,
                top = context.dp(ThemePanelStyle.SKIN_LABEL_TOP + 14f + 8f),
            ),
        )
        val bgTop = ThemePanelStyle.SKIN_LABEL_TOP + 14f + 8f + ThemePanelStyle.SKIN_ROW_HEIGHT + 16f
        contentView.addView(
            bgSectionLabel,
            frameParams(MATCH, WRAP, start = inset, end = inset, top = context.dp(bgTop)),
        )
        backgrounds.forEachIndexed { index, background ->
            val cell = BackgroundCell(context)
            cell.onClick { selectBackground(background.id) }
            cardRow.addView(
                cell,
                linearParams(
                    context.dp(ThemePanelStyle.CARD_WIDTH), MATCH,
                    start = if (index == 0) inset else context.dp(ThemePanelStyle.CARD_SPACING),
                    end = if (index == backgrounds.lastIndex) inset else 0,
                ),
            )
            backgroundCells.add(cell)
        }
        scrollView.addView(cardRow, FrameLayout.LayoutParams(WRAP, MATCH))
        contentView.addView(
            scrollView,
            frameParams(MATCH, context.dp(ThemePanelStyle.CARD_HEIGHT), top = context.dp(bgTop + 14f + 10f)),
        )
    }

    private fun setupStyles() {
        sheetTitle = context.getString(R.string.livekit_voiceroom_prepare_theme_title)
        skinSectionLabel.text = context.getString(R.string.livekit_voiceroom_prepare_section_skin)
        skinRow.orientation = LinearLayout.HORIZONTAL
        bgSectionLabel.text = context.getString(R.string.livekit_voiceroom_prepare_section_background)
        scrollView.isHorizontalScrollBarEnabled = false
        scrollView.overScrollMode = View.OVER_SCROLL_NEVER
        cardRow.orientation = LinearLayout.HORIZONTAL
        reloadBackgrounds()
        syncSkinSelection()
    }

    private fun selectSkin(id: String) {
        if (id == selectedSkinID) return
        selectedSkinID = id
        syncSkinSelection()
        onSelectSkin?.invoke(id)
    }

    private fun selectBackground(id: String) {
        if (id == selectedID) return
        selectedID = id
        reloadBackgrounds()
        onSelect?.invoke(id)
    }

    private fun reloadBackgrounds() {
        backgrounds.forEachIndexed { index, background ->
            backgroundCells.getOrNull(index)?.configure(background, background.id == selectedID)
        }
    }

    private fun syncSkinSelection() {
        skinSwatches.forEach { it.setSelectedState(it.skinID == selectedSkinID) }
    }
}

private class SkinSwatch(context: Context, skin: VoiceLoungeSkin) : FrameLayout(context) {
    val skinID: String = skin.id
    var onTap: ((String) -> Unit)? = null

    private val selectionBox = View(context)
    private val dotView = View(context)
    private val nameLabel: LiveText = voiceLoungeText(
        context, LiveTheme.TextRole.METADATA, LiveTheme.TextTone.SECONDARY, Gravity.CENTER,
    )

    init {
        selectionBox.background = roundedDrawable(
            Color.TRANSPARENT, context.dp(ThemePanelStyle.SELECTION_BOX_CORNER).toFloat(), context.dp(2),
            ThemePanelStyle.selectionBorder,
        )
        selectionBox.visibility = View.GONE
        val boxSize = context.dp(ThemePanelStyle.SELECTION_BOX_SIZE)
        addView(selectionBox, frameParams(boxSize, boxSize, Gravity.TOP or Gravity.CENTER_HORIZONTAL))

        dotView.background = GradientDrawable(GradientDrawable.Orientation.TL_BR, skin.swatchColors).apply {
            shape = GradientDrawable.OVAL
        }
        val dotSize = context.dp(ThemePanelStyle.DOT_SIZE)
        val dotOffset = (boxSize - dotSize) / 2
        addView(dotView, frameParams(dotSize, dotSize, Gravity.TOP or Gravity.CENTER_HORIZONTAL, top = dotOffset))

        nameLabel.text = skin.displayName
        nameLabel.singleLineEnd()
        addView(nameLabel, frameParams(MATCH, WRAP, Gravity.TOP, top = boxSize + context.dp(6)))

        onClick { onTap?.invoke(skinID) }
    }

    fun setSelectedState(selected: Boolean) {
        isSelected = selected
        selectionBox.visibility = if (selected) View.VISIBLE else View.GONE
        nameLabel.tone = if (selected) LiveTheme.TextTone.PRIMARY else LiveTheme.TextTone.SECONDARY
    }
}

private class BackgroundCell(context: Context) : FrameLayout(context) {
    private val previewView = ImageView(context)
    private val nameLabel: LiveText = voiceLoungeText(
        context, LiveTheme.TextRole.METADATA, LiveTheme.TextTone.SECONDARY, Gravity.CENTER,
    )
    private val selectionOverlay = View(context)

    init {
        val radius = context.dp(12).toFloat()
        val previewHeight = context.dp(ThemePanelStyle.PREVIEW_HEIGHT)
        previewView.scaleType = ImageView.ScaleType.CENTER_CROP
        previewView.clipRounded(radius)
        addView(previewView, frameParams(MATCH, previewHeight))

        selectionOverlay.background = roundedDrawable(
            Color.TRANSPARENT, radius, context.dp(2), ThemePanelStyle.selectionBorder,
        )
        selectionOverlay.visibility = View.GONE
        addView(selectionOverlay, frameParams(MATCH, previewHeight))

        nameLabel.singleLineEnd()
        addView(nameLabel, frameParams(MATCH, WRAP, top = previewHeight + context.dp(6)))
    }

    fun configure(background: VoiceLoungeBackground, isSelected: Boolean) {
        previewView.setImageResource(background.previewRes)
        nameLabel.text = background.displayName
        nameLabel.tone = if (isSelected) LiveTheme.TextTone.PRIMARY else LiveTheme.TextTone.SECONDARY
        selectionOverlay.visibility = if (isSelected) View.VISIBLE else View.GONE
    }
}

private object ThemePanelStyle {
    const val SECTION_INSET = 16f
    const val SKIN_LABEL_TOP = 66f
    const val DOT_SIZE = 36f
    const val SELECTION_BOX_SIZE = 48f
    const val SELECTION_BOX_CORNER = 14f
    const val SKIN_ROW_HEIGHT = 48f + 6f + 15f

    const val CARD_WIDTH = 81f
    const val PREVIEW_HEIGHT = CARD_WIDTH * 16f / 9f
    const val CARD_HEIGHT = PREVIEW_HEIGHT + 6f + 15f
    const val CARD_SPACING = 10f

    val selectionBorder = VoiceLoungeColor.prepareMid

    const val SHEET_HEIGHT = SKIN_LABEL_TOP + 14f + 8f + SKIN_ROW_HEIGHT + 16f + 14f + 10f + CARD_HEIGHT + 28f
}
