package io.trtc.tuikit.chat.uikit.pages

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.LinearLayout
import io.trtc.tuikit.chat.uikit.components.contactlist.config.ChatContactListConfig
import io.trtc.tuikit.chat.uikit.components.contactlist.config.ContactListConfigProtocol
import io.trtc.tuikit.chat.uikit.components.contactlist.ui.ContactListView
import io.trtc.tuikit.atomicx.theme.ThemeStore
import io.trtc.tuikit.atomicx.theme.tokens.ColorTokens
import io.trtc.tuikit.atomicxcore.api.contact.ContactInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class ContactsPageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    private val pageHeader: PageHeaderView
    private val contactListView: ContactListView

    private val themeStore = ThemeStore.shared(context)
    private var viewScope: CoroutineScope? = null

    init {
        orientation = VERTICAL
        layoutDirection = LAYOUT_DIRECTION_LOCALE

        pageHeader = PageHeaderView(context)
        addView(pageHeader, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))

        contactListView = ContactListView(context)
        addView(contactListView, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))

        applyColors(themeStore.themeState.value.currentTheme.tokens.color)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        viewScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
        viewScope?.launch {
            themeStore.themeState.collectLatest { state ->
                applyColors(state.currentTheme.tokens.color)
            }
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        viewScope?.cancel()
        viewScope = null
    }

    private fun applyColors(colors: ColorTokens) {
        setBackgroundColor(colors.bgColorOperate)
    }

    @JvmOverloads
    fun setup(
        config: ContactListConfigProtocol = ChatContactListConfig(),
        headerTitle: String? = null,
        headerRightAction: View? = null,
        onContactClick: ((ContactInfo) -> Unit)? = null,
        onGroupClick: ((ContactInfo) -> Unit)? = null
    ) {
        headerTitle?.let { pageHeader.setTitle(it) }
        headerRightAction?.let { pageHeader.setEditContent(it) }
        contactListView.setup(
            config = config,
            onContactClick = onContactClick,
            onGroupClick = onGroupClick
        )
    }
}
