package com.trtc.uikit.livekit.features.livelist.view.access

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Shader
import android.graphics.drawable.PaintDrawable
import android.graphics.drawable.ShapeDrawable
import android.graphics.drawable.shapes.RectShape
import android.text.TextUtils
import android.util.AttributeSet
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import com.trtc.uikit.livekit.R
import io.trtc.tuikit.atomicx.widget.basicwidget.avatar.AtomicAvatar
import io.trtc.tuikit.atomicx.widget.basicwidget.avatar.AtomicAvatar.AvatarContent
import io.trtc.tuikit.atomicxcore.api.live.LiveInfo

class DoubleColumnWidgetView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private val avatar: AtomicAvatar
    private val textRoomName: TextView
    private val textAnchorName: TextView
    private val textAudienceCountInfo: TextView
    private val gradientMask: View

    init {
        LayoutInflater.from(context).inflate(R.layout.livelist_double_column_widget_item, this, true)
        textRoomName = findViewById(R.id.tv_room_name)
        textAnchorName = findViewById(R.id.tv_anchor_name)
        avatar = findViewById(R.id.iv_avatar)
        textAudienceCountInfo = findViewById(R.id.tv_audience_count_info)

        gradientMask = View(context)
        val lp = LayoutParams(LayoutParams.MATCH_PARENT, 0, Gravity.BOTTOM)
        addView(gradientMask, 0, lp)
        setupGradientMask()
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        val params = gradientMask.layoutParams
        val targetHeight = (bottom - top) / 2
        if (params.height != targetHeight) {
            params.height = targetHeight
            gradientMask.layoutParams = params
        }
    }

    private fun setupGradientMask() {
        val colors = intArrayOf(
            Color.argb(0, 0, 0, 0),
            Color.argb(0, 0, 0, 0),
            Color.argb((0.05f * 255).toInt(), 0, 0, 0),
            Color.argb((0.2f * 255).toInt(), 0, 0, 0),
            Color.argb((0.4f * 255).toInt(), 0, 0, 0)
        )
        val positions = floatArrayOf(0f, 0.3f, 0.5f, 0.75f, 1f)
        val drawable = PaintDrawable()
        drawable.shape = RectShape()
        drawable.shaderFactory = object : ShapeDrawable.ShaderFactory() {
            override fun resize(width: Int, height: Int): Shader {
                return LinearGradient(
                    0f, 0f, 0f, height.toFloat(),
                    colors, positions,
                    Shader.TileMode.CLAMP
                )
            }
        }
        gradientMask.background = drawable
    }

    fun init(liveInfo: LiveInfo) {
        updateLiveInfoView(liveInfo)
    }

    @SuppressLint("StringFormatMatches")
    fun updateLiveInfoView(liveInfo: LiveInfo) {
        avatar.setContent(AvatarContent.URL(liveInfo.liveOwner.avatarURL, R.drawable.livelist_default_avatar))
        textRoomName.text = if (TextUtils.isEmpty(liveInfo.liveName)) liveInfo.liveID else liveInfo.liveName
        textAnchorName.text = if (TextUtils.isEmpty(liveInfo.liveOwner.userName)) liveInfo.liveOwner.userID else liveInfo.liveOwner.userName
        textAudienceCountInfo.text = context.getString(R.string.livelist_viewed_audience_count, liveInfo.totalViewerCount)
    }
}
