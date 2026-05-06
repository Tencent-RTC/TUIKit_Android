package com.trtc.uikit.livekit.features.endstatistics

import android.content.Context
import android.util.AttributeSet
import android.util.Size
import android.view.LayoutInflater
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.RelativeLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.trtc.uikit.livekit.R
import com.trtc.uikit.livekit.common.LiveKitLogger
import com.trtc.uikit.livekit.features.endstatistics.store.EndStatisticsStore
import io.trtc.tuikit.atomicx.common.util.ScreenUtil.dip2px
import io.trtc.tuikit.atomicx.widget.basicwidget.label.AtomicLabel
import io.trtc.tuikit.atomicxcore.api.live.LiveEndedReason
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.max

class AnchorEndStatisticsView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {

    private val logger = LiveKitLogger.getFeaturesLogger("AnchorEndStatisticsView")
    private val store = EndStatisticsStore()
    private val state = store.getState()
    private var subscribeStateJob: Job? = null

    private lateinit var textDuration: TextView
    private lateinit var textViewersCount: TextView
    private lateinit var textMessageCount: TextView
    private lateinit var textGiftSenderCount: TextView
    private lateinit var textGiftIncome: TextView
    private lateinit var textLikeCount: TextView
    private lateinit var textPIPTitle: AtomicLabel
    private lateinit var textTitle: AtomicLabel
    private lateinit var layoutContent: RelativeLayout
    private var listener: EndStatisticsDefine.AnchorEndStatisticsViewListener? = null

    init {
        initView()
    }

    private fun initView() {
        LayoutInflater.from(context).inflate(R.layout.livekit_anchor_dashboard_view, this, true)
        textTitle = findViewById(R.id.tv_title)
        textPIPTitle = findViewById(R.id.tv_title_pip)
        layoutContent = findViewById(R.id.rl_statistics_data)
        textDuration = findViewById(R.id.tv_duration)
        textViewersCount = findViewById(R.id.tv_viewers)
        textMessageCount = findViewById(R.id.tv_message)
        textGiftIncome = findViewById(R.id.tv_gift_income)
        textGiftSenderCount = findViewById(R.id.tv_gift_people)
        textLikeCount = findViewById(R.id.tv_like)
        findViewById<ImageView>(R.id.iv_back).setOnClickListener { onExitClick() }
    }

    fun init(info: EndStatisticsDefine.AnchorEndStatisticsInfo?) {
        if (info == null) {
            logger.error("init, info is null")
        } else {
            store.setRoomId(info.roomId)
            store.setLiveDuration(info.liveDurationMS)
            store.setMaxViewersCount(max(0, info.maxViewersCount))
            store.setMessageCount(max(0, info.messageCount))
            store.setLikeCount(info.likeCount)
            store.setGiftIncome(info.giftIncome)
            store.setGiftSenderCount(info.giftSenderCount)
            store.setLiveEndedReason(info.liveEndedReason)
            logger.info("init, ${state}")
        }
    }

    fun enablePipMode(inPictureInPictureMode: Boolean) {
        if (inPictureInPictureMode) {
            textPIPTitle.visibility = VISIBLE
            layoutContent.visibility = GONE
        } else {
            textPIPTitle.visibility = GONE
            layoutContent.visibility = VISIBLE
        }
    }

    fun setListener(listener: EndStatisticsDefine.AnchorEndStatisticsViewListener?) {
        this.listener = listener
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        addObserver()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        removeObserver()
    }

    private fun addObserver() {
        subscribeStateJob = CoroutineScope(Dispatchers.Main).launch {
            launch {
                state.liveDurationMS.collect {
                    onLiveDurationChange(it)
                }
            }

            launch {
                state.maxViewersCount.collect {
                    onMaxViewersCountChange(it)
                }
            }

            launch {
                state.messageCount.collect {
                    onMessageCountChange(it)
                }
            }

            launch {
                state.likeCount.collect {
                    onLikeCountChange(it)
                }
            }

            launch {
                state.giftIncome.collect {
                    onGiftIncomeChange(it)
                }
            }

            launch {
                state.giftSenderCount.collect {
                    onGiftSenderCountChange(it)
                }
            }

            launch {
                state.liveEndedReason.collect {
                    onLiveEndedReasonChange(it)
                }
            }
        }
    }

    private fun removeObserver() {
        subscribeStateJob?.cancel()
    }

    private fun onExitClick() {
        listener?.onCloseButtonClick()
    }

    private fun onLiveDurationChange(durationMS: Long) {
        val duration = (durationMS / 1000).toInt()
        val formatSeconds = store.formatSeconds(duration)
        textDuration.text = formatSeconds
    }

    private fun onMaxViewersCountChange(count: Long) {
        val info = String.format(Locale.getDefault(), "%d", count)
        textViewersCount.text = info
    }

    private fun onMessageCountChange(count: Long) {
        val info = String.format(Locale.getDefault(), "%d", count)
        textMessageCount.text = info
    }

    private fun onLikeCountChange(count: Long) {
        val info = String.format(Locale.getDefault(), "%d", count)
        textLikeCount.text = info
    }

    private fun onGiftIncomeChange(count: Long) {
        val info = String.format(Locale.getDefault(), "%d", count)
        textGiftIncome.text = info
    }

    private fun onGiftSenderCountChange(count: Long) {
        val info = String.format(Locale.getDefault(), "%d", count)
        textGiftSenderCount.text = info
    }

    private fun onLiveEndedReasonChange(reason: LiveEndedReason) {
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