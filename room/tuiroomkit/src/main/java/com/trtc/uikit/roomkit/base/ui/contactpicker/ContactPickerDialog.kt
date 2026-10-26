package com.trtc.uikit.roomkit.base.ui.contactpicker

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.trtc.uikit.roomkit.R

class ContactPickerDialog(
    context: Context,
    private val initialSelectedIds: List<String>,
    private val onConfirm: (List<String>) -> Unit
) : Dialog(context, R.style.RoomKitFullScreenDialog) {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val view = ContactPickerView(context).apply {
            setInitialSelectedIds(initialSelectedIds)
            onBackClick = { dismiss() }
            onConfirm = { ids ->
                onConfirm(ids)
                dismiss()
            }
        }
        setContentView(view)

        window?.apply {
            WindowCompat.setDecorFitsSystemWindows(this, false)
            setLayout(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            statusBarColor = Color.WHITE
            WindowCompat.getInsetsController(this, decorView).isAppearanceLightStatusBars = true
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                attributes.layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
            setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        }

        ViewCompat.setOnApplyWindowInsetsListener(view) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            v.updatePadding(
                top = systemBars.top,
                bottom = maxOf(systemBars.bottom, ime.bottom)
            )
            insets
        }
    }

    override fun dismiss() {
        window?.decorView?.let { decorView ->
            val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            imm?.hideSoftInputFromWindow(decorView.windowToken, 0)
        }
        super.dismiss()
    }
}
