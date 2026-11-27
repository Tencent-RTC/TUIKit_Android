package io.trtc.tuikit.chat.uikit.components.imageviewer.ui

import android.graphics.Color as AndroidColor
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.ViewConfiguration
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsIgnoringVisibility
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import com.tencent.qcloud.tuicore.permission.PermissionCallback
import io.trtc.tuikit.chat.uikit.compose.R
import io.trtc.tuikit.chat.uikit.components.theme.LocalTheme
import io.trtc.tuikit.chat.uikit.components.common.ChatPermissionHelper
import io.trtc.tuikit.chat.uikit.components.imageviewer.ImageElement
import io.trtc.tuikit.chat.uikit.components.imageviewer.ImageViewer
import io.trtc.tuikit.chat.uikit.components.imageviewer.ImageViewerBoundaryLoadPolicy
import io.trtc.tuikit.chat.uikit.components.imageviewer.ImageViewerBoundaryLoadState
import io.trtc.tuikit.chat.uikit.components.imageviewer.ImageViewerGallerySaver
import io.trtc.tuikit.chat.uikit.components.imageviewer.ImageViewerMediaIdentity
import io.trtc.tuikit.chat.uikit.components.imageviewer.ImageViewerSessionState
import io.trtc.tuikit.chat.uikit.components.imageviewer.ImageViewerVideoPageRefreshPolicy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.math.abs

class ImageViewerActivity : AppCompatActivity() {

    private val mainHandler = Handler(Looper.getMainLooper())
    private val showLoadingRunnable = Runnable { loadingVisible = true }
    private val hideToastRunnable = Runnable { toastMessage = null }
    private val loadMoreTimeoutRunnable = Runnable { finishAllLoadMore(hasNew = false) }

    private var session: ImageViewer.Session? = null
    private var activeForCallbacks = true
    private var hasAppliedInitialPage = false
    private var currentPage = 0
    private var currentLogicalItem: ImageElement? = null
    private var latestMediaList: List<ImageElement> = emptyList()
    private val boundaryLoadState = ImageViewerBoundaryLoadState()
    private val boundaryLoadPolicy = ImageViewerBoundaryLoadPolicy()

    private var toastMessage by mutableStateOf<String?>(null)
    private var loadingVisible by mutableStateOf(false)
    private var currentItemState by mutableStateOf<ImageElement?>(null)
    private var pageRefreshTick by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.ImageViewer_Theme_FullscreenMedia)
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = AndroidColor.TRANSPARENT
        window.navigationBarColor = AndroidColor.TRANSPARENT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isStatusBarContrastEnforced = false
            window.isNavigationBarContrastEnforced = false
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes = window.attributes.apply {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }

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

        setContent {
            ImageViewerContent()
        }
        enterImmersiveMode()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            enterImmersiveMode()
        }
    }

    override fun onDestroy() {
        activeForCallbacks = false
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

    private fun enterImmersiveMode() {
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
    }

    @OptIn(ExperimentalLayoutApi::class)
    @androidx.compose.runtime.Composable
    private fun ImageViewerContent() {
        val currentSession = session
        val mediaListFlow = currentSession?.mediaListFlow ?: ImageViewer.mediaList
        val mediaList by mediaListFlow.collectAsState()
        val colors = LocalTheme.current.colors

        val initialPage = currentSession?.initDataInternal?.let { initElement ->
            ImageViewerSessionState.findBestIndex(mediaList, initElement, 0)
        } ?: 0
        val pagerState = rememberPagerState(initialPage = initialPage, pageCount = { mediaList.size })
        var currentPagePagerScrollEnabled by remember { mutableStateOf(true) }
        val pagerSettleScope = rememberCoroutineScope()
        val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
        val minFlingVelocity = ViewConfiguration.get(this).scaledMinimumFlingVelocity.toFloat()

        LaunchedEffect(mediaList) {
            applyMediaList(mediaList, pagerState)
        }

        LaunchedEffect(pagerState) {
            snapshotFlow { pagerState.currentPage }.collect { position ->
                currentPagePagerScrollEnabled = true
                if (hasAppliedInitialPage) {
                    handlePageSelected(position, mediaList.size)
                }
            }
        }

        LaunchedEffect(pagerState) {
            snapshotFlow { pagerState.isScrollInProgress }.collect { inProgress ->
                if (!hasAppliedInitialPage) {
                    return@collect
                }
                if (inProgress) {
                    boundaryLoadPolicy.markDragStarted(pagerState.currentPage, mediaList.size)
                } else if (boundaryLoadPolicy.shouldDispatchBoundaryLoad(pagerState.currentPage, mediaList.size)) {
                    triggerBoundaryEventIfNeeded(
                        position = pagerState.currentPage,
                        itemCount = mediaList.size,
                        showNoMoreToast = true,
                        allowSameItemCount = true
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(colors.bgColorMask)
        ) {
            if (mediaList.isNotEmpty()) {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize(),
                    beyondViewportPageCount = 1,
                    userScrollEnabled = currentPagePagerScrollEnabled,
                    key = { index ->
                        val item = mediaList.getOrNull(index)
                        item?.stableId?.takeIf { it.isNotBlank() }
                            ?: item?.data?.toString()?.takeIf { it.isNotBlank() }
                            ?: index
                    }
                ) { index ->
                    val element = mediaList.getOrNull(index) ?: return@HorizontalPager
                    val onPagerScrollEnabled: (Boolean) -> Unit = { enabled ->
                        if (pagerState.currentPage == index) {
                            currentPagePagerScrollEnabled = enabled
                        }
                    }
                    val onPagerOverscroll: (Float) -> Unit = { fingerDx ->
                        pagerState.dispatchRawDelta(fingerDeltaToPagerDelta(fingerDx, isRtl))
                    }
                    val onPagerOverscrollEnd: (Float) -> Unit = { fingerVelocityX ->
                        pagerSettleScope.launch {
                            settlePagerAfterOverscroll(
                                pagerState = pagerState,
                                scrollVelocity = fingerDeltaToPagerDelta(fingerVelocityX, isRtl),
                                minFlingVelocity = minFlingVelocity
                            )
                        }
                    }
                    if (element.type == MEDIA_TYPE_IMAGE) {
                        ZoomablePhotoView(
                            modifier = Modifier.fillMaxSize(),
                            data = element.data,
                            onTap = {
                                dispatchImageTap()
                                finish()
                            },
                            onPagerScrollEnabled = onPagerScrollEnabled,
                            onPagerOverscroll = onPagerOverscroll,
                            onPagerOverscrollEnd = onPagerOverscrollEnd
                        )
                    } else {
                        VideoMediaPage(
                            element = element,
                            effectiveVideoData = resolveEffectiveVideoData(element),
                            isDownloading = isElementDownloading(element),
                            isCurrentPage = pagerState.currentPage == index,
                            refreshSignal = pageRefreshTick,
                            onImageTap = {
                                dispatchImageTap()
                                finish()
                            },
                            onCloseRequested = { finish() },
                            onDownloadRequested = { target -> handleDownloadRequested(target) },
                            onPagerScrollEnabled = onPagerScrollEnabled,
                            onPagerOverscroll = onPagerOverscroll,
                            onPagerOverscrollEnd = onPagerOverscrollEnd
                        )
                    }
                }
            }

            if (currentItemState != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .windowInsetsPadding(
                            WindowInsets.systemBarsIgnoringVisibility
                                .union(WindowInsets.displayCutout)
                                .only(WindowInsetsSides.End + WindowInsetsSides.Bottom)
                        )
                        .padding(
                            end = SAVE_BUTTON_BASE_END_MARGIN_DP,
                            bottom = SAVE_BUTTON_BASE_BOTTOM_MARGIN_DP
                        )
                        .size(SAVE_BUTTON_TOUCH_SIZE_DP)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { handleSaveCurrentMedia() },
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(R.drawable.image_viewer_save_button),
                        contentDescription = stringResource(R.string.image_viewer_save_to_album),
                        modifier = Modifier.size(SAVE_BUTTON_SIZE_DP)
                    )
                }
            }

            toastMessage?.let { message ->
                Text(
                    text = message,
                    color = Color.White,
                    fontSize = 14.sp,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .windowInsetsPadding(
                            WindowInsets.systemBarsIgnoringVisibility
                                .union(WindowInsets.displayCutout)
                                .only(WindowInsetsSides.Bottom)
                        )
                        .padding(bottom = TOAST_BASE_BOTTOM_MARGIN_DP)
                        .background(Color.Black.copy(alpha = TOAST_BACKGROUND_ALPHA), RoundedCornerShape(20.dp))
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                )
            }

            if (loadingVisible) {
                Column(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .background(Color.Black.copy(alpha = LOADING_BACKGROUND_ALPHA), RoundedCornerShape(12.dp))
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(36.dp),
                        color = Color.White
                    )
                    Text(
                        text = stringResource(R.string.image_viewer_loading),
                        color = Color.White,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(top = 12.dp)
                    )
                }
            }
        }
    }

    private fun fingerDeltaToPagerDelta(fingerDelta: Float, isRtl: Boolean): Float {
        return if (isRtl) fingerDelta else -fingerDelta
    }

    private suspend fun settlePagerAfterOverscroll(
        pagerState: PagerState,
        scrollVelocity: Float,
        minFlingVelocity: Float
    ) {
        val pageCount = pagerState.pageCount
        if (pageCount <= 0) {
            return
        }
        val offset = pagerState.currentPageOffsetFraction
        val page = pagerState.currentPage
        val target = when {
            scrollVelocity > minFlingVelocity -> page + 1
            scrollVelocity < -minFlingVelocity -> page - 1
            offset >= 0.5f -> page + 1
            offset <= -0.5f -> page - 1
            else -> page
        }.coerceIn(0, pageCount - 1)
        if (target != page || abs(offset) > 0.001f) {
            pagerState.animateScrollToPage(target)
        }
    }

    private suspend fun applyMediaList(mediaList: List<ImageElement>, pagerState: PagerState) {
        val previousItem = currentLogicalItem ?: session?.initDataInternal
        val previousIndex = currentPage
        val previousSize = latestMediaList.size
        val isResponseToLoadMore = boundaryLoadState.hasActiveLoad()
        latestMediaList = mediaList
        if (mediaList.isEmpty()) {
            currentPage = 0
            currentLogicalItem = null
            currentItemState = null
            if (isResponseToLoadMore) {
                finishLoadMore(hasNew = false)
            }
            return
        }
        val targetIndex = ImageViewerSessionState.findBestIndex(
            mediaList = mediaList,
            previousItem = previousItem,
            previousIndex = if (hasAppliedInitialPage) previousIndex else 0
        )
        val wasInitialApply = !hasAppliedInitialPage
        currentPage = targetIndex
        currentLogicalItem = mediaList.getOrNull(targetIndex)
        currentItemState = currentLogicalItem
        val shouldMovePager = !hasAppliedInitialPage || pagerState.currentPage != targetIndex
        hasAppliedInitialPage = true
        if (shouldMovePager && pagerState.currentPage != targetIndex) {
            pagerState.scrollToPage(targetIndex)
        }
        if (wasInitialApply) {
            handlePageSelected(targetIndex, mediaList.size)
        }
        if (isResponseToLoadMore) {
            finishLoadMore(hasNew = mediaList.size != previousSize)
        }
    }

    private fun handlePageSelected(position: Int, itemCount: Int) {
        currentPage = position
        currentLogicalItem = latestMediaList.getOrNull(position)
        currentItemState = currentLogicalItem
        val element = currentLogicalItem
        if (element != null && ImageViewerVideoPageRefreshPolicy.shouldRefreshOnPageSelected(element.type)) {
            refreshVideoPage()
        }
        triggerBoundaryEventIfNeeded(
            position = position,
            itemCount = itemCount,
            showNoMoreToast = false,
            allowSameItemCount = false
        )
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
        startLoadingTimer()
        handler.onEvent(mutableMapOf(eventName to "")) {
            mainHandler.postDelayed({
                if (!canHandleUiCallbacks() || !boundaryLoadState.isLoading(eventName)) {
                    return@postDelayed
                }
                finishLoadMore(
                    eventName = eventName,
                    hasNew = latestMediaList.size != itemCount,
                    showNoMoreToast = showNoMoreToast
                )
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
        refreshVideoPage()
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
                refreshVideoPage()
                if (resolvedPath == null) {
                    showToast(getString(R.string.image_viewer_video_download_failed))
                }
            }
        }
    }

    private fun handleSaveCurrentMedia() {
        val element = currentLogicalItem ?: latestMediaList.getOrNull(currentPage) ?: return
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
                    refreshVideoPage()
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

    private fun refreshVideoPage() {
        pageRefreshTick++
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
        loadingVisible = false
    }

    private fun showToast(message: String) {
        toastMessage = message
        mainHandler.removeCallbacks(hideToastRunnable)
        mainHandler.postDelayed(hideToastRunnable, TOAST_DURATION_MS)
    }

    private fun canHandleUiCallbacks(): Boolean {
        return activeForCallbacks && !isFinishing && !isDestroyed
    }

    companion object {
        private const val LOADING_INDICATOR_DELAY_MS = 3000L
        private const val LOAD_MORE_CALLBACK_SETTLE_MS = 100L
        private const val LOAD_MORE_TIMEOUT_MS = 15000L
        private const val TOAST_DURATION_MS = 2000L
        private const val MEDIA_TYPE_IMAGE = 0
        private const val MEDIA_TYPE_VIDEO = 1
        private const val TOAST_BACKGROUND_ALPHA = 204f / 255f
        private const val LOADING_BACKGROUND_ALPHA = 179f / 255f
        private val SAVE_BUTTON_SIZE_DP = 25.dp
        private val SAVE_BUTTON_TOUCH_SIZE_DP = 48.dp
        private val SAVE_BUTTON_BASE_END_MARGIN_DP = 16.dp
        private val SAVE_BUTTON_BASE_BOTTOM_MARGIN_DP = 8.dp
        private val TOAST_BASE_BOTTOM_MARGIN_DP = 100.dp
    }
}
