package com.tencent.rtcube.modules.voiceroom.karaoke

import android.os.Handler
import android.os.Looper
import android.util.Log
import com.tencent.rtcube.v2.login.LoginEntry
import com.tencent.rtcube.v2.login.tokenauth.utils.TokenCacheManager
import com.trtc.uikit.livekit.component.karaoke.store.ActionCallback
import com.trtc.uikit.livekit.component.karaoke.store.GetSongListCallBack
import com.trtc.uikit.livekit.component.karaoke.store.MusicCatalogService
import com.trtc.uikit.livekit.component.karaoke.store.QueryPlayTokenCallBack
import com.trtc.uikit.livekit.component.karaoke.store.utils.MusicInfo
import io.trtc.tuikit.atomicxcore.api.live.LiveListStore
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONObject
import java.io.IOException

/**
 * Online music catalog service.
 *
 * Reflectively loaded by `SongServiceFactory`. The FQN
 * `com.tencent.rtcube.modules.voiceroom.karaoke.OnlineMusicService` and the public
 * no-arg constructor MUST be preserved together with the factory's class-name constant.
 */
class OnlineMusicService : MusicCatalogService() {
    private val defaultTagId = "72"
    private val mainHandler = Handler(Looper.getMainLooper())
    private val okHttpClient: OkHttpClient by lazy { OkHttpClient() }

    private val licenseKey: String get() = OnlineMusicService.licenseKey
    private val licenseUrl: String get() = OnlineMusicService.licenseUrl

    private fun currentToken(): String =
        LoginEntry.currentUser.value?.token?.takeIf { it.isNotEmpty() } ?: TokenCacheManager.getToken().orEmpty()

    private fun currentApaasUserId(): String = LoginEntry.currentUser.value?.apaasUserId.orEmpty()

    override fun getSongList(callback: GetSongListCallBack) {
        val body = JSONObject().apply {
            put("apaasUserId", currentApaasUserId())
            put("token", currentToken())
            put("scrollToken", "")
            put("limit", 10)
            put("tagId", defaultTagId)
        }
        request(
            url = baseUrl() + PATH_SEARCH_MUSIC_BY_TAG,
            body = body,
            onSuccess = { json ->
                val list = parseMusicInfoList(json)
                mainHandler.post { callback.onSuccess(list) }
            },
            onFailure = { code, msg ->
                mainHandler.post { callback.onFailure(code, msg ?: "unknown error") }
            },
        )
    }

    override fun queryPlayToken(musicId: String, userId: String, callback: QueryPlayTokenCallBack) {
        val roomId = runCatching {
            LiveListStore.shared().liveState.currentLive.value.liveID
        }.getOrDefault("")

        val body = JSONObject().apply {
            put("userId", userId)
            put("token", currentToken())
            put("musicId", musicId)
            put("roomId", roomId)
        }
        request(
            url = baseUrl() + PATH_QUERY_TOKEN,
            body = body,
            onSuccess = { json ->
                val data = json.optJSONObject("data")
                if (data == null) {
                    mainHandler.post { callback.onFailure(ERR_DATA_NULL, "Data is null") }
                    return@request
                }
                val resolvedMusicId = data.optString("musicId", musicId)
                val playToken = data.optString("playToken", "")
                mainHandler.post {
                    callback.onSuccess(resolvedMusicId, playToken, licenseKey, licenseUrl)
                }
            },
            onFailure = { code, msg ->
                mainHandler.post { callback.onFailure(code, msg ?: "queryPlayToken failed") }
            },
        )
    }

    override fun generateUserSig(userId: String, callback: ActionCallback) {
        val body = JSONObject().apply {
            put("userId", currentApaasUserId())
            put("token", currentToken())
            put("apaasUserId", currentApaasUserId())
            put("doctorUserId", userId)
        }
        request(
            url = baseUrl() + PATH_USER_DOCTOR_AUTH,
            body = body,
            onSuccess = { json ->
                val userSig = json.optJSONObject("data")?.optString("userSig", "").orEmpty()
                mainHandler.post { callback.onSuccess(userSig) }
            },
            onFailure = { code, msg ->
                mainHandler.post { callback.onFailed(code, msg) }
            },
        )
    }

    private fun baseUrl(): String {
        val raw = LoginEntry.config.httpBaseUrl
        return if (raw.endsWith("/")) raw else "$raw/"
    }

    private fun request(
        url: String,
        body: JSONObject,
        onSuccess: (JSONObject) -> Unit,
        onFailure: (Int, String?) -> Unit,
    ) {
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            Log.e(TAG, "request failed: invalid url=$url, httpBaseUrl not configured")
            mainHandler.post { onFailure(ERR_INVALID_URL, "invalid url: $url") }
            return
        }
        val req = Request.Builder()
            .url(url)
            .post(body.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()
        okHttpClient.newCall(req).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e(TAG, "request failed url=$url, err=${e.message}")
                onFailure(ERR_NETWORK_FAILURE, e.message)
            }

            override fun onResponse(call: Call, response: Response) {
                response.use { result ->
                    if (!result.isSuccessful) {
                        Log.e(TAG, "request failed url=$url, err=${result.code}")
                        onFailure(result.code, "HTTP ${result.code}")
                        return
                    }
                    val raw = result.body?.string()
                    if (raw.isNullOrEmpty()) {
                        Log.e(TAG, "request failed url=$url, err=empty body")
                        onFailure(ERR_RESPONSE_INVALID, "empty body")
                        return
                    }
                    val json = runCatching { JSONObject(raw) }.getOrNull()
                    if (json == null) {
                        Log.e(TAG, "request failed url=$url, err=invalid json")
                        onFailure(ERR_RESPONSE_INVALID, "invalid json")
                        return
                    }
                    val errorCode = json.optInt("errorCode", -1)
                    if (errorCode != 0) {
                        Log.e(TAG, "request failed url=$url, unknown error")
                        onFailure(errorCode, json.optString("errorMessage", "unknown error"))
                        return
                    }
                    Log.i(TAG, "request: $url success")
                    onSuccess(json)
                }
            }
        })
    }

    private fun parseMusicInfoList(json: JSONObject?): List<MusicInfo> {
        json ?: return emptyList()
        val data = json.optJSONObject("data") ?: return emptyList()
        val musicArray = data.optJSONArray("ktvMusicInfoSet") ?: return emptyList()
        val list = ArrayList<MusicInfo>(musicArray.length())
        for (i in 0 until musicArray.length()) {
            val item = musicArray.optJSONObject(i) ?: continue
            val info = MusicInfo(
                musicId = item.optString("MusicId", ""),
                musicName = item.optString("Name", ""),
                coverUrl = parseCoverUrl(item),
                artist = run {
                    val singers = item.optJSONArray("SingerSet") ?: return@run ""
                    val sb = StringBuilder()
                    for (j in 0 until singers.length()) {
                        if (j > 0) sb.append(", ")
                        sb.append(singers.optString(j, ""))
                    }
                    sb.toString()
                },
            )
            list += info
        }
        return list
    }

    private fun parseCoverUrl(dict: JSONObject): String {
        val albumInfo = dict.optJSONObject("AlbumInfo") ?: return ""
        val coverSet = albumInfo.optJSONArray("CoverInfoSet") ?: return ""
        for (i in 0 until coverSet.length()) {
            val cover = coverSet.optJSONObject(i) ?: continue
            if (cover.optString("Dimension") == "Mini") {
                val url = cover.optString("Url", "")
                if (url.isNotEmpty()) return url
            }
        }
        if (coverSet.length() > 0) {
            return coverSet.optJSONObject(0)?.optString("Url", "").orEmpty()
        }
        return ""
    }

    companion object {
        private const val TAG = "OnlineMusicService"
        private val JSON_MEDIA_TYPE: MediaType = "application/json; charset=utf-8".toMediaType()
        private const val PATH_SEARCH_MUSIC_BY_TAG = "base/v1/music/search_music_by_tag"
        private const val PATH_QUERY_TOKEN = "base/v1/music/query_token"
        private const val PATH_USER_DOCTOR_AUTH = "base/v1/auth_users/user_doctor_auth"

        private const val ERR_DATA_NULL = -1
        private const val ERR_RESPONSE_INVALID = -2
        private const val ERR_NETWORK_FAILURE = -3
        private const val ERR_INVALID_URL = -4

        @Volatile
        private var licenseKey: String = ""

        @Volatile
        private var licenseUrl: String = ""

        @JvmStatic
        fun setLicense(key: String, url: String) {
            licenseKey = key
            licenseUrl = url
        }
    }
}
