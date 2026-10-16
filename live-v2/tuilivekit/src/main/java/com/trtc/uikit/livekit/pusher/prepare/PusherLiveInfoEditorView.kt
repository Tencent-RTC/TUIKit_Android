package com.trtc.uikit.livekit.pusher.prepare

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import com.trtc.uikit.livekit.R
import com.trtc.uikit.livekit.common.LiveKitResources
import com.trtc.uikit.livekit.common.basic.imageloader.ImageLoader
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.constraintlayout.widget.ConstraintLayout
import com.trtc.uikit.livekit.common.theme.LiveTextInputView
import androidx.core.content.ContextCompat
import androidx.core.widget.doAfterTextChanged

/** Live cover, title field and edit hint of the prepare stage. */
class PusherLiveInfoEditorView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : ConstraintLayout(context, attrs, defStyleAttr) {

    /** Raised by the cover thumbnail and its "change cover" hint. */
    var onChangeCover: (() -> Unit)? = null
    internal var onTitleChanged: ((String) -> Unit)? = null

    private val coverView: ImageView
    private val titleInput: LiveTextInputView

    private var representedCoverURL = ""
    private var isBindingTitle = false

    /** Current live title, trimmed. Empty when the host cleared the field. */
    val title: String
        get() = titleInput.text.toString().trim()

    internal val draftTitle: String
        get() = titleInput.text.toString()

    init {
        LayoutInflater.from(context).inflate(R.layout.livekit_pusher_live_setup_view, this, true)
        coverView = findViewById(R.id.livekit_pusher_prepare_cover)
        titleInput = findViewById(R.id.livekit_pusher_prepare_title)

        titleInput.setInputLimit(100, R.string.livekit_topic_byte_limit, LiveTextInputView.LimitUnit.UTF8_BYTES)
        titleInput.doAfterTextChanged {
            if (!isBindingTitle) onTitleChanged?.invoke(titleInput.text.toString())
        }
        titleInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                titleInput.clearFocus()
                hideKeyboard()
                true
            } else {
                false
            }
        }
        findViewById<TextView>(R.id.livekit_pusher_prepare_edit_hint).setOnClickListener {
            titleInput.requestFocus()
            titleInput.setSelection(titleInput.length())
            showKeyboard()
        }
        findViewById<View>(R.id.livekit_pusher_prepare_cover_entry).setOnClickListener {
            titleInput.clearFocus()
            hideKeyboard()
            onChangeCover?.invoke()
        }
        bindCover(LiveKitResources.defaultCoverURL)
    }

    /** Applies the stored title, leaving an in-progress edit untouched. */
    fun bindTitle(title: String) {
        if (titleInput.hasFocus()) return
        if (titleInput.text.toString() == title) return
        isBindingTitle = true
        try {
            titleInput.setText(title)
        } finally {
            isBindingTitle = false
        }
    }

    /** Applies the picked cover to the header thumbnail. */
    fun bindCover(coverURL: String) {
        if (representedCoverURL == coverURL) return
        representedCoverURL = coverURL
        ImageLoader.load(context, coverView, coverURL, 0)
    }

    private fun showKeyboard() {
        ViewCompat.getWindowInsetsController(titleInput)?.show(WindowInsetsCompat.Type.ime())
    }

    private fun hideKeyboard() {
        ViewCompat.getWindowInsetsController(titleInput)?.hide(WindowInsetsCompat.Type.ime())
    }
}

/**
 * Cover thumbnail of the prepare header. Draws the diagonal gradient placeholder and the
 * hairline border of the Ardot 封面缩略图 58x58 node, and clips its child image view to the
 * same rounded rectangle.
 *
 * The Ardot node carries a drop shadow, but the iOS cover sets clipsToBounds, so the
 * template shows no shadow and neither does this view.
 */
class PusherCoverThumbnailView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr) {

    private val radius = resources.getDimension(R.dimen.livekit_pusher_prepare_cover_radius)
    private val borderWidth =
        resources.getDimension(R.dimen.livekit_pusher_prepare_cover_border_width)

    private val gradientStartColor =
        ContextCompat.getColor(context, R.color.livekit_pusher_prepare_cover_gradient_start)
    private val gradientEndColor =
        ContextCompat.getColor(context, R.color.livekit_pusher_prepare_cover_gradient_end)

    private val gradientPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = borderWidth
        color = ContextCompat.getColor(context, R.color.livekit_pusher_prepare_cover_border)
    }

    private val bounds = RectF()
    private val borderBounds = RectF()
    private val clipPath = Path()
    private var borderRadius = radius

    init {
        setWillNotDraw(false)
    }

    override fun onSizeChanged(width: Int, height: Int, oldWidth: Int, oldHeight: Int) {
        super.onSizeChanged(width, height, oldWidth, oldHeight)

        bounds.set(0f, 0f, width.toFloat(), height.toFloat())
        val inset = borderWidth / 2f
        borderBounds.set(inset, inset, width - inset, height - inset)
        borderRadius = (radius - inset).coerceAtLeast(0f)

        clipPath.reset()
        clipPath.addRoundRect(bounds, radius, radius, Path.Direction.CW)

        gradientPaint.shader = LinearGradient(
            0f,
            0f,
            width.toFloat(),
            height.toFloat(),
            gradientStartColor,
            gradientEndColor,
            Shader.TileMode.CLAMP
        )
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawRoundRect(bounds, radius, radius, gradientPaint)
        super.onDraw(canvas)
    }

    override fun dispatchDraw(canvas: Canvas) {
        val saveCount = canvas.save()
        canvas.clipPath(clipPath)
        super.dispatchDraw(canvas)
        canvas.restoreToCount(saveCount)

        canvas.drawRoundRect(borderBounds, borderRadius, borderRadius, borderPaint)
    }
}
