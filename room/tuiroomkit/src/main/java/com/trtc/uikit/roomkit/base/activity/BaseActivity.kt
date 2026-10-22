package com.trtc.uikit.roomkit.base.activity

import android.os.Bundle
import android.view.MotionEvent
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import com.trtc.uikit.roomkit.base.util.dismissKeyboardOnOutsideTouch

abstract class BaseActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        dismissKeyboardOnOutsideTouch(event)
        return super.dispatchTouchEvent(event)
    }

    protected fun finishSafely(finishAll: Boolean = false) {
        window.decorView.postDelayed({
            if (isFinishing || isDestroyed) return@postDelayed
            runCatching {
                if (finishAll) finishAffinity() else finish()
            }
        }, FINISH_DELAY_MS)
    }

    private companion object {
        const val FINISH_DELAY_MS = 250L
    }
}
