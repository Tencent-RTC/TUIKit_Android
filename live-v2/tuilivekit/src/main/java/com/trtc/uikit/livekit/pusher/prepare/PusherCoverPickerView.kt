package com.trtc.uikit.livekit.pusher.prepare

import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.LinearLayout
import com.trtc.uikit.livekit.R
import com.trtc.uikit.livekit.common.LiveKitResources
import com.trtc.uikit.livekit.common.theme.BottomSheetView
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.core.content.ContextCompat
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.load.resource.bitmap.CenterCrop
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target

/** Cover picker drawer for the five preset covers of the prepare stage. */
class PusherCoverPickerView(context: Context) : BottomSheetView(context) {

    /** Raised with the picked preset cover URL. */
    var onSelect: ((String) -> Unit)? = null

    private val options: List<Pair<String, PusherCoverOptionView>>

    private var selectedURL = LiveKitResources.defaultCoverURL

    override val sheetHeight: Int
        get() = context.resources.getDimensionPixelSize(R.dimen.livekit_pusher_cover_picker_height)

    init {
        sheetTitle = context.getString(R.string.livekit_pusher_prepare_change_cover)
        LayoutInflater.from(context).inflate(
            R.layout.livekit_pusher_cover_picker_drawer,
            contentView,
            true,
        )
        val optionsView = contentView.findViewById<LinearLayout>(
            R.id.livekit_pusher_cover_picker_options
        )
        val spacing = context.resources.getDimensionPixelSize(
            R.dimen.livekit_pusher_cover_picker_option_spacing
        )
        options = LiveKitResources.presetCoverURLs.mapIndexed { index, url ->
            val optionView = PusherCoverOptionView(context)
            optionView.contentDescription =
                context.getString(R.string.livekit_pusher_prepare_cover_option, index + 1)
            optionView.setOnClickListener {
                if (!optionView.isCandidateReady()) return@setOnClickListener
                selectedURL = url
                bind(url)
                onSelect?.invoke(url)
                dismiss()
            }
            optionsView.addView(
                optionView,
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                    if (index > 0) marginStart = spacing
                },
            )
            url to optionView
        }
        bind(selectedURL)
    }

    /** Marks the current cover and starts loading the candidates. */
    fun bind(coverURL: String) {
        selectedURL = coverURL
        options.forEach { (url, optionView) -> optionView.bind(url, url == coverURL) }
    }

    override fun show() {
        bind(selectedURL)
        super.show()
    }
}
/**
 * One cover candidate of the picker. Mirrors the private PusherCoverOptionView of
 * ios/live/.../Pusher/Prepare/PusherCoverPickerView.swift: a square rounded plate that stays
 * dimmed and unclickable until its remote thumbnail is loaded, and carries the blue stroke while
 * it is the picked cover.
 */
class PusherCoverOptionView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr) {

    private val imageView: ImageView
    private val cornerRadius =
        resources.getDimensionPixelSize(R.dimen.livekit_pusher_cover_picker_option_radius)

    private var representedURL = ""
    private var isLoaded = false

    init {
        LayoutInflater.from(context).inflate(R.layout.livekit_pusher_cover_option_view, this, true)
        imageView = findViewById(R.id.livekit_pusher_cover_option_image)
        background = ContextCompat.getDrawable(
            context,
            R.drawable.livekit_pusher_cover_option_background
        )
        isEnabled = false
        alpha = DIMMED_ALPHA
    }

    /** Keeps the candidate square, matching the iOS height == width constraint. */
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val side = MeasureSpec.getSize(widthMeasureSpec)
        val squareSpec = MeasureSpec.makeMeasureSpec(side, MeasureSpec.EXACTLY)
        super.onMeasure(squareSpec, squareSpec)
    }

    /** Applies the picked stroke, and loads the candidate once per URL. */
    fun bind(url: String, isSelected: Boolean) {
        this.isSelected = isSelected
        if (representedURL == url) return
        representedURL = url
        isLoaded = false
        isEnabled = false
        alpha = DIMMED_ALPHA
        imageView.setImageDrawable(null)
        Glide.with(this)
            .load(url)
            .diskCacheStrategy(DiskCacheStrategy.ALL)
            .transform(CenterCrop(), RoundedCorners(cornerRadius))
            .listener(object : RequestListener<Drawable> {
                override fun onLoadFailed(
                    error: GlideException?,
                    model: Any?,
                    target: Target<Drawable>?,
                    isFirstResource: Boolean,
                ): Boolean = false

                override fun onResourceReady(
                    resource: Drawable?,
                    model: Any?,
                    target: Target<Drawable>?,
                    dataSource: DataSource?,
                    isFirstResource: Boolean,
                ): Boolean {
                    if (representedURL != url) return false
                    isLoaded = true
                    isEnabled = true
                    alpha = 1f
                    return false
                }
            })
            .into(imageView)
    }

    /** True once the remote candidate is available, so the picker can ignore early taps. */
    fun isCandidateReady(): Boolean = isLoaded

    private companion object {
        const val DIMMED_ALPHA = 0.55f
    }
}
