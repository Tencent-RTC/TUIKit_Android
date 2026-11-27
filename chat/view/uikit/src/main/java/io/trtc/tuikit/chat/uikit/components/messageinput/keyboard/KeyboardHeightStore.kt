package io.trtc.tuikit.chat.uikit.components.messageinput.keyboard
import android.content.Context
import com.tencent.mmkv.MMKV

internal interface KeyboardHeightStore {
    fun getHeight(): Int
    fun saveHeight(height: Int)
}

internal object KeyboardHeightPersistencePolicy {
    fun shouldSaveHeight(currentHeight: Int, newHeight: Int): Boolean {
        return newHeight > 0 && currentHeight != newHeight
    }
}

internal class MmkvKeyboardHeightStore(context: Context) : KeyboardHeightStore {
    private val mmkv: MMKV

    init {
        MMKV.initialize(context.applicationContext)
        mmkv = MMKV.mmkvWithID(MMKV_ID)
    }

    override fun getHeight(): Int {
        return mmkv.decodeInt(KEY_KEYBOARD_HEIGHT, 0)
    }

    override fun saveHeight(height: Int) {
        val currentHeight = getHeight()
        if (KeyboardHeightPersistencePolicy.shouldSaveHeight(currentHeight, height)) {
            mmkv.encode(KEY_KEYBOARD_HEIGHT, height)
        }
    }

    companion object {
        private const val MMKV_ID = "atomicx_keyboard_height"
        private const val KEY_KEYBOARD_HEIGHT = "keyboard_height"
    }
}
