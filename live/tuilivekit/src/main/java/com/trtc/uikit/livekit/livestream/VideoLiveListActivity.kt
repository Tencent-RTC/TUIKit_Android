package com.trtc.uikit.livekit.livestream

import android.content.Context
import android.content.Intent
import android.graphics.Rect
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.view.View.GONE
import android.view.View.VISIBLE
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageView
import androidx.activity.addCallback
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import com.tencent.cloud.tuikit.engine.common.ContextProvider
import com.tencent.qcloud.tuicore.TUIConstants
import com.tencent.qcloud.tuicore.TUICore
import com.tencent.qcloud.tuicore.util.SPUtils
import com.trtc.uikit.livekit.R
import com.trtc.uikit.livekit.common.EVENT_KEY_LIVE_KIT
import com.trtc.uikit.livekit.common.EVENT_SUB_KEY_DESTROY_LIVE_VIEW
import com.trtc.uikit.livekit.common.ErrorLocalized
import com.trtc.uikit.livekit.common.LiveIdentityGenerator
import com.trtc.uikit.livekit.common.LiveKitLogger
import com.trtc.uikit.livekit.common.PermissionRequest
import com.trtc.uikit.livekit.component.pippanel.PIPPanelStore
import com.trtc.uikit.livekit.features.livelist.LiveListView
import com.trtc.uikit.livekit.features.livelist.Style
import com.trtc.uikit.livekit.livestream.impl.LiveInfoUtils.asEngineLiveInfo
import com.trtc.uikit.livekit.voiceroom.VoiceRoomKit
import io.trtc.tuikit.atomicx.common.FullScreenActivity
import io.trtc.tuikit.atomicx.common.permission.PermissionCallback
import io.trtc.tuikit.atomicx.widget.basicwidget.toast.AtomicToast
import io.trtc.tuikit.atomicxcore.api.CompletionHandler
import io.trtc.tuikit.atomicxcore.api.live.LiveInfo
import io.trtc.tuikit.atomicxcore.api.login.LoginStore

class VideoLiveListActivity : FullScreenActivity() {

    companion object {
        const val EVENT_ADVANCE_SETTING_EXTENSION = "AdvanceSettingExtension"
        const val EVENT_SUB_KEY_SHOW_ADVANCE_SETTING_VIEW = "showAdvanceSettingView"
        const val EVENT_SUB_KEY_HIDE_ADVANCE_SETTING_VIEW = "hideAdvanceSettingView"
        const val EVENT_SUB_KEY_REAL_NAME_VERIFY = "eventRealNameVerify"

        private val LOGGER = LiveKitLogger.getComponentLogger("VideoLiveListActivity")
    }

    private var isAdvanceSettingsViewVisible = false
    private var style: Style = Style.DOUBLE_COLUMN
    private lateinit var mainLayout: ConstraintLayout
    private lateinit var liveListView: LiveListView
    private lateinit var toolbarLiveView: View
    private lateinit var startLiveView: View
    private lateinit var liveListColumnTypeView: ImageView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.livekit_activity_video_live_list)
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN

        mainLayout = findViewById(R.id.main)
        liveListView = findViewById(R.id.live_list_view)
        toolbarLiveView = findViewById(R.id.toolbar_live)
        startLiveView = findViewById(R.id.atomic_btn_start)
        liveListColumnTypeView = findViewById(R.id.btn_live_list_column_type)

        liveListColumnTypeView.setOnClickListener { changeColumnStyle() }
        initStartLiveView()
        initBackButton()
        initVideoLiveTitle()
        initLiveListView()
        onBackPressedDispatcher.addCallback(this) {
            if (PIPPanelStore.sharedInstance().state.anchorIsPictureInPictureMode
                || PIPPanelStore.sharedInstance().state.audienceIsPictureInPictureMode
            ) {
                val homeIntent = Intent(Intent.ACTION_MAIN).apply {
                    addCategory(Intent.CATEGORY_HOME)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                startActivity(homeIntent)
            }
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_DOWN) {
            val v = currentFocus
            if (v is EditText) {
                val outRect = Rect()
                v.getGlobalVisibleRect(outRect)
                if (!outRect.contains(event.rawX.toInt(), event.rawY.toInt())) {
                    v.clearFocus()
                    val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                    imm.hideSoftInputFromWindow(v.windowToken, 0)
                }
            }
        }
        return super.onTouchEvent(event)
    }

    private fun initVideoLiveTitle() {
        findViewById<View>(R.id.tv_title).setOnLongClickListener {
            if (isAdvanceSettingsViewVisible) {
                hideAdvanceSettingView()
            } else {
                showAdvanceSettingView()
            }
            isAdvanceSettingsViewVisible = !isAdvanceSettingsViewVisible
            false
        }
    }

    private fun initLiveListView() {
        liveListView.init(this, style)
        updateLiveStyleUI(style)
        liveListView.setOnItemClickListener { view, liveInfo ->
            if (!view.isEnabled) return@setOnItemClickListener
            view.isEnabled = false
            view.postDelayed({ view.isEnabled = true }, 1000)
            enterRoom(liveInfo)
        }
    }

    private fun changeColumnStyle() {
        style = if (style == Style.DOUBLE_COLUMN) Style.SINGLE_COLUMN else Style.DOUBLE_COLUMN
        updateLiveStyleUI(style)
    }

    private fun updateLiveStyleUI(style: Style) {
        val constraintSet = ConstraintSet()
        constraintSet.clone(mainLayout)
        if (style == Style.DOUBLE_COLUMN) {
            constraintSet.connect(liveListView.id, ConstraintSet.TOP, toolbarLiveView.id, ConstraintSet.BOTTOM)
            liveListColumnTypeView.setImageResource(R.drawable.livekit_ic_single_item_type)
            constraintSet.setVisibility(startLiveView.id, VISIBLE)
        } else {
            constraintSet.connect(liveListView.id, ConstraintSet.TOP, ConstraintSet.PARENT_ID, ConstraintSet.TOP)
            liveListColumnTypeView.setImageResource(R.drawable.livekit_ic_double_item_type)
            constraintSet.setVisibility(startLiveView.id, GONE)
        }
        constraintSet.applyTo(mainLayout)
        this.style = style
        liveListView.updateColumnStyle(style)
    }

    private fun initStartLiveView() {
        startLiveView.setOnClickListener {
            requestPermission(object : CompletionHandler {
                override fun onSuccess() {
                    if (packageName == "com.tencent.trtc" &&
                        LoginStore.shared.loginState.loginUserInfo.value?.userID?.startsWith("moa")
                        == false) {
                        realNameVerifyAndStartLive()
                        return
                    }
                    startVideoLive()
                }

                override fun onFailure(code: Int, desc: String) {
                    LOGGER.info("requestPermission onFailure. code:$code,desc:$desc")
                    ErrorLocalized.onError(code)
                }
            })

        }
    }

    private fun realNameVerifyAndStartLive() {
        if (SPUtils.getInstance("sp_verify").getBoolean("sp_verify", false)) {
            startVideoLive()
        } else {
            try {
                val map = HashMap<String, Any>()
                map[TUIConstants.Privacy.PARAM_DIALOG_CONTEXT] = this
                TUICore.notifyEvent(TUIConstants.Privacy.EVENT_ROOM_STATE_CHANGED, EVENT_SUB_KEY_REAL_NAME_VERIFY, map)
            } catch (e: Exception) {
                LOGGER.error("real name verify fail, exception: ${e.message}")
            }
        }
    }

    private fun startVideoLive() {
        if (PIPPanelStore.sharedInstance().state.isAnchorStreaming) {
            AtomicToast.show(this, getString(R.string.common_exit_float_window_tip), AtomicToast.Style.WARNING)
            return
        }
        TUICore.notifyEvent(EVENT_KEY_LIVE_KIT, EVENT_SUB_KEY_DESTROY_LIVE_VIEW, null)
        val roomId = LiveIdentityGenerator.generateId(
            LoginStore.shared.loginState.loginUserInfo.value?.userID ?: "",
            LiveIdentityGenerator.RoomType.LIVE
        )
        VideoLiveKit.createInstance(applicationContext).startLive(roomId)
    }

    private fun initBackButton() {
        findViewById<View>(R.id.iv_back).setOnClickListener {
            hideAdvanceSettingView()
            finish()
        }
    }

    private fun showAdvanceSettingView() {
        TUICore.notifyEvent(EVENT_ADVANCE_SETTING_EXTENSION, EVENT_SUB_KEY_SHOW_ADVANCE_SETTING_VIEW, null)
    }

    private fun hideAdvanceSettingView() {
        TUICore.notifyEvent(EVENT_ADVANCE_SETTING_EXTENSION, EVENT_SUB_KEY_HIDE_ADVANCE_SETTING_VIEW, null)
    }

    private fun enterRoom(info: LiveInfo) {
        if (PIPPanelStore.sharedInstance().state.isAnchorStreaming) {
            AtomicToast.show(this, getString(R.string.common_exit_float_window_tip), AtomicToast.Style.WARNING)
            return
        }
        TUICore.notifyEvent(EVENT_KEY_LIVE_KIT, EVENT_SUB_KEY_DESTROY_LIVE_VIEW, null)
        if (info.liveID.startsWith("voice_")) {
            VoiceRoomKit.createInstance(this).enterRoom(info.asEngineLiveInfo())
        } else {
            VideoLiveKit.createInstance(this).joinLive(info)
        }
    }

    private fun requestPermission(callback: CompletionHandler?) {
        LOGGER.info("requestCameraPermissions:[]")
        ContextProvider.getApplicationContext()?.apply {
            PermissionRequest.requestCameraPermissions(this, object :
                PermissionCallback() {
                override fun onRequesting() {
                    LOGGER.info("requestCameraPermissions:[onRequesting]")
                }

                override fun onGranted() {
                    LOGGER.info("requestCameraPermissions:[onGranted]")
                    PermissionRequest.requestMicrophonePermissions(
                        this@apply,
                        object : PermissionCallback() {
                            override fun onGranted() {
                                LOGGER.info("requestMicrophonePermissions success")
                                callback?.onSuccess()
                            }

                            override fun onDenied() {
                                LOGGER.error("requestMicrophonePermissions:[onDenied]")
                                callback?.onFailure(
                                    -1101,
                                    "requestMicrophonePermissions:[onDenied]"
                                )
                            }
                        })
                }

                override fun onDenied() {
                    LOGGER.error("requestCameraPermissions:[onDenied]")
                    callback?.onFailure(
                        -1101,
                        "requestCameraPermissions:[onDenied]"
                    )
                }
            })
        }
    }
}