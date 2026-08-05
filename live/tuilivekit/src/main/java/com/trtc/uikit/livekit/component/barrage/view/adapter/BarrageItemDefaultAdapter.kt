package com.trtc.uikit.livekit.component.barrage.view.adapter

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.method.LinkMovementMethod
import android.text.style.ForegroundColorSpan
import android.text.style.ReplacementSpan
import android.util.Log
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.trtc.uikit.livekit.R
import com.trtc.uikit.livekit.common.ENABLE_LIVEKIT_BARRAGE_USER_LEVEL
import com.trtc.uikit.livekit.component.barrage.store.model.DefaultEmojiResource
import com.trtc.uikit.livekit.component.barrage.view.AnchorTagSpan
import com.trtc.uikit.livekit.component.barrage.view.EmojiSpan
import io.trtc.tuikit.atomicx.theme.ThemeStore
import io.trtc.tuikit.atomicxcore.api.barrage.Barrage
import kotlin.math.ceil

class BarrageItemDefaultAdapter(
    private val context: Context,
    private val ownerId: String
) : BarrageItemAdapter {

    private val mLayoutInflater = LayoutInflater.from(context)
    private val mEmojiResource = DefaultEmojiResource()
    private val isRtl = context.resources.configuration.layoutDirection == View.LAYOUT_DIRECTION_RTL
    private val userNameColor = ContextCompat.getColor(context, R.color.livekit_barrage_user_name_color)
    private val contentColor = ContextCompat.getColor(context, R.color.livekit_barrage_g8)

    private val anchorTagText = context.getString(R.string.live_barrage_anchor)
    private val anchorTagBackground = ContextCompat.getDrawable(context, R.drawable.livekit_barrage_bg_anchor_flag)!!
    private val anchorTagWidth =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 44f, context.resources.displayMetrics).toInt()
    private val anchorTagHeight =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 16f, context.resources.displayMetrics).toInt()
    private val anchorTagMarginEnd =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 2f, context.resources.displayMetrics).toInt()
    private val anchorTagTextSize =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 9f, context.resources.displayMetrics)

    private val levelTagHeight =
        anchorTagHeight
    private val levelTagPaddingStart =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 6f, context.resources.displayMetrics)
    private val levelTagPaddingEnd =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 8f, context.resources.displayMetrics)
    private val levelTagIconSize =
        (levelTagHeight * 0.7f).toInt()
    private val levelTagIconTextGap =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 3f, context.resources.displayMetrics)
    private val levelTagMarginEnd =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 4f, context.resources.displayMetrics).toInt()
    private val levelTagTextSize =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 11f, context.resources.displayMetrics)
    private val levelTagTextColor = ContextCompat.getColor(context, android.R.color.white)

    @SuppressLint("SetTextI18n")
    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int, barrage: Barrage) {
        val viewHolder = holder as ViewHolder
        val fontSize = getFontSize(viewHolder.textMsgContent)

        val userName = barrage.sender.userName.takeIf { it.isNotEmpty() } ?: barrage.sender.userID
        val isOwner = ownerId == barrage.sender.userID

        viewHolder.textMsgContent.text = buildMessageContent(userName, barrage, fontSize, isOwner)
        viewHolder.textMsgContent.movementMethod = if (isOwner) LinkMovementMethod.getInstance() else null
    }

    private fun buildMessageContent(
        userName: String,
        barrage: Barrage,
        fontSize: Int,
        isOwner: Boolean
    ): SpannableStringBuilder {
        val textContent = barrage.textContent
        val contentPart = getContentWithEmoji(textContent, fontSize)
        Log.i("barrage", "buildMessageContent sender: ${barrage.sender}")
        val levelTag = if (ENABLE_LIVEKIT_BARRAGE_USER_LEVEL) getLevelTag(barrage.sender.level, context)
        else null

        return SpannableStringBuilder().apply {
            if (isRtl) {

                appendLevelTag(levelTag, isRtl = true)

                if (isOwner) {
                    val anchorStartIndex = length
                    append("\u200B")
                    setSpan(
                        AnchorTagSpan(
                            anchorTagText,
                            contentColor,
                            anchorTagTextSize,
                            anchorTagBackground.constantState!!.newDrawable().mutate(),
                            anchorTagWidth,
                            anchorTagHeight,
                            anchorTagMarginEnd,
                            isRtl = true
                        ),
                        anchorStartIndex, anchorStartIndex + 1,
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                }

                val userNameStart = length
                append("$userName\u200F:")
                val userNameEnd = length
                setSpan(
                    ForegroundColorSpan(userNameColor),
                    userNameStart,
                    userNameEnd,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )

                append("\u2066")
                val contentStart = length
                append(contentPart)
                val contentEnd = length
                append("\u2069")
                setSpan(
                    ForegroundColorSpan(contentColor),
                    contentStart,
                    contentEnd,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            } else {
                appendLevelTag(levelTag, isRtl = false)
                if (isOwner) {
                    val anchorStartIndex = length
                    append("\u200B")
                    setSpan(
                        AnchorTagSpan(
                            anchorTagText,
                            contentColor,
                            anchorTagTextSize,
                            anchorTagBackground.constantState!!.newDrawable().mutate(),
                            anchorTagWidth,
                            anchorTagHeight,
                            anchorTagMarginEnd,
                            isRtl = false
                        ),
                        anchorStartIndex, anchorStartIndex + 1,
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                }
                val anchorLength = length
                append("$userName: ")
                val userNameEndIndex = length
                setSpan(
                    ForegroundColorSpan(userNameColor),
                    anchorLength,
                    userNameEndIndex,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                append(contentPart)
                setSpan(
                    ForegroundColorSpan(contentColor),
                    userNameEndIndex,
                    length,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
        }
    }

    private fun SpannableStringBuilder.appendLevelTag(levelTag: LevelTag?, isRtl: Boolean) {
        if (levelTag == null) {
            return
        }
        val icon = ContextCompat.getDrawable(context, levelTag.iconResId)
            ?.constantState
            ?.newDrawable()
            ?.mutate() ?: return
        val start = length
        append("\u200B")
        setSpan(
            LevelTagSpan(
                text = levelTag.level.toString(),
                textColor = levelTagTextColor,
                textSize = levelTagTextSize,
                backgroundColor = levelTag.backgroundColor,
                icon = icon,
                tagHeight = levelTagHeight,
                iconSize = levelTagIconSize,
                paddingStart = levelTagPaddingStart,
                paddingEnd = levelTagPaddingEnd,
                iconTextGap = levelTagIconTextGap,
                margin = levelTagMarginEnd,
                isRtl = isRtl
            ),
            start,
            start + 1,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )
    }

    override fun onCreateViewHolder(parent: ViewGroup): RecyclerView.ViewHolder {
        val view = mLayoutInflater.inflate(R.layout.livekit_barrage_item_msg, parent, false)
        return ViewHolder(view)
    }

    fun getFontSize(textView: TextView): Int {
        return Paint().apply {
            textSize = textView.textSize
        }.let { paint ->
            val fm = paint.fontMetrics
            ceil(fm.bottom - fm.top).toInt()
        }
    }

    private fun getContentWithEmoji(textContent: String, fontSize: Int): SpannableStringBuilder {
        return SpannableStringBuilder(textContent).apply {
            processEmojiSpan(this, fontSize)
        }
    }

    private fun processEmojiSpan(sb: SpannableStringBuilder, fontSize: Int) {
        val text = sb.toString()
        var startIndex = 0
        var i = 0
        while (i < text.length) {
            if (text[i] == '[') {
                val endIndex = text.indexOf(']', i)
                if (endIndex != -1) {
                    val emojiKey = text.substring(i, endIndex + 1)
                    mEmojiResource.getResId(emojiKey).takeIf { it != 0 }?.let { resId ->
                        mEmojiResource.getDrawable(context, resId, Rect(0, 0, fontSize, fontSize))
                            .apply { setBounds(0, 0, fontSize, fontSize) }
                            .let { drawable ->
                                sb.setSpan(
                                    EmojiSpan(drawable, 0),
                                    startIndex, endIndex + 1,
                                    SpannableStringBuilder.SPAN_EXCLUSIVE_EXCLUSIVE
                                )
                            }
                    }
                    startIndex = endIndex + 1
                    i = endIndex
                }
            } else {
                startIndex++
            }
            i++
        }
    }

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val textMsgContent: TextView = itemView.findViewById(R.id.tv_msg_content)
    }

    private class LevelTagSpan(
        private val text: String,
        private val textColor: Int,
        private val textSize: Float,
        private val backgroundColor: Int,
        private val icon: Drawable,
        private val tagHeight: Int,
        private val iconSize: Int,
        private val paddingStart: Float,
        private val paddingEnd: Float,
        private val iconTextGap: Float,
        private val margin: Int,
        private val isRtl: Boolean = false
    ) : ReplacementSpan() {

        override fun getSize(
            paint: Paint,
            text: CharSequence,
            start: Int,
            end: Int,
            fm: Paint.FontMetricsInt?
        ): Int {
            fm?.run {
                val pfm = paint.fontMetricsInt
                val centerOffset = (tagHeight - (pfm.descent - pfm.ascent)) / 2
                ascent = pfm.ascent - centerOffset
                top = pfm.top - centerOffset
                bottom = pfm.descent + centerOffset
                descent = pfm.descent + centerOffset
            }
            return (getTagWidth(paint) + margin).toInt()
        }

        override fun draw(
            canvas: Canvas,
            text: CharSequence,
            start: Int,
            end: Int,
            x: Float,
            top: Int,
            y: Int,
            bottom: Int,
            paint: Paint
        ) {
            val originalTextSize = paint.textSize
            val originalColor = paint.color
            val originalStyle = paint.style
            val originalFakeBoldText = paint.isFakeBoldText

            val tagWidth = getTagWidth(paint)
            val pfm = paint.fontMetricsInt
            val fontCenter = y + (pfm.descent + pfm.ascent) / 2
            val tagTop = fontCenter - tagHeight / 2f
            val tagX = if (isRtl) x + margin else x
            val radius = tagHeight / 2f

            paint.color = backgroundColor
            paint.style = Paint.Style.FILL
            canvas.drawRoundRect(
                RectF(tagX, tagTop, tagX + tagWidth, tagTop + tagHeight),
                radius,
                radius,
                paint
            )

            val iconLeft = tagX + paddingStart
            val iconTop = tagTop + (tagHeight - iconSize) / 2f
            icon.setBounds(
                iconLeft.toInt(),
                iconTop.toInt(),
                (iconLeft + iconSize).toInt(),
                (iconTop + iconSize).toInt()
            )
            icon.draw(canvas)

            paint.color = textColor
            paint.textSize = textSize
            paint.isFakeBoldText = false
            val textX = iconLeft + iconSize + iconTextGap
            val textY = tagTop + tagHeight / 2f - (paint.descent() + paint.ascent()) / 2
            canvas.drawText(this.text, textX, textY, paint)

            paint.textSize = originalTextSize
            paint.color = originalColor
            paint.style = originalStyle
            paint.isFakeBoldText = originalFakeBoldText
        }

        private fun getTagWidth(paint: Paint): Float {
            val originalTextSize = paint.textSize
            val originalFakeBoldText = paint.isFakeBoldText
            paint.textSize = textSize
            paint.isFakeBoldText = true
            val width = paddingStart + iconSize + iconTextGap + paint.measureText(text) + paddingEnd
            paint.textSize = originalTextSize
            paint.isFakeBoldText = originalFakeBoldText
            return width
        }
    }

    fun getLevelTag(level: Int, context: Context): LevelTag? {
        val colorTokens = ThemeStore.shared(context).themeState.value.currentTheme.tokens.color
        return when (level) {
            in 0..20 -> LevelTag(level, R.drawable.live_barrage_level1, colorTokens.tagColorLevel1)
            in 21..40 -> LevelTag(level, R.drawable.live_barrage_level2, colorTokens.tagColorLevel2)
            in 41..60 -> LevelTag(level, R.drawable.live_barrage_level3, colorTokens.tagColorLevel3)
            else -> LevelTag(level, R.drawable.live_barrage_level4, colorTokens.tagColorLevel4)
        }
    }
}

data class LevelTag(
    val level: Int,
    val iconResId: Int,
    val backgroundColor: Int
)