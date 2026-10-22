package com.trtc.uikit.roomkit.inroom.features.setting

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import com.trtc.uikit.roomkit.base.activity.BaseActivity

class RoomSettingsActivity : BaseActivity(), RoomSettingsView.NavigationHost {

    private lateinit var host: FrameLayout
    private val stack = ArrayDeque<View>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        host = FrameLayout(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
        }
        setContentView(host)

        val root = RoomSettingsView(this).apply { navigationHost = this@RoomSettingsActivity }
        push(root)
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (stack.size > 1) {
            finishCurrent()
        } else {
            super.onBackPressed()
        }
    }

    override fun pushOptions(mode: RoomSettingsOptionsView.Mode) {
        val options = RoomSettingsOptionsView(this, mode = mode).apply {
            navigationHost = this@RoomSettingsActivity
        }
        push(options)
    }

    override fun finishCurrent() {
        if (stack.size <= 1) {
            finishSafely()
            return
        }
        host.removeView(stack.removeLast())
        stack.last().visibility = View.VISIBLE
    }

    private fun push(view: View) {
        stack.lastOrNull()?.visibility = View.GONE
        stack.addLast(view)
        host.addView(
            view,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )
    }
}
