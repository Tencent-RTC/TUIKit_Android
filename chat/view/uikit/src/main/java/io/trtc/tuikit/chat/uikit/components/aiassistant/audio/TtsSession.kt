package io.trtc.tuikit.chat.uikit.components.aiassistant.audio

import android.util.Log
import com.tencent.imsdk.v2.V2TIMCallback
import com.tencent.imsdk.v2.V2TIMMessage
import com.tencent.imsdk.v2.V2TIMStreamElem
import com.tencent.imsdk.v2.V2TIMTTSVoiceFormat

private const val TAG = "TtsSession"

/**
 * 流式消息的 TTS 会话封装。
 *
 * TTS 挂在 V2TIMStreamElem 上，不是独立会话：必须先有一条带 streamID 的流式消息。
 * 音频不从这里出，走 V2TIMAdvancedMsgListener.onRecvTTSResponse，由 AICallController 统一接收。
 *
 * 非线程安全，只在主线程上使用。
 */
class TtsSession {

    private var elem: V2TIMStreamElem? = null
    private var msgId: String? = null

    /**
     * 当前绑定的消息 ID，未绑定时为 null。
     *
     * 收到的 TTS 音频分片要按它过滤：`stopTTS` 是异步的网络往返，已经在路上的分片
     * 照样会到达，只有 msgID 比对能把上一轮的尾音挡在下一轮的播放队列之外。
     */
    val boundMsgId: String?
        get() = msgId

    /**
     * 对一条流式消息发起 TTS。
     *
     * 消息刚到时 streamID 可能还没就绪，此时 startTTS 会以 ERR_INVALID_PARAMETERS 同步失败
     * （见 V2TIMStreamElem.java:60-66，这个分支不走网络，[onFailed] 在本方法返回前就被调到）。
     * 调用方应在下一次 onRecvMessageModified 时重试，直到 [onStarted] 被回调——
     * 这就是「首次观察到非空 streamID」的实际判定方式。
     *
     * [onStarted] / [onFailed] 都在主线程上回调（IMCallback 走 IMContext.runOnMainThread），
     * 上面那个同步失败分支则直接在调用线程上回调。
     *
     * @return false 表示消息上根本没有 streamElem，不必重试
     */
    fun startFor(
        message: V2TIMMessage,
        onStarted: () -> Unit,
        onFailed: (Int, String) -> Unit
    ): Boolean {
        val streamElem = message.streamElem ?: return false
        val voiceFormat = V2TIMTTSVoiceFormat().apply {
            // 音色与语速本期都用默认：voiceID 空串走后台默认音色，speed 1.0
            voiceID = ""
            speed = 1.0f
        }
        // 绑定与解绑都留痕：boundMsgId 是在途分片的唯一过滤依据，而它被谁、在什么时候改掉
        // 一旦看不见，「回答开头被吞掉一小段」这类现象就只能靠猜。
        Log.i(TAG, "bind $msgId -> ${message.msgID}")
        elem = streamElem
        msgId = message.msgID
        streamElem.startTTS(voiceFormat, object : V2TIMCallback {
            override fun onSuccess() {
                Log.i(TAG, "TTS started for ${message.msgID}")
                onStarted()
            }

            override fun onError(code: Int, desc: String?) {
                Log.w(TAG, "startTTS failed: $code $desc")
                onFailed(code, desc.orEmpty())
            }
        })
        return true
    }

    fun stop() {
        val streamElem = elem ?: return
        Log.i(TAG, "unbind $msgId")
        elem = null
        msgId = null
        streamElem.stopTTS(object : V2TIMCallback {
            override fun onSuccess() {
                Log.i(TAG, "TTS stopped")
            }

            override fun onError(code: Int, desc: String?) {
                Log.w(TAG, "stopTTS failed: $code $desc")
            }
        })
    }
}
