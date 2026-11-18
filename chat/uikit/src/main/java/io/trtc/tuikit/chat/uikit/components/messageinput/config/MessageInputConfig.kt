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

    /**
     * 长按录音滑到「转文字」时是否边说边出字。
     *
     * 关掉之后退回原来的「松手后上传整段音频再识别」。机型不支持实时采集或后台没开通
     * ASR 时也会自动退回，接入方不需要关心。
     */
    val enableRealtimeVoiceToText: Boolean
        get() = true

    /**
     * 长按录音松手是否发送原语音，以及转文字草稿里是否保留「发送原语音」。
     *
     * 机器人还不识别语音消息时关掉。取消和转文字仍可用，普通会话默认开着。
     */
    val enableSendRecordedVoice: Boolean
        get() = true
    val audioMaxRecordDurationMs: Int
        get() = 60 * 1000
    val actionCustomizer: MessageInputActionCustomizer?
        get() = null
    val appearance: MessageInputAppearance?
        get() = null

    /**
     * Keeps the send button on screen while the text field is empty instead of swapping it for the
     * more button. Sending an empty draft stays a no-op.
     */
    val keepSendButtonVisible: Boolean
        get() = false

    /**
     * 输入框还是空的、并且没有会话草稿时填入的引导文案。
     * 用户已经输入过，或发送按钮红点已经被点掉时，不再填入。
     */
    val initialInputText: String?
        get() = null

    /** 发送按钮右上角是否还要显示红点。返回 false 后红点消失，并停止填入 [initialInputText]。 */
    val shouldShowSendButtonRedDot: (() -> Boolean)?
        get() = null

    /** 用户点了带红点的发送按钮，或用键盘发送当前文字时回调，方便宿主把红点记成本地已点过。 */
    val onSendButtonRedDotClick: (() -> Unit)?
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
    private var _enableRealtimeVoiceToText: Boolean? = null
    private var _enableSendRecordedVoice: Boolean? = null
    private var _audioMaxRecordDurationMs: Int? = null
    private var _actionCustomizer: MessageInputActionCustomizer? = null
    private var _appearance: MessageInputAppearance? = null
    private var _keepSendButtonVisible: Boolean? = null
    private var _initialInputText: String? = null
    private var _shouldShowSendButtonRedDot: (() -> Boolean)? = null
    private var _onSendButtonRedDotClick: (() -> Unit)? = null
    constructor(
        isShowAudioRecorder: Boolean? = null,
        isShowPhotoTaker: Boolean? = null,
        isShowAudioCall: Boolean? = null,
        isShowVideoCall: Boolean? = null,
        isShowMore: Boolean? = null,
        enableMention: Boolean? = null,
        enableLongPressToTalk: Boolean? = null,
        audioMaxRecordDurationMs: Int? = null,
        isShowEmoji: Boolean? = null,
        enableRealtimeVoiceToText: Boolean? = null,
        enableSendRecordedVoice: Boolean? = null
    ) {
        this._enableRealtimeVoiceToText = enableRealtimeVoiceToText
        this._enableSendRecordedVoice = enableSendRecordedVoice
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

    override var enableRealtimeVoiceToText: Boolean
        get() = _enableRealtimeVoiceToText ?: true
        set(value) {
            _enableRealtimeVoiceToText = value
        }

    override var enableSendRecordedVoice: Boolean
        get() = _enableSendRecordedVoice ?: true
        set(value) {
            _enableSendRecordedVoice = value
        }

    override var audioMaxRecordDurationMs: Int
        get() = _audioMaxRecordDurationMs ?: 60 * 1000
        set(value) {
            _audioMaxRecordDurationMs = value
        }

    override val actionCustomizer: MessageInputActionCustomizer?
        get() = _actionCustomizer

    override var appearance: MessageInputAppearance?
        get() = _appearance
        set(value) {
            _appearance = value
        }

    override var keepSendButtonVisible: Boolean
        get() = _keepSendButtonVisible ?: false
        set(value) {
            _keepSendButtonVisible = value
        }

    override var initialInputText: String?
        get() = _initialInputText
        set(value) {
            _initialInputText = value?.takeIf { it.isNotEmpty() }
        }

    override var shouldShowSendButtonRedDot: (() -> Boolean)?
        get() = _shouldShowSendButtonRedDot
        set(value) {
            _shouldShowSendButtonRedDot = value
        }

    override var onSendButtonRedDotClick: (() -> Unit)?
        get() = _onSendButtonRedDotClick
        set(value) {
            _onSendButtonRedDotClick = value
        }

    fun customizeActions(block: MessageInputActionEditor.() -> Unit): ChatMessageInputConfig = apply {
        _actionCustomizer = MessageInputActionCustomizer { editor ->
            editor.block()
        }
    }

    fun setAppearance(appearance: MessageInputAppearance?): ChatMessageInputConfig = apply {
        _appearance = appearance
    }

    fun setKeepSendButtonVisible(keepVisible: Boolean): ChatMessageInputConfig = apply {
        _keepSendButtonVisible = keepVisible
    }

}
