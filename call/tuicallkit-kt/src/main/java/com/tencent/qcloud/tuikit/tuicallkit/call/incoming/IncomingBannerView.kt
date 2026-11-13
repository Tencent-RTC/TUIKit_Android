package com.tencent.qcloud.tuikit.tuicallkit.call.incoming

import android.content.Context
import android.graphics.Outline
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.tencent.qcloud.tuikit.tuicallkit.R
import com.tencent.qcloud.tuikit.tuicallkit.call.CallKitConfig
import com.tencent.qcloud.tuikit.tuicallkit.common.CallKitContactLoader
import com.tencent.qcloud.tuikit.tuicallkit.common.dp
import io.trtc.tuikit.atomicxcore.api.call.CallMediaType
import io.trtc.tuikit.atomicxcore.api.call.CallStore

internal class IncomingBannerView(
    context: Context,
    private val mediaType: CallMediaType?,
) : FrameLayout(context) {

    var onAccept: (() -> Unit)? = null
    var onReject: (() -> Unit)? = null
    var onExpand: (() -> Unit)? = null

    private val avatarImageView: ImageView
    private val nameLabel: TextView
    private val kickerLabel: TextView
    private val inviterLoader = InviterProfileLoader()

    init {
        setBackgroundResource(R.drawable.tuicallkit_bg_banner)
        clipToOutline = true
        elevation = 12f.dp(context).toFloat()
        outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setRoundRect(0, 0, view.width, view.height, 18f.dp(context).toFloat())
            }
        }
        setOnClickListener { onExpand?.invoke() }

        avatarImageView = ImageView(context).apply {
            setImageResource(R.drawable.tuicallkit_default_avatar)
            scaleType = ImageView.ScaleType.CENTER_CROP
            clipToOutline = true
            outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) {
                    outline.setRoundRect(0, 0, view.width, view.height, 12f.dp(context).toFloat())
                }
            }
        }
        val avatarSize = 44.dp(context)
        val avatarParams = LayoutParams(avatarSize, avatarSize, Gravity.START or Gravity.CENTER_VERTICAL)
        avatarParams.marginStart = 16.dp(context)
        addView(avatarImageView, avatarParams)

        nameLabel = TextView(context).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            setTextColor(ContextCompat.getColor(context, android.R.color.white))
            typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        }
        kickerLabel = TextView(context).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            setTextColor(ContextCompat.getColor(context, R.color.tuicallkit_text_on_dark_secondary))
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        }
        val textStack = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(nameLabel)
            addView(kickerLabel)
        }
        val textParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.START or Gravity.CENTER_VERTICAL)
        textParams.marginStart = (16 + 44 + 12).dp(context)
        textParams.marginEnd = (16 + 44 + 12 + 44 + 8).dp(context)
        addView(textStack, textParams)

        val buttonSize = 44.dp(context)

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

        val acceptParams = LayoutParams(buttonSize, buttonSize, Gravity.END or Gravity.CENTER_VERTICAL)
        acceptParams.marginEnd = 16.dp(context)
        addView(acceptButton, acceptParams)
        val rejectParams = LayoutParams(buttonSize, buttonSize, Gravity.END or Gravity.CENTER_VERTICAL)
        rejectParams.marginEnd = (16 + 44 + 12).dp(context)
        addView(rejectButton, rejectParams)
    }

    fun reload() {
        val call = CallStore.shared.observerState.activeCall.value
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
        inviterLoader.load(inviterID, nameLabel, avatarImageView)
    }
}

/** 主叫昵称/头像加载（仅横幅使用），带请求令牌防止串响应。 */
internal class InviterProfileLoader {

    private var currentInviterID = ""
    private var requestToken = 0

    fun load(inviterID: String, nameLabel: TextView, avatarImageView: ImageView) {
        if (inviterID.isEmpty()) {
            return
        }
        nameLabel.text = inviterID
        if (currentInviterID == inviterID) {
            return
        }
        currentInviterID = inviterID
        requestToken += 1
        val token = requestToken
        CallKitContactLoader.load(listOf(inviterID)) { _, nickname, _, bitmap ->
            if (requestToken != token) {
                return@load
            }
            if (!nickname.isNullOrEmpty()) {
                nameLabel.text = nickname
            }
            if (bitmap != null) {
                avatarImageView.setImageBitmap(bitmap)
            }
        }
    }
}
