package com.trtc.uikit.livekit.component.gift

import android.content.Context
import android.text.TextUtils
import android.util.AttributeSet
import android.util.TypedValue
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.lifecycleScope
import com.trtc.uikit.livekit.R
import com.trtc.uikit.livekit.common.LiveKitLogger
import com.trtc.uikit.livekit.common.LiveKitLogger.Companion.getComponentLogger
import com.trtc.uikit.livekit.common.completionHandler
import com.trtc.uikit.livekit.common.reportEventData
import com.trtc.uikit.livekit.component.gift.viewmodel.GiftConstants
import com.trtc.uikit.livekit.component.gift.viewmodel.GiftConstants.LANGUAGE_EN
import com.trtc.uikit.livekit.component.gift.viewmodel.GiftConstants.LANGUAGE_ZH_HANS
import com.trtc.uikit.livekit.component.gift.viewmodel.GiftConstants.LANGUAGE_ZH_HANT
import com.trtc.uikit.livekit.component.gift.view.GiftCategoryViewPagerManager
import com.trtc.uikit.livekit.component.gift.view.GiftTabLayoutManager
import com.trtc.uikit.livekit.component.gift.view.cell.GiftBaseCell
import com.trtc.uikit.livekit.component.gift.view.cell.GiftCellListener
import io.trtc.tuikit.atomicxcore.api.gift.Gift
import io.trtc.tuikit.atomicxcore.api.gift.GiftCategory
import io.trtc.tuikit.atomicxcore.api.gift.GiftStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.abs

class GiftListView : LinearLayout, LifecycleOwner, GiftCellListener {
    private val logger: LiveKitLogger = getComponentLogger("GiftListPanel")
    private var giftTabLayoutManager: GiftTabLayoutManager? = null
    private var roomId: String = ""
    private var adapter: GiftListViewAdapter? = null
    private var giftStore: GiftStore? = null
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val collectJobs = mutableListOf<Job>()
    private var isObserverAdded = false

    private var giftCategories: List<GiftCategory> = emptyList()
    private var currentSelectedCategoryIndex: Int = 0

    private var gestureDetector: GestureDetector? = null

    private lateinit var topContainer: FrameLayout
    private lateinit var headerRightContainer: FrameLayout
    private lateinit var bottomContainer: FrameLayout

    constructor(context: Context) : this(context, null)

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {
        orientation = VERTICAL
        setupUIContainers()
        setupGestureDetector()
    }

    private fun setupUIContainers() {
        val topContainerHeight = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, TOP_CONTAINER_HEIGHT_DP.toFloat(), resources.displayMetrics
        ).toInt()

        topContainer = FrameLayout(context).apply {
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, topContainerHeight)
        }
        addView(topContainer)

        headerRightContainer = FrameLayout(context).apply {
            layoutParams =
                FrameLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
                    .apply {
                        gravity = Gravity.END or Gravity.CENTER_VERTICAL
                        marginEnd = TypedValue.applyDimension(
                            TypedValue.COMPLEX_UNIT_DIP, 12f, resources.displayMetrics
                        ).toInt()
                    }
        }
        topContainer.addView(headerRightContainer)

        bottomContainer = FrameLayout(context).apply {
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
        }
        addView(bottomContainer)
    }

    private fun setupGestureDetector() {
        gestureDetector =
            GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
                override fun onFling(
                    e1: MotionEvent?,
                    e2: MotionEvent,
                    velocityX: Float,
                    velocityY: Float
                ): Boolean {
                    if (e1 == null || giftCategories.isEmpty()) return false

                    val deltaX = e2.x - e1.x
                    val deltaY = e2.y - e1.y

                    if (abs(deltaX) > abs(deltaY) && abs(deltaX) > 100) {
                        if (deltaX > 0) {
                            selectPreviousCategory()
                        } else {
                            selectNextCategory()
                        }
                        return true
                    }
                    return false
                }
            })
    }

    override fun onTouchEvent(event: MotionEvent?): Boolean {
        event?.let { gestureDetector?.onTouchEvent(it) }
        return super.onTouchEvent(event)
    }

    private fun selectNextCategory() {
        if (giftCategories.isNotEmpty()) {
            val nextIndex = minOf(currentSelectedCategoryIndex + 1, giftCategories.size - 1)
            if (nextIndex != currentSelectedCategoryIndex) {
                selectCategory(nextIndex)
            }
        }
    }

    private fun selectPreviousCategory() {
        if (giftCategories.isNotEmpty()) {
            val prevIndex = maxOf(currentSelectedCategoryIndex - 1, 0)
            if (prevIndex != currentSelectedCategoryIndex) {
                selectCategory(prevIndex)
            }
        }
    }

    private fun selectCategory(index: Int) {
        if (index < 0 || index >= giftCategories.size) return
        currentSelectedCategoryIndex = index
        giftTabLayoutManager?.selectCategory(index)
    }

    fun init(roomId: String) {
        if (roomId.isEmpty()) {
            return
        }
        this.roomId = roomId
        initStore()
        setupLifecycleIfNeeded()
    }

    fun setAdapter(adapter: GiftListViewAdapter?) {
        this.adapter = adapter
        refreshSlots()
    }

    private fun refreshSlots() {
        headerRightContainer.removeAllViews()
        adapter?.onCreateHeaderRightView(this)?.let { rightView ->
            headerRightContainer.addView(rightView)
        }

        bottomContainer.removeAllViews()
        adapter?.onCreateBottomView(this)?.let { bottomView ->
            bottomContainer.addView(bottomView)
        }
    }

    private fun getLanguage(): String {
        val language = Locale.getDefault().getLanguage()
        var languageTag = Locale.getDefault().toLanguageTag()
        if (TextUtils.isEmpty(language) || TextUtils.isEmpty(languageTag)) {
            return LANGUAGE_EN
        }
        languageTag = languageTag.lowercase(Locale.getDefault())
        if ("zh".equals(language, ignoreCase = true)) {
            if (languageTag.contains("zh-hans")
                || languageTag == "zh"
                || languageTag == "zh-cn"
                || languageTag == "zh-sg"
                || languageTag == "zh-my"
            ) {
                return LANGUAGE_ZH_HANS
            } else {
                return LANGUAGE_ZH_HANT
            }
        } else {
            return LANGUAGE_EN
        }
    }

    private fun initStore() {
        giftStore = GiftStore.create(roomId)
        giftStore?.setLanguage(getLanguage())
        giftStore?.refreshUsableGifts(null)
    }

    private fun addObserver() {
        launch {
            giftStore?.giftState?.usableGifts?.collect {
                onGiftListChange(it)
            }
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        setupLifecycleIfNeeded()
    }

    override fun onDetachedFromWindow() {
        collectJobs.forEach { it.cancel() }
        collectJobs.clear()
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        isObserverAdded = false
        super.onDetachedFromWindow()
    }

    override val lifecycle: Lifecycle get() = lifecycleRegistry

    private fun setGiftCategoryList(categoryList: List<GiftCategory>) {
        if (categoryList.isEmpty()) {
            logger.error("setGiftCategoryList categoryList is empty")
            return
        }

        giftCategories = categoryList

        if (currentSelectedCategoryIndex >= categoryList.size) {
            currentSelectedCategoryIndex = 0
        }

        val childCount = childCount
        for (i in childCount - 1 downTo 0) {
            val child = getChildAt(i)
            if (child != topContainer && child != bottomContainer) {
                removeViewAt(i)
            }
        }

        for (i in topContainer.childCount - 1 downTo 0) {
            val child = topContainer.getChildAt(i)
            if (child != headerRightContainer) {
                topContainer.removeViewAt(i)
            }
        }

        if (giftTabLayoutManager == null) {
            giftTabLayoutManager = GiftTabLayoutManager(context)
            giftTabLayoutManager?.cellFactory = { parent, gift ->
                adapter?.onCreateGiftCell(this, parent, gift)
            }
            giftTabLayoutManager?.onGiftSelectedListener = { gift ->
                adapter?.onGiftSelected(this, gift)
            }
            giftTabLayoutManager?.setGiftClickListener(object :
                GiftCategoryViewPagerManager.GiftClickListener {
                override fun onClick(position: Int, gift: Gift, count: Int) {
                    sendGift(gift, count)
                }
            })
        }

        val tabLayout = giftTabLayoutManager!!.createLayout(categoryList, COLUMNS, ROWS)

        val tabContainer = tabLayout.findViewById<LinearLayout>(R.id.ll_tab_container)
        val divider = tabLayout.findViewById<View>(R.id.view_divider)
        val contentContainer = tabLayout.findViewById<FrameLayout>(R.id.fl_content_container)

        (tabLayout as? LinearLayout)?.let { parent ->
            parent.removeView(tabContainer)
            parent.removeView(divider)
            parent.removeView(contentContainer)
        }

        topContainer.addView(
            tabContainer,
            0,
            FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT).apply {
                marginStart = TypedValue.applyDimension(
                    TypedValue.COMPLEX_UNIT_DIP, 10f, resources.displayMetrics
                ).toInt()
                marginEnd = TypedValue.applyDimension(
                    TypedValue.COMPLEX_UNIT_DIP, 10f, resources.displayMetrics
                ).toInt()
            }
        )

        val bottomIndex = indexOfChild(bottomContainer)
        val dividerHeight = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, 1f, resources.displayMetrics
        ).toInt()
        addView(divider, bottomIndex, LayoutParams(LayoutParams.MATCH_PARENT, dividerHeight))
        addView(
            contentContainer,
            bottomIndex + 1,
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                setMargins(
                    TypedValue.applyDimension(
                        TypedValue.COMPLEX_UNIT_DIP,
                        10f,
                        resources.displayMetrics
                    ).toInt(),
                    TypedValue.applyDimension(
                        TypedValue.COMPLEX_UNIT_DIP,
                        16f,
                        resources.displayMetrics
                    ).toInt(),
                    TypedValue.applyDimension(
                        TypedValue.COMPLEX_UNIT_DIP,
                        10f,
                        resources.displayMetrics
                    ).toInt(),
                    0
                )
            })
    }

    private fun setupLifecycleIfNeeded() {
        if (roomId.isEmpty()) {
            return
        }
        if (!isObserverAdded) {
            lifecycleRegistry.currentState = Lifecycle.State.STARTED
            addObserver()
            isObserverAdded = true
        }
    }

    fun sendGift(gift: Gift, giftCount: Int) {
        giftStore?.sendGift(gift.giftID, giftCount, completionHandler {
            onSuccess {
                adapter?.onGiftSent(this@GiftListView, gift, giftCount, Result.success(Unit))
            }
            onError { code, message ->
                adapter?.onGiftSent(
                    this@GiftListView,
                    gift,
                    giftCount,
                    Result.failure(Exception(message ?: "Error code: $code"))
                )
            }
        })

        if (!TextUtils.isEmpty(gift.resourceURL)) {
            val isSvgGift = gift.resourceURL.lowercase(Locale.getDefault()).endsWith(".svga")
            val key = getReportKey(isSvgGift)
            reportEventData(key)
        }
    }

    override fun onSendGift(
        cell: GiftBaseCell,
        gift: Gift,
        count: Int
    ) {
        sendGift(gift, count)
    }

    private fun onGiftListChange(list: List<GiftCategory>) {
        if (!TextUtils.isEmpty(roomId)) {
            setGiftCategoryList(list)
        }
    }

    private fun getReportKey(isSvgGift: Boolean): Int {
        val isVoiceRoom = !TextUtils.isEmpty(roomId) && roomId!!.startsWith("voice_")
        val key: Int
        if (isVoiceRoom) {
            key = if (isSvgGift)
                GiftConstants.DATA_REPORT_VOICE_GIFT_SVGA_SEND_COUNT
            else
                GiftConstants.DATA_REPORT_VOICE_GIFT_EFFECT_SEND_COUNT
        } else {
            key = if (isSvgGift)
                GiftConstants.DATA_REPORT_LIVE_GIFT_SVGA_SEND_COUNT
            else
                GiftConstants.DATA_REPORT_LIVE_GIFT_EFFECT_SEND_COUNT
        }
        return key
    }

    private fun launch(block: suspend CoroutineScope.() -> Unit): Job {
        return lifecycleScope.launch(block = block).also { job ->
            collectJobs.add(job)
        }
    }

    companion object {
        private const val COLUMNS = 4
        private const val ROWS = 2
        private const val TOP_CONTAINER_HEIGHT_DP = 44
    }
}

interface GiftListViewAdapter {
    fun onCreateGiftCell(view: GiftListView, parent: ViewGroup, gift: Gift): GiftBaseCell? = null
    fun onCreateHeaderRightView(view: GiftListView): View? = null
    fun onCreateBottomView(view: GiftListView): View? = null
    fun onGiftSelected(view: GiftListView, gift: Gift) {}
    fun onGiftSent(view: GiftListView, gift: Gift, count: Int, result: Result<Unit>) {}
}
