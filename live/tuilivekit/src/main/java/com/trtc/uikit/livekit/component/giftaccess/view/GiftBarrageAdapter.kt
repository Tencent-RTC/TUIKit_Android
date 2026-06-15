package com.trtc.uikit.livekit.component.giftaccess.view

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.text.SpannableStringBuilder
import android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
import android.text.style.ForegroundColorSpan
import android.text.style.ImageSpan
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import io.trtc.tuikit.atomicx.common.util.ScreenUtil
import com.trtc.uikit.livekit.R
import com.trtc.uikit.livekit.common.displayName
import com.trtc.uikit.livekit.component.barrage.view.adapter.BarrageItemAdapter
import com.trtc.uikit.livekit.component.giftaccess.service.GiftConstants
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
            val sender = barrage.sender.displayName
            sb.append(sender)
            
            val userNameColor = context.resources.getColor(R.color.common_barrage_user_name_color)
            val senderSpan = ForegroundColorSpan(userNameColor)
            sb.setSpan(senderSpan, 0, sender.length, SPAN_EXCLUSIVE_EXCLUSIVE)
            
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
    }
}
