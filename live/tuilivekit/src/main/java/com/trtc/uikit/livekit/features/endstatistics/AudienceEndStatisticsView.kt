package com.trtc.uikit.livekit.features.endstatistics

import android.content.Context
import android.text.TextUtils
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import com.trtc.uikit.livekit.R
import com.trtc.uikit.livekit.common.LiveKitLogger
import com.trtc.uikit.livekit.features.endstatistics.store.EndStatisticsStore
import io.trtc.tuikit.atomicx.widget.basicwidget.avatar.AtomicAvatar
import io.trtc.tuikit.atomicx.widget.basicwidget.avatar.AtomicAvatar.AvatarContent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class AudienceEndStatisticsView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {

    private val logger = LiveKitLogger.getFeaturesLogger("AudienceEndStatisticsView")
    private val store = EndStatisticsStore()
    private val state = store.getState()
    private var subscribeStateJob: Job? = null
    private lateinit var textName: TextView
    private lateinit var imageHead: AtomicAvatar

    private var listener: EndStatisticsDefine.AudienceEndStatisticsViewListener? = null

    init {
        initView()
    }

    private fun initView() {
        LayoutInflater.from(context).inflate(R.layout.livekit_audience_dashboard_view, this, true)
        textName = findViewById(R.id.tv_name)
        imageHead = findViewById(R.id.iv_head)
        findViewById<ImageView>(R.id.iv_back).setOnClickListener { onExitClick() }
    }

    fun init(roomId: String?, ownerName: String?, ownerAvatarUrl: String?) {
        store.setRoomId(if (TextUtils.isEmpty(roomId)) "" else roomId!!)
        store.setOwnerName(if (TextUtils.isEmpty(ownerName)) "" else ownerName!!)
        store.setOwnerAvatarUrl(if (TextUtils.isEmpty(ownerAvatarUrl)) "" else ownerAvatarUrl!!)
        logger.info("init, $state")
    }

    fun setListener(listener: EndStatisticsDefine.AudienceEndStatisticsViewListener?) {
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
                state.ownerName.collect {
                    onOwnerNameChange(it)
                }
            }
            launch {
                state.ownerAvatarUrl.collect {
                    onOwnerAvatarUrlChange(it)
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

    private fun onOwnerNameChange(name: String) {
        textName.text = name
    }

    private fun onOwnerAvatarUrlChange(url: String) {
        imageHead.setContent(AvatarContent.URL(url, R.drawable.livekit_ic_avatar))
    }
}