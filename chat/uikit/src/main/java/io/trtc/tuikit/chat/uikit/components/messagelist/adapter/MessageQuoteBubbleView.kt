package io.trtc.tuikit.chat.uikit.components.messagelist.adapter
import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import io.trtc.tuikit.atomicx.common.imageloader.ImageLoader
import io.trtc.tuikit.atomicx.common.imageloader.ImageOptions
import io.trtc.tuikit.atomicx.theme.tokens.ColorTokens
import io.trtc.tuikit.atomicxcore.api.message.MessageQuoteInfo
import io.trtc.tuikit.chat.uikit.R
import io.trtc.tuikit.chat.uikit.components.common.RtlUtils
import io.trtc.tuikit.chat.uikit.components.emojipicker.EmojiSpanHelper
import io.trtc.tuikit.chat.uikit.components.messagelist.ui.MessageListTouchTargetTags

internal class MessageQuoteBubbleView(context: Context) : MaxWidthLinearLayout(context) {
    private val density = context.resources.displayMetrics.density
    private val senderView: TextView
    private val contentView: TextView
    private val thumbnailContainer: FrameLayout
    private val thumbnailView: ImageView
    private val videoOverlayView: TextView

    init {
        orientation = VERTICAL
        gravity = Gravity.CENTER_VERTICAL
        isClickable = true
        isFocusable = true
        MessageListTouchTargetTags.mark(this)

        senderView = createTextView().apply {
            typeface = Typeface.DEFAULT_BOLD
            maxLines = 1
        }
        addView(senderView, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))

        contentView = createTextView().apply {
            maxLines = 2
        }
        addView(
            contentView,
            LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
                topMargin = dpToPx(QUOTE_CONTENT_TEXT_TOP_MARGIN_DP)
            }
        )

        thumbnailContainer = FrameLayout(context).apply {
            visibility = GONE
        }
        addView(
            thumbnailContainer,
            LayoutParams(dpToPx(QUOTE_THUMBNAIL_SIZE_DP), dpToPx(QUOTE_THUMBNAIL_SIZE_DP)).apply {
                topMargin = dpToPx(QUOTE_THUMBNAIL_TOP_MARGIN_DP)
                gravity = Gravity.CENTER_VERTICAL
            }
        )

        thumbnailView = ImageView(context).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
        }
        thumbnailContainer.addView(
            thumbnailView,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )

        videoOverlayView = TextView(context).apply {
            text = VIDEO_PLAY_TEXT
            gravity = Gravity.CENTER
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            visibility = GONE
        }
        thumbnailContainer.addView(
            videoOverlayView,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )
    }

    fun bind(
        quoteInfo: MessageQuoteInfo,
        colors: ColorTokens,
        labels: MessageQuoteLabels = createLabels(),
        quoteMaxWidth: Int,
        onClick: (() -> Unit)?
    ) {
        maxWidth = quoteMaxWidth
        val displayData = MessageQuoteDisplayPolicy.resolve(quoteInfo, labels)
        val style = MessageQuoteBubbleStylePolicy.resolve(colors)
        orientation = style.orientation.toLinearLayoutOrientation()
        setPadding(
            dpToPx(style.contentPaddingDp),
            dpToPx(style.contentPaddingDp),
            dpToPx(style.contentPaddingDp),
            dpToPx(style.contentPaddingDp)
        )
        background = GradientDrawable().apply {
            cornerRadius = dpToPx(style.cornerRadiusDp).toFloat()
            setColor(style.backgroundColor)
        }
        applyContentAlignment(style.contentAlignment)
        senderView.setTextColor(style.senderTextColor)
        contentView.setTextColor(style.contentTextColor)
        videoOverlayView.setTextColor(colors.textColorAntiPrimary)
        senderView.visibility = if (displayData.senderName.isNotBlank()) VISIBLE else GONE
        senderView.text = context.getString(R.string.message_list_quote_sender_format, displayData.senderName)
        contentView.visibility = if (displayData.thumbnail == null) VISIBLE else GONE
        bindContentText(displayData)
        contentView.setTypeface(null, if (displayData.isStatusText) Typeface.ITALIC else Typeface.NORMAL)
        bindThumbnail(displayData.thumbnail, colors)
        contentDescription = buildContentDescription(displayData)
        setOnClickListener(if (onClick == null) null else View.OnClickListener { onClick() })
        isClickable = onClick != null
    }

    private fun bindContentText(displayData: MessageQuoteDisplayData) {
        val rawText = displayData.contentText
        val bindToken = "$rawText|${contentView.textSize}"
        contentView.setTag(R.id.message_list_text_bind_token_tag, bindToken)
        contentView.text = rawText
        if (contentView.visibility != VISIBLE || !displayData.shouldRenderEmoji) {
            return
        }
        EmojiSpanHelper.setEmojiSpanText(
            context = contentView.context,
            text = rawText,
            textSizePx = contentView.textSize,
            requestView = contentView
        ) { spanned ->
            if (contentView.getTag(R.id.message_list_text_bind_token_tag) == bindToken) {
                contentView.text = spanned
            }
        }
    }

    private fun bindThumbnail(thumbnail: MessageQuoteThumbnail?, colors: ColorTokens) {
        if (thumbnail == null) {
            thumbnailContainer.visibility = GONE
            thumbnailView.setImageDrawable(null)
            videoOverlayView.visibility = GONE
            return
        }
        thumbnailContainer.visibility = VISIBLE
        thumbnailContainer.background = GradientDrawable().apply {
            cornerRadius = dpToPx(QUOTE_THUMBNAIL_CORNER_RADIUS_DP).toFloat()
            setColor(colors.bgColorBubbleReciprocal)
        }
        val options = ImageOptions.Builder()
            .setPlaceImage(R.drawable.message_list_image_error_image)
            .setErrorImage(R.drawable.message_list_image_error_image)
            .build()
        ImageLoader.load(context, thumbnailView, thumbnail.path, options)
        videoOverlayView.visibility = if (thumbnail.isVideo) VISIBLE else GONE
    }

    private fun buildContentDescription(displayData: MessageQuoteDisplayData): String {
        return if (displayData.senderName.isBlank()) {
            displayData.contentText
        } else {
            context.getString(
                R.string.message_list_quote_accessibility_format,
                displayData.senderName,
                displayData.contentText
            )
        }
    }

    private fun createTextView(): TextView {
        return TextView(context).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            ellipsize = TextUtils.TruncateAt.END
            includeFontPadding = false
            textAlignment = View.TEXT_ALIGNMENT_TEXT_START
            textDirection = View.TEXT_DIRECTION_LOCALE
        }
    }

    private fun applyContentAlignment(alignment: MessageQuoteBubbleContentAlignment) {
        val gravityValue = alignment.toAbsoluteGravity(RtlUtils.isLocaleRtl(context))
        gravity = gravityValue
        applyLinearLayoutChildGravity(senderView, gravityValue)
        applyLinearLayoutChildGravity(contentView, gravityValue)
        applyLinearLayoutChildGravity(thumbnailContainer, gravityValue)
        senderView.gravity = gravityValue
        contentView.gravity = gravityValue
        senderView.textAlignment = View.TEXT_ALIGNMENT_TEXT_START
        contentView.textAlignment = View.TEXT_ALIGNMENT_TEXT_START
        senderView.textDirection = View.TEXT_DIRECTION_LOCALE
        contentView.textDirection = View.TEXT_DIRECTION_LOCALE
    }

    private fun applyLinearLayoutChildGravity(view: View, gravityValue: Int) {
        val params = view.layoutParams as? LayoutParams ?: return
        params.gravity = gravityValue
        view.layoutParams = params
    }

    private fun createLabels(): MessageQuoteLabels {
        return MessageQuoteLabels(
            deleted = context.getString(R.string.message_list_quote_deleted),
            revoked = context.getString(R.string.message_list_quote_revoked),
            image = context.getString(R.string.message_list_message_type_image),
            video = context.getString(R.string.message_list_message_type_video),
            voice = context.getString(R.string.message_list_message_type_voice),
            file = context.getString(R.string.message_list_message_type_file),
            face = context.getString(R.string.message_list_message_type_animate_emoji),
            custom = context.getString(R.string.message_list_message_tips_unsupport_custom_message),
            merged = context.getString(R.string.message_list_message_type_merged),
            unknown = context.getString(R.string.message_list_unsupported_message)
        )
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * density).toInt()
    }

    private companion object {
        private const val QUOTE_CONTENT_TEXT_TOP_MARGIN_DP = 2
        private const val QUOTE_THUMBNAIL_SIZE_DP = 36
        private const val QUOTE_THUMBNAIL_TOP_MARGIN_DP = 6
        private const val QUOTE_THUMBNAIL_CORNER_RADIUS_DP = 4
        private const val VIDEO_PLAY_TEXT = "▶"
    }
}

internal object MessageQuoteBubbleStylePolicy {
    fun resolve(colors: ColorTokens): MessageQuoteBubbleStyle {
        return MessageQuoteBubbleStyle(
            backgroundColor = colors.bgColorBubbleReciprocal,
            senderTextColor = colors.textColorSecondary,
            contentTextColor = colors.textColorSecondary,
            orientation = MessageQuoteBubbleOrientation.VERTICAL,
            contentAlignment = MessageQuoteBubbleContentAlignment.START,
            accentWidthDp = 0,
            cornerRadiusDp = 8,
            contentPaddingDp = 8
        )
    }
}

internal data class MessageQuoteBubbleStyle(
    val backgroundColor: Int,
    val senderTextColor: Int,
    val contentTextColor: Int,
    val orientation: MessageQuoteBubbleOrientation,
    val contentAlignment: MessageQuoteBubbleContentAlignment,
    val accentWidthDp: Int,
    val cornerRadiusDp: Int,
    val contentPaddingDp: Int
)

internal enum class MessageQuoteBubbleOrientation {
    VERTICAL;

    fun toLinearLayoutOrientation(): Int {
        return when (this) {
            VERTICAL -> LinearLayout.VERTICAL
            else -> LinearLayout.VERTICAL
        }
    }
}

internal enum class MessageQuoteBubbleContentAlignment {
    START;

    fun toAbsoluteGravity(isRtl: Boolean): Int {
        return when (this) {
            START -> if (isRtl) Gravity.RIGHT else Gravity.LEFT
            else -> if (isRtl) Gravity.RIGHT else Gravity.LEFT
        }
    }
}
