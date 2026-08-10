package io.trtc.tuikit.chat.uikit.components.common.uicustom

import android.content.Context

@DslMarker
annotation class CustomItemDsl

interface CustomItem {
    val ID: String
}

interface EditorContext {
    val androidContext: Context
}

fun interface Customizer<C : EditorContext, I : CustomItem> {
    fun customize(editor: CustomEditor<C, I>)
}
