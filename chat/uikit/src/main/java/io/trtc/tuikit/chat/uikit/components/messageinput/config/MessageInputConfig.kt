package io.trtc.tuikit.chat.uikit.components.messageinput.config

interface MessageInputConfigProtocol {
    val isShowAudioRecorder: Boolean
    val isShowPhotoTaker: Boolean
    val isShowAudioCall: Boolean
    val isShowVideoCall: Boolean
    val isShowMore: Boolean
    val isShowEmoji: Boolean
    val enableMention: Boolean
    val enableLongPressToTalk: Boolean
        get() = true
    val audioMaxRecordDurationMs: Int
        get() = 60 * 1000
    val actionCustomizer: MessageInputActionCustomizer?
        get() = null
}

class ChatMessageInputConfig : MessageInputConfigProtocol {

    private var _isShowAudioRecorder: Boolean? = null
    private var _isShowPhotoTaker: Boolean? = null
    private var _isShowAudioCall: Boolean? = null
    private var _isShowVideoCall: Boolean? = null
    private var _isShowMore: Boolean? = null
    private var _isShowEmoji: Boolean? = null
    private var _enableMention: Boolean? = null
    private var _enableLongPressToTalk: Boolean? = null
    private var _audioMaxRecordDurationMs: Int? = null
    private var _actionCustomizer: MessageInputActionCustomizer? = null

    constructor(
        isShowAudioRecorder: Boolean? = null,
        isShowPhotoTaker: Boolean? = null,
        isShowAudioCall: Boolean? = null,
        isShowVideoCall: Boolean? = null,
        isShowMore: Boolean? = null,
        enableMention: Boolean? = null,
        enableLongPressToTalk: Boolean? = null,
        audioMaxRecordDurationMs: Int? = null,
        isShowEmoji: Boolean? = null
    ) {
        this._isShowAudioRecorder = isShowAudioRecorder
        this._isShowPhotoTaker = isShowPhotoTaker
        this._isShowAudioCall = isShowAudioCall
        this._isShowVideoCall = isShowVideoCall
        this._isShowMore = isShowMore
        this._enableMention = enableMention
        this._enableLongPressToTalk = enableLongPressToTalk
        this._audioMaxRecordDurationMs = audioMaxRecordDurationMs
        this._isShowEmoji = isShowEmoji
    }

    override var isShowAudioRecorder: Boolean
        get() = _isShowAudioRecorder ?: true
        set(value) {
            _isShowAudioRecorder = value
        }

    override var isShowPhotoTaker: Boolean
        get() = _isShowPhotoTaker ?: true
        set(value) {
            _isShowPhotoTaker = value
        }

    override var isShowAudioCall: Boolean
        get() = _isShowAudioCall ?: true
        set(value) {
            _isShowAudioCall = value
        }

    override var isShowVideoCall: Boolean
        get() = _isShowVideoCall ?: true
        set(value) {
            _isShowVideoCall = value
        }

    override var isShowMore: Boolean
        get() = _isShowMore ?: true
        set(value) {
            _isShowMore = value
        }

    override var isShowEmoji: Boolean
        get() = _isShowEmoji ?: true
        set(value) {
            _isShowEmoji = value
        }

    override var enableMention: Boolean
        get() = _enableMention ?: true
        set(value) {
            _enableMention = value
        }

    override var enableLongPressToTalk: Boolean
        get() = _enableLongPressToTalk ?: true
        set(value) {
            _enableLongPressToTalk = value
        }

    override var audioMaxRecordDurationMs: Int
        get() = _audioMaxRecordDurationMs ?: 60 * 1000
        set(value) {
            _audioMaxRecordDurationMs = value
        }

    override val actionCustomizer: MessageInputActionCustomizer?
        get() = _actionCustomizer

    fun customizeActions(block: MessageInputActionEditor.() -> Unit): ChatMessageInputConfig = apply {
        _actionCustomizer = MessageInputActionCustomizer { editor ->
            editor.block()
        }
    }
}
