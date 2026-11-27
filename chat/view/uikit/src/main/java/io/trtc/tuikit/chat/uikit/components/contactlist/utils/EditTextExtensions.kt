package io.trtc.tuikit.chat.uikit.components.contactlist.utils
import android.text.Editable
import android.text.TextWatcher
import android.widget.EditText

internal fun EditText.setAfterTextChangedListener(onChanged: (String) -> Unit) {
    addTextChangedListener(object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        override fun afterTextChanged(s: Editable?) {
            onChanged(s?.toString().orEmpty())
        }
    })
}
