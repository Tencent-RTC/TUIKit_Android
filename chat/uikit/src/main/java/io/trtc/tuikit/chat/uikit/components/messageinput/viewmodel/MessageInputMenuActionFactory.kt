package io.trtc.tuikit.chat.uikit.components.messageinput.viewmodel

import android.content.Context
import io.trtc.tuikit.chat.uikit.R
import io.trtc.tuikit.chat.uikit.components.common.uicustom.CustomEditor
import io.trtc.tuikit.chat.uikit.components.messageinput.config.MessageInputConfigProtocol
import io.trtc.tuikit.chat.uikit.components.messageinput.config.MessageInputActionCustomizer
import io.trtc.tuikit.chat.uikit.components.messageinput.data.MessageInputActionIDs
import io.trtc.tuikit.chat.uikit.components.messageinput.data.MessageInputMenuAction
import io.trtc.tuikit.chat.uikit.components.messageinput.data.MessageInputMenuActionContext

internal data class MessageInputMenuActionCallbacks(
    val onPickMedia: () -> Unit = {},
    val onCaptureImage: () -> Unit = {},
    val onRecordVideo: () -> Unit = {},
    val onPickFile: () -> Unit = {},
    val onStartAudioCall: () -> Unit = {},
    val onStartVideoCall: () -> Unit = {}
)

internal data class MessageInputMenuActionLabels(
    val album: String,
    val takePhoto: String,
    val recordVideo: String,
    val file: String,
    val audioCall: String,
    val videoCall: String
) {
    companion object {
        fun from(context: Context): MessageInputMenuActionLabels {
            return MessageInputMenuActionLabels(
                album = context.getString(R.string.message_input_album),
                takePhoto = context.getString(R.string.message_input_take_photo),
                recordVideo = context.getString(R.string.message_input_record_video),
                file = context.getString(R.string.message_input_file),
                audioCall = context.getString(R.string.message_input_audio_call),
                videoCall = context.getString(R.string.message_input_video_call)
            )
        }
    }
}

internal data class MessageInputMenuActionIcons(
    val album: Int,
    val takePhoto: Int,
    val recordVideo: Int,
    val file: Int,
    val videoCall: Int,
    val audioCall: Int,
) {
    companion object {
        fun defaults(): MessageInputMenuActionIcons {
            return MessageInputMenuActionIcons(
                album = R.drawable.message_input_menu_album_icon,
                takePhoto = R.drawable.message_input_menu_camera_icon,
                recordVideo = R.drawable.message_input_menu_record_icon,
                file = R.drawable.message_input_menu_file_icon,
                videoCall = R.drawable.message_input_menu_video_call_icon,
                audioCall = R.drawable.message_input_menu_audio_call_icon,
            )
        }
    }
}

internal fun buildDefaultMessageInputMenuActions(
    isShowPhotoTaker: Boolean,
    isShowVideoCall: Boolean,
    isShowAudioCall: Boolean,
    labels: MessageInputMenuActionLabels,
    callbacks: MessageInputMenuActionCallbacks,
    icons: MessageInputMenuActionIcons = MessageInputMenuActionIcons.defaults(),
): List<MessageInputMenuAction> {
    val actions = mutableListOf<MessageInputMenuAction>()
    actions += MessageInputMenuAction(
        ID = MessageInputActionIDs.ALBUM,
        title = labels.album,
        iconResID = icons.album,
        onClick = callbacks.onPickMedia,
    )
    if (isShowPhotoTaker) {
        actions += MessageInputMenuAction(
            ID = MessageInputActionIDs.TAKE_PHOTO,
            title = labels.takePhoto,
            iconResID = icons.takePhoto,
            onClick = callbacks.onCaptureImage,
        )
        actions += MessageInputMenuAction(
            ID = MessageInputActionIDs.RECORD_VIDEO,
            title = labels.recordVideo,
            iconResID = icons.recordVideo,
            onClick = callbacks.onRecordVideo,
        )
    }
    actions += MessageInputMenuAction(
        ID = MessageInputActionIDs.FILE,
        title = labels.file,
        iconResID = icons.file,
        onClick = callbacks.onPickFile,
    )
    if (isShowVideoCall) {
        actions += MessageInputMenuAction(
            ID = MessageInputActionIDs.VIDEO_CALL,
            title = labels.videoCall,
            iconResID = icons.videoCall,
            onClick = callbacks.onStartVideoCall,
        )
    }
    if (isShowAudioCall) {
        actions += MessageInputMenuAction(
            ID = MessageInputActionIDs.AUDIO_CALL,
            title = labels.audioCall,
            iconResID = icons.audioCall,
            onClick = callbacks.onStartAudioCall,
        )
    }
    return actions
}

internal fun applyMessageInputActionCustomizer(
    actionContext: MessageInputMenuActionContext,
    defaults: List<MessageInputMenuAction>,
    customizer: MessageInputActionCustomizer?,
): List<MessageInputMenuAction> {
    if (customizer == null) {
        return defaults
    }
    val editor = CustomEditor(actionContext, defaults)
    customizer.customize(editor)
    return editor.build()
}

internal class MessageInputMenuActionFactory(
    private val config: MessageInputConfigProtocol,
    private val callbacks: MessageInputMenuActionCallbacks
) {
    fun create(context: Context, conversationID: String): List<MessageInputMenuAction> {
        val labels = MessageInputMenuActionLabels.from(context)
        val defaults = buildDefaultMessageInputMenuActions(
            isShowPhotoTaker = config.isShowPhotoTaker,
            isShowVideoCall = config.isShowVideoCall,
            isShowAudioCall = config.isShowAudioCall,
            labels = labels,
            callbacks = callbacks,
        )
        return applyMessageInputActionCustomizer(
            actionContext = MessageInputMenuActionContext(
                androidContext = context,
                conversationID = conversationID,
            ),
            defaults = defaults,
            customizer = config.actionCustomizer,
        )
    }
}
