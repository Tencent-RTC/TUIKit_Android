package com.trtc.uikit.roomkit.inroom.features.setting

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import androidx.core.content.withStyledAttributes
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.trtc.uikit.roomkit.R
import androidx.appcompat.widget.AppCompatTextView

class RoomNavigationBar @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr) {

    private val statusSpacer: View
    private val titleView: AppCompatTextView
    private var onBackClick: (() -> Unit)? = null

    init {
        LayoutInflater.from(context).inflate(R.layout.roomkit_navigation_bar, this, true)
        statusSpacer = findViewById(R.id.status_spacer)
        titleView = findViewById(R.id.title)
        findViewById<View>(R.id.back_button).setOnClickListener { onBackClick?.invoke() }
        if (attrs != null) {
            context.withStyledAttributes(attrs, R.styleable.RoomNavigationBar) {
                titleView.text = getText(R.styleable.RoomNavigationBar_navigationTitle)
            }
        }
    }

    var title: CharSequence?
        get() = titleView.text
        set(value) { titleView.text = value }

    fun setOnBackClickListener(listener: (() -> Unit)?) {
        onBackClick = listener
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        applyStatusBarInset()
    }

    private fun applyStatusBarInset() {
        ViewCompat.setOnApplyWindowInsetsListener(statusSpacer) { view, insets ->
            val statusBarHeight = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            if (statusBarHeight > 0) {
                val layoutParams = view.layoutParams
                if (layoutParams.height != statusBarHeight) {
                    layoutParams.height = statusBarHeight
                    view.layoutParams = layoutParams
                }
            }
            insets
        }
        ViewCompat.requestApplyInsets(statusSpacer)
    }
}
