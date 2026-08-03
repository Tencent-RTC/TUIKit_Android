package com.trtc.uikit.roomkit.base.ui.widget

import android.app.Activity
import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.trtc.uikit.roomkit.R

/**
 * Generic top bar: status-bar spacer + back button + title + right-side slot.
 *
 * Automatically adapts to an immersive status bar. Supports the xml attribute `app:title`.
 * Mount a custom right-side View via [setRightView].
 */
class RoomTopBar @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr) {

    private val ivBack: ImageView
    private val tvTitle: TextView
    private val flRightSlot: FrameLayout

    /** Back-button click callback. When unset, falls back to `Activity.finish()`. */
    var onBackClick: (() -> Unit)? = null

    /** Right-view click callback. Only takes effect after [setRightView] has been called. */
    var onRightClick: (() -> Unit)? = null
        set(value) {
            field = value
            rightView?.setOnClickListener(if (value == null) null else View.OnClickListener { value.invoke() })
        }

    /** Title text. */
    var title: CharSequence?
        get() = tvTitle.text
        set(value) {
            tvTitle.text = value
        }

    /** The View currently mounted in the right-side slot, or `null` if none. */
    val rightView: View?
        get() = if (flRightSlot.childCount > 0) flRightSlot.getChildAt(0) else null

    init {
        LayoutInflater.from(context).inflate(R.layout.roomkit_top_bar, this, true)
        ivBack = findViewById(R.id.iv_back)
        tvTitle = findViewById(R.id.tv_title)
        flRightSlot = findViewById(R.id.fl_right_slot)

        if (attrs != null) {
            val ta = context.obtainStyledAttributes(attrs, R.styleable.RoomTopBar)
            try {
                ta.getString(R.styleable.RoomTopBar_title)?.let { tvTitle.text = it }
            } finally {
                ta.recycle()
            }
        }

        ivBack.setOnClickListener {
            val callback = onBackClick
            if (callback != null) {
                callback.invoke()
            } else {
                (getContext() as? Activity)?.finish()
            }
        }
    }

    /**
     * Mounts a custom right-side View; pass `null` to clear the current one.
     * The View must not already be attached to another parent. If [onRightClick] is set,
     * it will be applied to the new View automatically.
     */
    fun setRightView(view: View?) {
        flRightSlot.removeAllViews()
        if (view != null) {
            val callback = onRightClick
            if (callback != null) {
                view.setOnClickListener { callback.invoke() }
            }
            flRightSlot.addView(view)
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        applyStatusBarInset(findViewById(R.id.top_bar))
    }

    /** Dynamically sets the spacer height to the real status-bar inset, covering notches. */
    private fun applyStatusBarInset(topBar: View?) {
        if (topBar == null) return
        val spacer = topBar.findViewById<View>(R.id.v_status_bar_spacer) ?: return
        ViewCompat.setOnApplyWindowInsetsListener(spacer) { view, insets ->
            val statusBarHeight = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            if (statusBarHeight > 0) {
                val lp: ViewGroup.LayoutParams? = view.layoutParams
                if (lp != null && lp.height != statusBarHeight) {
                    lp.height = statusBarHeight
                    view.layoutParams = lp
                }
            }
            insets
        }
        ViewCompat.requestApplyInsets(spacer)
    }
}
