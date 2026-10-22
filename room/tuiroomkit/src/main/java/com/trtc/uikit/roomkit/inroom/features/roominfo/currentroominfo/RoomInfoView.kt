package com.trtc.uikit.roomkit.inroom.features.roominfo.currentroominfo

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.View.MeasureSpec
import android.widget.FrameLayout
import android.widget.LinearLayout
import com.trtc.uikit.roomkit.R
import com.trtc.uikit.roomkit.base.toast.RoomToast
import androidx.appcompat.widget.AppCompatTextView
import io.trtc.tuikit.atomicxcore.api.room.RoomInfo
import io.trtc.tuikit.atomicxcore.api.room.RoomStore
import com.trtc.uikit.roomkit.base.util.dp
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class RoomInfoView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr) {

    private val roomNameText: AppCompatTextView
    private val elapsedText: AppCompatTextView
    private val chip: View
    private val content: LinearLayout
    private val mainRow: LinearLayout
    private val secondRow: LinearLayout
    private val divider: View
    private val roomStore = RoomStore.shared()
    private var roomID: String = ""
    private var subscribeJob: Job? = null
    private var timerBaseMillis: Long = 0L
    private var onInfoClickListener: (() -> Unit)? = null

    private val elapsedTicker = object : Runnable {
        override fun run() {
            updateElapsed()
            postDelayed(this, 1000L)
        }
    }

    var availableWidth: Int = Int.MAX_VALUE
        set(value) {
            val next = max(value, 0)
            if (abs(field - next) < 1) return
            field = next
            updateStacked()
        }

    private var isStacked = false

    init {
        LayoutInflater.from(context).inflate(R.layout.roomkit_current_room_info, this, true)
        chip = findViewById(R.id.room_info_chip)
        content = findViewById(R.id.room_info_content)
        mainRow = findViewById(R.id.room_info_main_row)
        secondRow = findViewById(R.id.room_info_second_row)
        divider = findViewById(R.id.room_info_divider)
        roomNameText = findViewById(R.id.room_name_text)
        elapsedText = findViewById(R.id.elapsed_text)
        content.setPaddingRelative(0, 0, context.dp(CARET_AREA), 0)
        chip.setOnClickListener {
            onInfoClickListener?.invoke()
                ?: RoomToast.show(context, context.getString(R.string.roomkit_main_feature_coming))
        }
    }

    fun setOnInfoClickListener(listener: (() -> Unit)?) {
        onInfoClickListener = listener
    }

    fun bind(roomID: String) {
        this.roomID = roomID
        if (isAttachedToWindow) observeRoom()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (roomID.isNotBlank()) observeRoom()
        if (timerBaseMillis <= 0) timerBaseMillis = System.currentTimeMillis()
        removeCallbacks(elapsedTicker)
        post(elapsedTicker)
    }

    override fun onDetachedFromWindow() {
        subscribeJob?.cancel()
        subscribeJob = null
        removeCallbacks(elapsedTicker)
        super.onDetachedFromWindow()
    }

    private fun observeRoom() {
        subscribeJob?.cancel()
        subscribeJob = CoroutineScope(Dispatchers.Main).launch {
            roomStore.state.currentRoom.collect { room ->
                val info = room?.takeIf { it.roomID == roomID } ?: return@collect
                render(info)
            }
        }
    }

    private fun updateStacked() {
        val oneLine = measureText(roomNameText) + measureText(elapsedText) + context.dp(INLINE_CHROME)
        setStacked(oneLine > effectiveWidth)
    }

    private val effectiveWidth: Int
        get() = min(availableWidth, (parent as? View)?.width?.takeIf { it > 0 } ?: Int.MAX_VALUE)

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val limit = effectiveWidth
        val size = MeasureSpec.getSize(widthMeasureSpec)
        val spec = if (limit != Int.MAX_VALUE && size > limit) {
            MeasureSpec.makeMeasureSpec(limit, MeasureSpec.AT_MOST)
        } else {
            widthMeasureSpec
        }
        super.onMeasure(spec, heightMeasureSpec)
    }

    private fun setStacked(stacked: Boolean) {
        val flipped = stacked != isStacked
        if (flipped) {
            isStacked = stacked
            divider.visibility = if (stacked) GONE else VISIBLE
            if (stacked) {
                mainRow.removeView(elapsedText)
                secondRow.addView(elapsedText)
                secondRow.visibility = VISIBLE
            } else {
                secondRow.removeView(elapsedText)
                mainRow.addView(elapsedText)
                secondRow.visibility = GONE
            }
            chip.setPaddingRelative(
                context.dp(PADDING_START),
                context.dp(if (stacked) STACKED_VERTICAL else VERTICAL),
                context.dp(if (stacked) STACKED_PADDING_END else PADDING_END),
                context.dp(if (stacked) STACKED_VERTICAL else VERTICAL),
            )
        }
        if (flipped || applyNameLimit()) requestLayout()
    }

    private fun applyNameLimit(): Boolean {
        val width = effectiveWidth
        val limit = if (width == Int.MAX_VALUE) {
            Int.MAX_VALUE
        } else {
            val reserved = context.dp(if (isStacked) STACKED_CHROME else INLINE_CHROME) +
                if (isStacked) 0 else measureText(elapsedText)
            max(0, width - reserved)
        }
        if (roomNameText.maxWidth == limit) return false
        roomNameText.maxWidth = limit
        return true
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        updateStacked()
    }

    private fun measureText(view: AppCompatTextView): Int =
        view.paint.measureText(view.text?.toString().orEmpty()).toInt()

    private fun render(info: RoomInfo) {
        if (info.roomName.isNotEmpty() && roomNameText.text?.toString() != info.roomName) {
            roomNameText.text = info.roomName
            updateStacked()
        }
        val createTime = normalizeToMillis(info.createTime)
        if (createTime > 0 && (timerBaseMillis <= 0 || createTime < timerBaseMillis)) {
            timerBaseMillis = createTime
        }
        updateElapsed()
    }

    private fun normalizeToMillis(raw: Long): Long {
        if (raw <= 0L) return 0L
        return if (raw >= MILLIS_MAGNITUDE) raw else raw * 1000L
    }

    private fun updateElapsed() {
        if (timerBaseMillis <= 0) return
        val totalSeconds = (System.currentTimeMillis() - timerBaseMillis) / 1000L
            .coerceAtLeast(0L)
        val minutes = totalSeconds / SECONDS_PER_MINUTE
        val seconds = totalSeconds % SECONDS_PER_MINUTE
        elapsedText.text = if (minutes < MINUTES_PER_HOUR) {
            String.format("%02d:%02d", minutes, seconds)
        } else {
            String.format(
                "%02d:%02d:%02d",
                minutes / MINUTES_PER_HOUR,
                minutes % MINUTES_PER_HOUR,
                seconds,
            )
        }
        updateStacked()
    }

    private companion object {
        const val SECONDS_PER_MINUTE = 60L
        const val MINUTES_PER_HOUR = 60L
        const val MILLIS_MAGNITUDE = 1_000_000_000_000L

        const val PADDING_START = 6
        const val PADDING_END = 6
        const val VERTICAL = 4
        const val STACKED_PADDING_END = 4
        const val STACKED_VERTICAL = 1
        const val CARET_AREA = 13
        const val INLINE_CHROME = 40
        const val STACKED_CHROME = 23
    }
}