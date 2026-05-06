package com.trtc.uikit.livekit.features.anchorprepare.view.liveinfoedit

import android.content.Context
import android.text.Editable
import android.text.InputType
import android.text.TextUtils
import android.text.TextWatcher
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import com.trtc.uikit.livekit.R
import com.trtc.uikit.livekit.common.COVER_URL_LIST
import com.trtc.uikit.livekit.features.anchorprepare.LiveStreamPrivacyStatus
import com.trtc.uikit.livekit.features.anchorprepare.store.AnchorPrepareState
import com.trtc.uikit.livekit.features.anchorprepare.store.AnchorPrepareState.Companion.MAX_INPUT_BYTE_LENGTH
import com.trtc.uikit.livekit.features.anchorprepare.store.AnchorPrepareStore
import com.trtc.uikit.livekit.features.anchorprepare.view.liveinfoedit.livecoverpicker.LiveCoverPicker
import io.trtc.tuikit.atomicx.common.imageloader.ImageLoader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.nio.charset.Charset

class LiveInfoEditView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private var store: AnchorPrepareStore? = null
    private var state: AnchorPrepareState? = null
    private lateinit var editRoomName: EditText
    private lateinit var textStreamPrivacyStatus: TextView
    private lateinit var imageStreamCover: ImageView
    private var liveCoverPicker: LiveCoverPicker? = null
    private var subscribeStateJob: Job? = null

    init {
        LayoutInflater.from(context).inflate(R.layout.anchor_prepare_layout_live_info_edit_view, this, true)
    }

    fun init(store: AnchorPrepareStore) {
        initStore(store)
        initView()
    }

    private fun initStore(s: AnchorPrepareStore) {
        this.store = s
        this.state = s.getState()
    }

    private fun initView() {
        bindViewId()
        initLiveNameEditText()
        initLiveCoverPicker()
        initLivePrivacyStatusPicker()
    }

    @Synchronized
    private fun addObserver() {
        state?.let { s ->
            subscribeStateJob = CoroutineScope(Dispatchers.Main).launch {
                launch {
                    s.coverURL.collect {
                        onLiveCoverChange(it)
                    }
                }

                launch {
                    s.liveMode.collect {
                        onLivePrivacyStatusChange(it)
                    }
                }
            }
        }
    }

    @Synchronized
    fun removeObserver() {
        subscribeStateJob?.cancel()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        addObserver()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        removeObserver()
    }

    private fun bindViewId() {
        imageStreamCover = findViewById(R.id.iv_cover)
        editRoomName = findViewById(R.id.et_stream_name)
        textStreamPrivacyStatus = findViewById(R.id.tv_stream_privacy_status)
    }

    private fun initLiveCoverPicker() {
        val coverSettingsLayout = findViewById<View>(R.id.fl_cover_edit)
        ImageLoader.load(
            context,
            imageStreamCover,
            state?.coverURL?.value,
            R.drawable.anchor_prepare_live_stream_default_cover
        )

        coverSettingsLayout.setOnClickListener {
            if (liveCoverPicker == null) {
                val config = LiveCoverPicker.Config().apply {
                    title = context.getString(R.string.common_cover)
                    confirmButtonText = context.getString(R.string.common_set_as_cover)
                    data = COVER_URL_LIST.toList()
                    currentImageUrl = state?.coverURL?.value ?: ""
                }
                liveCoverPicker = LiveCoverPicker(context, config)
                liveCoverPicker?.setOnItemClickListener { imageUrl ->
                    store?.setCoverURL(imageUrl)
                }
            }
            liveCoverPicker?.show()
        }
    }

    private fun initLiveNameEditText() {
        val roomName = store?.getDefaultRoomName() ?: ""
        editRoomName.setText(roomName)
        store?.setRoomName(roomName)

        editRoomName.keyListener = editRoomName.keyListener
        editRoomName.ellipsize = TextUtils.TruncateAt.END
        editRoomName.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                editRoomName.ellipsize = null
                editRoomName.inputType = InputType.TYPE_CLASS_TEXT
                editRoomName.setSelection(editRoomName.text.length)
                editRoomName.post {
                    val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                    imm?.showSoftInput(editRoomName, InputMethodManager.SHOW_IMPLICIT)
                }
            } else {
                editRoomName.inputType = InputType.TYPE_NULL
                editRoomName.ellipsize = TextUtils.TruncateAt.END
            }
        }

        editRoomName.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

            override fun afterTextChanged(editable: Editable?) {
                if (editable.isNullOrBlank()) {
                    return
                }
                var newString = editable.toString()
                if (!checkLength(newString)) {
                    for (i in editable.length downTo 0) {
                        if (i > 0 && Character.isHighSurrogate(editable[i - 1])) {
                            continue
                        }
                        val s = editable.subSequence(0, i).toString()
                        if (checkLength(s)) {
                            newString = s
                            break
                        }
                    }
                    editRoomName.setText(newString)
                    editRoomName.setSelection(newString.length)
                }
                store?.setRoomName(newString)
            }

            private fun checkLength(s: String): Boolean {
                return s.toByteArray(Charset.defaultCharset()).size <= MAX_INPUT_BYTE_LENGTH
            }
        })
    }

    private fun initLivePrivacyStatusPicker() {
        findViewById<View>(R.id.ll_stream_privacy_status).setOnClickListener {
            store?.let { mgr ->
                val picker = LivePrivacyStatusPicker(context, mgr)
                picker.show()
            }
        }
    }

    private fun onLiveCoverChange(coverURL: String?) {
        ImageLoader.load(
            context,
            imageStreamCover,
            coverURL,
            R.drawable.anchor_prepare_live_stream_default_cover
        )
    }

    private fun onLivePrivacyStatusChange(status: LiveStreamPrivacyStatus?) {
        status?.let {
            textStreamPrivacyStatus.setText(it.resId)
        }
    }
}