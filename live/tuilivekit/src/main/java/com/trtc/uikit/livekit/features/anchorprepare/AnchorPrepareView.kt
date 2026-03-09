package com.trtc.uikit.livekit.features.anchorprepare

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.util.AttributeSet
import android.view.Gravity
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import com.trtc.uikit.livekit.R
import com.trtc.uikit.livekit.common.ErrorLocalized
import com.trtc.uikit.livekit.common.LiveKitLogger
import com.trtc.uikit.livekit.common.ui.RoundFrameLayout
import com.trtc.uikit.livekit.component.beauty.BeautyUtils.resetBeauty
import com.trtc.uikit.livekit.component.beauty.tebeauty.store.TEBeautyStore
import com.trtc.uikit.livekit.features.anchorprepare.store.AnchorPrepareStore
import com.trtc.uikit.livekit.features.anchorprepare.store.AnchorPrepareConfig
import com.trtc.uikit.livekit.features.anchorprepare.store.AnchorPrepareState
import com.trtc.uikit.livekit.features.anchorprepare.view.function.PrepareFunctionView
import com.trtc.uikit.livekit.features.anchorprepare.view.liveinfoedit.LiveInfoEditView
import com.trtc.uikit.livekit.livestream.VideoLiveAnchorActivity
import io.trtc.tuikit.atomicx.common.util.ScreenUtil
import io.trtc.tuikit.atomicx.widget.basicwidget.button.AtomicButton
import io.trtc.tuikit.atomicx.widget.basicwidget.button.ButtonColorType
import io.trtc.tuikit.atomicx.widget.basicwidget.button.ButtonIconPosition
import io.trtc.tuikit.atomicx.widget.basicwidget.button.ButtonSize
import io.trtc.tuikit.atomicx.widget.basicwidget.button.ButtonVariant
import io.trtc.tuikit.atomicxcore.api.CompletionHandler
import io.trtc.tuikit.atomicxcore.api.device.AudioEffectStore
import io.trtc.tuikit.atomicxcore.api.device.DeviceStore
import io.trtc.tuikit.atomicxcore.api.view.CoreViewType
import io.trtc.tuikit.atomicxcore.api.view.LiveCoreView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class AnchorPrepareView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {
    private val logger = LiveKitLogger.getFeaturesLogger("AnchorPrepareView")
    private lateinit var layoutRoot: FrameLayout
    private var prepareStore: AnchorPrepareStore? = null
    private var state: AnchorPrepareState? = null
    private var liveCoreView: LiveCoreView? = null
    private var functionView: PrepareFunctionView? = null
    private lateinit var layoutCoreViewContainer: RoundFrameLayout
    private var layoutComponentContainer: FrameLayout? = null
    private lateinit var imageBack: ImageView
    private var subscribeStateJob: Job? = null

    init {
        logger.info("AnchorPrepareView Constructor.")
        initView()
    }

    private fun initView() {
        LayoutInflater.from(context).inflate(R.layout.anchor_prepare_layout_prepare_view, this, true)
        layoutRoot = findViewById(R.id.fl_root)
        imageBack = findViewById(R.id.iv_back)
        layoutCoreViewContainer = findViewById(R.id.fl_video_view_container)
        layoutComponentContainer = findViewById(R.id.fl_component_container)
    }

    fun init(roomId: String, liveCoreView: LiveCoreView?) {
        logger.info("AnchorPrepareView init. roomId:$roomId,liveCoreView:$liveCoreView")
        this.liveCoreView = liveCoreView
            ?: LiveCoreView(context, null, 0, CoreViewType.PUSH_VIEW).apply {
                setLiveID(roomId)
            }
        initPrepareStore(roomId)
        initComponent()
    }

    fun getCoreView(): LiveCoreView? = liveCoreView

    fun addAnchorPrepareViewListener(listener: AnchorPrepareViewListener) {
        prepareStore?.addAnchorPrepareViewListener(listener)
    }

    fun removeAnchorPrepareViewListener(listener: AnchorPrepareViewListener) {
        prepareStore?.removeAnchorPrepareViewListener(listener)
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
            AnchorPrepareConfig.disableFeatureMenu.collect {
                onFeatureMenuDisable()
            }
        }
    }

    private fun removeObserver() {
        subscribeStateJob?.cancel()
    }

    private fun initPrepareStore(roomId: String) {
        liveCoreView?.let {
            prepareStore = AnchorPrepareStore()
            state = prepareStore?.getState()
            state?.roomId = roomId
        }
    }

    private fun initComponent() {
        initBackView()
        initCoreView()
        initLiveInfoEditView()
        initFunctionView()
        initStartLiveButton()
    }

    private fun initBackView() {
        imageBack.setOnClickListener {
            prepareStore?.stopPreview()
            resetBeauty()
            TEBeautyStore.unInit()
            AudioEffectStore.shared().reset()
            DeviceStore.shared().reset()
        }
    }

    private fun initCoreView() {
        val coreView = liveCoreView
        if (coreView == null) {
            logger.error("Please call the AnchorPrepareView.init() method first.")
            return
        }

        if (coreView.parent == null) {
            layoutCoreViewContainer.setRadius(ScreenUtil.dip2px(16.0f))
            val layoutParams = LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.MATCH_PARENT
            )
            layoutCoreViewContainer.addView(coreView, layoutParams)
            layoutRoot.setBackgroundColor(resources.getColor(R.color.common_black))
        } else {
            layoutCoreViewContainer.visibility = GONE
            layoutRoot.setBackgroundColor(resources.getColor(R.color.common_design_standard_transparent))
        }

        state?.useFrontCamera?.value = DeviceStore.shared().deviceState.isFrontCamera.value
        prepareStore?.startPreview(object : CompletionHandler {
            override fun onSuccess() {
                val intent = Intent(context, VideoLiveAnchorActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            }

            override fun onFailure(code: Int, desc: String) {
                logger.info("startPreview onFailure. code:$code,desc:$desc")
                ErrorLocalized.onError(code)
                val context = this@AnchorPrepareView.context
                if (context is Activity) {
                    context.finish()
                }
            }
        })
    }

    private fun initLiveInfoEditView() {
        prepareStore?.let { mgr ->
            val liveInfoEditView = LiveInfoEditView(context)
            liveInfoEditView.init(mgr)

            val layoutParams = LayoutParams(
                ScreenUtil.dip2px(343.0f),
                ScreenUtil.dip2px(112.0f)
            ).apply {
                topMargin = ScreenUtil.dip2px(96.0f)
                gravity = Gravity.CENTER_HORIZONTAL
            }

            layoutComponentContainer?.addView(liveInfoEditView, layoutParams)
        }
    }

    private fun initFunctionView() {
        if (AnchorPrepareConfig.disableFeatureMenu.value) {
            functionView?.let { view ->
                (view.parent as? ViewGroup)?.removeView(view)
            }
        } else {
            if (functionView != null) {
                return
            }

            prepareStore?.let { mgr ->
                liveCoreView?.let { coreView ->
                    functionView = PrepareFunctionView(context)
                    functionView?.init(mgr, coreView)

                    val layoutParams = LayoutParams(
                        LayoutParams.MATCH_PARENT,
                        ScreenUtil.dip2px(56.0f)
                    ).apply {
                        marginStart = ScreenUtil.dip2px(22.0f)
                        marginEnd = ScreenUtil.dip2px(22.0f)
                        gravity = Gravity.BOTTOM
                        bottomMargin = ScreenUtil.dip2px(144.0f)
                    }
                    layoutComponentContainer?.addView(functionView, layoutParams)
                }
            }
        }
    }

    private fun initStartLiveButton() {
        prepareStore?.let { mgr ->
            val startLiveButton = AtomicButton(context).apply {
                text = context.getString(R.string.common_start_live)
                variant = ButtonVariant.FILLED
                colorType = ButtonColorType.PRIMARY
                size = ButtonSize.L
                iconPosition = ButtonIconPosition.NONE
                isBold = true

                setOnClickListener {
                    mgr.startLive()
                }
            }

            val layoutParams = LayoutParams(
                LayoutParams.MATCH_PARENT,
                ScreenUtil.dip2px(52.0f)
            ).apply {
                marginStart = ScreenUtil.dip2px(24.0f)
                marginEnd = ScreenUtil.dip2px(24.0f)
                bottomMargin = ScreenUtil.dip2px(60.0f)
                gravity = Gravity.CENTER_HORIZONTAL or Gravity.BOTTOM
            }

            layoutComponentContainer?.addView(startLiveButton, layoutParams)
        }
    }

    private fun onFeatureMenuDisable() {
        initFunctionView()
    }

    fun getState(): PrepareState? {
        return prepareStore?.getExternalState()
    }

    fun enablePipMode(inPictureInPictureMode: Boolean) {
        val layoutParams = layoutCoreViewContainer.layoutParams as FrameLayout.LayoutParams
        if (inPictureInPictureMode) {
            layoutParams.setMargins(0, 0, 0, 0)
            layoutCoreViewContainer.setRadius(ScreenUtil.dip2px(0f))
            layoutComponentContainer?.visibility = GONE
        } else {
            layoutParams.setMargins(0, ScreenUtil.dip2px(44f), 0, ScreenUtil.dip2px(96f))
            layoutCoreViewContainer.setRadius(ScreenUtil.dip2px(16f))
            layoutComponentContainer?.visibility = VISIBLE
        }
        layoutCoreViewContainer.layoutParams = layoutParams
    }

    fun disableFeatureMenu(disable: Boolean) {
        logger.info("disableFeatureMenu: disable = $disable")
        AnchorPrepareStore.disableFeatureMenu(disable)
    }

    @Deprecated("Use disableMenuSwitchCamera instead")
    fun disableMenuSwitchButton(disable: Boolean) {
        logger.info("disableMenuSwitchButton: disable = $disable")
        AnchorPrepareStore.disableMenuSwitchButton(disable)
    }

    @Deprecated("Use disableMenuBeauty instead")
    fun disableMenuBeautyButton(disable: Boolean) {
        logger.info("disableMenuBeautyButton: disable = $disable")
        AnchorPrepareStore.disableMenuBeautyButton(disable)
    }

    @Deprecated("Use disableMenuAudioEffect instead")
    fun disableMenuAudioEffectButton(disable: Boolean) {
        logger.info("disableMenuAudioEffectButton: disable = $disable")
        AnchorPrepareStore.disableMenuAudioEffectButton(disable)
    }

    fun disableMenuSwitchCamera(disable: Boolean) {
        logger.info("disableMenuSwitchButton: disable = $disable")
        AnchorPrepareStore.disableMenuSwitchButton(disable)
    }

    fun disableMenuBeauty(disable: Boolean) {
        logger.info("disableMenuBeautyButton: disable = $disable")
        AnchorPrepareStore.disableMenuBeautyButton(disable)
    }

    fun disableMenuAudioEffect(disable: Boolean) {
        logger.info("disableMenuAudioEffectButton: disable = $disable")
        AnchorPrepareStore.disableMenuAudioEffectButton(disable)
    }
}