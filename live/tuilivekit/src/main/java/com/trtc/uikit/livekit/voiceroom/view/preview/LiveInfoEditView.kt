package com.trtc.uikit.livekit.voiceroom.view.preview

import android.content.Context
import android.text.Editable
import android.text.TextUtils
import android.text.TextWatcher
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import com.trtc.uikit.livekit.R
import com.trtc.uikit.livekit.common.COVER_URL_LIST
import com.trtc.uikit.livekit.voiceroom.manager.VoiceRoomManager
import com.trtc.uikit.livekit.voiceroom.store.LiveStreamPrivacyStatus
import com.trtc.uikit.livekit.voiceroom.view.basic.BasicView
import io.trtc.tuikit.atomicx.common.imageloader.ImageLoader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.nio.charset.Charset

class LiveInfoEditView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : BasicView(context, attrs, defStyleAttr) {

    companion object {
        private const val MAX_INPUT_BYTE_LENGTH = 100
    }

    private var editRoomName: EditText
    private var textStreamPrivacyStatus: TextView
    private var imageStreamCover: ImageView
    private var streamPresetImagePicker: StreamPresetImagePicker? = null

    init {
        LayoutInflater.from(context).inflate(
            R.layout.livekit_layout_anchor_preview_live_info_edit,
            this, true
        )
        initLivePrivacyStatusPicker()
        imageStreamCover = findViewById(R.id.iv_cover)
        textStreamPrivacyStatus = findViewById(R.id.tv_stream_privacy_status)
        editRoomName = findViewById(R.id.et_stream_name)
        editRoomName.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {

            }

            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {

            }

            override fun afterTextChanged(editable: Editable) {
                if (TextUtils.isEmpty(editable)) {
                    return
                }
                var newString = editable.toString()
                if (!checkLength(editable.toString())) {
                    for (i in editable.length downTo 1) {
                        val s = editable.subSequence(0, i).toString()
                        if (checkLength(s)) {
                            newString = s
                            editRoomName.setText(s)
                            editRoomName.setSelection(s.length)
                            break
                        }
                    }
                }
                voiceRoomManager?.prepareStore?.updateLiveName(newString)
            }

            private fun checkLength(s: String): Boolean {
                return s.toByteArray(Charset.defaultCharset()).size <= MAX_INPUT_BYTE_LENGTH
            }
        })
    }

    override fun init(liveID: String, voiceRoomManager: VoiceRoomManager) {
        super.init(liveID, voiceRoomManager)
        initLiveNameEditText()
        initLiveCoverPicker()
    }

    override fun addObserver() {
        subscribeStateJob = CoroutineScope(Dispatchers.Main).launch {
            launch {
                voiceRoomManager?.prepareStore?.prepareState?.liveInfo
                    ?.map { it.coverURL }
                    ?.distinctUntilChanged()
                    ?.collect {
                        onLiveCoverChanged(it)
                    }
            }
            launch {
                voiceRoomManager?.prepareStore?.prepareState?.liveExtraInfo
                    ?.map { it.liveMode }
                    ?.distinctUntilChanged()
                    ?.collect {
                        onLivePrivacyStatusChange(it)
                    }
            }

        }
    }

    override fun removeObserver() {
        subscribeStateJob?.cancel()
    }

    private fun initLiveCoverPicker() {
        val coverSettingsLayout = findViewById<View>(R.id.fl_cover_edit)
        ImageLoader.load(
            context,
            imageStreamCover,
            voiceRoomManager?.prepareStore?.prepareState?.liveInfo?.value?.coverURL,
            R.drawable.anchor_prepare_live_stream_default_cover
        )
        coverSettingsLayout.setOnClickListener {
            if (streamPresetImagePicker == null) {
                val config = StreamPresetImagePicker.Config()
                config.title = context.getString(R.string.common_cover)
                config.confirmButtonText = context.getString(R.string.common_set_as_cover)
                config.data = COVER_URL_LIST
                config.currentImageUrl =
                    voiceRoomManager?.prepareStore?.prepareState?.liveInfo?.value?.coverURL
                streamPresetImagePicker = StreamPresetImagePicker(context, config)
                streamPresetImagePicker?.setOnConfirmListener(object :
                    StreamPresetImagePicker.OnConfirmListener {
                    override fun onConfirm(imageUrl: String) {
                        voiceRoomManager?.prepareStore?.updateLiveCoverURL(imageUrl)
                    }
                })
            }
            streamPresetImagePicker?.show()
        }
    }

    private fun initLiveNameEditText() {
        val roomName = voiceRoomManager?.prepareStore?.getDefaultRoomName()
        editRoomName.setText(roomName)
        voiceRoomManager?.prepareStore?.updateLiveName(roomName ?: "")
    }

    private fun initLivePrivacyStatusPicker() {
        findViewById<View>(R.id.ll_stream_privacy_status).setOnClickListener {
            if (voiceRoomManager == null) return@setOnClickListener
            val picker = StreamPrivacyStatusPicker(context, voiceRoomManager!!)
            picker.show()
        }
    }

    private fun onLiveCoverChanged(coverURL: String?) {
        ImageLoader.load(
            context,
            imageStreamCover,
            coverURL,
            R.drawable.anchor_prepare_live_stream_default_cover
        )
    }

    private fun onLivePrivacyStatusChange(status: LiveStreamPrivacyStatus) {
        textStreamPrivacyStatus.setText(status.resId)
    }

    override fun initStore() {

    }
}