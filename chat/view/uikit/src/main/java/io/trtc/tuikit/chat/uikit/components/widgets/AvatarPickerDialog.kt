package io.trtc.tuikit.chat.uikit.components.widgets
import android.R
import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import io.trtc.tuikit.atomicx.theme.ThemeStore
import io.trtc.tuikit.atomicx.theme.tokens.ColorTokens
import io.trtc.tuikit.chat.uikit.components.common.expandTouchTarget

class AvatarPickerDialog(
    private val context: Context,
    private val title: String,
    private val imageUrls: List<String>,
    private val columnCount: Int = 4,
    private val onImageSelected: (index: Int, url: String) -> Unit
) {

    private val dialog: Dialog = Dialog(context, R.style.Theme_Translucent_NoTitleBar)
    private val density = context.resources.displayMetrics.density

    fun show() {
        val colors = ThemeStore.shared(context).themeState.value.currentTheme.tokens.color

        val rootView = buildRootView(colors)
        dialog.setContentView(rootView)
        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setLayout(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setGravity(Gravity.BOTTOM)
            clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        }
        dialog.setCanceledOnTouchOutside(true)
        dialog.show()
    }

    fun dismiss() {
        if (dialog.isShowing) {
            dialog.dismiss()
        }
    }

    private fun buildRootView(colors: ColorTokens): View {
        val overlay = FrameLayout(context).apply {
            layoutDirection = View.LAYOUT_DIRECTION_LOCALE
            setBackgroundColor(0x66000000)
            setOnClickListener { dismiss() }
        }

        val panel = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_LOCALE
            val bg = GradientDrawable().apply {
                cornerRadii = floatArrayOf(
                    dp(16f), dp(16f),
                    dp(16f), dp(16f),
                    0f, 0f,
                    0f, 0f
                )
                setColor(colors.bgColorOperate)
            }
            background = bg
            isClickable = true
            isFocusable = true
        }

        panel.addView(
            buildHeader(colors),
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        panel.addView(
            buildGridList(colors),
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )

        val panelParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            (context.resources.displayMetrics.heightPixels * 0.6f).toInt()
        ).apply {
            gravity = Gravity.BOTTOM
        }
        overlay.addView(panel, panelParams)
        return overlay
    }

    private fun buildHeader(colors: ColorTokens): View {
        val header = FrameLayout(context).apply {
            layoutDirection = View.LAYOUT_DIRECTION_LOCALE
            val padH = dp(16f).toInt()
            val padV = dp(14f).toInt()
            setPadding(padH, padV, padH, padV)
        }

        val titleView = TextView(context).apply {
            text = title
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            setTextColor(colors.textColorPrimary)
            setTypeface(typeface, Typeface.BOLD)
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
            text = "✕"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            setTextColor(colors.textColorSecondary)
            gravity = Gravity.CENTER
            val pad = dp(4f).toInt()
            setPadding(pad, pad, pad, pad)
            setOnClickListener { dismiss() }
        }
        header.addView(
            closeView,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER_VERTICAL or Gravity.END
            )
        )
        closeView.expandTouchTarget()

        return header
    }

    private fun buildGridList(colors: ColorTokens): View {
        val recyclerView = RecyclerView(context).apply {
            layoutDirection = View.LAYOUT_DIRECTION_LOCALE
            layoutManager = GridLayoutManager(context, columnCount)
            adapter = AvatarAdapter(imageUrls) { index, url ->
                onImageSelected(index, url)
                dismiss()
            }
            val pad = dp(12f).toInt()
            setPadding(pad, pad, pad, pad)
            clipToPadding = false
            setBackgroundColor(colors.bgColorOperate)
        }
        return recyclerView
    }

    private fun dp(value: Float): Float = value * density

    private class AvatarAdapter(
        private val urls: List<String>,
        private val onClick: (Int, String) -> Unit
    ) : RecyclerView.Adapter<AvatarAdapter.AvatarViewHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AvatarViewHolder {
            val container = FrameLayout(parent.context).apply {
                layoutDirection = View.LAYOUT_DIRECTION_LOCALE
                val density = resources.displayMetrics.density
                val pad = (8f * density).toInt()
                setPadding(pad, pad, pad, pad)
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            }
            val avatar = Avatar(parent.context).apply {
                setSize(Avatar.AvatarSize.XL)
            }
            container.addView(
                avatar,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    Gravity.CENTER
                )
            )
            return AvatarViewHolder(container, avatar)
        }

        override fun onBindViewHolder(holder: AvatarViewHolder, position: Int) {
            val url = urls[position]
            holder.avatar.setContent(
                Avatar.AvatarContent.Image(url = url, fallbackName = "")
            )
            holder.itemView.setOnClickListener { onClick(position, url) }
        }

        override fun getItemCount(): Int = urls.size

        class AvatarViewHolder(
            itemView: View,
            val avatar: Avatar
        ) : RecyclerView.ViewHolder(itemView)
    }
}
