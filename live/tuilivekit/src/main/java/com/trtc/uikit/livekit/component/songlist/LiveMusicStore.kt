package com.trtc.uikit.livekit.component.songlist

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import com.tencent.cloud.tuikit.engine.common.TUICommonDefine
import com.tencent.cloud.tuikit.engine.extension.TUISongListManager
import com.tencent.cloud.tuikit.engine.extension.TUISongListManager.SongInfo
import com.tencent.cloud.tuikit.engine.extension.TUISongListManager.SongListChangeReason
import com.tencent.cloud.tuikit.engine.extension.TUISongListManager.SongListResult
import com.tencent.cloud.tuikit.engine.room.TUIRoomDefine
import com.tencent.cloud.tuikit.engine.room.TUIRoomEngine
import com.trtc.uikit.livekit.R
import com.trtc.uikit.livekit.common.DEFAULT_COVER_URL
import com.trtc.uikit.livekit.component.karaoke.service.SongServiceFactory
import com.trtc.uikit.livekit.component.karaoke.store.GetSongListCallBack
import com.trtc.uikit.livekit.component.karaoke.store.MusicCatalogService
import com.trtc.uikit.livekit.component.karaoke.store.utils.MusicInfo
import com.trtc.uikit.livekit.component.karaoke.store.utils.PlaybackState
import com.trtc.uikit.livekit.common.ErrorLocalized

private const val TAG = "LiveMusicStore"

data class LiveSong(
    val songId: String,
    val songName: String,
    val artist: String,
    val coverUrl: String,
    val playUrl: String,
    val requester: String = "",
    val requesterAvatarUrl: String = "",
) {
    companion object {
        fun from(songInfo: SongInfo): LiveSong {
            val requesterName = if (songInfo.requester.userName.isNullOrEmpty()) {
                songInfo.requester.userId ?: ""
            } else {
                songInfo.requester.userName
            }
            return LiveSong(
                songId = songInfo.songId ?: "",
                songName = songInfo.songName ?: "",
                requester = requesterName,
                requesterAvatarUrl = songInfo.requester.avatarUrl ?: "",
                artist = songInfo.artistName ?: "",
                coverUrl = songInfo.coverUrl ?: "",
                playUrl = songInfo.songId ?: "",
            )
        }
    }
}

class LiveMusicStore(private val context: Context, private val liveId: String) {

    val librarySongs: StateFlow<List<LiveSong>> get() = _librarySongs.asStateFlow()
    val selectedSongs: StateFlow<List<LiveSong>> get() = _selectedSongs.asStateFlow()
    val currentSongId: StateFlow<String> get() = _currentSongId.asStateFlow()
    val playStatus: StateFlow<PlaybackState> get() = _playStatus.asStateFlow()
    val errorMessage: StateFlow<String?> get() = _errorMessage.asStateFlow()

    private val _librarySongs = MutableStateFlow<List<LiveSong>>(emptyList())
    private val _selectedSongs = MutableStateFlow<List<LiveSong>>(emptyList())
    private val _currentSongId = MutableStateFlow("")
    private val _playStatus = MutableStateFlow(PlaybackState.IDLE)
    private val _errorMessage = MutableStateFlow<String?>(null)

    private val songListManager: TUISongListManager = TUIRoomEngine.sharedInstance().songListManager
    private val mainHandler = Handler(Looper.getMainLooper())
    private var musicCatalogService: MusicCatalogService? = null

    private val songListObserver: TUISongListManager.Observer =
        object : TUISongListManager.Observer() {
            override fun onWaitingListChanged(
                reason: SongListChangeReason?,
                changedSongs: MutableList<SongInfo?>?,
            ) {
                mainHandler.post {
                    handleWaitingListChanged(reason, changedSongs)
                }
            }
        }

    init {
        musicCatalogService = SongServiceFactory.getInstance()
        songListManager.addObserver(songListObserver)
        loadLibrarySongs()
        fetchWaitingList(null)
    }

    // MARK: - Public API

    fun addSong(song: LiveSong) {
        val currentList = _selectedSongs.value.toMutableList()
        if (currentList.any { it.songId == song.songId }) return

        currentList.add(song)
        _selectedSongs.value = currentList

        val songInfo = SongInfo().apply {
            songId = song.songId
            songName = song.songName
            artistName = song.artist
            coverUrl = song.coverUrl.ifEmpty { DEFAULT_COVER_URL }
        }
        val selfInfo = TUIRoomEngine.getSelfInfo()
        songInfo.requester.userId = selfInfo.userId
        songInfo.requester.userName = selfInfo.userName
        songInfo.requester.avatarUrl = selfInfo.avatarUrl

        songListManager.addSong(listOf(songInfo), object : TUIRoomDefine.ActionCallback {
            override fun onSuccess() {
                Log.d(TAG, "addSong: success")
            }

            override fun onError(code: TUICommonDefine.Error?, message: String?) {
                Log.e(TAG, "addSong: error, code=${code?.value}, msg=$message")
                val list = _selectedSongs.value.toMutableList()
                list.removeAll { it.songId == song.songId }
                _selectedSongs.value = list
                showError(code?.value, message)
            }
        })
    }

    fun removeSong(songId: String) {
        if (_currentSongId.value == songId) {
            // Current playing song - use playNext
            songListManager.playNextSong(object : TUIRoomDefine.ActionCallback {
                override fun onSuccess() {
                    Log.d(TAG, "removeSong(playNext): success")
                    _playStatus.value = PlaybackState.IDLE
                }

                override fun onError(code: TUICommonDefine.Error?, message: String?) {
                    Log.e(TAG, "removeSong(playNext): error, code=${code?.value}, msg=$message")
                    showError(code?.value, message)
                }
            })
        } else {
            val currentList = _selectedSongs.value.toMutableList()
            val removedSong = currentList.firstOrNull { it.songId == songId }
            val removedIndex = currentList.indexOfFirst { it.songId == songId }
            currentList.removeAll { it.songId == songId }
            _selectedSongs.value = currentList

            songListManager.removeSong(listOf(songId), object : TUIRoomDefine.ActionCallback {
                override fun onSuccess() {
                    Log.d(TAG, "removeSong: success")
                }

                override fun onError(code: TUICommonDefine.Error?, message: String?) {
                    Log.e(TAG, "removeSong: error, code=${code?.value}, msg=$message")
                    if (removedSong != null && removedIndex >= 0) {
                        val list = _selectedSongs.value.toMutableList()
                        val insertIndex = minOf(removedIndex, list.size)
                        list.add(insertIndex, removedSong)
                        _selectedSongs.value = list
                    }
                    showError(code?.value, message)
                }
            })
        }
    }

    fun topSong(songId: String) {
        val currentList = _selectedSongs.value.toMutableList()
        val index = currentList.indexOfFirst { it.songId == songId }
        if (index > 1) {
            val song = currentList.removeAt(index)
            val insertIndex = minOf(1, currentList.size)
            currentList.add(insertIndex, song)
            _selectedSongs.value = currentList
        }

        songListManager.setNextSong(songId, object : TUIRoomDefine.ActionCallback {
            override fun onSuccess() {
                Log.d(TAG, "topSong: success")
            }

            override fun onError(code: TUICommonDefine.Error?, message: String?) {
                Log.e(TAG, "topSong: error, code=${code?.value}, msg=$message")
                fetchWaitingList(null)
                showError(code?.value, message)
            }
        })
    }

    fun playNext() {
        songListManager.playNextSong(object : TUIRoomDefine.ActionCallback {
            override fun onSuccess() {
                Log.d(TAG, "playNext: success")
                _playStatus.value = PlaybackState.IDLE
            }

            override fun onError(code: TUICommonDefine.Error?, message: String?) {
                Log.e(TAG, "playNext: error, code=${code?.value}, msg=$message")
                showError(code?.value, message)
            }
        })
    }

    fun pause() {
        _playStatus.value = PlaybackState.PAUSE
    }

    fun resume() {
        _playStatus.value = PlaybackState.RESUME
    }

    fun isSongSelected(songId: String): Boolean {
        return _selectedSongs.value.any { it.songId == songId }
    }

    fun destroy() {
        songListManager.removeObserver(songListObserver)
    }

    // MARK: - Private

    private fun startPlay(song: LiveSong) {
        _currentSongId.value = song.songId
        _playStatus.value = PlaybackState.START
    }

    private fun loadLibrarySongs() {
        musicCatalogService?.getSongList(object : GetSongListCallBack {
            override fun onSuccess(songList: List<MusicInfo>) {
                if (songList.isNotEmpty()) {
                    _librarySongs.value = songList.map { musicInfo ->
                        LiveSong(
                            songId = musicInfo.musicId,
                            songName = musicInfo.musicName,
                            artist = musicInfo.artist,
                            coverUrl = musicInfo.coverUrl,
                            playUrl = musicInfo.musicId,
                        )
                    }
                } else {
                    _librarySongs.value = buildDefaultSongList(context)
                }
            }

            override fun onFailure(code: Int, desc: String) {
                Log.e(TAG, "loadLibrarySongs: failed, code=$code, desc=$desc")
                _librarySongs.value = buildDefaultSongList(context)
            }
        }) ?: run {
            _librarySongs.value = buildDefaultSongList(context)
        }
    }

    companion object {
        fun buildDefaultSongList(context: Context): List<LiveSong> {
            val unknown = context.getString(R.string.live_song_unknown_artist)
            return listOf(
                LiveSong(
                    songId = "001",
                    songName = context.getString(R.string.common_music_cheerful),
                    artist = unknown,
                    coverUrl = "",
                    playUrl = "https://dldir1.qq.com/hudongzhibo/TUIKit/resource/music/PositiveHappyAdvertising.mp3",
                ),
                LiveSong(
                    songId = "002",
                    songName = context.getString(R.string.common_music_melancholy),
                    artist = unknown,
                    coverUrl = "",
                    playUrl = "https://dldir1.qq.com/hudongzhibo/TUIKit/resource/music/SadCinematicPiano.mp3",
                ),
                LiveSong(
                    songId = "003",
                    songName = context.getString(R.string.common_music_wonder_world),
                    artist = unknown,
                    coverUrl = "",
                    playUrl = "https://dldir1.qq.com/hudongzhibo/TUIKit/resource/music/WonderWorld.mp3",
                ),
            )
        }
    }

    private fun fetchWaitingList(cursor: String?) {
        val allSongs = mutableListOf<SongInfo>()
        fetchNextPage(cursor, allSongs)
    }

    private fun fetchNextPage(cursor: String?, accumulator: MutableList<SongInfo>) {
        songListManager.getWaitingList(cursor, 20, object : TUISongListManager.SongListCallback {
            override fun onSuccess(result: SongListResult?) {
                if (result == null) {
                    updateWaitingList(accumulator)
                    return
                }
                if (!result.songList.isNullOrEmpty()) {
                    accumulator.addAll(result.songList)
                }
                val nextCursor = result.cursor
                if (!nextCursor.isNullOrEmpty()) {
                    fetchNextPage(nextCursor, accumulator)
                } else {
                    updateWaitingList(accumulator)
                }
            }

            override fun onError(code: TUICommonDefine.Error?, msg: String?) {
                Log.e(TAG, "fetchWaitingList: error, code=${code?.value}, msg=$msg")
            }
        })
    }

    private fun updateWaitingList(songList: List<SongInfo>) {
        val wasEmpty = _selectedSongs.value.isEmpty()
        _selectedSongs.value = songList.map { LiveSong.from(it) }

        if (wasEmpty && _selectedSongs.value.isNotEmpty()) {
            val firstSong = _selectedSongs.value.first()
            startPlay(firstSong)
        }
    }

    private fun showError(code: Int?, message: String?) {
        val errorCode = code ?: -1
        mainHandler.post {
            ErrorLocalized.onError(errorCode)
        }
    }

    // MARK: - Song List Observer

    private fun handleWaitingListChanged(
        reason: SongListChangeReason?,
        changedSongs: MutableList<SongInfo?>?,
    ) {
        if (reason == null || changedSongs.isNullOrEmpty()) return

        val currentList = _selectedSongs.value.toMutableList()

        when (reason) {
            SongListChangeReason.ADD -> {
                changedSongs.filterNotNull().forEach { songInfo ->
                    val song = LiveSong.from(songInfo)
                    val existingIndex = currentList.indexOfFirst { it.songId == song.songId }
                    if (existingIndex >= 0) {
                        currentList[existingIndex] = song
                    } else {
                        currentList.add(song)
                    }
                }
                _selectedSongs.value = currentList

                if (_playStatus.value == PlaybackState.IDLE && _currentSongId.value.isEmpty()) {
                    val firstSong = _selectedSongs.value.firstOrNull()
                    if (firstSong != null) {
                        startPlay(firstSong)
                    }
                }
            }

            SongListChangeReason.REMOVE -> {
                val removedIds = changedSongs.filterNotNull().map { it.songId }.toSet()
                val isCurrentSongRemoved = removedIds.contains(_currentSongId.value)

                currentList.removeAll { removedIds.contains(it.songId) }
                _selectedSongs.value = currentList

                if (isCurrentSongRemoved) {
                    _currentSongId.value = ""
                    _playStatus.value = PlaybackState.IDLE

                    val nextSong = _selectedSongs.value.firstOrNull()
                    if (nextSong != null) {
                        startPlay(nextSong)
                    }
                }
            }

            SongListChangeReason.ORDER_CHANGED -> {
                changedSongs.filterNotNull().firstOrNull()?.let { songInfo ->
                    val songId = songInfo.songId ?: return@let
                    val index = currentList.indexOfFirst { it.songId == songId }
                    if (index >= 0) {
                        val song = currentList.removeAt(index)
                        val insertIndex = minOf(1, currentList.size)
                        currentList.add(insertIndex, song)
                        _selectedSongs.value = currentList
                    }
                }
            }

            SongListChangeReason.UNKNOWN -> {
                val removedIds = changedSongs.filterNotNull().map { it.songId }.toSet()
                currentList.removeAll { removedIds.contains(it.songId) }
                _selectedSongs.value = currentList

                if (_selectedSongs.value.isEmpty()) {
                    _currentSongId.value = ""
                    _playStatus.value = PlaybackState.IDLE
                }
            }
        }
    }
}
