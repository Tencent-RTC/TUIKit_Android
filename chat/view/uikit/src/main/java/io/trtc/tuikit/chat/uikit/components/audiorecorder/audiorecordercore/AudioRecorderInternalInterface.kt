package io.trtc.tuikit.chat.uikit.components.audiorecorder.audiorecordercore
import io.trtc.tuikit.chat.uikit.components.audiorecorder.audiorecorderimpl.RecorderListener

interface AudioRecorderInternalInterface {
    fun setListener(listener: RecorderListener?)
    fun startRecord(filePath: String? = null, minRecordDurationMs: Int, maxRecordDurationMs: Int)
    fun stopRecord()
    fun enableAIDeNoise(enable: Boolean)
}