package io.trtc.tuikit.chat.uikit.components.ai
import android.os.Handler
import android.os.Looper
import com.tencent.imsdk.v2.V2TIMManager
import com.tencent.imsdk.v2.V2TIMValueCallback
import io.trtc.tuikit.chat.uikit.components.ai.tts.CustomVoiceItem
import org.json.JSONObject

// Chat-side facade over the AI media experimental APIs.
//
// This object talks directly to the IM Java SDK because AtomicXCore does not yet
// expose these AI capabilities. It deliberately does NOT use the message-bound
// MessageActionStore: record-overlay translation runs before any message exists,
// so it relies on the message-manager translateText API instead.
//
// All callbacks are delivered on the main thread. JSON parsing of experimental
// API results is defensive: malformed payloads surface as failures rather than
// crashes.
object AiMediaProcessManager {
    private val mainHandler = Handler(Looper.getMainLooper())

    fun uploadAudioFile(
        filePath: String,
        onSuccess: (String) -> Unit,
        onFailure: (Int, String) -> Unit
    ) {
        callExperimentalApi(
            api = API_UPLOAD_FILE,
            params = buildUploadFileParams(filePath),
            onSuccess = { result ->
                if (result.isBlank()) {
                    onFailure(ERROR_UNKNOWN, "upload url is empty")
                } else {
                    onSuccess(result)
                }
            },
            onFailure = onFailure
        )
    }

    fun convertTextToVoice(
        text: String,
        voiceId: String = "",
        audioFormat: String = "wav",
        language: String = "",
        onSuccess: (String) -> Unit,
        onFailure: (Int, String) -> Unit
    ) {
        callExperimentalApi(
            api = API_CONVERT_TEXT_TO_VOICE,
            params = buildConvertTextToVoiceParams(text, voiceId, audioFormat, language),
            onSuccess = { result ->
                val audioUrl = parseAudioUrl(result)
                if (audioUrl.isNullOrEmpty()) {
                    onFailure(ERROR_UNKNOWN, "tts audio url is empty")
                } else {
                    onSuccess(audioUrl)
                }
            },
            onFailure = onFailure
        )
    }

    fun voiceClone(
        filePath: String,
        voiceName: String,
        promptText: String = "",
        language: String = "",
        onSuccess: (String) -> Unit,
        onFailure: (Int, String) -> Unit
    ) {
        uploadAudioFile(
            filePath = filePath,
            onSuccess = { audioUrl ->
                callExperimentalApi(
                    api = API_VOICE_CLONE,
                    params = buildVoiceCloneParams(voiceName, audioUrl, promptText, language),
                    onSuccess = { result ->
                        val voiceId = parseVoiceId(result)
                        if (voiceId.isNullOrEmpty()) {
                            onFailure(ERROR_UNKNOWN, "voice clone id is empty")
                        } else {
                            onSuccess(voiceId)
                        }
                    },
                    onFailure = onFailure
                )
            },
            onFailure = onFailure
        )
    }

    fun getCustomVoiceList(
        onSuccess: (List<CustomVoiceItem>) -> Unit,
        onFailure: (Int, String) -> Unit
    ) {
        callExperimentalApi(
            api = API_GET_CUSTOM_VOICE_LIST,
            params = EMPTY_JSON,
            onSuccess = { result -> onSuccess(parseVoiceList(result)) },
            onFailure = onFailure
        )
    }

    fun deleteCustomVoice(
        voiceId: String,
        onSuccess: () -> Unit,
        onFailure: (Int, String) -> Unit
    ) {
        callExperimentalApi(
            api = API_DELETE_CUSTOM_VOICE,
            params = buildDeleteCustomVoiceParams(voiceId),
            onSuccess = { _ -> onSuccess() },
            onFailure = onFailure
        )
    }

    fun translateSingleText(
        text: String,
        targetLanguage: String,
        sourceLanguage: String = "",
        onSuccess: (String) -> Unit,
        onFailure: (Int, String) -> Unit
    ) {
        try {
            V2TIMManager.getMessageManager().translateText(
                listOf(text),
                sourceLanguage.ifEmpty { null },
                targetLanguage,
                object : V2TIMValueCallback<HashMap<String, String>> {
                    override fun onSuccess(result: HashMap<String, String>?) {
                        val translated = result?.get(text)
                        if (translated.isNullOrEmpty()) {
                            runOnMain { onFailure(ERROR_UNKNOWN, "translation result is empty") }
                        } else {
                            runOnMain { onSuccess(translated) }
                        }
                    }

                    override fun onError(code: Int, desc: String?) {
                        runOnMain { onFailure(code, desc.orEmpty()) }
                    }
                }
            )
        } catch (exception: Exception) {
            runOnMain { onFailure(ERROR_UNKNOWN, exception.message.orEmpty()) }
        }
    }

    private fun callExperimentalApi(
        api: String,
        params: String,
        onSuccess: (String) -> Unit,
        onFailure: (Int, String) -> Unit
    ) {
        try {
            V2TIMManager.getInstance().callExperimentalAPI(
                api,
                params,
                object : V2TIMValueCallback<Any> {
                    override fun onSuccess(result: Any?) {
                        runOnMain { onSuccess(result?.toString().orEmpty()) }
                    }

                    override fun onError(code: Int, desc: String?) {
                        runOnMain { onFailure(code, desc.orEmpty()) }
                    }
                }
            )
        } catch (exception: Exception) {
            runOnMain { onFailure(ERROR_UNKNOWN, exception.message.orEmpty()) }
        }
    }

    private fun parseAudioUrl(result: String): String? {
        if (result.isBlank()) return null
        return try {
            JSONObject(result).optString(KEY_AUDIO_URL).ifEmpty { null }
        } catch (exception: Exception) {
            null
        }
    }

    private fun parseVoiceId(result: String): String? {
        if (result.isBlank()) return null
        return try {
            JSONObject(result).optString(KEY_VOICE_ID).ifEmpty { null }
        } catch (exception: Exception) {
            null
        }
    }

    private fun parseVoiceList(result: String): List<CustomVoiceItem> {
        if (result.isBlank()) return emptyList()
        return try {
            val array = JSONObject(result).optJSONArray(KEY_VOICE_LIST) ?: return emptyList()
            val list = ArrayList<CustomVoiceItem>(array.length())
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val id = item.optString(KEY_VOICE_ID)
                if (id.isEmpty()) continue
                list.add(
                    CustomVoiceItem(
                        voiceId = id,
                        name = item.optString(KEY_VOICE_NAME),
                        isDefault = false
                    )
                )
            }
            list
        } catch (exception: Exception) {
            emptyList()
        }
    }

    private fun runOnMain(action: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            action()
        } else {
            mainHandler.post(action)
        }
    }

    private fun buildUploadFileParams(filePath: String): String {
        return JSONObject()
            .put(KEY_FILE_PATH, filePath)
            .put(KEY_FILE_TYPE, FILE_TYPE_AUDIO)
            .toString()
    }

    private fun buildConvertTextToVoiceParams(
        text: String,
        voiceId: String,
        audioFormat: String,
        language: String
    ): String {
        val json = JSONObject()
            .put(KEY_TEXT, text)
            .put(KEY_AUDIO_FORMAT, audioFormat)
            .put(KEY_LANGUAGE, language)
        if (voiceId.isNotEmpty()) {
            json.put(KEY_VOICE_ID, voiceId)
        }
        return json.toString()
    }

    private fun buildVoiceCloneParams(
        voiceName: String,
        audioUrl: String,
        promptText: String,
        language: String
    ): String {
        return JSONObject()
            .put(KEY_VOICE_NAME, voiceName)
            .put(KEY_AUDIO_URL, audioUrl)
            .put(KEY_PROMPT_TEXT, promptText)
            .put(KEY_LANGUAGE, language)
            .toString()
    }

    private fun buildDeleteCustomVoiceParams(voiceId: String): String {
        return JSONObject()
            .put(KEY_VOICE_ID, voiceId)
            .toString()
    }

    private const val API_UPLOAD_FILE = "uploadFile"
    private const val API_CONVERT_TEXT_TO_VOICE = "convertTextToVoice"
    private const val API_VOICE_CLONE = "voiceClone"
    private const val API_GET_CUSTOM_VOICE_LIST = "getCustomVoiceList"
    private const val API_DELETE_CUSTOM_VOICE = "deleteCustomVoice"

    private const val KEY_FILE_PATH = "filePath"
    private const val KEY_FILE_TYPE = "fileType"
    private const val KEY_TEXT = "text"
    private const val KEY_VOICE_ID = "voiceId"
    private const val KEY_VOICE_NAME = "voiceName"
    private const val KEY_AUDIO_FORMAT = "audioFormat"
    private const val KEY_AUDIO_URL = "audioUrl"
    private const val KEY_LANGUAGE = "language"
    private const val KEY_PROMPT_TEXT = "promptText"
    private const val KEY_VOICE_LIST = "voiceList"

    private const val EMPTY_JSON = "{}"
    private const val ERROR_UNKNOWN = -1

    // IMSDK im::FileType::kAudio = 3.
    private const val FILE_TYPE_AUDIO = 3
}
