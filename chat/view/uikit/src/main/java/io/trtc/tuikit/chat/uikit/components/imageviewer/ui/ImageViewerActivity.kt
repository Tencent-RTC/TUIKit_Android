package io.trtc.tuikit.chat.uikit.components.imageviewer.ui
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.ColorUtils
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import io.trtc.tuikit.atomicx.common.permission.PermissionCallback
import io.trtc.tuikit.chat.uikit.R
import io.trtc.tuikit.chat.uikit.components.common.ChatPermissionHelper
import io.trtc.tuikit.chat.uikit.components.common.expandTouchTarget
import io.trtc.tuikit.chat.uikit.components.imageviewer.ImageElement
import io.trtc.tuikit.chat.uikit.components.imageviewer.ImageViewer
import io.trtc.tuikit.chat.uikit.components.imageviewer.ImageViewerBoundaryLoadState
import io.trtc.tuikit.chat.uikit.components.imageviewer.ImageViewerBoundaryLoadPolicy
import io.trtc.tuikit.chat.uikit.components.imageviewer.ImageViewerGallerySaver
import io.trtc.tuikit.chat.uikit.components.imageviewer.ImageViewerMediaIdentity
import io.trtc.tuikit.chat.uikit.components.imageviewer.ImageViewerOverlayInsetPolicy
import io.trtc.tuikit.chat.uikit.components.imageviewer.ImageViewerSafeAreaInsets
import io.trtc.tuikit.chat.uikit.components.imageviewer.ImageViewerSessionState
import io.trtc.tuikit.chat.uikit.components.imageviewer.ImageViewerVideoPageRefreshPolicy
import io.trtc.tuikit.chat.uikit.components.imageviewer.ImageViewerVideoPageReleasePolicy
import io.trtc.tuikit.atomicx.theme.ThemeStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlin.math.max

class ImageViewerActivity : AppCompatActivity() {

    private lateinit var rootView: FrameLayout
    private lateinit var viewPager: ViewPager2
    private lateinit var adapter: ImageViewerPagerAdapter
    private lateinit var loadingOverlay: LinearLayout
    private lateinit var loadingText: TextView
    private lateinit var loadingProgress: ProgressBar
    private lateinit var toastView: TextView
    private lateinit var saveButton: ImageView

    private val mainHandler = Handler(Looper.getMainLooper())
    private val showLoadingRunnable = Runnable { showLoadingIndicator() }
    private val hideToastRunnable = Runnable { hideToast() }
    private val loadMoreTimeoutRunnable = Runnable { finishAllLoadMore(hasNew = false) }

    private var currentPage = 0
    private var currentLogicalItem: ImageElement? = null
    private var hasAppliedInitialPage = false
    private var session: ImageViewer.Session? = null
    private var activeForCallbacks = true
    private var shouldResumeInlineVideoPlayback = false
    private var currentSafeAreaInsets = ImageViewerSafeAreaInsets()
    private val boundaryLoadState = ImageViewerBoundaryLoadState()
    private val boundaryLoadPolicy = ImageViewerBoundaryLoadPolicy()

    private val pageChangeCallback = object : ViewPager2.OnPageChangeCallback() {
        override fun onPageSelected(position: Int) {
            currentPage = position
            currentLogicalItem = adapter.getItemOrNull(position)
            releaseNonCurrentVideoPages(position)
            ensureCurrentVideoPagePlayerReady(position)
            updateSaveButtonVisibility()
            triggerBoundaryEventIfNeeded(
                position = position,
                itemCount = adapter.itemCount,
                showNoMoreToast = false,
                allowSameItemCount = false
            )
        }

        override fun onPageScrollStateChanged(state: Int) {
            when (state) {
                ViewPager2.SCROLL_STATE_DRAGGING -> {
                    boundaryLoadPolicy.markDragStarted(currentPage, adapter.itemCount)
                }

                ViewPager2.SCROLL_STATE_IDLE -> {
                    if (boundaryLoadPolicy.shouldDispatchBoundaryLoad(currentPage, adapter.itemCount)) {
                        triggerBoundaryEventIfNeeded(
                            position = currentPage,
                            itemCount = adapter.itemCount,
                            showNoMoreToast = true,
                            allowSameItemCount = true
                        )
                    }
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes = window.attributes.apply {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
        setContentView(R.layout.image_viewer_activity)

        val sessionId = intent.getLongExtra(ImageViewer.EXTRA_SESSION_ID, -1L)
        session = ImageViewer.acquireSession(sessionId)
        if (session == null) {
            session = ImageViewer.Session(
                id = -1L,
                eventHandler = ImageViewer.eventHandler,
                initDataInternal = ImageViewer.initDataInternal,
                mediaListFlow = ImageViewer.mediaList
            )
        }

        bindViews()
        installOverlays()
        installSystemBarInsets()
        enterImmersiveMode()
        setupPager()
        observeThemeChanges()
        observeMediaList()
    }

    override fun onStart() {
        super.onStart()
        if (shouldResumeInlineVideoPlayback) {
            currentVideoPageView()?.resumeInlinePlayback()
            shouldResumeInlineVideoPlayback = false
        }
    }

    override fun onStop() {
        shouldResumeInlineVideoPlayback = currentVideoPageView()?.pauseInlinePlaybackForHostStop() == true
        super.onStop()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            enterImmersiveMode()
        }
    }

    override fun onDestroy() {
        activeForCallbacks = false
        releaseAllVideoPages()
        viewPager.unregisterOnPageChangeCallback(pageChangeCallback)
        mainHandler.removeCallbacks(showLoadingRunnable)
        mainHandler.removeCallbacks(hideToastRunnable)
        mainHandler.removeCallbacks(loadMoreTimeoutRunnable)
        val current = session
        if (isFinishing && !isChangingConfigurations) {
            if (current != null && current.id >= 0) {
                ImageViewer.releaseSession(current.id)
            } else {
                ImageViewer.clearRuntimeState()
            }
        }
        super.onDestroy()
    }

    private fun bindViews() {
        rootView = findViewById(R.id.image_viewer_root)
        viewPager = findViewById(R.id.image_viewer_pager)
    }

    private fun installOverlays() {
        loadingProgress = ProgressBar(this).apply {
            isIndeterminate = true
            indeterminateTintList = ColorStateList.valueOf(Color.WHITE)
        }
        loadingText = TextView(this).apply {
            text = getString(R.string.image_viewer_loading)
            setTextColor(Color.WHITE)
            textSize = 14f
        }
        loadingOverlay = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dpToPx(20), dpToPx(20), dpToPx(20), dpToPx(20))
            visibility = View.GONE
            addView(
                loadingProgress,
                LinearLayout.LayoutParams(dpToPx(36), dpToPx(36))
            )
            addView(
                loadingText,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = dpToPx(12) }
            )
            background = GradientDrawable().apply {
                cornerRadius = dpToPx(12).toFloat()
                setColor(ColorUtils.setAlphaComponent(Color.BLACK, 179))
            }
        }
        rootView.addView(
            loadingOverlay,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
            )
        )

        toastView = TextView(this).apply {
            setTextColor(Color.WHITE)
            textSize = 14f
            setPadding(dpToPx(16), dpToPx(10), dpToPx(16), dpToPx(10))
            background = GradientDrawable().apply {
                cornerRadius = dpToPx(20).toFloat()
                setColor(ColorUtils.setAlphaComponent(Color.BLACK, 204))
            }
            visibility = View.GONE
        }
        rootView.addView(
            toastView,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER_HORIZONTAL or Gravity.BOTTOM
            ).apply { bottomMargin = dpToPx(TOAST_BASE_BOTTOM_MARGIN_DP) }
        )

        saveButton = ImageView(this).apply {
            setImageResource(R.drawable.image_viewer_save_button)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            visibility = View.GONE
            contentDescription = getString(R.string.image_viewer_save_to_album)
            setOnClickListener { handleSaveCurrentMedia() }
        }
        rootView.addView(
            saveButton,
            FrameLayout.LayoutParams(
                dpToPx(SAVE_BUTTON_SIZE_DP),
                dpToPx(SAVE_BUTTON_SIZE_DP),
                Gravity.BOTTOM or Gravity.END
            ).apply {
                marginEnd = dpToPx(SAVE_BUTTON_BASE_END_MARGIN_DP)
                bottomMargin = dpToPx(SAVE_BUTTON_BASE_BOTTOM_MARGIN_DP)
            }
        )
        saveButton.expandTouchTarget()
    }

    private fun installSystemBarInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(rootView) { _, insets ->
            val systemBars = insets.getInsetsIgnoringVisibility(WindowInsetsCompat.Type.systemBars())
            val displayCutout = insets.getInsets(WindowInsetsCompat.Type.displayCutout())
            applyOverlayInsets(
                ImageViewerSafeAreaInsets(
                    left = max(systemBars.left, displayCutout.left),
                    top = max(systemBars.top, displayCutout.top),
                    right = max(systemBars.right, displayCutout.right),
                    bottom = max(systemBars.bottom, displayCutout.bottom)
                )
            )
            insets
        }
        ViewCompat.requestApplyInsets(rootView)
    }

    private fun applyOverlayInsets(insets: ImageViewerSafeAreaInsets) {
        currentSafeAreaInsets = insets
        updateSaveButtonLayout(insets)
        updateToastLayout(insets)
    }

    private fun updateSaveButtonLayout(insets: ImageViewerSafeAreaInsets = currentSafeAreaInsets) {
        val isRtl = rootView.layoutDirection == View.LAYOUT_DIRECTION_RTL
        val saveButtonParams = saveButton.layoutParams as FrameLayout.LayoutParams
        saveButtonParams.bottomMargin = ImageViewerOverlayInsetPolicy.bottomMargin(
            baseBottom = dpToPx(SAVE_BUTTON_BASE_BOTTOM_MARGIN_DP),
            insets = insets
        )
        saveButtonParams.marginEnd = ImageViewerOverlayInsetPolicy.endMargin(
            baseEnd = dpToPx(SAVE_BUTTON_BASE_END_MARGIN_DP),
            insets = insets,
            isRtl = isRtl
        )
        saveButton.layoutParams = saveButtonParams
    }

    private fun updateToastLayout(insets: ImageViewerSafeAreaInsets = currentSafeAreaInsets) {
        val toastParams = toastView.layoutParams as FrameLayout.LayoutParams
        toastParams.bottomMargin = ImageViewerOverlayInsetPolicy.bottomMargin(
            baseBottom = dpToPx(TOAST_BASE_BOTTOM_MARGIN_DP),
            insets = insets
        )
        toastView.layoutParams = toastParams
    }

    private fun enterImmersiveMode() {
        val controller = WindowCompat.getInsetsController(window, rootView)
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.systemBars())
    }

    private fun setupPager() {
        adapter = ImageViewerPagerAdapter(
            onImageTap = {
                dispatchImageTap()
                finish()
            },
            onCloseRequested = { finish() },
            onDownloadRequested = { element -> handleDownloadRequested(element) },
            resolveVideoData = { element -> resolveEffectiveVideoData(element) },
            isDownloading = { element -> isElementDownloading(element) }
        )
        viewPager.layoutDirection = View.LAYOUT_DIRECTION_LOCALE
        viewPager.offscreenPageLimit = 1
        viewPager.adapter = adapter
        viewPager.registerOnPageChangeCallback(pageChangeCallback)
    }

    private fun observeThemeChanges() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                ThemeStore.shared(this@ImageViewerActivity).themeState.collectLatest { state ->
                    rootView.setBackgroundColor(state.currentTheme.tokens.color.bgColorMask)
                    adapter.notifyDataSetChanged()
                }
            }
        }
    }

    private fun observeMediaList() {
        val current = session ?: return
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                current.mediaListFlow.collectLatest { mediaList ->
                    applyMediaList(mediaList)
                }
            }
        }
    }

    private fun applyMediaList(mediaList: List<ImageElement>) {
        val current = session
        val previousItem = currentLogicalItem ?: current?.initDataInternal
        val previousIndex = viewPager.currentItem
        val previousSize = adapter.itemCount
        val isResponseToLoadMore = boundaryLoadState.hasActiveLoad()
        Log.d(DIAG_TAG, "applyMediaList size=${mediaList.size} previousSize=$previousSize " +
            "previousIndex=$previousIndex activeLoad=$isResponseToLoadMore " +
            "videos=${mediaList.count { it.type == MEDIA_TYPE_VIDEO }} " +
            "playableVideos=${mediaList.count { it.type == MEDIA_TYPE_VIDEO && it.videoData != null }}")
        adapter.submitItems(mediaList)
        if (mediaList.isEmpty()) {
            currentPage = 0
            currentLogicalItem = null
            updateSaveButtonVisibility()
            if (isResponseToLoadMore) {
                finishLoadMore(hasNew = false)
            }
            return
        }
        val targetIndex = if (!hasAppliedInitialPage) {
            ImageViewerSessionState.findBestIndex(
                mediaList = mediaList,
                previousItem = current?.initDataInternal,
                previousIndex = 0
            )
        } else {
            ImageViewerSessionState.findBestIndex(
                mediaList = mediaList,
                previousItem = previousItem,
                previousIndex = previousIndex
            )
        }
        currentPage = targetIndex
        currentLogicalItem = mediaList.getOrNull(targetIndex)
        val shouldMovePager = !hasAppliedInitialPage || viewPager.currentItem != targetIndex
        Log.d(DIAG_TAG, "applyMediaList targetIndex=$targetIndex shouldMovePager=$shouldMovePager " +
            "currentItem=${viewPager.currentItem} target=${describeElement(currentLogicalItem)}")
        hasAppliedInitialPage = true
        if (shouldMovePager) {
            viewPager.post {
                if (!isDestroyed) {
                    viewPager.setCurrentItem(targetIndex, false)
                }
            }
        }
        if (isResponseToLoadMore) {
            finishLoadMore(hasNew = mediaList.size != previousSize)
        }
        updateSaveButtonVisibility()
    }

    private fun triggerBoundaryEventIfNeeded(
        position: Int,
        itemCount: Int,
        showNoMoreToast: Boolean,
        allowSameItemCount: Boolean
    ) {
        boundaryLoadState.eventsForPosition(position, itemCount).forEach { eventName ->
            dispatchBoundaryEvent(
                eventName = eventName,
                itemCount = itemCount,
                showNoMoreToast = showNoMoreToast,
                allowSameItemCount = allowSameItemCount
            )
        }
    }

    private fun dispatchBoundaryEvent(
        eventName: String,
        itemCount: Int,
        showNoMoreToast: Boolean,
        allowSameItemCount: Boolean
    ) {
        val handler = session?.eventHandler ?: return
        if (!boundaryLoadState.tryStart(eventName, itemCount, hasHandler = true, allowSameItemCount = allowSameItemCount)) {
            return
        }
        Log.d(DIAG_TAG, "dispatchBoundaryEvent event=$eventName itemCount=$itemCount " +
            "showNoMoreToast=$showNoMoreToast allowSameItemCount=$allowSameItemCount")
        startLoadingTimer()
        handler.onEvent(mutableMapOf(eventName to "")) {
            mainHandler.postDelayed({
                if (!canHandleUiCallbacks() || !boundaryLoadState.isLoading(eventName)) {
                    return@postDelayed
                }
                finishLoadMore(
                    eventName = eventName,
                    hasNew = adapter.itemCount != itemCount,
                    showNoMoreToast = showNoMoreToast
                )
                Log.d(DIAG_TAG, "boundaryEventSettled event=$eventName before=$itemCount after=${adapter.itemCount} " +
                    "hasNew=${adapter.itemCount != itemCount}")
            }, LOAD_MORE_CALLBACK_SETTLE_MS)
        }
    }

    private fun finishLoadMore(hasNew: Boolean) {
        boundaryLoadState.finishAll()
        cancelLoadingTimerIfIdle()
        if (!hasNew) {
            showToast(getString(R.string.image_viewer_no_more_data))
        }
    }

    private fun finishLoadMore(eventName: String, hasNew: Boolean, showNoMoreToast: Boolean = true) {
        boundaryLoadState.finish(eventName)
        cancelLoadingTimerIfIdle()
        if (!hasNew && showNoMoreToast) {
            showToast(getString(R.string.image_viewer_no_more_data))
        }
    }

    private fun finishAllLoadMore(hasNew: Boolean) {
        if (!boundaryLoadState.hasActiveLoad()) {
            return
        }
        boundaryLoadState.finishAll()
        cancelLoadingTimerIfIdle()
        if (!hasNew && canHandleUiCallbacks()) {
            showToast(getString(R.string.image_viewer_no_more_data))
        }
    }

    private fun dispatchImageTap() {
        session?.eventHandler?.onEvent(mutableMapOf(ImageViewer.EVENT_IMAGE_TAP to "")) { }
    }

    private fun handleDownloadRequested(element: ImageElement) {
        val handler = session?.eventHandler ?: return
        val currentSession = session ?: return
        val key = elementKey(element) ?: return
        if (!currentSession.downloadingKeys.add(key)) {
            return
        }
        refreshVideoPage(element)
        val eventData = mutableMapOf<String, Any>(
            ImageViewer.EVENT_DOWNLOAD_VIDEO to mapOf(
                "path" to (element.data?.toString() ?: ""),
                "stableId" to (element.stableId ?: "")
            )
        )
        handler.onEvent(eventData) { result ->
            mainHandler.post {
                currentSession.downloadingKeys.remove(key)
                val resolvedPath = when (result) {
                    is String -> result.takeIf { it.isNotBlank() }
                    is List<*> -> result.firstOrNull()?.toString()?.takeIf { it.isNotBlank() }
                    else -> null
                }
                if (resolvedPath != null) {
                    currentSession.videoOverrides[key] = resolvedPath
                }
                if (!canHandleUiCallbacks()) {
                    return@post
                }
                if (resolvedPath != null) {
                    refreshVideoPage(element)
                } else {
                    refreshVideoPage(element)
                    showToast(getString(R.string.image_viewer_video_download_failed))
                }
            }
        }
    }

    private fun handleSaveCurrentMedia() {
        val element = currentLogicalItem ?: adapter.getItemOrNull(viewPager.currentItem) ?: return
        requestStoragePermissionIfNeeded(element)
    }

    private fun requestStoragePermissionIfNeeded(element: ImageElement) {
        ChatPermissionHelper.requestPermission(
            ChatPermissionHelper.PERMISSION_STORAGE,
            object : PermissionCallback() {
                override fun onGranted() {
                    startSaveMedia(element)
                }

                override fun onDenied() {
                    showToast(getString(R.string.image_viewer_save_failed))
                }
            }
        )
    }

    private fun startSaveMedia(element: ImageElement) {
        val key = saveOperationKey(element) ?: return
        val currentSession = session ?: return
        if (!currentSession.savingKeys.add(key)) {
            return
        }
        showToast(getString(R.string.image_viewer_saving))
        val localSource = resolveLocalMediaSource(element)?.takeIf { source -> source.canOpen(this) }
        if (localSource != null) {
            saveSourceToGallery(element, key, localSource)
            return
        }
        requestLocalSourceForSave(element, key)
    }

    private fun requestLocalSourceForSave(element: ImageElement, key: String) {
        val handler = session?.eventHandler
        if (handler == null) {
            finishSaveMedia(key, success = false)
            return
        }
        val eventData = mutableMapOf<String, Any>(
            ImageViewer.EVENT_SAVE_MEDIA to mapOf(
                "path" to (element.data?.toString() ?: ""),
                "stableId" to (element.stableId ?: ""),
                "mediaType" to element.type
            )
        )
        handler.onEvent(eventData) { result ->
            mainHandler.post {
                val resolvedPath = when (result) {
                    is String -> result.takeIf { it.isNotBlank() }
                    is List<*> -> result.firstOrNull()?.toString()?.takeIf { it.isNotBlank() }
                    else -> null
                }
                if (!canHandleUiCallbacks()) {
                    session?.savingKeys?.remove(key)
                    return@post
                }
                if (resolvedPath == null) {
                    finishSaveMedia(key, success = false)
                    return@post
                }
                if (element.type == MEDIA_TYPE_VIDEO) {
                    session?.videoOverrides?.put(key, resolvedPath)
                    refreshVideoPage(element)
                }
                val source = ImageViewerGallerySaver.MediaSource.from(resolvedPath)
                    ?.takeIf { it.canOpen(this@ImageViewerActivity) }
                if (source == null) {
                    finishSaveMedia(key, success = false)
                } else {
                    saveSourceToGallery(element, key, source)
                }
            }
        }
    }

    private fun saveSourceToGallery(
        element: ImageElement,
        key: String,
        source: ImageViewerGallerySaver.MediaSource
    ) {
        lifecycleScope.launch(Dispatchers.IO) {
            val success = if (element.type == MEDIA_TYPE_VIDEO) {
                ImageViewerGallerySaver.saveVideoToGallery(this@ImageViewerActivity, source)
            } else {
                ImageViewerGallerySaver.saveImageToGallery(this@ImageViewerActivity, source)
            }
            mainHandler.post {
                finishSaveMedia(key, success)
            }
        }
    }

    private fun finishSaveMedia(key: String, success: Boolean) {
        session?.savingKeys?.remove(key)
        if (!canHandleUiCallbacks()) {
            return
        }
        showToast(
            getString(
                if (success) {
                    R.string.image_viewer_save_success
                } else {
                    R.string.image_viewer_save_failed
                }
            )
        )
    }

    private fun resolveLocalMediaSource(element: ImageElement): ImageViewerGallerySaver.MediaSource? {
        val sourceData = if (element.type == MEDIA_TYPE_VIDEO) {
            resolveEffectiveVideoData(element)
        } else {
            element.data
        }
        return ImageViewerGallerySaver.MediaSource.from(sourceData)
    }

    private fun resolveEffectiveVideoData(element: ImageElement): Any? {
        val key = elementKey(element) ?: return element.videoData
        return session?.videoOverrides?.get(key) ?: element.videoData
    }

    private fun isElementDownloading(element: ImageElement): Boolean {
        val key = elementKey(element) ?: return false
        return session?.downloadingKeys?.contains(key) == true
    }

    private fun elementKey(element: ImageElement): String? {
        return ImageViewerMediaIdentity.keyFor(element)
    }

    private fun saveOperationKey(element: ImageElement): String? {
        return elementKey(element)
            ?: element.data?.toString()?.takeIf { it.isNotBlank() }?.let { "data:${element.type}:$it" }
    }

    private fun describeElement(element: ImageElement?): String {
        if (element == null) {
            return "null"
        }
        return "type=${element.type},stableId=${element.stableId},hasVideoData=${element.videoData != null}," +
            "data=${element.data?.toString()?.take(80)}"
    }

    private fun refreshVideoPage(element: ImageElement) {
        val view = findVideoPageViewFor(element) ?: return
        view.setDownloading(isElementDownloading(element))
        view.setEffectiveVideoData(resolveEffectiveVideoData(element))
        updateSaveButtonLayout()
    }

    private fun updateSaveButtonVisibility() {
        val visible = currentLogicalItem != null
        saveButton.visibility = if (visible) View.VISIBLE else View.GONE
        if (visible) {
            saveButton.expandTouchTarget()
        }
        updateSaveButtonLayout()
    }

    private fun findVideoPageViewFor(element: ImageElement): VideoMediaPageView? {
        return findVideoHolderFor(element)?.pageView
    }

    private fun currentVideoPageView(): VideoMediaPageView? {
        val element = currentLogicalItem ?: adapter.getItemOrNull(viewPager.currentItem) ?: return null
        return findVideoPageViewFor(element)
    }

    private fun ensureCurrentVideoPagePlayerReady(position: Int) {
        val element = adapter.getItemOrNull(position) ?: return
        if (!ImageViewerVideoPageRefreshPolicy.shouldRefreshOnPageSelected(element.type)) {
            return
        }
        Log.d(DIAG_TAG, "ensureCurrentVideoPagePlayerReady position=$position " +
            "target=${describeElement(element)}")
        refreshVideoPage(element)
    }

    private fun releaseNonCurrentVideoPages(currentPosition: Int) {
        val recyclerView = pagerRecyclerView() ?: return
        for (index in 0 until recyclerView.childCount) {
            val holder = recyclerView.getChildViewHolder(recyclerView.getChildAt(index))
            if (holder is ImageViewerPagerAdapter.VideoPageViewHolder &&
                ImageViewerVideoPageReleasePolicy.shouldRelease(
                    holderPosition = holder.bindingAdapterPosition,
                    currentPosition = currentPosition
                )
            ) {
                holder.pageView.releaseInlinePlayback()
            }
        }
    }

    private fun releaseAllVideoPages() {
        val recyclerView = pagerRecyclerView() ?: return
        for (index in 0 until recyclerView.childCount) {
            val holder = recyclerView.getChildViewHolder(recyclerView.getChildAt(index))
            if (holder is ImageViewerPagerAdapter.VideoPageViewHolder) {
                holder.pageView.releaseInlinePlayback()
            }
        }
    }

    private fun findVideoHolderFor(element: ImageElement): ImageViewerPagerAdapter.VideoPageViewHolder? {
        val recyclerView = pagerRecyclerView() ?: return null
        val targetKey = elementKey(element) ?: return null
        for (index in 0 until recyclerView.childCount) {
            val holder = recyclerView.getChildViewHolder(recyclerView.getChildAt(index))
            if (holder is ImageViewerPagerAdapter.VideoPageViewHolder) {
                val holderKey = adapter.getItemOrNull(holder.bindingAdapterPosition)?.let { elementKey(it) }
                if (holderKey == targetKey) {
                    return holder
                }
            }
        }
        return null
    }

    private fun startLoadingTimer() {
        mainHandler.removeCallbacks(showLoadingRunnable)
        mainHandler.removeCallbacks(loadMoreTimeoutRunnable)
        mainHandler.postDelayed(showLoadingRunnable, LOADING_INDICATOR_DELAY_MS)
        mainHandler.postDelayed(loadMoreTimeoutRunnable, LOAD_MORE_TIMEOUT_MS)
    }

    private fun cancelLoadingTimerIfIdle() {
        if (boundaryLoadState.hasActiveLoad()) {
            return
        }
        mainHandler.removeCallbacks(showLoadingRunnable)
        mainHandler.removeCallbacks(loadMoreTimeoutRunnable)
        hideLoadingIndicator()
    }

    private fun showLoadingIndicator() {
        loadingOverlay.visibility = View.VISIBLE
    }

    private fun hideLoadingIndicator() {
        loadingOverlay.visibility = View.GONE
    }

    private fun showToast(message: String) {
        toastView.text = message
        toastView.visibility = View.VISIBLE
        mainHandler.removeCallbacks(hideToastRunnable)
        mainHandler.postDelayed(hideToastRunnable, TOAST_DURATION_MS)
    }

    private fun hideToast() {
        toastView.visibility = View.GONE
    }

    private fun pagerRecyclerView(): RecyclerView? {
        return viewPager.getChildAt(0) as? RecyclerView
    }

    private fun dpToPx(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }

    private fun canHandleUiCallbacks(): Boolean {
        return activeForCallbacks && !isFinishing && !isDestroyed
    }

    companion object {
        private const val LOADING_INDICATOR_DELAY_MS = 3000L
        private const val LOAD_MORE_CALLBACK_SETTLE_MS = 100L
        private const val LOAD_MORE_TIMEOUT_MS = 15000L
        private const val TOAST_DURATION_MS = 2000L
        private const val MEDIA_TYPE_VIDEO = 1
        private const val SAVE_BUTTON_SIZE_DP = 25
        private const val SAVE_BUTTON_BASE_END_MARGIN_DP = 16
        private const val SAVE_BUTTON_BASE_BOTTOM_MARGIN_DP = 8
        private const val TOAST_BASE_BOTTOM_MARGIN_DP = 100
        private const val DIAG_TAG = "ImageViewerMediaDiag"
    }
}
