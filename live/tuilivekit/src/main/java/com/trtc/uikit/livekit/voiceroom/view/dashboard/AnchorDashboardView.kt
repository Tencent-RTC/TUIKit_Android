package com.trtc.uikit.livekit.voiceroom.view.dashboard

import android.app.Activity
import android.content.Context
import android.util.AttributeSet
import android.util.Size
import android.view.LayoutInflater
import android.view.View
import android.widget.RelativeLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.trtc.uikit.livekit.R
import com.trtc.uikit.livekit.voiceroom.manager.VoiceRoomManager
import com.trtc.uikit.livekit.voiceroom.view.basic.BasicView
import io.trtc.tuikit.atomicx.common.util.ScreenUtil.dip2px
import io.trtc.tuikit.atomicx.widget.basicwidget.label.AtomicLabel
import io.trtc.tuikit.atomicxcore.api.live.LiveEndedReason
import java.util.Locale

class AnchorDashboardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : BasicView(context, attrs, defStyleAttr) {

    private lateinit var textTitle: AtomicLabel

    init {
        LayoutInflater.from(context).inflate(R.layout.livekit_anchor_dashboard_view, this, true)
        textTitle = findViewById(R.id.tv_title)
    }

    override fun init(liveID: String, voiceRoomManager: VoiceRoomManager) {
        super.init(liveID, voiceRoomManager)
        findViewById<TextView>(R.id.tv_duration).text = formatSecondsTo00(
            ((System.currentTimeMillis() - voiceRoomManager.prepareStore.prepareState.liveInfo.value.createTime) / 1000).toInt()
        )

        with(voiceRoomManager.prepareStore.prepareState.liveExtraInfo.value) {
            findViewById<TextView>(R.id.tv_viewers).text = maxAudienceCount.toString()
            findViewById<TextView>(R.id.tv_message).text = messageCount.toString()
            findViewById<TextView>(R.id.tv_gift_income).text = giftIncome.toString()
            findViewById<TextView>(R.id.tv_gift_people).text = giftSenderCount.toString()
            findViewById<TextView>(R.id.tv_like).text = likeCount.toString()
            setTextTitle(liveEndedReason)
        }

        findViewById<View>(R.id.iv_back).setOnClickListener {
            (context as? Activity)?.finish()
        }
    }

    override fun addObserver() = Unit

    override fun removeObserver() = Unit

    override fun initStore() = Unit

    private fun formatSecondsTo00(timeSeconds: Int): String {
        if (timeSeconds <= 0) return "-- --"
        val hour = timeSeconds / 3600
        val min = timeSeconds % 3600 / 60
        val sec = timeSeconds % 60
        return String.format(Locale.getDefault(), "%02d:%02d:%02d", hour, min, sec)
    }

    private fun setTextTitle(reason: LiveEndedReason) {
        val layoutParams = textTitle.layoutParams as RelativeLayout.LayoutParams
        if (reason == LiveEndedReason.ENDED_BY_SERVER) {
            textTitle.text = context.getString(R.string.common_end_live_by_server)
            layoutParams.removeRule(RelativeLayout.CENTER_HORIZONTAL)
        } else {
            textTitle.text = context.getString(R.string.common_live_has_stop)
            layoutParams.addRule(RelativeLayout.CENTER_HORIZONTAL)
        }
        textTitle.layoutParams = layoutParams

        if (reason == LiveEndedReason.ENDED_BY_SERVER) {
            textTitle.iconConfiguration = AtomicLabel.IconConfiguration(
                drawable = ContextCompat.getDrawable(context, R.drawable.livekit_end_live_by_server),
                position = AtomicLabel.IconConfiguration.Position.LEFT,
                spacing = dip2px(8.0f).toFloat(),
                size = Size(dip2px(20f), dip2px(20f))
            )
        } else {
            textTitle.iconConfiguration = null
        }
    }
}
