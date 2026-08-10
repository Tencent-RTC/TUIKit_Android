package io.trtc.tuikit.chat.uikit.components.userpicker.ui

import android.app.Dialog
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.LinearLayout
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import io.trtc.tuikit.atomicx.theme.ThemeStore
import io.trtc.tuikit.atomicx.theme.tokens.ColorTokens
import io.trtc.tuikit.chat.uikit.components.common.WindowThemeUtil
import io.trtc.tuikit.chat.uikit.components.userpicker.model.UserPickerData
import io.trtc.tuikit.chat.uikit.components.widgets.DialogNavBar

internal class UserPickerDialog<T>(
    private val context: Context,
    private val title: String,
    private val dataSource: List<UserPickerData<T>>,
    private val maxCount: Int? = null,
    private val preSelectedKeys: List<String> = emptyList(),
    private val allowEmptyConfirm: Boolean = false,
    private val onConfirm: (List<T>) -> Unit
) {

    private var dialog: Dialog? = null
    private var selectedItems: List<T> = emptyList()
    private lateinit var navBar: DialogNavBar
    private var lifecycleOwner: LifecycleOwner? = null
    private var lifecycleObserver: DefaultLifecycleObserver? = null

    fun show() {
        dismissExistingDialog()
        bindLifecycleIfNeeded()

        val colors = getColors()

        dialog = Dialog(context, android.R.style.Theme_NoTitleBar).apply {
            window?.apply {
                setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
                setLayout(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT
                )
                addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
                WindowThemeUtil.applyDialogSystemBarStyle(this, colors)
            }
            setOnDismissListener {
                if (dialog === this) {
                    dialog = null
                }
            }
        }

        val rootLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_LOCALE
            setBackgroundColor(colors.bgColorTopBar)
            fitsSystemWindows = true
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        navBar = DialogNavBar.create(
            context,
            DialogNavBar.Config(
                mode = DialogNavBar.Mode.CancelTitleConfirm,
                title = title,
                colors = colors,
                onLeadingClick = { dismiss() },
                onConfirmClick = confirm@{
                    if (selectedItems.isEmpty() && !allowEmptyConfirm) return@confirm
                    onConfirm(selectedItems)
                    dismiss()
                }
            )
        )
        rootLayout.addView(navBar)

        val pickerView = UserPickerView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
            setMaxCount(maxCount)
            setDataSource(dataSource)
            setOnSelectedChangedListener<T> { selected ->
                selectedItems = selected.map { it.extraData }
                updateConfirmState(colors)
            }
            if (preSelectedKeys.isNotEmpty()) {
                setDefaultSelectedItems(preSelectedKeys)
            }
            selectedItems = getSelectedItems<T>().map { it.extraData }
        }
        rootLayout.addView(pickerView)

        dialog?.setContentView(rootLayout)
        dialog?.show()
        updateConfirmState(colors)
    }

    fun dismiss() {
        dismissExistingDialog()
    }

    private fun dismissExistingDialog() {
        val current = dialog ?: return
        dialog = null
        if (current.isShowing) {
            current.dismiss()
        }
    }

    private fun bindLifecycleIfNeeded() {
        if (lifecycleObserver != null) return
        val owner = findLifecycleOwner(context) ?: return
        val observer = object : DefaultLifecycleObserver {
            override fun onDestroy(owner: LifecycleOwner) {
                dismiss()
                unbindLifecycle()
            }
        }
        lifecycleOwner = owner
        lifecycleObserver = observer
        owner.lifecycle.addObserver(observer)
    }

    private fun unbindLifecycle() {
        val owner = lifecycleOwner
        val observer = lifecycleObserver
        lifecycleOwner = null
        lifecycleObserver = null
        if (owner != null && observer != null) {
            owner.lifecycle.removeObserver(observer)
        }
    }

    private fun findLifecycleOwner(context: Context): LifecycleOwner? {
        var current: Context? = context
        while (current is ContextWrapper) {
            if (current is LifecycleOwner) {
                return current
            }
            current = current.baseContext
        }
        return current as? LifecycleOwner
    }

    private fun updateConfirmState(colors: ColorTokens) {
        val enabled = selectedItems.isNotEmpty() || allowEmptyConfirm
        navBar.setConfirmEnabled(enabled, colors)
    }

    private fun getColors(): ColorTokens {
        return ThemeStore.shared(context).themeState.value.currentTheme.tokens.color
    }
}
