package com.tencent.uikit.app.main.call

import android.content.Intent
import android.os.Bundle
import android.text.TextUtils
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.AdapterView
import android.widget.CheckBox
import android.widget.CompoundButton
import android.widget.EditText
import android.widget.ImageView
import android.widget.RelativeLayout
import android.widget.TextView
import androidx.appcompat.widget.AppCompatSpinner
import androidx.appcompat.widget.SwitchCompat
import com.tencent.cloud.tuikit.engine.call.TUICallEngine
import com.tencent.cloud.tuikit.engine.common.TUICommonDefine
import com.tencent.cloud.tuikit.engine.common.TUICommonDefine.VideoRenderParams
import com.tencent.cloud.tuikit.engine.common.TUICommonDefine.VideoRenderParams.FillMode
import com.tencent.cloud.tuikit.engine.common.TUICommonDefine.VideoRenderParams.Rotation
import com.tencent.qcloud.tuicore.TUILogin
import com.tencent.qcloud.tuikit.tuicallkit.TUICallKit.Companion.createInstance
import io.trtc.tuikit.atomicx.widget.basicwidget.toast.AtomicToast
import com.tencent.qcloud.tuikit.tuicallkit.common.data.Constants
import com.tencent.qcloud.tuikit.tuicallkit.state.GlobalState
import com.tencent.qcloud.tuikit.tuicallkit.state.GlobalState.Companion.instance
import com.tencent.qcloud.tuikit.tuicallkit.view.CallAdapter
import com.tencent.uikit.app.R
import com.tencent.uikit.app.main.BaseActivity
import com.tencent.uikit.app.main.call.SettingDetailActivity.Companion.ITEM_AVATAR
import com.tencent.uikit.app.main.call.SettingDetailActivity.Companion.ITEM_KEY
import com.tencent.uikit.app.main.call.SettingDetailActivity.Companion.ITEM_OFFLINE_MESSAGE
import com.tencent.uikit.app.main.call.SettingDetailActivity.Companion.ITEM_RING_PATH
import com.tencent.uikit.app.main.call.SettingDetailActivity.Companion.ITEM_USER_DATA
import io.trtc.tuikit.atomicxcore.api.CompletionHandler

class SettingsActivity : BaseActivity() {
    private var textAvatar: TextView? = null
    private var editNickname: EditText? = null
    private var textRingPath: TextView? = null
    private var switchMute: SwitchCompat? = null
    private var switchFloating: SwitchCompat? = null
    private var switchBlurBackground: SwitchCompat? = null
    private var switchIncomingBanner: SwitchCompat? = null
    private var switchAISubtitle: SwitchCompat? = null
    private var editDigitalRoomId: EditText? = null
    private var editStringRoomId: EditText? = null
    private var editTimeout: EditText? = null
    private var textUserData: TextView? = null
    private var textOfflineMessage: TextView? = null
    private var spinnerResolution: AppCompatSpinner? = null
    private var spinnerResolutionMode: AppCompatSpinner? = null
    private var spinnerRotation: AppCompatSpinner? = null
    private var spinnerFitMode: AppCompatSpinner? = null
    private var editBeauty: EditText? = null
    private var switchAddMainView: SwitchCompat? = null
    private var switchUseStreamView: SwitchCompat? = null
    private var checkboxMicrophone: CheckBox? = null
    private var checkboxAudioDevice: CheckBox? = null
    private var checkboxCamera: CheckBox? = null
    private var checkboxSwitchCamera: CheckBox? = null
    private var checkboxInviteUser: CheckBox? = null

    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.app_activity_settings)
        initView()
    }

    protected override fun onResume() {
        super.onResume()
        initData()
    }

    private fun initView() {
        textAvatar = findViewById(R.id.tv_avatar)
        editNickname = findViewById(R.id.et_nickname)
        textRingPath = findViewById(R.id.tv_ring_path)
        switchMute = findViewById(R.id.switch_mute)
        switchFloating = findViewById(R.id.switch_floating)
        switchBlurBackground = findViewById(R.id.switch_blur_background)
        switchIncomingBanner = findViewById(R.id.switch_incoming_banner)
        switchAISubtitle = findViewById(R.id.app_switch_ai_subtitle)

        editDigitalRoomId = findViewById(R.id.et_room_id_num)
        editStringRoomId = findViewById(R.id.et_room_id_str)
        editTimeout = findViewById(R.id.et_timeout)
        textUserData = findViewById(R.id.tv_user_data)
        textOfflineMessage = findViewById(R.id.tv_offline_message)

        spinnerResolution = findViewById(R.id.spinner_resolution)
        spinnerResolutionMode = findViewById(R.id.spinner_resolution_mode)
        spinnerFitMode = findViewById(R.id.spinner_fit_mode)
        spinnerRotation = findViewById(R.id.spinner_rotation)
        editBeauty = findViewById(R.id.et_beauty)

        switchAddMainView = findViewById(R.id.switch_add_main_view)
        switchUseStreamView = findViewById(R.id.switch_use_stream_view)
        checkboxMicrophone = findViewById(R.id.checkbox_microphone)
        checkboxAudioDevice = findViewById(R.id.checkbox_audio_device)
        checkboxCamera = findViewById(R.id.checkbox_camera)
        checkboxSwitchCamera = findViewById(R.id.checkbox_switch_camera)
        checkboxInviteUser = findViewById(R.id.checkbox_invite_user)
        findViewById<ImageView>(R.id.iv_back).setOnClickListener {
            onBackPressed()
        }
        findViewById<RelativeLayout>(R.id.rl_avatar).setOnClickListener {
            Intent(this@SettingsActivity, SettingDetailActivity::class.java).apply {
                putExtra(ITEM_KEY, ITEM_AVATAR)
                startActivity(this)
            }
        }

        editNickname?.setOnEditorActionListener { v: TextView?, actionId: Int, event: KeyEvent? ->
            if (EditorInfo.IME_ACTION_DONE == actionId) {
                setUserName()
            }
            false
        }
        findViewById<View>(R.id.rl_ring_path).setOnClickListener {
            Intent(this@SettingsActivity, SettingDetailActivity::class.java).apply {
                putExtra(ITEM_KEY, ITEM_RING_PATH)
                startActivity(this)
            }
        }

        switchMute?.setOnCheckedChangeListener { buttonView: CompoundButton?, isChecked: Boolean ->
            SettingsConfig.isMute = isChecked
            createInstance(applicationContext).enableMuteMode(isChecked)
        }

        switchFloating?.setOnCheckedChangeListener { buttonView: CompoundButton?, isChecked: Boolean ->
            SettingsConfig.isShowFloatingWindow = isChecked
            createInstance(applicationContext).enableFloatWindow(isChecked)
        }

        switchBlurBackground?.setOnCheckedChangeListener { buttonView: CompoundButton?, isChecked: Boolean ->
            SettingsConfig.isShowBlurBackground = isChecked
            createInstance(applicationContext).enableVirtualBackground(isChecked)
        }
        switchIncomingBanner?.setOnCheckedChangeListener { buttonView: CompoundButton?, isChecked: Boolean ->
            SettingsConfig.isIncomingBanner = isChecked
            createInstance(application).enableIncomingBanner(isChecked)
        }

        switchAISubtitle?.setOnCheckedChangeListener{ buttonView: CompoundButton?, isChecked: Boolean ->
            createInstance(application).enableAITranscriber(isChecked)
        }

        editDigitalRoomId?.setOnEditorActionListener { v: TextView?, actionId: Int, event: KeyEvent? ->
            if (EditorInfo.IME_ACTION_DONE == actionId) {
                setDigitalRoomId()
            }
            false
        }

        editStringRoomId?.setOnEditorActionListener { v: TextView?, actionId: Int, event: KeyEvent? ->
            if (EditorInfo.IME_ACTION_DONE == actionId) {
                setStringRoomId()
            }
            false
        }

        editTimeout?.setOnEditorActionListener { v: TextView?, actionId: Int, event: KeyEvent? ->
            if (EditorInfo.IME_ACTION_DONE == actionId) {
                setCallTimeout()
            }
            false
        }

        findViewById<RelativeLayout>(R.id.rl_user_data).setOnClickListener {
            startActivity(
                Intent(this@SettingsActivity, SettingDetailActivity::class.java).apply {
                    putExtra(ITEM_KEY, ITEM_USER_DATA)
                }
            )
        }

        findViewById<RelativeLayout>(R.id.rl_offline_message).setOnClickListener {
            startActivity(
                Intent(this@SettingsActivity, SettingDetailActivity::class.java).apply {
                    putExtra(ITEM_KEY, ITEM_OFFLINE_MESSAGE)
                }
            )
        }

        checkboxMicrophone?.setOnCheckedChangeListener { buttonView: CompoundButton?, isChecked: Boolean ->
            if (isChecked) {
                createInstance(application).disableControlButton(Constants.ControlButton.Microphone)
            } else {
                instance.disableControlButtonSet.remove(Constants.ControlButton.Microphone)
            }
        }
        checkboxAudioDevice?.setOnCheckedChangeListener { buttonView: CompoundButton?, isChecked: Boolean ->
            if (isChecked) {
                createInstance(application).disableControlButton(Constants.ControlButton.AudioPlaybackDevice)
            } else {
                instance.disableControlButtonSet.remove(Constants.ControlButton.AudioPlaybackDevice)
            }
        }
        checkboxCamera?.setOnCheckedChangeListener { buttonView: CompoundButton?, isChecked: Boolean ->
            if (isChecked) {
                createInstance(application).disableControlButton(Constants.ControlButton.Camera)
            } else {
                instance.disableControlButtonSet.remove(Constants.ControlButton.Camera)
            }
        }
        checkboxSwitchCamera?.setOnCheckedChangeListener { buttonView: CompoundButton?, isChecked: Boolean ->
            if (isChecked) {
                createInstance(application).disableControlButton(Constants.ControlButton.SwitchCamera)
            } else {
                instance.disableControlButtonSet.remove(Constants.ControlButton.SwitchCamera)
            }
        }
        checkboxInviteUser?.setOnCheckedChangeListener { buttonView: CompoundButton?, isChecked: Boolean ->
            if (isChecked) {
                createInstance(application).disableControlButton(Constants.ControlButton.InviteUser)
            } else {
                instance.disableControlButtonSet.remove(Constants.ControlButton.InviteUser)
            }
        }
        switchAddMainView?.setOnCheckedChangeListener { buttonView: CompoundButton?, isChecked: Boolean ->
            var adapter: CallAdapter? = null
            if (isChecked) {
                switchUseStreamView!!.setChecked(false)
                adapter = SettingsConfig.customUIAdapter.mainViewAdapter
            }
            createInstance(application).setAdapter(adapter)
        }

        switchUseStreamView?.setOnCheckedChangeListener { buttonView: CompoundButton?, isChecked: Boolean ->
            var adapter: CallAdapter? = null
            if (isChecked) {
                switchAddMainView!!.setChecked(false)
                adapter = SettingsConfig.customUIAdapter.streamViewAdapter
            }
            createInstance(application).setAdapter(adapter)
        }
        spinnerResolution?.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                SettingsConfig.resolution = position
                setVideoEncoderParams()
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {
            }
        }

        spinnerResolutionMode?.onItemSelectedListener = object :
            AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                SettingsConfig.resolutionMode = position
                setVideoEncoderParams()
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {
            }
        }

        spinnerRotation?.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                SettingsConfig.rotation = position
                setVideoRenderParams()
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {
            }
        }

        spinnerFitMode?.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                SettingsConfig.fillMode = position
                setVideoRenderParams()
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {
            }
        }

        editBeauty?.setOnEditorActionListener { v: TextView?, actionId: Int, event: KeyEvent? ->
            if (EditorInfo.IME_ACTION_DONE == actionId) {
                setBeautyLevel()
            }
            false
        }
    }

    private fun initData() {
        editNickname?.setText(TUILogin.getNickName())
        textAvatar?.setText(TUILogin.getFaceUrl())
        textRingPath?.setText(SettingsConfig.ringPath)
        switchMute?.setChecked(SettingsConfig.isMute)
        switchFloating?.setChecked(SettingsConfig.isShowFloatingWindow)
        switchBlurBackground?.setChecked(SettingsConfig.isShowBlurBackground)
        switchIncomingBanner?.setChecked(SettingsConfig.isIncomingBanner)
        switchAISubtitle?.setChecked(GlobalState.instance.enableAITranscriber)

        editDigitalRoomId?.setText("${SettingsConfig.intRoomId}")
        editStringRoomId?.setText(SettingsConfig.strRoomId)
        editTimeout?.setText("${SettingsConfig.callTimeOut}")
        textUserData?.setText(SettingsConfig.userData)
        textOfflineMessage?.setText(SettingsConfig.offlineParams)

        spinnerResolution?.setSelection(SettingsConfig.resolution, true)
        spinnerResolutionMode?.setSelection(SettingsConfig.resolutionMode, true)
        spinnerRotation?.setSelection(SettingsConfig.rotation, true)
        spinnerFitMode?.setSelection(SettingsConfig.fillMode, true)
        editBeauty?.setText("${SettingsConfig.beautyLevel}")
        switchAddMainView?.setChecked(false)
        switchUseStreamView?.setChecked(false)

        val buttonSet: MutableSet<Constants.ControlButton> = instance.disableControlButtonSet
        checkboxMicrophone?.setChecked(buttonSet.contains(Constants.ControlButton.Microphone))
        checkboxAudioDevice?.setChecked(buttonSet.contains(Constants.ControlButton.AudioPlaybackDevice))
        checkboxCamera?.setChecked(buttonSet.contains(Constants.ControlButton.Camera))
        checkboxSwitchCamera?.setChecked(buttonSet.contains(Constants.ControlButton.SwitchCamera))
        checkboxInviteUser?.setChecked(buttonSet.contains(Constants.ControlButton.InviteUser))
    }

    private fun setUserName() {
        val nickname = editNickname?.getText().toString()
        createInstance(applicationContext).setSelfInfo(
            nickname, TUILogin.getFaceUrl(), object : CompletionHandler {
                override fun onSuccess() {
                    AtomicToast.show(this@SettingsActivity, getString(R.string.app_set_success), AtomicToast.Style.SUCCESS)
                }

                override fun onFailure(code: Int, desc: String) {
                    editNickname?.setText(TUILogin.getNickName())
                    AtomicToast.show(this@SettingsActivity, getString(R.string.app_set_fail), AtomicToast.Style.ERROR)
                }
            })
    }

    private fun setCallTimeout() {
        val text = editTimeout!!.getText().toString().trim { it <= ' ' }
        if (TextUtils.isEmpty(text)) {
            AtomicToast.show(this, getString(R.string.app_please_set_call_waiting_timeout), AtomicToast.Style.ERROR)
            return
        }
        try {
            val timeout = text.toInt()
            SettingsConfig.callTimeOut = timeout
            AtomicToast.show(this, getString(R.string.app_set_success), AtomicToast.Style.SUCCESS)
        } catch (_: Exception) {
        }
    }

    private fun setBeautyLevel() {
        if (TextUtils.isEmpty(editBeauty?.getText().toString())) {
            AtomicToast.show(this, getString(R.string.app_please_set_beauty_level), AtomicToast.Style.ERROR)
            return
        }
        val beauty = editBeauty?.getText().toString().toInt()
        SettingsConfig.beautyLevel = beauty
        TUICallEngine.createInstance(applicationContext)
            .setBeautyLevel(beauty.toFloat(), object : TUICommonDefine.Callback {
                override fun onSuccess() {
                    AtomicToast.show(this@SettingsActivity, getString(R.string.app_set_success), AtomicToast.Style.SUCCESS)
                }

                override fun onError(errCode: Int, errMsg: String?) {
                    AtomicToast.show(
                        this@SettingsActivity,
                        getString(R.string.app_set_fail) + "| errorCode:" + errCode + ", " + "errMsg:" + errMsg,
                        AtomicToast.Style.ERROR
                    )
                }
            })
    }

    fun setVideoEncoderParams() {
        val videoEncoderParams = TUICommonDefine.VideoEncoderParams()
        videoEncoderParams.resolutionMode =
            TUICommonDefine.VideoEncoderParams.ResolutionMode.fromInt(SettingsConfig.resolutionMode)
        videoEncoderParams.resolution =
            TUICommonDefine.VideoEncoderParams.Resolution.fromInt(SettingsConfig.resolution)
        TUICallEngine.createInstance(applicationContext).setVideoEncoderParams(
            videoEncoderParams, object : TUICommonDefine.Callback {
                override fun onSuccess() {
                    AtomicToast.show(this@SettingsActivity, getString(R.string.app_set_success), AtomicToast.Style.SUCCESS)
                }

                override fun onError(errCode: Int, errMsg: String?) {
                    AtomicToast.show(
                        this@SettingsActivity,
                        getString(R.string.app_set_fail) + "| errorCode:" + errCode + ", " + "errMsg:" + errMsg,
                        AtomicToast.Style.ERROR
                    )
                }
            })
    }

    fun setVideoRenderParams() {
        val videoRenderParams = VideoRenderParams()
        videoRenderParams.rotation = Rotation.entries.toTypedArray()[SettingsConfig.rotation]
        videoRenderParams.fillMode = FillMode.entries.toTypedArray()[SettingsConfig.fillMode]
        TUICallEngine.createInstance(applicationContext).setVideoRenderParams(
            TUILogin.getLoginUser(), videoRenderParams, object : TUICommonDefine.Callback {
                override fun onSuccess() {
                    AtomicToast.show(this@SettingsActivity, getString(R.string.app_set_success), AtomicToast.Style.SUCCESS)
                }

                override fun onError(errCode: Int, errMsg: String?) {
                    AtomicToast.show(
                        this@SettingsActivity,
                        getString(R.string.app_set_fail) + "| errorCode:" + errCode + ", " + "errMsg:" + errMsg,
                        AtomicToast.Style.ERROR
                    )
                }
            })
    }

    private fun setDigitalRoomId() {
        val text = editDigitalRoomId?.getText().toString().trim { it <= ' ' }
        if (!TextUtils.isEmpty(text)) {
            SettingsConfig.intRoomId = text.toInt()
        } else {
            SettingsConfig.intRoomId = 0
            editDigitalRoomId?.setText("0")
        }
        AtomicToast.show(this, getString(R.string.app_set_success), AtomicToast.Style.SUCCESS)
    }

    private fun setStringRoomId() {
        SettingsConfig.strRoomId = editStringRoomId?.getText().toString().trim { it <= ' ' }
        AtomicToast.show(this, getString(R.string.app_set_success), AtomicToast.Style.SUCCESS)
    }
}