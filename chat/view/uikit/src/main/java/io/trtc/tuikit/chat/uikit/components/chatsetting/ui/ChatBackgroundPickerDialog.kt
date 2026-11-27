package io.trtc.tuikit.chat.uikit.components.chatsetting.ui
import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import io.trtc.tuikit.chat.uikit.R
import io.trtc.tuikit.chat.uikit.components.chatsetting.background.ChatBackgroundPresetItem
import io.trtc.tuikit.chat.uikit.components.chatsetting.background.ChatBackgroundPresetProvider
import io.trtc.tuikit.chat.uikit.components.common.expandTouchTarget
import io.trtc.tuikit.atomicx.theme.ThemeStore
import io.trtc.tuikit.atomicx.theme.tokens.ColorTokens

class ChatBackgroundPickerDialog(
    private val context: Context,
    private val selectedImageUri: String?,
    private val onBackgroundSelected: (String?) -> Unit
) {
    private val dialog = Dialog(context, android.R.style.Theme_Translucent_NoTitleBar)
    private val density = context.resources.displayMetrics.density

    fun show() {
        val colors = ThemeStore.shared(context).themeState.value.currentTheme.tokens.color
        dialog.setContentView(buildRootView(colors))
        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            setGravity(Gravity.BOTTOM)
            clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        }
        dialog.setCanceledOnTouchOutside(true)
        dialog.show()
    }

    private fun dismiss() {
        if (dialog.isShowing) {
            dialog.dismiss()
        }
    }

    private fun buildRootView(colors: ColorTokens): View {
        val overlay = FrameLayout(context).apply {
            layoutDirection = View.LAYOUT_DIRECTION_LOCALE
            setBackgroundColor(colors.bgColorMask)
            setOnClickListener { dismiss() }
        }

        val panel = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_LOCALE
            background = GradientDrawable().apply {
                cornerRadii = floatArrayOf(dp(16f), dp(16f), dp(16f), dp(16f), 0f, 0f, 0f, 0f)
                setColor(colors.bgColorOperate)
            }
            isClickable = true
            isFocusable = true
        }
        panel.addView(buildHeader(colors))
        panel.addView(
            buildGrid(colors),
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
        )

        overlay.addView(
            panel,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                (context.resources.displayMetrics.heightPixels * 0.65f).toInt(),
                Gravity.BOTTOM
            )
        )
        return overlay
    }

    private fun buildHeader(colors: ColorTokens): View {
        val header = FrameLayout(context).apply {
            layoutDirection = View.LAYOUT_DIRECTION_LOCALE
            setPadding(dp(16f).toInt(), dp(14f).toInt(), dp(16f).toInt(), dp(14f).toInt())
        }
        val titleView = TextView(context).apply {
            text = context.getString(R.string.chat_setting_select_chat_background)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            setTextColor(colors.textColorPrimary)
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            gravity = Gravity.CENTER
        }
        header.addView(
            titleView,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
            )
        )
        val closeView = TextView(context).apply {
            text = "×"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
            setTextColor(colors.textColorSecondary)
            gravity = Gravity.CENTER
            setOnClickListener { dismiss() }
        }
        header.addView(
            closeView,
            FrameLayout.LayoutParams(
                dp(32f).toInt(),
                dp(32f).toInt(),
                Gravity.CENTER_VERTICAL or Gravity.END
            )
        )
        closeView.expandTouchTarget()
        return header
    }

    private fun buildGrid(colors: ColorTokens): View {
        return RecyclerView(context).apply {
            layoutDirection = View.LAYOUT_DIRECTION_LOCALE
            layoutManager = GridLayoutManager(context, COLUMN_COUNT)
            adapter = ChatBackgroundAdapter(
                items = ChatBackgroundPresetProvider.getPresetItems(),
                selectedImageUri = selectedImageUri,
                colors = colors,
                onClick = { item ->
                    onBackgroundSelected(item.imageUri)
                    dismiss()
                }
            )
            val padding = dp(12f).toInt()
            setPadding(padding, padding, padding, padding)
            clipToPadding = false
            setBackgroundColor(colors.bgColorOperate)
        }
    }

    private fun dp(value: Float): Float = value * density

    private class ChatBackgroundAdapter(
        private val items: List<ChatBackgroundPresetItem>,
        private val selectedImageUri: String?,
        private val colors: ColorTokens,
        private val onClick: (ChatBackgroundPresetItem) -> Unit
    ) : RecyclerView.Adapter<ChatBackgroundAdapter.ViewHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val density = parent.resources.displayMetrics.density
            val container = FrameLayout(parent.context).apply {
                val padding = (8f * density).toInt()
                setPadding(padding, padding, padding, padding)
                layoutParams = RecyclerView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            }
            val preview = FrameLayout(parent.context).apply {
                background = GradientDrawable().apply {
                    cornerRadius = 8f * density
                    setColor(this@ChatBackgroundAdapter.colors.bgColorTopBar)
                }
                clipToOutline = true
            }
            container.addView(
                preview,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    (120f * density).toInt(),
                    Gravity.CENTER
                )
            )
            return ViewHolder(container, preview)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = items[position]
            holder.preview.removeAllViews()
            holder.preview.background = buildPreviewBackground(holder.preview.context, item)
            if (item.isDefault) {
                holder.preview.addView(buildDefaultLabel(holder.preview.context))
            } else {
                val imageView = ImageView(holder.preview.context).apply {
                    scaleType = ImageView.ScaleType.CENTER_CROP
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }
                holder.preview.addView(imageView)
                Glide.with(holder.preview.context.applicationContext)
                    .load(item.thumbnailUri)
                    .centerCrop()
                    .into(imageView)
            }
            holder.itemView.setOnClickListener { onClick(item) }
        }

        override fun getItemCount(): Int = items.size

        private fun buildPreviewBackground(context: Context, item: ChatBackgroundPresetItem): GradientDrawable {
            val density = context.resources.displayMetrics.density
            val selected = item.imageUri == selectedImageUri || (item.isDefault && selectedImageUri.isNullOrBlank())
            return GradientDrawable().apply {
                cornerRadius = 8f * density
                setColor(this@ChatBackgroundAdapter.colors.bgColorTopBar)
                if (selected) {
                    setStroke(
                        (2f * density).toInt(),
                        this@ChatBackgroundAdapter.colors.buttonColorPrimaryDefault
                    )
                }
            }
        }

        private fun buildDefaultLabel(context: Context): View {
            return TextView(context).apply {
                text = context.getString(R.string.chat_setting_chat_background_default)
                setTextColor(colors.textColorSecondary)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                gravity = Gravity.CENTER
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            }
        }

        class ViewHolder(
            itemView: View,
            val preview: FrameLayout
        ) : RecyclerView.ViewHolder(itemView)
    }

    private companion object {
        const val COLUMN_COUNT = 2
    }
}
