package com.trtc.uikit.livekit.pusher.settings

import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import androidx.core.view.updateLayoutParams
import com.trtc.uikit.livekit.R
import com.trtc.uikit.livekit.common.theme.LiveText
import com.trtc.uikit.livekit.common.theme.LiveTheme

/** Settings action item shared by the Prepare action row and settings panels. */
class PusherSettingsItemView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr) {
    enum class Style {
        PREPARE,
        PANEL,
        SOUND_OPTION,
    }

    private val iconBackground: FrameLayout
    private val iconView: ImageView
    private val titleView: LiveText
    private var style = Style.PANEL
    private val soundPlateRadius = resources.getDimension(R.dimen.livekit_pusher_sound_option_plate_radius)
    private val soundPlateSize = resources.getDimension(R.dimen.livekit_pusher_sound_option_plate_size)
    private val soundBorderWidth = resources.getDimension(R.dimen.livekit_pusher_sound_option_plate_border_width)
    private val soundSelectedBorderWidth =
        resources.getDimension(R.dimen.livekit_pusher_sound_option_plate_selected_border_width)
    private val soundPlatePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val soundBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val soundPlateBounds = RectF()
    private val soundBorderBounds = RectF()
    private var soundBorderRadius = soundPlateRadius
    private var soundSelectedFill: Shader? = null
    private var soundSelectedBorder: Shader? = null

    init {
        LayoutInflater.from(context).inflate(R.layout.livekit_pusher_settings_item_view, this, true)
        iconBackground = findViewById(R.id.livekit_pusher_settings_item_icon_background)
        iconView = findViewById(R.id.livekit_pusher_settings_item_icon)
        titleView = findViewById(R.id.livekit_pusher_settings_item_title)
        isClickable = true
        isFocusable = true
        applySelectionState()
    }

    fun configure(
        @StringRes titleRes: Int,
        @DrawableRes iconRes: Int,
        style: Style = Style.PANEL,
        iconWidthDp: Float = 22f,
        iconHeightDp: Float = 22f,
        showsIconBackground: Boolean = true,
    ) {
        this.style = style
        val isPrepare = style == Style.PREPARE
        val isSoundOption = style == Style.SOUND_OPTION
        iconBackground.updateLayoutParams<LayoutParams> {
            val size = resources.getDimensionPixelSize(
                if (isPrepare || isSoundOption) {
                    R.dimen.livekit_pusher_prepare_action_plate_size
                } else {
                    R.dimen.livekit_pusher_settings_item_plate_size
                },
            )
            width = size
            height = size
            topMargin = if (isPrepare || isSoundOption) 0 else resources.getDimensionPixelSize(
                R.dimen.livekit_pusher_settings_item_plate_top,
            )
        }
        iconBackground.setBackgroundResource(
            if (!showsIconBackground) {
                0
            } else if (isPrepare) {
                R.drawable.livekit_pusher_prepare_action_plate
            } else if (isSoundOption) {
                0
            } else {
                R.drawable.livekit_pusher_settings_item_plate
            },
        )
        setIcon(iconRes)
        setIconSize(
            if (isPrepare) 24f else if (isSoundOption) 25f else iconWidthDp,
            if (isPrepare) 24f else if (isSoundOption) 25f else iconHeightDp,
        )
        titleView.maxLines = if (style == Style.PANEL) 2 else 1
        titleView.updateLayoutParams<LayoutParams> {
            height = resources.getDimensionPixelSize(
                if (isPrepare) {
                    R.dimen.livekit_pusher_prepare_action_title_height
                } else if (isSoundOption) {
                    R.dimen.livekit_pusher_sound_option_label_height
                } else {
                    R.dimen.livekit_pusher_settings_item_title_height
                },
            )
            topMargin = resources.getDimensionPixelSize(
                if (isPrepare) {
                    R.dimen.livekit_pusher_prepare_action_plate_size
                } else if (isSoundOption) {
                    R.dimen.livekit_pusher_sound_option_label_top
                } else {
                    R.dimen.livekit_pusher_settings_item_title_top
                },
            ) + if (isPrepare) {
                resources.getDimensionPixelSize(R.dimen.livekit_pusher_prepare_action_title_top)
            } else {
                0
            }
        }
        setWillNotDraw(!isSoundOption)
        titleView.tone = if (isPrepare) LiveTheme.TextTone.PRIMARY else LiveTheme.TextTone.SECONDARY
        titleView.isEmphasized = isPrepare
        titleView.setText(titleRes)
        contentDescription = context.getString(titleRes)
        applySelectionState()
    }

    fun setIcon(@DrawableRes iconRes: Int) {
        iconView.setImageResource(iconRes)
    }

    fun setIconSize(widthDp: Float, heightDp: Float) {
        iconView.updateLayoutParams {
            width = (widthDp * resources.displayMetrics.density).toInt()
            height = (heightDp * resources.displayMetrics.density).toInt()
        }
    }

    fun setTitle(@StringRes titleRes: Int) = setTitle(context.getString(titleRes))

    fun setTitle(title: String) {
        titleView.text = title
        contentDescription = title
    }

    override fun setSelected(selected: Boolean) {
        if (isSelected == selected) return
        super.setSelected(selected)
        applySelectionState()
        invalidate()
    }

    override fun onSizeChanged(width: Int, height: Int, oldWidth: Int, oldHeight: Int) {
        super.onSizeChanged(width, height, oldWidth, oldHeight)
        if (style != Style.SOUND_OPTION) return
        val left = (width - soundPlateSize) / 2f
        soundPlateBounds.set(left, 0f, left + soundPlateSize, soundPlateSize)
        soundSelectedFill = LinearGradient(
            soundPlateBounds.left,
            soundPlateBounds.top,
            soundPlateBounds.left,
            soundPlateBounds.bottom,
            color(R.color.livekit_pusher_video_quality_row_selected_fill_start),
            color(R.color.livekit_pusher_video_quality_row_selected_fill_end),
            Shader.TileMode.CLAMP,
        )
        soundSelectedBorder = LinearGradient(
            soundPlateBounds.left,
            soundPlateBounds.bottom,
            soundPlateBounds.right,
            soundPlateBounds.top,
            intArrayOf(
                color(R.color.livekit_pusher_video_quality_row_selected_border_start),
                color(R.color.livekit_pusher_video_quality_row_selected_border_middle),
                color(R.color.livekit_pusher_video_quality_row_selected_border_end),
            ),
            floatArrayOf(0f, 0.55f, 1f),
            Shader.TileMode.CLAMP,
        )
        updateSoundBorderBounds()
    }

    override fun onDraw(canvas: Canvas) {
        if (style == Style.SOUND_OPTION && !soundPlateBounds.isEmpty) {
            soundPlatePaint.shader = if (isSelected) soundSelectedFill else null
            soundPlatePaint.color = if (isSelected) -1 else color(R.color.livekit_pusher_audio_card_fill)
            canvas.drawRoundRect(soundPlateBounds, soundPlateRadius, soundPlateRadius, soundPlatePaint)
            soundPlatePaint.shader = null

            soundBorderPaint.shader = if (isSelected) soundSelectedBorder else null
            soundBorderPaint.color = if (isSelected) -1 else color(R.color.livekit_pusher_audio_card_border)
            canvas.drawRoundRect(
                soundBorderBounds,
                soundBorderRadius,
                soundBorderRadius,
                soundBorderPaint,
            )
            soundBorderPaint.shader = null
        }
        super.onDraw(canvas)
    }

    private fun applySelectionState() {
        if (style != Style.SOUND_OPTION) return
        titleView.tone = if (isSelected) LiveTheme.TextTone.PRIMARY else LiveTheme.TextTone.SECONDARY
        titleView.isEmphasized = isSelected
        soundBorderPaint.strokeWidth = if (isSelected) soundSelectedBorderWidth else soundBorderWidth
        updateSoundBorderBounds()
    }

    private fun updateSoundBorderBounds() {
        if (soundPlateBounds.isEmpty) return
        val inset = soundBorderPaint.strokeWidth / 2f
        soundBorderBounds.set(soundPlateBounds)
        soundBorderBounds.inset(inset, inset)
        soundBorderRadius = (soundPlateRadius - inset).coerceAtLeast(0f)
    }

    private fun color(colorRes: Int): Int = ContextCompat.getColor(context, colorRes)
}
