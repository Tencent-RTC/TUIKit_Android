package com.trtc.uikit.livekit.component.pippanel

class PIPPanelStore private constructor() {

    val state = PIPPanelState()

    companion object {
        @Volatile
        private var instance: PIPPanelStore? = null
        const val DEFAULT_VIDEO_WIDTH = 720
        const val DEFAULT_VIDEO_HEIGHT = 1280

        @JvmStatic
        fun sharedInstance(): PIPPanelStore {
            return instance ?: synchronized(this) {
                instance ?: PIPPanelStore().also { instance = it }
            }
        }
    }

    fun setPictureInPictureModeRoomId(roomId: String) {
        state.roomId.value = roomId
    }

    fun reset() {
        state.roomId.value = ""
        state.anchorIsPictureInPictureMode = false
        state.audienceIsPictureInPictureMode = false
        state.isAnchorStreaming = false
        state.width = DEFAULT_VIDEO_WIDTH
        state.height = DEFAULT_VIDEO_HEIGHT
    }
}