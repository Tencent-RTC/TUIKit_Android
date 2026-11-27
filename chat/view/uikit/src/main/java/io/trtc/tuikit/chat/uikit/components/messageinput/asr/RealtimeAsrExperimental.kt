package io.trtc.tuikit.chat.uikit.components.messageinput.asr

import com.tencent.imsdk.v2.V2TIMManager
import com.tencent.imsdk.v2.V2TIMSDKListener
import com.tencent.imsdk.v2.V2TIMValueCallback

/**
 * 实时 ASR 的实验性接口封装。正式的 startASR / onRecvASRResponse 已不再对业务开放。
 */
internal object RealtimeAsrExperimental {
    const val NOTIFY_KEY = "onRecvRealtimeTranscriberResponse"

    private const val API_START = "startRealtimeTranscriber"
    private const val API_FEED = "feedRealtimeAudioData"
    private const val API_STOP = "stopRealtimeTranscriber"

    fun addListener(listener: V2TIMSDKListener) {
        V2TIMManager.getInstance().addIMSDKListener(listener)
    }

    fun removeListener(listener: V2TIMSDKListener) {
        V2TIMManager.getInstance().removeIMSDKListener(listener)
    }

    fun start(language: String, onSuccess: (String) -> Unit, onError: (Int, String) -> Unit) {
        val param = hashMapOf<String, Any>("language" to language)
        V2TIMManager.getInstance().callExperimentalAPI(API_START, param, object : V2TIMValueCallback<Any> {
            override fun onSuccess(data: Any?) {
                onSuccess(data as? String ?: "")
            }

            override fun onError(code: Int, desc: String?) {
                onError(code, desc.orEmpty())
            }
        })
    }

    fun feed(sessionID: String, dataList: List<ByteArray>, onError: (Int, String) -> Unit) {
        val param = hashMapOf<String, Any>(
            "sessionID" to sessionID,
            "dataList" to dataList,
        )
        V2TIMManager.getInstance().callExperimentalAPI(API_FEED, param, object : V2TIMValueCallback<Any> {
            override fun onSuccess(data: Any?) = Unit

            override fun onError(code: Int, desc: String?) {
                onError(code, desc.orEmpty())
            }
        })
    }

    fun feed(
        sessionID: String,
        dataList: List<ByteArray>,
        onSuccess: () -> Unit,
        onError: (Int, String) -> Unit,
    ) {
        val param = hashMapOf<String, Any>(
            "sessionID" to sessionID,
            "dataList" to dataList,
        )
        V2TIMManager.getInstance().callExperimentalAPI(API_FEED, param, object : V2TIMValueCallback<Any> {
            override fun onSuccess(data: Any?) {
                onSuccess()
            }

            override fun onError(code: Int, desc: String?) {
                onError(code, desc.orEmpty())
            }
        })
    }

    fun stop(sessionID: String, onSuccess: (() -> Unit)? = null, onError: ((Int, String) -> Unit)? = null) {
        val param = hashMapOf<String, Any>("sessionID" to sessionID)
        val callback = if (onSuccess == null && onError == null) {
            null
        } else {
            object : V2TIMValueCallback<Any> {
                override fun onSuccess(data: Any?) {
                    onSuccess?.invoke()
                }

                override fun onError(code: Int, desc: String?) {
                    onError?.invoke(code, desc.orEmpty())
                }
            }
        }
        V2TIMManager.getInstance().callExperimentalAPI(API_STOP, param, callback)
    }

    data class Response(
        val sessionID: String?,
        val chunkIndex: Int,
        val text: String,
        val isFinished: Boolean,
    )

    fun parseNotify(key: String?, param: Any?): Response? {
        if (key != NOTIFY_KEY || param !is Map<*, *>) {
            return null
        }
        val chunkIndex = param["chunkIndex"]
        return Response(
            sessionID = param["sessionID"] as? String,
            chunkIndex = (chunkIndex as? Number)?.toInt() ?: 0,
            text = param["text"] as? String ?: "",
            isFinished = param["isFinished"] == true,
        )
    }
}
