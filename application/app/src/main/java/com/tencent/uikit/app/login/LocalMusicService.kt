package com.tencent.uikit.app.login

import com.trtc.uikit.livekit.component.karaoke.store.ActionCallback
import com.trtc.uikit.livekit.component.karaoke.store.GetSongListCallBack
import com.trtc.uikit.livekit.component.karaoke.store.MusicCatalogService
import com.trtc.uikit.livekit.component.karaoke.store.utils.MusicInfo
import com.tencent.qcloud.tuikit.debug.GenerateTestUserSig

class LocalMusicService : MusicCatalogService() {

    override fun getSongList(callback: GetSongListCallBack) {
        val localList = ArrayList<MusicInfo>()
        callback.onSuccess(localList)
    }

    override fun generateUserSig(
        userId: String,
        callback: ActionCallback,
    ) {
        callback.onSuccess(GenerateTestUserSig.genTestUserSig(userId))
    }
}