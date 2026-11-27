package io.trtc.tuikit.chat.uikit.components.audiorecorder.audiorecorderimpl
import android.os.Looper
import android.util.Log
import io.trtc.tuikit.chat.uikit.components.audiorecorder.AudioRecorderListener
import io.trtc.tuikit.chat.uikit.components.audiorecorder.AudioRecorderResult
import io.trtc.tuikit.chat.uikit.components.audiorecorder.AudioRecorderResultCode
import io.trtc.tuikit.chat.uikit.components.audiorecorder.audiorecordercore.AudioRecorderInternalInterface
import io.trtc.tuikit.chat.uikit.components.audiorecorder.audiorecordercore.AudioRecorderSystem
import io.trtc.tuikit.chat.uikit.components.audiorecorder.audiorecordercore.AudioRecorderTXUGC
import io.trtc.tuikit.atomicx.common.permission.PermissionCallback
import io.trtc.tuikit.atomicxcore.impl.common.ContextProvider
import io.trtc.tuikit.chat.uikit.components.common.ChatPermissionHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

interface RecorderListener {
    fun onRecordTime(time: Int)
    fun onAmplitudeChanged(db: Int)
    fun onCompleted(resultCode: AudioRecorderResultCode, path: String?)
}

class AudioRecorderImpl {
    companion object {
        private const val TAG = "AudioRecorderImpl"
    }

    private var recorder: AudioRecorderInternalInterface? = null
    private val mainCoroutineScope = CoroutineScope(Dispatchers.Main)
    private val _currentPower = MutableStateFlow(0)
    private val _recordTimeMs = MutableStateFlow(0)
    internal val currentPowerFlow = _currentPower.asStateFlow()
    internal val recordTimeMsFlow = _recordTimeMs.asStateFlow()
    private var isRecording = false
    private var isCancelRecord = false
    private var audioFilePath: String? = null

    private var listener: AudioRecorderListener? = null

    init {
        val context = ContextProvider.getApplicationContext()
        try {
            recorder = AudioRecorderTXUGC(context).also { it.init() }
        } catch (e: Exception) {
            Log.i(TAG, "can not create ugc audio recorder. ${e.message}")
            if (context != null) recorder = AudioRecorderSystem(context)
        }
        recorder?.setListener(RecorderListenerImpl())
    }

    fun isRecording(): Boolean = isRecording

    fun startRecord(
        filePath: String?,
        enableAIDeNoise: Boolean,
        minRecordDurationMs: Int,
        maxRecordDurationMs: Int,
        listener: AudioRecorderListener
    ) {
        this.listener = listener
        Log.i(TAG, "start record filePath:$filePath")
        ChatPermissionHelper.requestPermission(
            ChatPermissionHelper.PERMISSION_MICROPHONE,
            object : PermissionCallback() {
                override fun onGranted() {
                    runOnMainThread {
                        startRecordInternal(filePath, enableAIDeNoise, minRecordDurationMs, maxRecordDurationMs)
                    }
                }

                override fun onDenied() {
                    Log.i(TAG, "request record audio permission refuse")
                    onRecordingComplete(AudioRecorderResultCode.ERROR_RECORD_PERMISSION_DENIED, "", 0)
                }
            }
        )
    }

    private fun startRecordInternal(
        filePath: String?,
        enableAIDeNoise: Boolean,
        minRecordDurationMs: Int,
        maxRecordDurationMs: Int
    ) {
        if (isRecording) {
            onRecordingComplete(AudioRecorderResultCode.ERROR_RECORDING, "", 0)
            return
        }
        isRecording = true
        isCancelRecord = false

        audioFilePath = filePath
        if (audioFilePath.isNullOrEmpty()) {
            audioFilePath = generateFilePath()
        }

        if (audioFilePath.isNullOrEmpty()) {
            onRecordingComplete(AudioRecorderResultCode.ERROR_STORAGE_UNAVAILABLE, audioFilePath, 0)
            return
        }

        recorder?.enableAIDeNoise(enableAIDeNoise)
        recorder?.startRecord(audioFilePath, minRecordDurationMs, maxRecordDurationMs)
        _currentPower.value = 0
        _recordTimeMs.value = 0
        listener?.onStart()
    }

    fun stopRecord() {
        Log.i(TAG, "stop record")
        runOnMainThread {
            if (isRecording) {
                recorder?.stopRecord()
                isRecording = false
            }
        }
    }

    fun cancelRecord() {
        Log.i(TAG, "cancel record")
        runOnMainThread {
            if (isRecording) {
                isCancelRecord = true
                recorder?.stopRecord()
                onRecordingComplete(AudioRecorderResultCode.ERROR_CANCEL, audioFilePath, 0)
            }
        }
    }

    private fun generateFilePath(): String? {
        val context = ContextProvider.getApplicationContext() ?: return null
        val audioDir = File(context.filesDir, "audio")
        if (!audioDir.exists() && !audioDir.mkdirs()) {
            return null
        }

        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        return File(audioDir, "audio_$timestamp.m4a").absolutePath
    }

    private fun onRecordingComplete(
        resultCode: AudioRecorderResultCode,
        recordFilePath: String?,
        recordDuration: Int
    ) {
        runOnMainThread {
            val current = this.listener
            this.listener = null
            current?.onCompleted(
                AudioRecorderResult(
                    resultCode = resultCode,
                    filePath = recordFilePath,
                    durationMs = recordDuration
                )
            )
        }
    }

    private fun runOnMainThread(action: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            action()
        } else {
            mainCoroutineScope.launch { action() }
        }
    }

    private inner class RecorderListenerImpl : RecorderListener {
        private var recordTimeMs: Int = 0

        override fun onRecordTime(time: Int) {
            runOnMainThread {
                recordTimeMs = time
                _recordTimeMs.value = time
                this@AudioRecorderImpl.listener?.onProgress(time, _currentPower.value)
            }
        }

        override fun onAmplitudeChanged(db: Int) {
            runOnMainThread {
                _currentPower.value = db
                this@AudioRecorderImpl.listener?.onProgress(recordTimeMs, db)
            }
        }

        override fun onCompleted(resultCode: AudioRecorderResultCode, path: String?) {
            if (resultCode != AudioRecorderResultCode.SUCCESS) {
                Log.e(TAG, "on record completed. resultCode:$resultCode")
            }
            Log.i(TAG, "on record completed. path:$path")
            runOnMainThread {
                isRecording = false
                if (!isCancelRecord) {
                    onRecordingComplete(resultCode, path, recordTimeMs)
                }
            }
        }
    }
}
