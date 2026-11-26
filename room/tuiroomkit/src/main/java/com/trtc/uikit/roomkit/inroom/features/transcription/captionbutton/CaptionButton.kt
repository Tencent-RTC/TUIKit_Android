package com.trtc.uikit.roomkit.inroom.features.transcription.captionbutton

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.util.AttributeSet
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.trtc.uikit.roomkit.R
import com.trtc.uikit.roomkit.base.error.ErrorLocalized
import com.trtc.uikit.roomkit.base.popup.RoomPopup
import com.trtc.uikit.roomkit.base.util.dp
import com.trtc.uikit.roomkit.inroom.features.transcription.transcriptlist.TranscriptListView
import io.trtc.tuikit.atomicxcore.api.CompletionHandler
import io.trtc.tuikit.atomicxcore.api.ai.AITranscriberStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class CaptionButton @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : LinearLayout(context, attrs, defStyleAttr) {

    var onRequestDismiss: (() -> Unit)? = null

    private val icon: ImageView
    private val label: TextView
    private var subscribeJob: Job? = null
    private val actionPopup = RoomPopup(context)
    private val transcriptPopup = RoomPopup(context)
    private var actionContent: View? = null
    private var transcriptContent: TranscriptListView? = null
    private var isRunning = false
    private var roomID = ""

    init {
        orientation = VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        setPadding(context.dp(2), context.dp(10), context.dp(2), context.dp(10))
        LayoutInflater.from(context).inflate(R.layout.roomkit_caption_button, this, true)
        icon = findViewById(R.id.icon)
        label = findViewById(R.id.label)
        setOnClickListener {
            onRequestDismiss?.invoke()
            if (isRunning) showActions() else startTranscription()
        }
    }

    fun init(roomID: String) {
        this.roomID = roomID
        if (isAttachedToWindow) subscribe()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        subscribe()
    }

    override fun onDetachedFromWindow() {
        subscribeJob?.cancel()
        subscribeJob = null
        super.onDetachedFromWindow()
    }

    override fun setPressed(pressed: Boolean) {
        super.setPressed(pressed)
        val scale = if (pressed) 0.96f else 1f
        animate().setDuration(80L).scaleX(scale).scaleY(scale)
    }

    private fun subscribe() {
        subscribeJob?.cancel()
        subscribeJob = CoroutineScope(Dispatchers.Main).launch {
            AITranscriberStore.create(roomID).transcriberState.isTranscriptionRunning.collect { running ->
                isRunning = running
                updateButton()
            }
        }
    }

    private fun startTranscription() {
        val transcriberStore = AITranscriberStore.create(roomID)
        transcriberStore.startTranscription(
            transcriberStore.transcriberState.selfLanguage.value,
            transcriberStore.transcriberState.transcriptionConfig.value,
            object : CompletionHandler {
                override fun onSuccess() {}

                override fun onFailure(code: Int, desc: String) {
                    ErrorLocalized.showError(context, code, desc)
                }
            }
        )
    }

    private fun stopTranscription() {
        AITranscriberStore.create(roomID).stopTranscription(object : CompletionHandler {
            override fun onSuccess() {}

            override fun onFailure(code: Int, desc: String) {
                ErrorLocalized.showError(context, code, desc)
            }
        })
    }

    private fun updateButton() {
        val color = ContextCompat.getColor(
            context, if (isRunning) R.color.roomkit_accent else R.color.roomkit_fg_secondary,
        )
        icon.imageTintList = ColorStateList.valueOf(color)
        label.setTextColor(ContextCompat.getColor(context, R.color.roomkit_fg_secondary))
        if (!isRunning) actionPopup.dismiss()
    }

    private fun showActions() {
        if (actionContent == null) {
            actionPopup.panelBackgroundColor = Color.TRANSPARENT
            actionPopup.panelBorderColor = Color.TRANSPARENT
            actionPopup.showsHandleBar = false
            actionContent = LayoutInflater.from(context)
                .inflate(R.layout.roomkit_caption_actions, this, false).also { content ->
                    actionPopup.setContentView(content, RoomPopup.ContentHeight.AUTO)
                    content.findViewById<View>(R.id.caption_view_full).setOnClickListener {
                        actionPopup.dismiss()
                        showTranscript()
                    }
                    content.findViewById<View>(R.id.caption_close_live).setOnClickListener {
                        actionPopup.dismiss()
                        stopTranscription()
                    }
                    content.findViewById<View>(R.id.caption_cancel).setOnClickListener { actionPopup.dismiss() }
                }
        }
        actionPopup.show()
    }

    private fun showTranscript() {
        if (transcriptContent == null) {
            transcriptContent = TranscriptListView(context).also {
                it.init(roomID)
                transcriptPopup.setContentView(it)
            }
        }
        transcriptPopup.show()
    }

}
