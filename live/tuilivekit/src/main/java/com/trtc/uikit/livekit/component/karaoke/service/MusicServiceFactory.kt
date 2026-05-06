package com.trtc.uikit.livekit.component.karaoke.service

import com.tencent.cloud.tuikit.engine.common.ContextProvider
import com.trtc.uikit.livekit.component.karaoke.store.MusicCatalogService

const val PACKAGE_RT_CUBE = "com.tencent.trtc"
class SongServiceFactory {
    companion object {
        private const val ONLINE_MUSIC_SERVICE_CLASS = "com.tencent.liteav.karaoke.service.OnlineMusicService"
        private const val LOCAL_MUSIC_SERVICE_CLASS = "com.tencent.uikit.app.login.LocalMusicService"

        fun getInstance(): MusicCatalogService? {
            val serviceClassName = if (isInternalDemo()) {
                ONLINE_MUSIC_SERVICE_CLASS
            } else {
                LOCAL_MUSIC_SERVICE_CLASS
            }

            return try {
                val clz = Class.forName(serviceClassName)
                val constructor = clz.getConstructor()
                constructor.newInstance() as MusicCatalogService
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }

        private fun isInternalDemo(): Boolean {
            return PACKAGE_RT_CUBE == ContextProvider.getApplicationContext()?.packageName
        }

    }
}