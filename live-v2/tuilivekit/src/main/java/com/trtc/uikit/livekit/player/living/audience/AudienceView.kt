package com.trtc.uikit.livekit.player.living.audience

import com.trtc.uikit.livekit.common.audience.AudienceWelcome
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.util.AttributeSet
import android.util.TypedValue
import android.view.GestureDetector
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.core.content.ContextCompat.getString
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.trtc.uikit.livekit.R
import com.trtc.uikit.livekit.common.basic.setDebounceClickListener
import com.trtc.uikit.livekit.common.theme.avatar.AtomicAvatar
import com.trtc.uikit.livekit.common.theme.label.AtomicLabel
import com.trtc.uikit.livekit.common.theme.toast.LiveToast
import io.trtc.tuikit.atomicxcore.api.live.LiveAudienceListener
import io.trtc.tuikit.atomicxcore.api.live.LiveAudienceStore
import io.trtc.tuikit.atomicxcore.api.live.LiveInfo
import io.trtc.tuikit.atomicxcore.api.live.LiveUserInfo
import io.trtc.tuikit.atomicxcore.api.login.LoginStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

class AudienceView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : LinearLayout(context, attrs, defStyleAttr) {

    companion object {
        private const val MAX_SHOW_AVATAR_COUNT = 3
        private const val AVATAR_OVERLAP_DP = 9f
    }

    private val audienceCount: AtomicLabel
    private val audienceList: RecyclerView

    private val adapter = AudienceAvatarAdapter()
    private var subscribeJob: Job? = null
    private var popupDialog: AudienceListPanel? = null
    private val mutableAudienceEntered = MutableSharedFlow<AudienceWelcome>(extraBufferCapacity = 32)
    val audienceEntered = mutableAudienceEntered.asSharedFlow()

    private var liveInfo: LiveInfo = LiveInfo()
    private val liveAudienceListener = object : LiveAudienceListener() {
        override fun onAudienceMessageDisabled(audience: LiveUserInfo, isDisable: Boolean) {
            if (audience.userID == LoginStore.shared.loginState.loginUserInfo.value?.userID) {
                if (isDisable) {
                    LiveToast.show(context, getString(context, R.string.common_send_message_disabled))
                } else {
                    LiveToast.show(context, getString(context, R.string.common_send_message_enable))
                }
            }
        }

        override fun onAudienceJoined(audience: LiveUserInfo) {
            if (audience.userID.isEmpty()) return
            mutableAudienceEntered.tryEmit(audience.toWelcome()) 
        }

        private fun LiveUserInfo.toWelcome() = AudienceWelcome(
            userID = userID,
            userName = userName.ifEmpty { userID },
            level = level.coerceAtLeast(0),
        )
    }

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        clipChildren = false
        clipToPadding = false
        LayoutInflater.from(context).inflate(R.layout.livekit_audience_list_view, this, true)

        audienceList = findViewById(R.id.rv_audience_list)
        audienceCount = findViewById(R.id.tv_audience_count)
        val llAudienceCount = findViewById<View>(R.id.ll_audience_count)

        audienceList.layoutManager =
            LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
        audienceList.adapter = adapter
        audienceList.addItemDecoration(AudienceAvatarOverlapDecoration(dpToPx(AVATAR_OVERLAP_DP).toInt()))
        audienceList.setChildDrawingOrderCallback { childCount, i ->
            childCount - 1 - i
        }

        val onClick: (View) -> Unit = { showAudienceListPanel() }
        llAudienceCount.setDebounceClickListener(action = onClick)
        attachRecyclerViewClick(onClick)
        audienceCount.text = "0"
    }

    private fun attachRecyclerViewClick(onClick: (View) -> Unit) {
        val gesture = GestureDetector(
            context,
            object : GestureDetector.SimpleOnGestureListener() {
                private var lastClickTime = 0L
                override fun onSingleTapUp(e: MotionEvent): Boolean {
                    val now = System.currentTimeMillis()
                    if (now - lastClickTime >= 1000L) {
                        lastClickTime = now
                        onClick(audienceList)
                    }
                    return true
                }
            }
        )
        audienceList.addOnItemTouchListener(object : RecyclerView.OnItemTouchListener {
            override fun onInterceptTouchEvent(rv: RecyclerView, e: MotionEvent): Boolean {
                gesture.onTouchEvent(e)
                return false
            }

            override fun onTouchEvent(rv: RecyclerView, e: MotionEvent) {}
            override fun onRequestDisallowInterceptTouchEvent(disallowIntercept: Boolean) {}
        })
    }

    fun bind(liveInfo: LiveInfo) {
        subscribeJob?.cancel()
        if (this.liveInfo.liveID != liveInfo.liveID) {
            popupDialog?.dismiss()
            popupDialog = null
        }
        this.liveInfo = liveInfo
        val store = LiveAudienceStore.create(liveInfo.liveID)
        val state = store.liveAudienceState
        store.fetchAudienceList(null)
        subscribeJob = CoroutineScope(Dispatchers.Main).launch {
            launch {
                state.audienceList.collect { list ->
                    adapter.setData(list)
                }
            }
            launch {
                state.audienceCount.collect { count ->
                    audienceCount.text = count.toString()
                }
            }
        }
        store.removeLiveAudienceListener(liveAudienceListener)
        store.addLiveAudienceListener(liveAudienceListener)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (liveInfo.liveID.isEmpty()) return
        val store = LiveAudienceStore.create(liveInfo.liveID)
        val state = store.liveAudienceState
        store.fetchAudienceList(null)
        subscribeJob?.cancel()
        subscribeJob = CoroutineScope(Dispatchers.Main).launch {
            launch {
                state.audienceList.collect { list ->
                    adapter.setData(list)
                }
            }
            launch {
                state.audienceCount.collect { count ->
                    audienceCount.text = count.toString()
                }
            }
        }
        store.removeLiveAudienceListener(liveAudienceListener)
        store.addLiveAudienceListener(liveAudienceListener)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        subscribeJob?.cancel()
        subscribeJob = null
        popupDialog?.dismiss()
        popupDialog = null
        if (liveInfo.liveID.isEmpty()) return
        val store = LiveAudienceStore.create(liveInfo.liveID)
        store.removeLiveAudienceListener(liveAudienceListener)
    }

    private fun showAudienceListPanel() {
        if (liveInfo.liveID.isEmpty()) return
        popupDialog?.dismiss()
        popupDialog = AudienceListPanel(context, liveInfo.liveID).also { it.show() }
    }

    private fun dpToPx(dp: Float): Float =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, resources.displayMetrics)

    private inner class AudienceAvatarAdapter :
        RecyclerView.Adapter<AudienceAvatarAdapter.AvatarHolder>() {

        private val data = mutableListOf<LiveUserInfo>()

        @SuppressLint("NotifyDataSetChanged")
        fun setData(list: List<LiveUserInfo>) {
            data.clear()
            data.addAll(list.take(MAX_SHOW_AVATAR_COUNT))
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AvatarHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.livekit_audience_list_item, parent, false)
            return AvatarHolder(view)
        }

        override fun onBindViewHolder(holder: AvatarHolder, position: Int) {
            val user = data[position]
            holder.avatar.setContent(
                AtomicAvatar.AvatarContent.URL(user.avatarURL, R.drawable.livekit_livelist_default_avatar)
            )
            val cell = holder.itemView as AudienceAvatarCellView
            val cellSize = dpToPx(26f)
            val overlap = dpToPx(AVATAR_OVERLAP_DP)
            val neighborCenterX = -(cellSize - overlap) + cellSize / 2f
            val cutoutRadius = cellSize / 2f + dpToPx(1f)
            cell.setLeftCutout(position > 0, neighborCenterX, cutoutRadius)
        }

        override fun getItemCount(): Int = data.size

        inner class AvatarHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val avatar: AtomicAvatar = itemView.findViewById(R.id.iv_avatar)
        }
    }

    private class AudienceAvatarOverlapDecoration(
        private val overlapPx: Int,
    ) : RecyclerView.ItemDecoration() {
        override fun getItemOffsets(
            outRect: Rect,
            view: View,
            parent: RecyclerView,
            state: RecyclerView.State,
        ) {
            val position = parent.getChildAdapterPosition(view)
            outRect.set(if (position > 0) -overlapPx else 0, 0, 0, 0)
        }
    }
}

class AudienceAvatarCellView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr) {

    private val cutoutPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT)
        color = -0x1
    }

    private var cutoutEnabled = false
    private var cutoutCenterX = 0f
    private var cutoutCenterY = 0f
    private var cutoutRadius = 0f

    init {
        setWillNotDraw(false)
        clipChildren = false
        clipToPadding = false
    }

    fun setLeftCutout(enabled: Boolean, neighborCenterOffsetX: Float, radius: Float) {
        cutoutEnabled = enabled
        cutoutCenterX = neighborCenterOffsetX
        cutoutRadius = radius
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        cutoutCenterY = h / 2f
    }

    override fun dispatchDraw(canvas: Canvas) {
        if (!cutoutEnabled) {
            super.dispatchDraw(canvas)
            return
        }
        val saveCount = canvas.saveLayer(0f, 0f, width.toFloat(), height.toFloat(), null)
        super.dispatchDraw(canvas)
        canvas.drawCircle(cutoutCenterX, cutoutCenterY, cutoutRadius, cutoutPaint)
        canvas.restoreToCount(saveCount)
    }
}
