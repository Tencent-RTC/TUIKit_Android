package com.trtc.uikit.livekit.component.karaoke.store

import com.trtc.uikit.livekit.component.karaoke.store.utils.MusicInfo

interface QueryPlayTokenCallBack {
    fun onSuccess(
        musicId: String, playToken: String,
        copyrightedLicenseKey: String?,
        copyrightedLicenseUrl: String?,
    )

    fun onFailure(code: Int, desc: String)
}


interface GetSongListCallBack {
    fun onSuccess(songList: List<MusicInfo>)
    fun onFailure(code: Int, desc: String)
}

interface ActionCallback {
    fun onSuccess(userSig: String)

    fun onFailed(code: Int, msg: String?)
}