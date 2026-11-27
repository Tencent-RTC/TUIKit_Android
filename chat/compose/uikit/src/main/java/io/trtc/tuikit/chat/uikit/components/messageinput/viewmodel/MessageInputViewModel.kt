package io.trtc.tuikit.chat.uikit.components.messageinput.viewmodel

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.util.Log
import android.webkit.MimeTypeMap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.trtc.tuikit.chat.uikit.compose.R
import io.trtc.tuikit.atomicx.albumpicker.AlbumMedia
import io.trtc.tuikit.atomicx.albumpicker.AlbumMediaType
import io.trtc.tuikit.atomicx.albumpicker.AlbumPickerConfig
import io.trtc.tuikit.atomicx.albumpicker.AlbumPickerMediaFilter
import io.trtc.tuikit.atomicx.albumpicker.AlbumPickerStyle
import io.trtc.tuikit.atomicx.albumpicker.AlbumPickerTheme
import io.trtc.tuikit.chat.uikit.components.ai.AiMediaProcessManager
import io.trtc.tuikit.chat.uikit.components.ai.tts.TtsPlaybackHelper
import io.trtc.tuikit.chat.uikit.components.ai.tts.TtsTextSanitizer
import io.trtc.tuikit.chat.uikit.components.ai.tts.VoiceMessageConfig
import com.tencent.imsdk.v2.V2TIMGroupListener
import com.tencent.imsdk.v2.V2TIMManager
import com.tencent.imsdk.v2.V2TIMValueCallback
import io.trtc.tuikit.chat.uikit.components.widgets.Toast
import io.trtc.tuikit.chat.uikit.components.common.AtomicCallEventPublisher
import io.trtc.tuikit.chat.uikit.components.common.GroupCallAttributeParser
import io.trtc.tuikit.chat.uikit.components.common.MessageOfflinePushInfoFactory
import io.trtc.tuikit.chat.uikit.components.common.appContext
import io.trtc.tuikit.chat.uikit.components.emojipicker.replaceEmojiKeysWithNames
import io.trtc.tuikit.chat.uikit.components.filepicker.FilePicker
import io.trtc.tuikit.chat.uikit.components.filepicker.FilePickerListener
import io.trtc.tuikit.chat.uikit.components.messageinput.config.ChatMessageInputConfig
import io.trtc.tuikit.chat.uikit.components.messageinput.config.MessageInputConfigProtocol
import io.trtc.tuikit.chat.uikit.components.messageinput.data.MessageInputActionRegistry
import io.trtc.tuikit.chat.uikit.components.messageinput.data.MessageInputMenuAction
import io.trtc.tuikit.chat.uikit.components.messageinput.model.MentionInfo
import io.trtc.tuikit.chat.uikit.components.messageinput.utils.FileUtils
import io.trtc.tuikit.chat.uikit.components.messageinput.utils.ImageUtil
import io.trtc.tuikit.chat.uikit.components.messageinput.utils.MessageInputAlbumPickerActivity
import io.trtc.tuikit.chat.uikit.components.messageinput.utils.VideoFrameExtractor
import io.trtc.tuikit.chat.uikit.components.messagelist.utils.isGroupConversation
import io.trtc.tuikit.chat.uikit.components.videorecorder.RecordMode
import io.trtc.tuikit.chat.uikit.components.videorecorder.VideoRecordListener
import io.trtc.tuikit.chat.uikit.components.videorecorder.VideoRecorder
import io.trtc.tuikit.chat.uikit.components.videorecorder.VideoRecorderConfig
import io.trtc.tuikit.atomicxcore.api.CompletionHandler
import io.trtc.tuikit.atomicxcore.api.conversation.ConversationInfo
import io.trtc.tuikit.atomicxcore.api.conversation.ConversationListStore
import io.trtc.tuikit.atomicxcore.api.conversation.GetConversationInfoCompletionHandler
import io.trtc.tuikit.atomicxcore.api.group.GroupMember
import io.trtc.tuikit.atomicxcore.api.group.GroupMemberFilterRole
import io.trtc.tuikit.atomicxcore.api.group.GroupMemberStore
import io.trtc.tuikit.atomicxcore.api.group.GroupType
import io.trtc.tuikit.atomicxcore.api.login.LoginStore
import io.trtc.tuikit.atomicxcore.api.message.MessageInfo
import io.trtc.tuikit.atomicxcore.api.message.MessageInputStore
import io.trtc.tuikit.atomicxcore.api.message.OfflinePushInfo
import io.trtc.tuikit.atomicxcore.api.message.SendMessageOption
import io.trtc.tuikit.atomicxcore.api.message.SendMessagePayload
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import java.util.Date
import kotlin.coroutines.resume
import kotlin.math.roundToInt
import kotlin.random.Random

const val FILE_MAX_SIZE = 100 * 1024 * 1024
const val VIDEO_MAX_SIZE = 100 * 1024 * 1024
const val IMAGE_MAX_SIZE = 28 * 1024 * 1024
const val GIF_IMAGE_MAX_SIZE = 10 * 1024 * 1024
const val AUDIO_MAX_RECORD_TIME = 60 * 1000
const val AUDIO_MIN_RECORD_TIME = 2 * 1000

data class GroupCallPickerRequest(
    val title: String,
    val mediaType: String,
    val groupId: String,
    val candidates: List<GroupMember>,
    val maxSelection: Int = 9
)

class MessageInputViewModel(
    private val messageInputStore: MessageInputStore,
    val conversationID: String,
    private val messageInputConfig: MessageInputConfigProtocol = ChatMessageInputConfig()
) : ViewModel() {

    val conversationListStore = ConversationListStore.create()
    private val conversationListState = conversationListStore.state

    private var canSendTextMessage: () -> Boolean = { true }
    private var onTextMessageSent: () -> Unit = {}
    private val attachmentFileResolver = MessageInputAttachmentFileResolver()
    private val audioTranscriber = AudioTranscriber()
    private val recordTranslationTtsHelper = TtsPlaybackHelper()

    private var currentConversationTitle: String? = null

    private val _conversationInfo = MutableStateFlow<ConversationInfo?>(null)
    val conversationInfo: StateFlow<ConversationInfo?> = _conversationInfo.asStateFlow()

    private val _groupCallPickerRequest = MutableStateFlow<GroupCallPickerRequest?>(null)
    val groupCallPickerRequest: StateFlow<GroupCallPickerRequest?> = _groupCallPickerRequest.asStateFlow()

    private val _hasOngoingGroupCall = MutableStateFlow(false)
    internal val hasOngoingGroupCall: StateFlow<Boolean> = _hasOngoingGroupCall.asStateFlow()
    private var groupCallAttributeListener: V2TIMGroupListener? = null

    init {
        conversationListStore.getConversationInfo(conversationID, object : GetConversationInfoCompletionHandler {
            override fun onSuccess(conversationInfo: ConversationInfo) {
                _conversationInfo.value = conversationInfo
            }

            override fun onFailure(code: Int, desc: String) {}
        })
        viewModelScope.launch {
            conversationListState.conversationList.collectLatest { list ->
                list.firstOrNull { it.conversationID == conversationID }?.let { info ->
                    _conversationInfo.value = info
                }
            }
        }
        viewModelScope.launch {
            conversationInfo.collectLatest { info ->
                currentConversationTitle = info?.title
            }
        }
        observeGroupCallStatus()
    }

    fun getActions(
        context: Context,
        config: MessageInputConfigProtocol = messageInputConfig,
    ): List<MessageInputMenuAction> {
        val isCallActionEnabled = !_hasOngoingGroupCall.value
        val defaults = MessageInputMenuActionFactory(
            config = config,
            callbacks = MessageInputMenuActionCallbacks(
                onPickMedia = { pickMediaAndSend(context) },
                onCaptureImage = { captureImageAndSend(context) },
                onRecordVideo = { recordVideoAndSend(context) },
                onPickFile = { pickFileAndSend(context) },
                onStartAudioCall = { startAudioCall(context) },
                onStartVideoCall = { startVideoCall(context) }
            )
        ).create(
            context = context,
            conversationID = conversationID,
            isCallActionEnabled = isCallActionEnabled
        )
        return defaults + MessageInputActionRegistry.resolveCustomActions(context, conversationID)
    }

    fun pickMediaAndSend(context: Context) {
        val config = AlbumPickerConfig(
            mediaFilter = AlbumPickerMediaFilter.ALL,
            maxSelectionCount = ALBUM_PICKER_MAX_SELECTION,
            style = AlbumPickerStyle.LIKE_WECHAT,
        )
        MessageInputAlbumPickerActivity.start(
            context = context,
            config = config,
            theme = AlbumPickerTheme(),
            listener = AlbumPickerMediaSendCoordinator(
                onProcessingStarted = { media ->
                    AlbumPickerProcessingMessageStore.upsert(conversationID, media, progress = 0)
                },
                onProcessingProgress = { media, progress ->
                    AlbumPickerProcessingMessageStore.upsert(conversationID, media, progress)
                },
                onProcessingFinished = { media ->
                    AlbumPickerProcessingMessageStore.remove(conversationID, media.id)
                },
                onSendProcessedMedia = { media, path ->
                    sendAlbumPickerMedia(context, media, path)
                },
                onSendOriginalMedia = { media ->
                    Log.w(TAG, "AlbumPicker media processing error: id=${media.id}")
                    sendOriginalAlbumPickerMedia(context, media)
                },
                onSendText = { text ->
                    sendTextMessage(context, text, emptyList())
                },
                shouldProcessMedia = { media ->
                    isAlbumPickerMediaWithinSizeLimit(context, media)
                },
                onMediaRejected = {
                    showFileTooLarge(context)
                }
            )
        )
    }

    private fun sendAlbumPickerMedia(context: Context, media: AlbumMedia, path: String) {
        if (media.mediaType == AlbumMediaType.VIDEO) {
            sendVideoMessage(context, path)
        } else {
            sendImageMessage(context, path)
        }
    }

    private fun sendOriginalAlbumPickerMedia(context: Context, media: AlbumMedia) {
        viewModelScope.launch(Dispatchers.IO) {
            val mediaPath = media.mediaPath?.takeIf { it.isNotBlank() }
            if (mediaPath != null) {
                if (!isAlbumPickerPathWithinSizeLimit(context, media, mediaPath)) {
                    showFileTooLarge(context)
                    return@launch
                }
                sendAlbumPickerMedia(context, media, mediaPath)
                return@launch
            }

            val uri = media.uri
            if (uri == null) {
                showSendFailed(context)
                return@launch
            }
            val resolvedFile = attachmentFileResolver.resolveFileForSend(
                context = context,
                uri = uri,
                maxFileSizeBytes = getAlbumPickerMediaMaxSizeBytes(context, media)
            )
            when (resolvedFile) {
                is MessageInputAttachmentFileResolver.ResolvedFile.Success -> {
                    sendAlbumPickerMedia(context, media, resolvedFile.filePath)
                }
                MessageInputAttachmentFileResolver.ResolvedFile.FileTooLarge -> {
                    showFileTooLarge(context)
                }
                MessageInputAttachmentFileResolver.ResolvedFile.Failure -> {
                    showSendFailed(context)
                }
            }
        }
    }

    private fun isAlbumPickerMediaWithinSizeLimit(context: Context, media: AlbumMedia): Boolean {
        val fileSize = media.mediaPath
            ?.takeIf { it.isNotBlank() && File(it).exists() }
            ?.let { attachmentFileResolver.getFileSize(it) }
            ?: media.uri?.let { attachmentFileResolver.getDeclaredFileSize(context, it) }
        if (fileSize == null) {
            return true
        }
        return !AlbumPickerMediaSizeGuard.isTooLarge(
            fileSize = fileSize,
            mediaType = media.mediaType,
            mimeType = getAlbumPickerMediaMimeType(context, media),
            fileName = getAlbumPickerMediaFileName(context, media)
        )
    }

    private fun isAlbumPickerPathWithinSizeLimit(
        context: Context,
        media: AlbumMedia,
        path: String
    ): Boolean {
        return !AlbumPickerMediaSizeGuard.isTooLarge(
            fileSize = attachmentFileResolver.getFileSize(path),
            mediaType = media.mediaType,
            mimeType = getAlbumPickerMediaMimeType(context, media),
            fileName = attachmentFileResolver.getFileName(path) ?: getAlbumPickerMediaFileName(context, media)
        )
    }

    private fun getAlbumPickerMediaMaxSizeBytes(context: Context, media: AlbumMedia): Long {
        return AlbumPickerMediaSizeGuard.maxSizeBytes(
            mediaType = media.mediaType,
            mimeType = getAlbumPickerMediaMimeType(context, media),
            fileName = getAlbumPickerMediaFileName(context, media)
        )
    }

    private fun getAlbumPickerMediaMimeType(context: Context, media: AlbumMedia): String? {
        val uriMimeType = media.uri?.let { uri ->
            runCatching { context.contentResolver.getType(uri) }.getOrNull()
        }
        if (!uriMimeType.isNullOrBlank()) {
            return uriMimeType
        }
        val fileName = getAlbumPickerMediaFileName(context, media).orEmpty()
        val extension = attachmentFileResolver.getFileExtensionFromUrl(fileName)
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
    }

    private fun getAlbumPickerMediaFileName(context: Context, media: AlbumMedia): String? {
        return media.mediaPath
            ?.let { attachmentFileResolver.getFileName(it) }
            ?: media.uri?.let { attachmentFileResolver.getFileName(context, it) }
    }

    fun captureImageAndSend(context: Context) {
        VideoRecorder.startRecord(
            VideoRecorderConfig(
                recordMode = RecordMode.PHOTO_ONLY,
            ), object : VideoRecordListener {
                override fun onPhotoCaptured(filePath: String?) {
                    if (!filePath.isNullOrEmpty()) {
                        sendImageMessage(context, filePath)
                    }
                }
            })
    }

    fun recordVideoAndSend(context: Context) {
        VideoRecorder.startRecord(
            VideoRecorderConfig(
                recordMode = RecordMode.MIXED,
            ), object : VideoRecordListener {
                override fun onVideoCaptured(filePath: String?, durationMs: Int, thumbnailPath: String?) {
                    if (!filePath.isNullOrEmpty()) {
                        sendVideoMessage(context, filePath)
                    }
                }

                override fun onPhotoCaptured(filePath: String?) {
                    if (!filePath.isNullOrEmpty()) {
                        sendImageMessage(context, filePath)
                    }
                }
            })
    }

    fun sendTextMessage(
        context: Context?,
        text: String,
        mentionList: List<MentionInfo>,
        quotedMessage: MessageInfo? = null,
        onSuccess: (() -> Unit)? = null
    ) {
        trySendTextMessage(context, text, mentionList, quotedMessage, onSuccess)
    }

    fun trySendTextMessage(
        context: Context?,
        text: String,
        mentionList: List<MentionInfo>,
        quotedMessage: MessageInfo? = null,
        onSuccess: (() -> Unit)? = null
    ): Boolean {
        if (!canSendTextMessage()) {
            context?.let {
                Toast.warning(it, it.getString(R.string.message_input_chatbot_waiting_tips))
            }
            return false
        }
        val payload = SendMessagePayload.TextSendMessagePayload(text)
        val option = createSendMessageOption(
            context = context,
            payload = payload,
            atUserList = mentionList.map { it.userID }.takeIf { it.isNotEmpty() },
            quotedMessage = quotedMessage
        )
        messageInputStore.sendMessage(payload, option, object : CompletionHandler {
            override fun onSuccess() {
                onTextMessageSent()
                onSuccess?.invoke()
            }

            override fun onFailure(code: Int, desc: String) {
                context?.let { showSendFailed(it) }
            }
        })
        return true
    }

    fun sendFaceMessage(
        context: Context?,
        faceIndex: Int,
        faceData: String,
        quotedMessage: MessageInfo? = null,
        onSuccess: (() -> Unit)? = null
    ) {
        if (faceData.isEmpty()) {
            return
        }
        val payload = SendMessagePayload.FaceSendMessagePayload(index = faceIndex, data = faceData)
        val option = createSendMessageOption(
            context = context,
            payload = payload,
            quotedMessage = quotedMessage
        )
        messageInputStore.sendMessage(payload, option, object : CompletionHandler {
            override fun onSuccess() {
                onSuccess?.invoke()
            }

            override fun onFailure(code: Int, desc: String) {
                context?.let { showSendFailed(it) }
            }
        })
    }

    fun sendImageMessage(context: Context, filePath: String) {
        viewModelScope.launch(Dispatchers.IO) {
            if (!MessageInputSendGuards.isReadableFilePath(filePath)) {
                showSendFailed(context)
                return@launch
            }
            val path: String = ImageUtil.getImagePathAfterRotate(context, filePath)
            if (!MessageInputSendGuards.isReadableFilePath(path)) {
                showSendFailed(context)
                return@launch
            }
            val size = ImageUtil.getImageSize(path)
            val fileSize = attachmentFileResolver.getFileSize(path)
            val fileName: String = attachmentFileResolver.getFileName(path) ?: ""
            val fileExtension: String = attachmentFileResolver.getFileExtensionFromUrl(fileName)
            val mimeType =
                MimeTypeMap.getSingleton().getMimeTypeFromExtension(fileExtension)
            if (TextUtils.equals(mimeType, "image/gif")) {
                if (fileSize > GIF_IMAGE_MAX_SIZE) {
                    showFileTooLarge(context)
                    return@launch
                }
            } else {
                if (fileSize > IMAGE_MAX_SIZE) {
                    showFileTooLarge(context)
                    return@launch
                }
            }
            val payload = SendMessagePayload.ImageSendMessagePayload(
                imagePath = path,
                imageWidth = size[0],
                imageHeight = size[1]
            )
            val option = createSendMessageOption(context, payload)
            messageInputStore.sendMessage(payload, option, object : CompletionHandler {
                override fun onSuccess() {
                }

                override fun onFailure(code: Int, desc: String) {
                    showSendFailed(context)
                }
            })
        }
    }

    fun sendVideoMessage(context: Context, filePath: String) {
        viewModelScope.launch(Dispatchers.IO) {
            if (!MessageInputSendGuards.isReadableFilePath(filePath)) {
                showSendFailed(context)
                return@launch
            }
            val fileSize = attachmentFileResolver.getFileSize(filePath)
            if (fileSize > VIDEO_MAX_SIZE) {
                showFileTooLarge(context)
                return@launch
            }
            val videoFrameInfo = VideoFrameExtractor.extractVideoFrameInfo(filePath)
            if (videoFrameInfo == null) {
                Log.e(TAG, "build video message, extract video frame failed.")
                showSendFailed(context)
                return@launch
            }
            val bitmap = videoFrameInfo.bitmap
            try {
                val bitmapWidth = bitmap.width
                val bitmapHeight = bitmap.height
                val sdkAppID = LoginStore.shared.sdkAppID
                val userID = LoginStore.shared.loginState.loginUserInfo.value?.userID
                val uuid = "${(Date().time / 1000)}_${Random.nextInt(1000)}"
                val basePath = context.filesDir?.absolutePath + "/atomicx_data/image/"
                val bitmapPath = "${basePath}_${sdkAppID}_${userID ?: ""}$uuid.jpg"
                val result: Boolean = FileUtils.saveBitmap(bitmapPath, bitmap)
                if (!result) {
                    Log.e(TAG, "build video message, save bitmap failed.")
                    showSendFailed(context)
                    return@launch
                }

                val payload = SendMessagePayload.VideoSendMessagePayload(
                    videoFilePath = filePath,
                    videoType = "mp4",
                    duration = (videoFrameInfo.durationMs / 1000f).roundToInt(),
                    snapshotPath = bitmapPath,
                    snapshotWidth = bitmapWidth,
                    snapshotHeight = bitmapHeight
                )
                val option = createSendMessageOption(context, payload)
                messageInputStore.sendMessage(payload, option, object : CompletionHandler {
                    override fun onSuccess() {
                        Log.i(TAG, "send video message success.")
                    }

                    override fun onFailure(code: Int, desc: String) {
                        Log.e(TAG, "send video message failed, code: $code, desc: $desc.")
                        showSendFailed(context)
                    }
                })
            } finally {
                if (!bitmap.isRecycled) {
                    bitmap.recycle()
                }
            }
        }
    }

    fun pickFileAndSend(context: Context) {
        FilePicker.pickFiles(listener = object : FilePickerListener {
            override fun onPicked(result: List<Uri>) {
                viewModelScope.launch(Dispatchers.IO) {
                    result.forEach { uri ->
                        sendFileMessage(context, uri)
                        delay(100)
                    }
                }
            }

            override fun onCanceled() {
            }
        })
    }

    fun sendFileMessage(context: Context, uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            val resolvedFile = attachmentFileResolver.resolveFileForSend(
                context = context,
                uri = uri,
                maxFileSizeBytes = FILE_MAX_SIZE.toLong()
            )
            val file = when (resolvedFile) {
                is MessageInputAttachmentFileResolver.ResolvedFile.Success -> resolvedFile
                MessageInputAttachmentFileResolver.ResolvedFile.FileTooLarge -> {
                    showFileTooLarge(context)
                    return@launch
                }

                MessageInputAttachmentFileResolver.ResolvedFile.Failure -> {
                    showSendFailed(context)
                    return@launch
                }
            }
            val payload = SendMessagePayload.FileSendMessagePayload(
                filePath = file.filePath,
                fileName = file.fileName,
                fileSize = file.fileSize.toInt()
            )
            val option = createSendMessageOption(context, payload)
            messageInputStore.sendMessage(payload, option, object : CompletionHandler {
                override fun onSuccess() {
                }

                override fun onFailure(code: Int, desc: String) {
                    showSendFailed(context)
                }
            })
        }
    }

    fun convertLocalAudioToText(context: Context, filePath: String, onCompleted: (String?) -> Unit) {
        if (!MessageInputSendGuards.isReadableFilePath(filePath)) {
            showConvertVoiceToTextFailed(context)
            runOnMain { onCompleted(null) }
            return
        }
        audioTranscriber.convert(
            filePath = filePath,
            onFailure = { code, desc ->
                Log.e(TAG, "convert local audio to text failed, code: $code, desc: $desc")
                showConvertVoiceToTextFailed(context)
            },
            onCompleted = { text -> runOnMain { onCompleted(text) } }
        )
    }

    fun translateRecordText(
        text: String,
        targetLanguage: String,
        onSuccess: (String) -> Unit,
        onFailure: () -> Unit
    ) {
        if (text.isBlank() || targetLanguage.isBlank()) {
            runOnMain { onFailure() }
            return
        }
        AiMediaProcessManager.translateSingleText(
            text = text,
            targetLanguage = targetLanguage,
            onSuccess = { translated -> onSuccess(translated) },
            onFailure = { _, _ -> onFailure() }
        )
    }

    fun startRecordTranslationSpeak(
        context: Context,
        text: String,
        onStart: () -> Unit,
        onComplete: () -> Unit,
        onError: () -> Unit
    ) {
        val sanitized = TtsTextSanitizer.sanitize(text)
        if (sanitized.isBlank()) {
            runOnMain { onError() }
            return
        }
        val voiceId = VoiceMessageConfig.getSelectedVoiceId(context)
        recordTranslationTtsHelper.speak(
            text = sanitized,
            voiceId = voiceId,
            onStart = { runOnMain { onStart() } },
            onComplete = { runOnMain { onComplete() } },
            onError = { runOnMain { onError() } }
        )
    }

    fun stopRecordTranslationSpeak() {
        recordTranslationTtsHelper.stop()
    }

    override fun onCleared() {
        super.onCleared()
        groupCallAttributeListener?.let { listener ->
            runCatching { V2TIMManager.getInstance().removeGroupListener(listener) }
            groupCallAttributeListener = null
        }
        recordTranslationTtsHelper.stop()
    }

    fun sendAudioMessage(filePath: String, duration: Int) {
        if (!MessageInputSendGuards.isReadableFilePath(filePath)) {
            Log.e(TAG, "send audio message failed, invalid file path.")
            return
        }
        val payload = SendMessagePayload.AudioSendMessagePayload(
            audioFilePath = filePath,
            duration = duration
        )
        val option = createSendMessageOption(null, payload)
        messageInputStore.sendMessage(payload, option, object : CompletionHandler {
            override fun onSuccess() {
                Log.i(TAG, "send audio message success.")
            }

            override fun onFailure(code: Int, desc: String) {
                Log.e(TAG, "send audio message failed, code: $code, desc: $desc.")
            }
        })
    }

    fun startAudioCall(context: Context) {
        startCall(context, AtomicCallEventPublisher.MEDIA_TYPE_AUDIO)
    }

    fun startVideoCall(context: Context) {
        startCall(context, AtomicCallEventPublisher.MEDIA_TYPE_VIDEO)
    }

    private fun startCall(context: Context, mediaType: String) {
        val targetUserId = c2cTargetUserId()
        if (targetUserId != null) {
            AtomicCallEventPublisher.publishStartCall(
                participantIds = listOf(targetUserId),
                mediaType = mediaType,
                chatGroupId = null
            )
            return
        }

        val groupId = groupTargetId() ?: return
        if (_hasOngoingGroupCall.value) {
            showGroupCallInProgressToast(context)
            return
        }
        showGroupCallMemberPicker(context, groupId, mediaType)
    }

    private fun c2cTargetUserId(): String? {
        return conversationID.takeIf { it.startsWith(C2C_CONVERSATION_PREFIX) }
            ?.removePrefix(C2C_CONVERSATION_PREFIX)
            ?.takeIf { it.isNotEmpty() }
    }

    private fun groupTargetId(): String? {
        return conversationID.takeIf { it.startsWith(GROUP_CONVERSATION_PREFIX) }
            ?.removePrefix(GROUP_CONVERSATION_PREFIX)
            ?.takeIf { it.isNotEmpty() }
    }

    private fun observeGroupCallStatus() {
        val groupId = groupTargetId() ?: return
        val listener = object : V2TIMGroupListener() {
            override fun onGroupAttributeChanged(
                groupID: String?,
                groupAttributeMap: MutableMap<String?, String>?
            ) {
                if (groupID != groupId) {
                    return
                }
                _hasOngoingGroupCall.value = GroupCallAttributeParser.hasOngoingGroupCall(
                    groupAttributeMap?.get(GroupCallAttributeParser.GROUP_CALL_ATTRIBUTE_KEY)
                )
            }
        }
        groupCallAttributeListener = listener
        runCatching {
            V2TIMManager.getInstance().addGroupListener(listener)
        }
        refreshGroupCallStatus()
    }

    internal fun refreshGroupCallStatus() {
        val groupId = groupTargetId() ?: return
        runCatching {
            V2TIMManager.getGroupManager().getGroupAttributes(
                groupId,
                listOf(GroupCallAttributeParser.GROUP_CALL_ATTRIBUTE_KEY),
                object : V2TIMValueCallback<Map<String?, String?>?> {
                    override fun onSuccess(map: Map<String?, String?>?) {
                        _hasOngoingGroupCall.value = GroupCallAttributeParser.hasOngoingGroupCall(
                            map?.get(GroupCallAttributeParser.GROUP_CALL_ATTRIBUTE_KEY)
                        )
                    }

                    override fun onError(code: Int, desc: String) = Unit
                }
            )
        }
    }

    private fun showGroupCallInProgressToast(context: Context) {
        runOnMain {
            Toast.warning(context, context.getString(R.string.message_input_group_call_in_progress))
        }
    }

    private fun showGroupCallMemberPicker(context: Context, groupId: String, mediaType: String) {
        val groupMemberStore = GroupMemberStore.create(groupId)
        viewModelScope.launch {
            fetchAllGroupMembers(groupMemberStore)
            if (_hasOngoingGroupCall.value) {
                showGroupCallInProgressToast(context)
                return@launch
            }
            val selfUserId = LoginStore.shared.loginState.loginUserInfo.value?.userID.orEmpty()
            val candidates = groupMemberStore.state.memberList.value
                .filter { it.userID.isNotEmpty() && it.userID != selfUserId }
            if (candidates.isEmpty()) {
                return@launch
            }
            _groupCallPickerRequest.value = GroupCallPickerRequest(
                title = context.getString(
                    if (mediaType == AtomicCallEventPublisher.MEDIA_TYPE_VIDEO) {
                        R.string.message_input_video_call
                    } else {
                        R.string.message_input_audio_call
                    }
                ),
                mediaType = mediaType,
                groupId = groupId,
                candidates = candidates,
                maxSelection = CALL_MEMBER_LIMIT
            )
        }
    }

    fun dismissGroupCallPicker() {
        _groupCallPickerRequest.value = null
    }

    fun confirmGroupCallSelection(selected: List<GroupMember>) {
        confirmGroupCallSelection(appContext, selected)
    }

    fun confirmGroupCallSelection(context: Context, selected: List<GroupMember>) {
        val request = _groupCallPickerRequest.value ?: return
        if (_hasOngoingGroupCall.value) {
            _groupCallPickerRequest.value = null
            showGroupCallInProgressToast(context)
            return
        }
        val participantIds = selected.map(GroupMember::userID).filter { it.isNotEmpty() }
        if (participantIds.isEmpty()) {
            return
        }
        AtomicCallEventPublisher.publishStartCall(
            participantIds = participantIds,
            mediaType = request.mediaType,
            chatGroupId = request.groupId
        )
        _groupCallPickerRequest.value = null
    }

    private suspend fun fetchAllGroupMembers(groupMemberStore: GroupMemberStore) {
        if (!loadInitialGroupMembers(groupMemberStore)) {
            return
        }
        while (groupMemberStore.state.hasMoreMembers.value) {
            if (!loadMoreGroupMembers(groupMemberStore)) {
                return
            }
        }
    }

    private suspend fun loadInitialGroupMembers(groupMemberStore: GroupMemberStore): Boolean {
        return suspendCancellableCoroutine { continuation ->
            groupMemberStore.loadMembers(
                roleList = listOf(GroupMemberFilterRole.ALL),
                completion = object : CompletionHandler {
                    override fun onSuccess() {
                        continuation.resume(true)
                    }

                    override fun onFailure(code: Int, desc: String) {
                        continuation.resume(false)
                    }
                }
            )
        }
    }

    private suspend fun loadMoreGroupMembers(groupMemberStore: GroupMemberStore): Boolean {
        return suspendCancellableCoroutine { continuation ->
            groupMemberStore.loadMoreMembers(
                completion = object : CompletionHandler {
                    override fun onSuccess() {
                        continuation.resume(true)
                    }

                    override fun onFailure(code: Int, desc: String) {
                        continuation.resume(false)
                    }
                }
            )
        }
    }

    fun setDraft(draft: String?) {
        conversationListStore.setConversationDraft(conversationID, draft?.takeIf { it.isNotEmpty() })
    }

    internal fun setTextSendCallbacks(
        canSend: () -> Boolean,
        onSent: () -> Unit
    ) {
        canSendTextMessage = canSend
        onTextMessageSent = onSent
    }

    private fun showSendFailed(context: Context) {
        showErrorOnMain(context, context.getString(R.string.message_input_send_failed))
    }

    private fun showFileTooLarge(context: Context) {
        showErrorOnMain(
            context,
            context.resources.getString(com.tencent.qcloud.tuicore.R.string.TUIKitErrorFileTooLarge)
        )
    }

    private fun showConvertVoiceToTextFailed(context: Context) {
        showErrorOnMain(context, context.getString(R.string.message_input_convert_to_text_failed))
    }

    private fun showErrorOnMain(context: Context, message: String) {
        runOnMain {
            Toast.error(context, message)
        }
    }

    private fun runOnMain(action: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            action()
        } else {
            Handler(Looper.getMainLooper()).post(action)
        }
    }

    private fun createSendMessageOption(
        context: Context?,
        payload: SendMessagePayload,
        atUserList: List<String>? = null,
        quotedMessage: MessageInfo? = null
    ): SendMessageOption {
        return SendMessageOption(
            atUserList = atUserList,
            quotedMessage = quotedMessage,
            needReadReceipt = needReadReceipt(
                isReadReceiptEnabled = messageInputConfig.enableReadReceipt,
                groupType = conversationInfo.value?.groupType
            ),
            offlinePushInfo = createOfflinePushInfo(context, payload)
        )
    }

    private fun needReadReceipt(isReadReceiptEnabled: Boolean, groupType: GroupType?): Boolean {
        return isReadReceiptEnabled && groupType != GroupType.COMMUNITY
    }

    private fun createOfflinePushInfo(context: Context?, payload: SendMessagePayload): OfflinePushInfo {
        val isGroup = isGroupConversation(conversationID)
        val groupId = if (isGroup) conversationID.removePrefix(GROUP_CONVERSATION_PREFIX) else ""

        val loginUserInfo = LoginStore.shared.loginState.loginUserInfo.value
        val selfUserId = loginUserInfo?.userID.orEmpty()
        val selfName = loginUserInfo?.nickname ?: selfUserId

        val senderNickName = if (isGroup) {
            currentConversationTitle?.takeIf { it.isNotBlank() } ?: groupId
        } else {
            selfName
        }

        val description = createOfflinePushDescription(context, payload)
        return MessageOfflinePushInfoFactory.create(
            title = senderNickName,
            description = description,
            isGroup = isGroup,
            senderId = if (isGroup) groupId else selfUserId,
            senderNickName = senderNickName,
            faceUrl = loginUserInfo?.avatarURL
        )
    }

    private fun createOfflinePushDescription(context: Context?, payload: SendMessagePayload): String {
        val actualContext = context ?: appContext

        val content = when (payload) {
            is SendMessagePayload.TextSendMessagePayload -> replaceEmojiKeysWithNames(payload.text)
            is SendMessagePayload.ImageSendMessagePayload -> actualContext.getString(R.string.message_input_message_type_image)
            is SendMessagePayload.VideoSendMessagePayload -> actualContext.getString(R.string.message_input_message_type_video)
            is SendMessagePayload.FileSendMessagePayload -> actualContext.getString(R.string.message_input_message_type_file)
            is SendMessagePayload.AudioSendMessagePayload -> actualContext.getString(R.string.message_input_message_type_voice)
            is SendMessagePayload.FaceSendMessagePayload -> actualContext.getString(R.string.message_input_message_type_animate_emoji)
            is SendMessagePayload.CustomSendMessagePayload -> payload.description ?: ""
            else -> ""
        }

        return MessageOfflinePushInfoFactory.trimDescription(content)
    }

    companion object {
        private const val TAG = "MessageInputViewModel"
        private const val C2C_CONVERSATION_PREFIX = "c2c_"
        private const val GROUP_CONVERSATION_PREFIX = "group_"
        private const val CALL_MEMBER_LIMIT = 9
        private const val ALBUM_PICKER_MAX_SELECTION = 9
    }
}
