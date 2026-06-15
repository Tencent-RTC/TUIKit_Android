package com.trtc.uikit.livekit.component.bgm.store

import android.util.Log
import io.trtc.tuikit.atomicxcore.api.device.MusicPlayStatus
import io.trtc.tuikit.atomicxcore.api.device.MusicStore
import kotlinx.coroutines.flow.MutableStateFlow

data class BGMInfo(
    val id: Int = INVALID_ID,
    val name: String = "",
    val path: String = "",
    val pitch: MutableStateFlow<Float> = MutableStateFlow(0.0f)
) {
    companion object {
        const val INVALID_ID = -1
    }

    constructor(id: Int, name: String, musicPath: String) : this(
        id = id,
        name = name,
        path = musicPath
    )
}

class BGMState {
    val currentMusicInfo = MutableStateFlow(BGMInfo())
    val musicList = mutableListOf<BGMInfo>()
}

class BGMStore(val roomId: String) {

    companion object {
        const val TAG = "MusicService"
    }

    val bgmState = BGMState()
    private val musicStore: MusicStore = MusicStore.create(roomId)

    /**
     * 判断指定音乐是否正在播放。
     * LOADING 和 PLAYING 状态均视为正在播放。
     */
    fun isMusicPlaying(musicInfo: BGMInfo): Boolean {
        val currentInfo = bgmState.currentMusicInfo.value
        if (currentInfo.id == BGMInfo.INVALID_ID || currentInfo.id != musicInfo.id) {
            return false
        }
        val status = musicStore.musicState.playStatus.value
        return status == MusicPlayStatus.PLAYING || status == MusicPlayStatus.LOADING
    }

    fun operatePlayMusic(musicInfo: BGMInfo) {
        val currentMusicInfo = bgmState.currentMusicInfo.value
        if (currentMusicInfo.id != BGMInfo.INVALID_ID && currentMusicInfo.id != musicInfo.id) {
            stopMusic()
        }
        bgmState.currentMusicInfo.value = musicInfo
        val isPlaying = isMusicPlaying(musicInfo)
        Log.i(TAG, "operatePlayMusic:[isPlaying:$isPlaying]")
        if (isPlaying) {
            stopMusic()
        } else {
            startMusic(musicInfo)
        }
    }

    fun refreshCurrentMusicInfo() {
        val currentPlayUrl = musicStore.musicState.playURL.value
        bgmState.currentMusicInfo.value = bgmState.musicList.find { it.path == currentPlayUrl } ?: BGMInfo()
    }

    private fun startMusic(musicInfo: BGMInfo) {
        Log.i(TAG, "[$roomId] startMusic:[musicInfo:$musicInfo]")
        musicStore.setPitch(musicInfo.pitch.value.toDouble())
        musicStore.startPlay(musicInfo.path, null)
    }

    private fun stopMusic() {
        Log.i(TAG, "[$roomId] stopMusic")
        musicStore.stopPlay()
    }
}