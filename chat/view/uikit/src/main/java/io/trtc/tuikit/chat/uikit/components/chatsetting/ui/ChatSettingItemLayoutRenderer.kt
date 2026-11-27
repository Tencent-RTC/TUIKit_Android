package io.trtc.tuikit.chat.uikit.components.chatsetting.ui

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import io.trtc.tuikit.atomicx.common.util.ScreenUtil.dp2px
import io.trtc.tuikit.atomicx.theme.ThemeStore
import io.trtc.tuikit.atomicx.theme.tokens.ColorTokens
import io.trtc.tuikit.chat.uikit.components.chatsetting.model.ChatSettingCustomItem
import io.trtc.tuikit.chat.uikit.components.common.uicustom.EditorContext

internal class ChatSettingItemLayoutRenderer<C : EditorContext>(
    private val context: Context,
    private val parent: LinearLayout,
) {
    private data class RenderedItem<C : EditorContext>(
        val item: ChatSettingCustomItem<C>,
        val view: View,
    )

    private var renderedItems: List<RenderedItem<C>> = emptyList()
    private var itemAvailability: Map<String, Boolean> = emptyMap()
    private val sectionContainers = mutableListOf<LinearLayout>()
    private val spacers = mutableListOf<View>()
    private val dividers = mutableListOf<View>()

    fun setItems(
        itemContext: C,
        items: List<ChatSettingCustomItem<C>>,
    ) {
        renderedItems = items.map { item ->
            RenderedItem(item, item.viewFactory(itemContext))
        }
        itemAvailability = emptyMap()
        rebuild()
    }

    fun setItemAvailability(availability: Map<String, Boolean>) {
        val newAvailability = availability.toMap()
        if (itemAvailability == newAvailability) {
            return
        }
        itemAvailability = newAvailability
        rebuild()
    }

    fun rebuild() {
        parent.removeAllViews()
        renderedItems.forEach { renderedItem ->
            (renderedItem.view.parent as? ViewGroup)?.removeView(renderedItem.view)
        }
        sectionContainers.clear()
        spacers.clear()
        dividers.clear()

        val visibleItems = renderedItems.filter { renderedItem ->
            renderedItem.view.visibility != View.GONE &&
                itemAvailability[renderedItem.item.ID] != false
        }

        var index = 0
        while (index < visibleItems.size) {
            if (parent.childCount > 0) {
                parent.addView(createSpacer())
            }

            val current = visibleItems[index]
            val sectionID = current.item.sectionID
            if (sectionID == null) {
                parent.addView(current.view)
                index++
                continue
            }

            val sectionItems = mutableListOf<RenderedItem<C>>()
            while (index < visibleItems.size && visibleItems[index].item.sectionID == sectionID) {
                sectionItems.add(visibleItems[index])
                index++
            }
            parent.addView(createSection(sectionItems))
        }
    }

    fun applyThemeColors(colors: ColorTokens) {
        parent.setBackgroundColor(colors.bgColorInput)
        sectionContainers.forEach { it.setBackgroundColor(colors.bgColorOperate) }
        spacers.forEach { it.setBackgroundColor(colors.bgColorInput) }
        dividers.forEach { it.setBackgroundColor(colors.strokeColorPrimary) }
    }

    private fun createSection(items: List<RenderedItem<C>>): LinearLayout {
        val section = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_LOCALE
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            )
            setBackgroundColor(currentColors().bgColorOperate)
        }
        sectionContainers.add(section)
        items.forEachIndexed { index, renderedItem ->
            section.addView(renderedItem.view)
            if (index != items.lastIndex) {
                section.addView(createDivider())
            }
        }
        return section
    }

    private fun createSpacer(): View {
        return View(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp2px(10f, resources.displayMetrics).toInt(),
            )
            setBackgroundColor(currentColors().bgColorInput)
            spacers.add(this)
        }
    }

    private fun createDivider(): View {
        return View(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp2px(0.5f, resources.displayMetrics).toInt().coerceAtLeast(1),
            )
            setBackgroundColor(currentColors().strokeColorPrimary)
            dividers.add(this)
        }
    }

    private fun currentColors(): ColorTokens {
        return ThemeStore.shared(context).themeState.value.currentTheme.tokens.color
    }
}
