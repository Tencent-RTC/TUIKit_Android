package com.trtc.uikit.roomkit.aitranscription.settingview

import android.content.Context
import android.graphics.Color
import android.graphics.Outline
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.PopupWindow
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.trtc.uikit.roomkit.R
import com.trtc.uikit.roomkit.base.error.ErrorLocalized
import com.trtc.uikit.roomkit.base.log.RoomKitLogger
import com.trtc.uikit.roomkit.base.utils.dpToPx
import com.trtc.uikit.roomkit.aitranscription.repository.AITranscriberRepository
import io.trtc.tuikit.atomicxcore.api.CompletionHandler
import io.trtc.tuikit.atomicxcore.api.login.LoginStore
import io.trtc.tuikit.atomicxcore.api.room.RoomStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

// MARK: - Data Model

enum class SettingRowType { DEFAULT, ALERT_SHEET, TOGGLE }

data class SettingRowData(
    val title: String,
    var detail: String = "",
    val type: SettingRowType,
    var isShowTips: Boolean = false,
    var isOn: Boolean = false,
    var onTap: (() -> Unit)? = null,
    var onToggle: ((Boolean) -> Unit)? = null,
    var onTipsTap: ((View) -> Unit)? = null,
)

// MARK: - Listener

interface AITranscriptionSettingViewListener {
    fun onSettingViewBackClicked()
}

// MARK: - AITranscriptionSettingView

class AITranscriptionSettingView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : ConstraintLayout(context, attrs, defStyleAttr) {

    var listener: AITranscriptionSettingViewListener? = null
    private var repository: AITranscriberRepository? = null
    private val rows = mutableListOf<SettingRowData>()
    private var subscribeJob: Job? = null
    private var settingAdapter: SettingAdapter? = null
    private var isOwner: Boolean = false
    private var tooltipWindow: PopupWindow? = null
    private val handler = Handler(Looper.getMainLooper())

    private val logger = RoomKitLogger.getLogger("AITranscriptionSettingView")

    // UI
    private val backButtonContainer: ConstraintLayout
    private val settingRecyclerView: RecyclerView
    private val loadMaskView: View
    private val loadingProgressBar: android.widget.ProgressBar

    init {
        LayoutInflater.from(context).inflate(R.layout.roomkit_view_ai_transcription_setting, this, true)

        backButtonContainer = findViewById(R.id.cl_back_button)
        settingRecyclerView = findViewById(R.id.rv_settings)
        loadMaskView = findViewById(R.id.view_load_mask)
        loadingProgressBar = findViewById(R.id.pb_loading)

        backButtonContainer.setOnClickListener { listener?.onSettingViewBackClicked() }

        settingRecyclerView.layoutManager = LinearLayoutManager(context)
        settingRecyclerView.clipToOutline = true
        settingRecyclerView.outlineProvider = object : android.view.ViewOutlineProvider() {
            override fun getOutline(view: View, outline: android.graphics.Outline) {
                outline.setRoundRect(0, 0, view.width, view.height, dpToPx(12).toFloat())
            }
        }
    }

    // MARK: - Public

    fun bindRepository(repository: AITranscriberRepository) {
        this.repository = repository
        subscribeJob?.cancel()

        subscribeJob = CoroutineScope(Dispatchers.Main).launch {
            launch {
                repository.selectedSourceLanguage.collect { lang ->
                    if (isOwner && rows.size >= 3) {
                        updateRowDetail(0, repository.displayName(context, lang))
                    }
                }
            }
            launch {
                repository.selectedTranslationLanguage.collect { lang ->
                    if (isOwner && rows.size >= 3) {
                        updateRowDetail(1, repository.displayName(context, lang))
                    }
                }
            }
            launch {
                repository.isBilingualEnabled.collect { isOn ->
                    val toggleIndex = if (isOwner) 2 else 0
                    if (toggleIndex < rows.size) {
                        updateRowToggle(toggleIndex, isOn)
                    }
                }
            }
            launch {
                RoomStore.shared().state.currentRoom.collect { roomInfo ->
                    val ownerUserId = roomInfo?.roomOwner?.userID
                    val selfUserId = LoginStore.shared.loginState.loginUserInfo.value?.userID
                    isOwner = ownerUserId != null && ownerUserId == selfUserId
                    buildRows()
                }
            }
        }

        buildRows()
    }

    // MARK: - Private

    private fun buildRows() {
        val repo = repository ?: return

        rows.clear()
        if (isOwner) {
            rows.add(
                SettingRowData(
                    title = context.getString(R.string.roomkit_transcription_identify_language),
                    detail = repo.displayName(context, repo.selectedSourceLanguage.value),
                    type = SettingRowType.ALERT_SHEET,
                    onTap = { showSourceLanguagePicker() },
                )
            )
            rows.add(
                SettingRowData(
                    title = context.getString(R.string.roomkit_transcription_translate_language),
                    detail = repo.displayName(context, repo.selectedTranslationLanguage.value),
                    type = SettingRowType.ALERT_SHEET,
                    onTap = { showTranslationLanguagePicker() },
                )
            )
            rows.add(
                SettingRowData(
                    title = context.getString(R.string.roomkit_transcription_bilingual_subtitle),
                    type = SettingRowType.TOGGLE,
                    isOn = repo.isBilingualEnabled.value,
                    onToggle = { isOn -> repo.setBilingualEnabled(isOn) },
                )
            )
        } else {
            rows.add(
                SettingRowData(
                    title = context.getString(R.string.roomkit_transcription_bilingual_subtitle),
                    type = SettingRowType.TOGGLE,
                    isOn = repo.isBilingualEnabled.value,
                    onToggle = { isOn -> repo.setBilingualEnabled(isOn) },
                )
            )
        }

        settingAdapter = SettingAdapter(rows)
        settingRecyclerView.adapter = settingAdapter
    }

    private fun showSourceLanguagePicker() {
        val repo = repository ?: return
        val parent = findPickerParent() ?: return

        val items = repo.sourceLanguageList.map { lang ->
            AITranscriptionPickerItem(
                title = repo.displayName(context, lang),
                isSelected = lang == repo.selectedSourceLanguage.value,
            )
        }

        val picker = AITranscriptionPickerView(context, context.getString(R.string.roomkit_transcription_select_recognition_language), items) { index, _ ->
            val selectedLang = repo.sourceLanguageList[index]
            showLoading()
            repo.updateTranscriptionSourceLanguage(selectedLang, completion = object :
                CompletionHandler {
                override fun onSuccess() {
                    logger.info("updateTranscription sourceLanguage success")
                    hideLoading()
                }

                override fun onFailure(code: Int, desc: String) {
                    logger.error("updateTranscription failed, code=$code, desc=$desc")
                    hideLoading()
                }
            })
        }
        picker.show(parent, animated = true)
    }

    private fun showTranslationLanguagePicker() {
        val repo = repository ?: return
        val parent = findPickerParent() ?: return

        val items = repo.translationLanguageList.map { lang ->
            AITranscriptionPickerItem(
                title = repo.displayName(context, lang),
                isSelected = lang == repo.selectedTranslationLanguage.value,
            )
        }

        val picker = AITranscriptionPickerView(context, context.getString(R.string.roomkit_transcription_select_translation_language), items) { index, _ ->
            val selectedLang = repo.translationLanguageList[index]
            showLoading()
            repo.updateTranscriptionTranslationLanguage(selectedLang, completion = object :
                CompletionHandler {
                override fun onSuccess() {
                    logger.info("updateTranscription translationLanguage success")
                    hideLoading()
                }

                override fun onFailure(code: Int, desc: String) {
                    logger.error("updateTranscription failed, code=$code, desc=$desc")
                    hideLoading()
                }
            })
        }
        picker.show(parent, animated = true)
    }

     private fun findPickerParent(): ViewGroup? {
        var p: View? = parent as? View
        while (p != null) {
            if (p.parent == null || p.parent !is View) return p as? ViewGroup
            p = p.parent as? View
        }
        return null
    }

    private fun updateRowDetail(index: Int, detail: String) {
        if (index >= rows.size) return
        rows[index].detail = detail
        settingAdapter?.notifyItemChanged(index)
    }

    private fun updateRowToggle(index: Int, isOn: Boolean) {
        if (index >= rows.size) return
        rows[index].isOn = isOn
        settingAdapter?.notifyItemChanged(index)
    }

    // MARK: - Tooltip

    private fun showTooltip(text: String, anchorView: View) {
        dismissTooltip()

        val padding = dpToPx(16)
        val cornerRadius = dpToPx(8).toFloat()
        val arrowWidth = dpToPx(12)
        val arrowHeight = dpToPx(6)
        val shadowPadding = dpToPx(16)

        // Bubble
        val textView = TextView(context).apply {
            this.text = text
            textSize = 14f
            setTextColor(Color.parseColor("#FF0F1014"))
        }

        val bubbleBackground = GradientDrawable().apply {
            setColor(Color.WHITE)
            this.cornerRadius = cornerRadius
        }

        val bubbleView = FrameLayout(context).apply {
            background = bubbleBackground
            setPadding(padding, padding, padding, padding)
            elevation = dpToPx(8).toFloat()
            outlineProvider = object : android.view.ViewOutlineProvider() {
                override fun getOutline(view: View, outline: android.graphics.Outline) {
                    outline.setRoundRect(0, 0, view.width, view.height, cornerRadius)
                }
            }
            clipToOutline = false
            addView(textView)
        }

        // Arrow (triangle pointing down, with shadow)
        val arrowView = object : View(context) {
            private val fillPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                style = android.graphics.Paint.Style.FILL
            }
            private val shadowPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                style = android.graphics.Paint.Style.FILL
                setShadowLayer(dpToPx(8).toFloat(), 0f, dpToPx(2).toFloat(), ContextCompat.getColor(context, R.color.roomkit_color_ai_setting_shadow))
            }

            init {
                setLayerType(LAYER_TYPE_SOFTWARE, null)
            }

            override fun onDraw(canvas: android.graphics.Canvas) {
                super.onDraw(canvas)
                val path = android.graphics.Path().apply {
                    moveTo(0f, 0f)
                    lineTo(width / 2f, height.toFloat())
                    lineTo(width.toFloat(), 0f)
                    close()
                }
                canvas.drawPath(path, shadowPaint)
                canvas.drawPath(path, fillPaint)
            }
        }

        // Wrapper with extra padding for shadow space
        val wrapper = FrameLayout(context).apply {
            clipChildren = false
            clipToPadding = false
            setPadding(shadowPadding, shadowPadding, shadowPadding, shadowPadding)
        }

        // Measure bubble to get its width
        val maxTextWidth = width - dpToPx(32)
        textView.measure(
            MeasureSpec.makeMeasureSpec(maxTextWidth - 2 * padding, MeasureSpec.AT_MOST),
            MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED),
        )
        val textWidth = textView.measuredWidth
        val textHeight = textView.measuredHeight
        val bubbleWidth = textWidth + 2 * padding
        val bubbleHeight = textHeight + 2 * padding

        // Add bubble to wrapper
        val bubbleLp = FrameLayout.LayoutParams(bubbleWidth, bubbleHeight).apply {
            leftMargin = 0
            topMargin = 0
        }
        wrapper.addView(bubbleView, bubbleLp)

        // Calculate anchor position
        val location = IntArray(2)
        anchorView.getLocationOnScreen(location)
        val anchorCenterX = location[0] + anchorView.width / 2

        // Tooltip X: center bubble on anchor, clamp to screen edges
        val screenWidth = context.resources.displayMetrics.widthPixels
        var tooltipX = anchorCenterX - bubbleWidth / 2
        tooltipX = tooltipX.coerceIn(dpToPx(16), screenWidth - bubbleWidth - dpToPx(16))

        // Arrow position relative to bubble
        val arrowCenterX = anchorCenterX - tooltipX
        val arrowLeft = (arrowCenterX - arrowWidth / 2).coerceIn(dpToPx(8), bubbleWidth - arrowWidth - dpToPx(8))

        val arrowLp = FrameLayout.LayoutParams(arrowWidth, arrowHeight).apply {
            leftMargin = arrowLeft
            topMargin = bubbleHeight - dpToPx(1) // overlap 1dp to cover bubble shadow edge
        }
        wrapper.addView(arrowView, arrowLp)
        arrowView.translationZ = dpToPx(10).toFloat() // above bubble elevation to prevent shadow covering

        val totalContentHeight = bubbleHeight + arrowHeight - dpToPx(1)
        val wrapperWidth = bubbleWidth + 2 * shadowPadding
        val wrapperHeight = totalContentHeight + 2 * shadowPadding

        val popup = PopupWindow(wrapper, wrapperWidth, wrapperHeight, false).apply {
            isOutsideTouchable = true
            isTouchable = true
            setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.TRANSPARENT))
        }

        val xOffset = tooltipX - shadowPadding
        val yOffset = location[1] - totalContentHeight - dpToPx(4) - shadowPadding

        popup.showAtLocation(anchorView, Gravity.NO_GRAVITY, xOffset, yOffset)
        tooltipWindow = popup

        handler.postDelayed({ dismissTooltip() }, 3000)
    }

    private fun dismissTooltip() {
        tooltipWindow?.dismiss()
        tooltipWindow = null
        handler.removeCallbacksAndMessages(null)
    }

    private fun showLoading() {
        loadMaskView.visibility = View.VISIBLE
        loadingProgressBar.visibility = View.VISIBLE
    }

    private fun hideLoading() {
        loadMaskView.visibility = View.GONE
        loadingProgressBar.visibility = View.GONE
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        subscribeJob?.cancel()
        dismissTooltip()
    }
}

// MARK: - SettingAdapter

private class SettingAdapter(
    private val rows: List<SettingRowData>,
) : RecyclerView.Adapter<SettingAdapter.VH>() {

    class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val titleLabel: TextView = itemView.findViewById(R.id.tv_title)
        val detailLabel: TextView = itemView.findViewById(R.id.tv_detail)
        val arrowView: ImageView = itemView.findViewById(R.id.iv_arrow)
        val tipsView: ImageView = itemView.findViewById(R.id.iv_tips)
        val toggleSwitch: ImageView = itemView.findViewById(R.id.iv_toggle)
        val separator: View = itemView.findViewById(R.id.view_separator)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.roomkit_item_ai_setting_row, parent, false)
        return VH(view)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val data = rows[position]
        holder.titleLabel.text = data.title
        holder.separator.visibility = if (position < rows.size - 1) View.VISIBLE else View.GONE
        holder.tipsView.visibility = if (data.isShowTips) View.VISIBLE else View.GONE
        holder.tipsView.setOnClickListener { view -> data.onTipsTap?.invoke(view) }

        val detailLayoutParams = holder.detailLabel.layoutParams as ConstraintLayout.LayoutParams
        when (data.type) {
            SettingRowType.DEFAULT -> {
                holder.detailLabel.text = data.detail
                holder.detailLabel.visibility = View.VISIBLE
                holder.detailLabel.setTextColor(Color.parseColor("#FF99A2B2"))
                holder.arrowView.visibility = View.GONE
                holder.toggleSwitch.visibility = View.GONE
                holder.itemView.setOnClickListener(null)

                detailLayoutParams.endToStart = ConstraintLayout.LayoutParams.UNSET
                detailLayoutParams.endToEnd = ConstraintLayout.LayoutParams.PARENT_ID
                detailLayoutParams.marginEnd = holder.itemView.dpToPx(16)
                holder.detailLabel.layoutParams = detailLayoutParams
            }
            SettingRowType.ALERT_SHEET -> {
                holder.detailLabel.text = data.detail
                holder.detailLabel.visibility = View.VISIBLE
                holder.detailLabel.setTextColor(Color.parseColor("#FF4E5461"))
                holder.arrowView.visibility = View.VISIBLE
                holder.toggleSwitch.visibility = View.GONE
                holder.itemView.setOnClickListener { data.onTap?.invoke() }

                detailLayoutParams.endToEnd = ConstraintLayout.LayoutParams.UNSET
                detailLayoutParams.endToStart = R.id.iv_arrow
                detailLayoutParams.marginEnd = 0
                holder.detailLabel.layoutParams = detailLayoutParams
            }
            SettingRowType.TOGGLE -> {
                holder.detailLabel.visibility = View.GONE
                holder.arrowView.visibility = View.GONE
                holder.toggleSwitch.visibility = View.VISIBLE
                holder.toggleSwitch.setImageResource(
                    if (data.isOn) R.drawable.roomkit_ic_switch_on else R.drawable.roomkit_ic_switch_off
                )
                holder.toggleSwitch.setOnClickListener {
                    val newState = !data.isOn
                    data.isOn = newState
                    holder.toggleSwitch.setImageResource(
                        if (newState) R.drawable.roomkit_ic_switch_on else R.drawable.roomkit_ic_switch_off
                    )
                    data.onToggle?.invoke(newState)
                }
                holder.itemView.setOnClickListener(null)
            }
        }
    }

    override fun getItemCount(): Int = rows.size
}
