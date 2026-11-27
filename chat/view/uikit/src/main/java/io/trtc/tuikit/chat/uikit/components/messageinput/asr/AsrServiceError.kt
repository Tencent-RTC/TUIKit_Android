package io.trtc.tuikit.chat.uikit.components.messageinput.asr

/**
 * 语音识别的后台错误归类。实时识别（startRealtimeTranscriber）与一句话识别
 * （convertVoiceToText）共用同一个资源包，错误码也一致。
 */
object AsrServiceError {

    /**
     * 当前 SDKAppID 没有可用的识别服务：未开通，或资源包已用完。
     * 后台目前对这两种情况都回 `This AppId has not yet enabled the ASR service`。
     */
    const val SERVICE_UNAVAILABLE = 140008

    /** 重试解决不了、要去控制台开通或续费才能恢复的错误。 */
    fun isServiceUnavailable(code: Int): Boolean = code == SERVICE_UNAVAILABLE
}
