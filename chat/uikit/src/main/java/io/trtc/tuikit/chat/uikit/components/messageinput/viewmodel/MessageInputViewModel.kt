package io.trtc.tuikit.chat.uikit.components.messageinput.viewmodel
import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.util.Log
import android.webkit.MimeTypeMap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.tencent.cloud.tuikit.engine.common.ContextProvider
import io.trtc.tuikit.chat.uikit.R
import io.trtc.tuikit.atomicx.albumpicker.AlbumMedia
import io.trtc.tuikit.atomicx.albumpicker.AlbumMediaType
import io.trtc.tuikit.atomicx.albumpicker.AlbumPickerConfig
import io.trtc.tuikit.atomicx.albumpicker.AlbumPickerMediaFilter
import io.trtc.tuikit.atomicx.albumpicker.AlbumPickerStyle
import io.trtc.tuikit.atomicx.albumpicker.AlbumPickerTheme
import io.trtc.tuikit.chat.uikit.components.messageinput.utils.MessageInputAlbumPickerActivity
import io.trtc.tuikit.chat.uikit.components.ai.AiMediaProcessManager
import io.trtc.tuikit.chat.uikit.components.ai.tts.TtsPlaybackHelper
import io.trtc.tuikit.chat.uikit.components.ai.tts.TtsTextSanitizer
import io.trtc.tuikit.chat.uikit.components.ai.tts.VoiceMessageConfig
import io.trtc.tuikit.chat.uikit.components.common.AtomicCallEventPublisher
import io.trtc.tuikit.chat.uikit.components.common.ConversationIDUtil
import io.trtc.tuikit.chat.uikit.components.common.MessageOfflinePushInfoFactory
import io.trtc.tuikit.chat.uikit.components.config.AppBuilderConfig
import io.trtc.tuikit.chat.uikit.components.emojipicker.EmojiSpanHelper
import io.trtc.tuikit.chat.uikit.components.filepicker.FilePicker
import io.trtc.tuikit.chat.uikit.components.filepicker.FilePickerListener
import io.trtc.tuikit.chat.uikit.components.messageinput.config.ChatMessageInputConfig
import io.trtc.tuikit.chat.uikit.components.messageinput.config.MessageInputConfigProtocol
import io.trtc.tuikit.chat.uikit.components.messageinput.data.MessageInputMenuAction
import io.trtc.tuikit.chat.uikit.components.messageinput.model.MentionInfo
import io.trtc.tuikit.chat.uikit.components.messageinput.ui.GroupMemberPickerDialog
import io.trtc.tuikit.chat.uikit.components.messageinput.utils.VideoFrameExtractor
import io.trtc.tuikit.chat.uikit.components.videorecorder.RecordMode
import io.trtc.tuikit.chat.uikit.components.videorecorder.VideoRecordListener
import io.trtc.tuikit.chat.uikit.components.videorecorder.VideoRecorder
import io.trtc.tuikit.chat.uikit.components.videorecorder.VideoRecorderConfig
import io.trtc.tuikit.atomicx.widget.basicwidget.toast.AtomicToast
import io.trtc.tuikit.atomicxcore.api.CompletionHandler
import io.trtc.tuikit.atomicxcore.api.conversation.ConversationInfo
import io.trtc.tuikit.atomicxcore.api.conversation.ConversationListStore
import io.trtc.tuikit.atomicxcore.api.conversation.GetConversationInfoCompletionHandler
import io.trtc.tuikit.atomicxcore.api.group.GroupMember
import io.trtc.tuikit.atomicxcore.api.group.GroupMemberFilterRole
import io.trtc.tuikit.atomicxcore.api.group.GroupMemberStore
import io.trtc.tuikit.atomicxcore.api.login.LoginStore
import io.trtc.tuikit.atomicxcore.api.message.MessageInputStore
import io.trtc.tuikit.atomicxcore.api.message.MessageInfo
import io.trtc.tuikit.atomicxcore.api.message.OfflinePushInfo
import io.trtc.tuikit.atomicxcore.api.message.SendMessageOption
import io.trtc.tuikit.atomicxcore.api.message.SendMessagePayload
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
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

class MessageInputViewModel(
    private val messageInputStore: MessageInputStore,
    val conversationID: String,
    private val messageInputConfig: MessageInputConfigProtocol = ChatMessageInputConfig(),
    val conversationListStore: ConversationListStore = ConversationListStore.create()
) : ViewModel() {

    private var currentConversationTitle: String? = null
    private val attachmentFileResolver = MessageInputAttachmentFileResolver()
    private val audioTranscriber = AudioTranscriber()
    private val recordTranslationTtsHelper = TtsPlaybackHelper()

    private val conversationState = conversationListStore.state

    private val _conversationInfo = MutableStateFlow<ConversationInfo?>(null)
    val conversationInfo: StateFlow<ConversationInfo?> = _conversationInfo.asStateFlow()

    init {
        conversationListStore.getConversationInfo(conversationID, object : GetConversationInfoCompletionHandler {
            override fun onSuccess(conversationInfo: ConversationInfo) {
                _conversationInfo.value = conversationInfo
            }
            override fun onFailure(code: Int, desc: String) {}
        })
        viewModelScope.launch {
            conversationState.conversationList.collectLatest { list ->
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
    }

    fun getActions(
        context: Context,
        config: MessageInputConfigProtocol = messageInputConfig,
    ): List<MessageInputMenuAction> {
        return MessageInputMenuActionFactory(
            config = config,
            callbacks = MessageInputMenuActionCallbacks(
                onPickMedia = { pickMediaAndSend(context) },
                onCaptureImage = { captureImageAndSend(context) },
                onRecordVideo = { recordVideoAndSend(context) },
                onPickFile = { pickFileAndSend(context) },
                onStartAudioCall = { startAudioCall(context) },
                onStartVideoCall = { startVideoCall(context) }
            )
        ).create(context, conversationID)
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
            VideoRecorderConfig(recordMode = RecordMode.PHOTO_ONLY),
            object : VideoRecordListener {
                override fun onPhotoCaptured(filePath: String?) {
                    if (!filePath.isNullOrEmpty()) {
                        sendImageMessage(context, filePath)
                    }
                }
            }
        )
    }

    fun recordVideoAndSend(context: Context) {
        VideoRecorder.startRecord(
            VideoRecorderConfig(recordMode = RecordMode.MIXED),
            object : VideoRecordListener {
                override fun onPhotoCaptured(filePath: String?) {
                    if (!filePath.isNullOrEmpty()) {
                        sendImageMessage(context, filePath)
                    }
                }

                override fun onVideoCaptured(filePath: String?, durationMs: Int, thumbnailPath: String?) {
                    if (!filePath.isNullOrEmpty()) {
                        sendVideoMessage(context, filePath)
                    }
                }
            }
        )
    }

    fun sendTextMessage(
        context: Context?,
        text: String,
        mentionList: List<MentionInfo>,
        quotedMessage: MessageInfo? = null,
        onSuccess: (() -> Unit)? = null
    ) {
        val payload = SendMessagePayload.TextSendMessagePayload(text)
        val option = createSendMessageOption(
            context = context,
            payload = payload,
            atUserList = mentionList.map { it.userID }.takeIf { it.isNotEmpty() },
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
            val path = getImagePathAfterRotate(context, filePath)
            if (!MessageInputSendGuards.isReadableFilePath(path)) {
                showSendFailed(context)
                return@launch
            }
            val imageSize = getImageSize(path)
            val fileSize = attachmentFileResolver.getFileSize(path)
            val fileName = attachmentFileResolver.getFileName(path) ?: ""
            val fileExtension = attachmentFileResolver.getFileExtensionFromUrl(fileName)
            val mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(fileExtension)
            if (TextUtils.equals(mimeType, "image/gif")) {
                if (fileSize > GIF_IMAGE_MAX_SIZE) {
                    showFileTooLarge(context)
                    return@launch
                }
            } else if (fileSize > IMAGE_MAX_SIZE) {
                showFileTooLarge(context)
                return@launch
            }
            val payload = SendMessagePayload.ImageSendMessagePayload(
                imagePath = path,
                imageWidth = imageSize[0],
                imageHeight = imageSize[1]
            )
            val option = createSendMessageOption(context, payload)
            messageInputStore.sendMessage(payload, option, object : CompletionHandler {
                override fun onSuccess() {}

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
                val basePath = context.filesDir.absolutePath + "/atomicx_data/image/"
                val bitmapPath = "${basePath}_${sdkAppID}_${userID ?: ""}$uuid.jpg"
                if (!saveBitmap(bitmapPath, bitmap)) {
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
        showGroupCallMemberPicker(context, groupId, mediaType)
    }

    private fun c2cTargetUserId(): String? = ConversationIDUtil.userIdOrNull(conversationID)

    private fun groupTargetId(): String? = ConversationIDUtil.groupIdOrNull(conversationID)

    private fun showGroupCallMemberPicker(context: Context, groupId: String, mediaType: String) {
        val groupMemberStore = GroupMemberStore.create(groupId)
        viewModelScope.launch {
            fetchAllGroupMembers(groupMemberStore)
            val selfUserId = LoginStore.shared.loginState.loginUserInfo.value?.userID.orEmpty()
            val candidates = groupMemberStore.state.memberList.value
                .filter { it.userID.isNotEmpty() && it.userID != selfUserId }
            if (candidates.isEmpty()) {
                return@launch
            }
            GroupMemberPickerDialog(
                context = context,
                title = context.getString(
                    if (mediaType == AtomicCallEventPublisher.MEDIA_TYPE_VIDEO) {
                        R.string.message_input_video_call
                    } else {
                        R.string.message_input_audio_call
                    }
                ),
                candidates = candidates,
                maxSelection = CALL_MEMBER_LIMIT,
                onConfirm = { selected ->
                    val participantIds = selected.map(GroupMember::userID).filter { it.isNotEmpty() }
                    if (participantIds.isEmpty()) {
                        return@GroupMemberPickerDialog
                    }
                    AtomicCallEventPublisher.publishStartCall(
                        participantIds = participantIds,
                        mediaType = mediaType,
                        chatGroupId = groupId
                    )
                }
            ).show()
        }
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
        return kotlinx.coroutines.suspendCancellableCoroutine { continuation ->
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
        return kotlinx.coroutines.suspendCancellableCoroutine { continuation ->
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
                override fun onSuccess() {}

                override fun onFailure(code: Int, desc: String) {
                    showSendFailed(context)
                }
            })
        }
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

    // Non-message-bound translation used by the record-transcription editing
    // overlay. Always translates the immutable original transcription text so
    // switching languages never compounds translation loss. Callbacks land on
    // the main thread (AiMediaProcessManager guarantees this).
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
        recordTranslationTtsHelper.stop()
    }

    fun setDraft(draft: String?) {
        conversationListStore.setConversationDraft(conversationID, draft?.takeIf { it.isNotEmpty() })
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
            AtomicToast.show(context, message, style = AtomicToast.Style.ERROR)
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
            needReadReceipt = MessageInputReadReceiptPolicy.needReadReceipt(
                isReadReceiptEnabled = AppBuilderConfig.enableReadReceipt,
                groupType = conversationInfo.value?.groupType
            ),
            offlinePushInfo = createOfflinePushInfo(context, payload)
        )
    }

    private fun createOfflinePushInfo(context: Context?, payload: SendMessagePayload): OfflinePushInfo {
        val isGroup = ConversationIDUtil.isGroup(conversationID)
        val groupId = ConversationIDUtil.groupId(conversationID)

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
        val actualContext = context ?: ContextProvider.getApplicationContext() ?: return ""
        val content = when (payload) {
            is SendMessagePayload.TextSendMessagePayload -> EmojiSpanHelper.replaceEmojiKeysWithNames(payload.text)
            is SendMessagePayload.ImageSendMessagePayload -> actualContext.getString(
                R.string.message_input_message_type_image
            )
            is SendMessagePayload.VideoSendMessagePayload -> actualContext.getString(
                R.string.message_input_message_type_video
            )
            is SendMessagePayload.FileSendMessagePayload -> actualContext.getString(
                R.string.message_input_message_type_file
            )
            is SendMessagePayload.AudioSendMessagePayload -> actualContext.getString(
                R.string.message_input_message_type_voice
            )
            is SendMessagePayload.FaceSendMessagePayload -> actualContext.getString(
                R.string.message_input_message_type_animate_emoji
            )
            is SendMessagePayload.CustomSendMessagePayload -> payload.description
            else -> ""
        } ?: ""
        return MessageOfflinePushInfoFactory.trimDescription(content)
    }

    private fun saveBitmap(path: String, bitmap: android.graphics.Bitmap): Boolean {
        return try {
            val file = File(path)
            file.parentFile?.mkdirs()
            val fout = FileOutputStream(path)
            val bos = BufferedOutputStream(fout)
            bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 100, bos)
            bos.flush()
            bos.close()
            true
        } catch (_: IOException) {
            false
        }
    }

    private fun getImagePathAfterRotate(context: Context, imagePath: String): String {
        return try {
            val originBitmap = android.graphics.BitmapFactory.decodeFile(imagePath) ?: return imagePath
            val degree = getBitmapDegree(imagePath)
            if (degree == 0) {
                return imagePath
            }
            val newBitmap = rotateBitmapByDegree(originBitmap, degree)
            val oldName = attachmentFileResolver.getFileName(imagePath)
            val cacheDir = File(context.cacheDir, DOCUMENTS_DIR)
            if (!cacheDir.exists()) {
                cacheDir.mkdirs()
            }
            val newFile = File(cacheDir, oldName ?: "rotated.jpg")
            saveBitmap(newFile.absolutePath, newBitmap)
            newBitmap.recycle()
            newFile.absolutePath
        } catch (_: Exception) {
            imagePath
        }
    }

    private fun getImageSize(path: String): IntArray {
        val size = IntArray(2)
        try {
            val options = android.graphics.BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            android.graphics.BitmapFactory.decodeFile(path, options)
            size[0] = options.outWidth
            size[1] = options.outHeight
        } catch (_: Exception) {
        }
        return size
    }

    private fun getBitmapDegree(fileName: String): Int {
        var degree = 0
        try {
            val exifInterface = android.media.ExifInterface(fileName)
            val orientation = exifInterface.getAttributeInt(
                android.media.ExifInterface.TAG_ORIENTATION,
                android.media.ExifInterface.ORIENTATION_NORMAL
            )
            when (orientation) {
                android.media.ExifInterface.ORIENTATION_ROTATE_90 -> degree = 90
                android.media.ExifInterface.ORIENTATION_ROTATE_180 -> degree = 180
                android.media.ExifInterface.ORIENTATION_ROTATE_270 -> degree = 270
            }
        } catch (_: IOException) {
        }
        return degree
    }

    private fun rotateBitmapByDegree(
        bitmap: android.graphics.Bitmap,
        degree: Int
    ): android.graphics.Bitmap {
        val matrix = android.graphics.Matrix()
        matrix.postRotate(degree.toFloat())
        return try {
            android.graphics.Bitmap.createBitmap(
                bitmap,
                0,
                0,
                bitmap.width,
                bitmap.height,
                matrix,
                true
            )
        } catch (_: OutOfMemoryError) {
            bitmap
        }
    }

    companion object {
        private const val TAG = "MessageInputViewModel"
        private const val CALL_MEMBER_LIMIT = 9
        private const val DOCUMENTS_DIR = "documents"
        private const val CAPTURE_DIRECTORY_NAME = "message_input_capture"
        private const val CAPTURE_IMAGE_SUFFIX = ".jpg"
        private const val CAPTURE_VIDEO_SUFFIX = ".mp4"
        private const val TIME_FORMAT_PATTERN = "yyyyMMdd_HHmmss"
        private const val ALBUM_PICKER_MAX_SELECTION = 9
    }
}

class MessageInputViewModelFactory(
    private val messageInputStore: MessageInputStore,
    private val conversationID: String,
    private val messageInputConfig: MessageInputConfigProtocol = ChatMessageInputConfig(),
    private val conversationListStore: ConversationListStore = ConversationListStore.create()
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MessageInputViewModel::class.java)) {
            return MessageInputViewModel(
                messageInputStore = messageInputStore,
                conversationID = conversationID,
                messageInputConfig = messageInputConfig,
                conversationListStore = conversationListStore
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
