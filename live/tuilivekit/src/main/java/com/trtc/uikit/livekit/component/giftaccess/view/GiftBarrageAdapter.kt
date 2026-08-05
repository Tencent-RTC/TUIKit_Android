package com.trtc.uikit.livekit.component.giftaccess.view

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
import android.text.style.ForegroundColorSpan
import android.text.style.ImageSpan
import android.text.style.ReplacementSpan
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import io.trtc.tuikit.atomicx.common.util.ScreenUtil
import com.trtc.uikit.livekit.R
import com.trtc.uikit.livekit.common.ENABLE_LIVEKIT_BARRAGE_USER_LEVEL
import com.trtc.uikit.livekit.common.displayName
import com.trtc.uikit.livekit.component.barrage.view.adapter.BarrageItemAdapter
import com.trtc.uikit.livekit.component.giftaccess.service.GiftConstants
import io.trtc.tuikit.atomicx.theme.ThemeStore
import io.trtc.tuikit.atomicxcore.api.barrage.Barrage
import java.security.SecureRandom

class GiftBarrageAdapter(private val mContext: Context) : BarrageItemAdapter {

    companion object {
        private const val TAG = "GiftBarrageAdapter"
    }

    private val mDefaultGiftIcon: Drawable = ColorDrawable(Color.DKGRAY)

    init {
        val giftIconSize = 13f
        val bounds = Rect(0, 0, ScreenUtil.dip2px(giftIconSize), ScreenUtil.dip2px(giftIconSize))
        mDefaultGiftIcon.bounds = bounds
    }

    override fun onCreateViewHolder(parent: ViewGroup): RecyclerView.ViewHolder {
        val ll = LinearLayout(mContext)
        ll.addView(TextView(mContext))
        return GiftViewHolder(ll, mDefaultGiftIcon)
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int, barrage: Barrage) {
        val viewHolder = holder as GiftViewHolder
        viewHolder.bind(barrage)
    }

    private class GiftViewHolder(
        itemView: View,
        private val defaultGiftIcon: Drawable
    ) : RecyclerView.ViewHolder(itemView) {

        private val textView: TextView
        private val context: Context
        private val random = SecureRandom()

        private val levelTagHeight =
            TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 16f, itemView.resources.displayMetrics).toInt()
        private val levelTagPaddingStart =
            TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 6f, itemView.resources.displayMetrics)
        private val levelTagPaddingEnd =
            TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 8f, itemView.resources.displayMetrics)
        private val levelTagIconSize = (levelTagHeight * 0.7f).toInt()
        private val levelTagIconTextGap =
            TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 3f, itemView.resources.displayMetrics)
        private val levelTagMarginEnd =
            TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 4f, itemView.resources.displayMetrics).toInt()
        private val levelTagTextSize =
            TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 11f, itemView.resources.displayMetrics)
        private val levelTagTextColor = ContextCompat.getColor(itemView.context, android.R.color.white)
        private val isRtl = itemView.resources.configuration.layoutDirection == View.LAYOUT_DIRECTION_RTL

        init {
            this.context = itemView.context
            val linearLayout = itemView as LinearLayout
            textView = linearLayout.getChildAt(0) as TextView
            linearLayout.setPadding(0, ScreenUtil.dip2px(3f), 0, ScreenUtil.dip2px(3f))
            textView.setTextColor(Color.WHITE)
            textView.textSize = 12f
            textView.setTypeface(null, Typeface.BOLD)
            textView.gravity = Gravity.START or Gravity.CENTER_VERTICAL
            textView.setPadding(
                ScreenUtil.dip2px(5f), ScreenUtil.dip2px(5f),
                ScreenUtil.dip2px(5f), ScreenUtil.dip2px(5f)
            )
            textView.setBackgroundResource(R.drawable.git_barrage_bg_msg_item)
        }

        fun bind(barrage: Barrage) {
            val sb = SpannableStringBuilder()

            val levelTag = if (ENABLE_LIVEKIT_BARRAGE_USER_LEVEL) {
                getLevelTag(barrage.sender.level, context)
            } else {
                null
            }
            sb.appendLevelTag(levelTag, isRtl)

            val sender = barrage.sender.displayName
            val senderStart = sb.length
            sb.append(sender)
            
            val userNameColor = context.resources.getColor(R.color.common_barrage_user_name_color)
            val senderSpan = ForegroundColorSpan(userNameColor)
            sb.setSpan(senderSpan, senderStart, senderStart + sender.length, SPAN_EXCLUSIVE_EXCLUSIVE)
            
            val send = " ${context.getString(R.string.common_sent)} "
            sb.append(send)
            
            val receiver = "${barrage.extensionInfo[GiftConstants.GIFT_RECEIVER_USERNAME]} "
            sb.append(receiver)
            val receiverSpan = ForegroundColorSpan(senderSpan.foregroundColor)
            sb.setSpan(receiverSpan, sb.length - receiver.length, sb.length, SPAN_EXCLUSIVE_EXCLUSIVE)
            
            val giftName = barrage.extensionInfo[GiftConstants.GIFT_NAME].toString()
            sb.append(giftName)
            val giftNameColor = context.resources.getColor(
                GiftConstants.MESSAGE_COLOR[random.nextInt(GiftConstants.MESSAGE_COLOR.size)]
            )
            val giftSpan = ForegroundColorSpan(giftNameColor)
            sb.setSpan(giftSpan, sb.length - giftName.length, sb.length, SPAN_EXCLUSIVE_EXCLUSIVE)
            
            sb.append(" ")
            val giftIconSpanStart = sb.length - 1
            val imageSpan = ImageSpan(defaultGiftIcon)
            imageSpan.drawable.bounds = defaultGiftIcon.bounds
            sb.setSpan(imageSpan, giftIconSpanStart, giftIconSpanStart + 1, SPAN_EXCLUSIVE_EXCLUSIVE)

            val count = barrage.extensionInfo[GiftConstants.GIFT_COUNT].toString()
            sb.append("x").append(count).append("   ")
            textView.text = sb

            val url = barrage.extensionInfo[GiftConstants.GIFT_ICON_URL].toString()
            loadGiftIcon(url, sb, giftIconSpanStart, giftIconSpanStart + 1)
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

        private fun loadGiftIcon(url: String, sb: SpannableStringBuilder, start: Int, end: Int) {
            Glide.with(context)
                .asBitmap()
                .load(url)
                .into(object : CustomTarget<Bitmap>() {
                    override fun onResourceReady(resource: Bitmap, transition: Transition<in Bitmap>?) {
                        val span = ImageSpan(context, resource)
                        span.drawable.bounds = defaultGiftIcon.bounds
                        sb.setSpan(span, start, end, SPAN_EXCLUSIVE_EXCLUSIVE)
                        textView.text = sb
                    }

                    override fun onLoadCleared(placeholder: Drawable?) {
                        Log.e(TAG, "glide load failed: $url")
                    }
                })
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
}


data class LevelTag(
    val level: Int,
    val iconResId: Int,
    val backgroundColor: Int
)