package io.trtc.tuikit.chat.demo.main

import io.trtc.tuikit.chat.demo.common.BadgeDragPolicy
import io.trtc.tuikit.chat.demo.common.BaseActivity
import io.trtc.tuikit.chat.demo.common.AppConstants
import io.trtc.tuikit.chat.demo.customerservice.CustomerServiceManager
import io.trtc.tuikit.chat.demo.settings.SettingsPageView

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Shader
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.FragmentContainerView
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.tencent.mmkv.MMKV
import com.tencent.qcloud.tuikit.tuicallkit.view.component.recents.RecentCallsFragment
import io.trtc.tuikit.chat.uikit.components.contactlist.ui.ContactFlowLauncher
import io.trtc.tuikit.atomicx.theme.ThemeStore
import io.trtc.tuikit.atomicx.theme.tokens.ColorTokens
import io.trtc.tuikit.atomicxcore.api.CompletionHandler
import io.trtc.tuikit.atomicxcore.api.contact.ContactStore
import io.trtc.tuikit.atomicxcore.api.conversation.ConversationListStore
import io.trtc.tuikit.atomicxcore.api.group.GetGroupInfoCompletionHandler
import io.trtc.tuikit.atomicxcore.api.group.GroupEvent
import io.trtc.tuikit.atomicxcore.api.group.GroupInfo
import io.trtc.tuikit.atomicxcore.api.group.GroupStore
import io.trtc.tuikit.atomicxcore.api.login.LoginStore
import io.trtc.tuikit.chat.app.R
import io.trtc.tuikit.chat.demo.chat.ChatActivity
import io.trtc.tuikit.chat.demo.search.SearchActivity
import io.trtc.tuikit.chat.uikit.components.widgets.AvatarBadgeView
import io.trtc.tuikit.chat.uikit.pages.ContactsPageView
import io.trtc.tuikit.chat.uikit.pages.ConversationsPageView
import io.trtc.tuikit.chat.uikit.pages.PopupMenuHelper
import io.trtc.tuikit.chat.uikit.pages.PopupMenuItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlin.math.abs

private data class BottomTab(
    val tabId: Int,
    val root: LinearLayout,
    val icon: ImageView,
    val text: TextView,
    val iconResId: Int,
    val iconCutoutResId: Int = 0
)

private const val BADGE_CLEAR_DRAG_THRESHOLD_DP = 48
private const val TAB_ICON_SIZE_DP = 24

// Gradient handle positions of the selected tab icon, normalized to the icon canvas.
private const val TAB_ICON_GRADIENT_LIGHT_X = 0.66f
private const val TAB_ICON_GRADIENT_LIGHT_Y = -0.33f
private const val TAB_ICON_GRADIENT_DARK_X = -0.24f
private const val TAB_ICON_GRADIENT_DARK_Y = 0.6875f

class MainActivity : BaseActivity() {

    private lateinit var viewPager: ViewPager2
    private lateinit var bottomNav: LinearLayout
    private lateinit var bottomNavContainer: FrameLayout
    private lateinit var messageUnreadBadge: AvatarBadgeView
    private lateinit var contactsUnreadBadge: AvatarBadgeView
    private lateinit var mainContainer: LinearLayout
    private lateinit var allBottomTabs: List<BottomTab>
    private lateinit var bottomTabs: List<BottomTab>

    private var conversationsAddButton: ImageView? = null
    private var contactsAddButton: ImageView? = null
    private var selectedTabIndex = 0
    private var currentTabId = R.id.demo_tab_messages
    private val tabPageCache = mutableMapOf<Int, View>()
    private var isDraggingMessageBadge = false
    private var messageBadgeDragStartRawX = 0f
    private var messageBadgeDragStartRawY = 0f
    private var messageBadgeAnchorLayoutListener: View.OnLayoutChangeListener? = null
    private var trackedMessageBadgeAnchorView: View? = null
    private var contactsBadgeAnchorLayoutListener: View.OnLayoutChangeListener? = null
    private var trackedContactsBadgeAnchorView: View? = null

    private val themeStore by lazy { ThemeStore.shared(this) }
    private val conversationListStore by lazy { ConversationListStore.create() }
    private val contactStore by lazy { ContactStore.shared }
    private val groupStore by lazy { GroupStore.shared }
    private var mainScope: CoroutineScope? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (isFinishing) {
            return
        }

        setContentView(R.layout.demo_activity_main)

        mainContainer = findViewById(R.id.demo_mainContainer)
        viewPager = findViewById(R.id.demo_viewPager)
        bottomNavContainer = findViewById(R.id.demo_bottomNavContainer)
        bottomNav = findViewById(R.id.demo_bottomNav)
        messageUnreadBadge = findViewById(R.id.demo_messageUnreadBadge)
        contactsUnreadBadge = findViewById(R.id.demo_contactsUnreadBadge)
        allBottomTabs = listOf(
            BottomTab(
                tabId = R.id.demo_tab_messages,
                root = findViewById(R.id.demo_tab_messages),
                icon = findViewById(R.id.demo_tab_messages_icon),
                text = findViewById(R.id.demo_tab_messages_text),
                iconResId = R.drawable.demo_ic_tab_messages,
                iconCutoutResId = R.drawable.demo_ic_tab_messages_lines
            ),
            BottomTab(
                tabId = R.id.demo_tab_calls,
                root = findViewById(R.id.demo_tab_calls),
                icon = findViewById(R.id.demo_tab_calls_icon),
                text = findViewById(R.id.demo_tab_calls_text),
                iconResId = R.drawable.demo_ic_tab_calls
            ),
            BottomTab(
                tabId = R.id.demo_tab_contacts,
                root = findViewById(R.id.demo_tab_contacts),
                icon = findViewById(R.id.demo_tab_contacts_icon),
                text = findViewById(R.id.demo_tab_contacts_text),
                iconResId = R.drawable.demo_ic_tab_contacts
            ),
            BottomTab(
                tabId = R.id.demo_tab_me,
                root = findViewById(R.id.demo_tab_me),
                icon = findViewById(R.id.demo_tab_me_icon),
                text = findViewById(R.id.demo_tab_me_text),
                iconResId = R.drawable.demo_ic_tab_me
            )
        )
        val showCallsTab = MMKV.defaultMMKV().decodeBool(AppConstants.KEY_SHOW_CALLS_TAB, true)
        bottomTabs = filterVisibleTabs(showCallsTab)
        allBottomTabs.first { it.tabId == R.id.demo_tab_calls }.root.visibility =
            if (showCallsTab) View.VISIBLE else View.GONE

        ViewCompat.setOnApplyWindowInsetsListener(mainContainer) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.updatePadding(top = systemBars.top)
            bottomNav.updatePadding(bottom = systemBars.bottom)
            insets
        }

        setupViewPager()
        setupBottomNav()
        applyColors(themeStore.themeState.value.currentTheme.tokens.color)
        refreshUnreadCounts()
    }

    override fun onStart() {
        super.onStart()
        mainScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
        refreshUnreadCounts()

        mainScope?.launch {
            themeStore.themeState.collectLatest { state ->
                applyColors(state.currentTheme.tokens.color)
            }
        }
        mainScope?.launch {
            conversationListStore.state.totalUnreadCount.collectLatest { unreadCount ->
                updateTabBadge(R.id.demo_tab_messages, unreadCount.toInt())
            }
        }
        mainScope?.launch {
            combine(
                contactStore.state.friendApplicationUnreadCount,
                groupStore.state.unreadApplicationCount
            ) { friendApplicationUnreadCount, groupApplicationUnreadCount ->
                friendApplicationUnreadCount + groupApplicationUnreadCount
            }.collectLatest { unreadCount ->
                updateTabBadge(R.id.demo_tab_contacts, unreadCount)
            }
        }
        mainScope?.launch {
            groupStore.groupEventFlow.collectLatest { event ->
                when (event) {
                    is GroupEvent.OnKickedFromGroup -> showGroupEventToast(
                        groupID = event.groupID,
                        messageResId = R.string.demo_group_event_kicked
                    )
                    is GroupEvent.OnGroupDismissed -> showGroupEventToast(
                        groupID = event.groupID,
                        messageResId = R.string.demo_group_event_dismissed
                    )
                    else -> Unit
                }
            }
        }
    }

    override fun onStop() {
        super.onStop()
        mainScope?.cancel()
        mainScope = null
    }

    private fun applyColors(colors: ColorTokens) {
        updateStatusBarAreaColor(colors)
        bottomNav.setBackgroundColor(colors.bgColorBottomBar)

        conversationsAddButton?.setColorFilter(colors.textColorPrimary)
        contactsAddButton?.setColorFilter(colors.textColorPrimary)

        updateSelectedTabColors(colors)
        if (::messageUnreadBadge.isInitialized) {
            messageUnreadBadge.updateColors()
        }
        if (::contactsUnreadBadge.isInitialized) {
            contactsUnreadBadge.updateColors()
        }
    }

    private fun setupViewPager() {
        viewPager.isUserInputEnabled = false
        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                selectTab(position, updatePager = false)
            }
        })
        rebuildPages()
    }

    private fun rebuildPages() {
        val pages = bottomTabs.map { getOrCreatePage(it.tabId) }
        viewPager.adapter = TabPagerAdapter(pages)
        viewPager.offscreenPageLimit = pages.size
        val targetIndex = bottomTabs.indexOfFirst { it.tabId == currentTabId }.coerceAtLeast(0)
        viewPager.setCurrentItem(targetIndex, false)
        selectTab(targetIndex, updatePager = false)
    }

    private fun getOrCreatePage(tabId: Int): View {
        return tabPageCache.getOrPut(tabId) {
            when (tabId) {
                R.id.demo_tab_calls -> createCallsPage()
                R.id.demo_tab_contacts -> createContactsPage()
                R.id.demo_tab_me -> createMePage()
                else -> createConversationsPage()
            }
        }
    }

    private fun filterVisibleTabs(showCalls: Boolean): List<BottomTab> {
        return allBottomTabs.filter { it.tabId != R.id.demo_tab_calls || showCalls }
    }

    fun setCallsTabVisible(show: Boolean) {
        val isVisible = bottomTabs.any { it.tabId == R.id.demo_tab_calls }
        if (show == isVisible) {
            return
        }
        allBottomTabs.first { it.tabId == R.id.demo_tab_calls }.root.visibility =
            if (show) View.VISIBLE else View.GONE
        bottomTabs = filterVisibleTabs(show)
        bindTabClicks()
        rebuildPages()
    }

    private fun setupBottomNav() {
        bindTabClicks()
        selectTab(bottomTabs.indexOfFirst { it.tabId == currentTabId }.coerceAtLeast(0), updatePager = false)
        setupMessageBadgeDrag()
        setupContactsBadgeLayout()
    }

    private fun bindTabClicks() {
        bottomTabs.forEachIndexed { index, tab ->
            tab.root.setOnClickListener {
                selectTab(index, updatePager = true)
            }
        }
    }

    private fun selectTab(index: Int, updatePager: Boolean) {
        if (index !in bottomTabs.indices) {
            return
        }
        selectedTabIndex = index
        currentTabId = bottomTabs[index].tabId
        if (updatePager && viewPager.currentItem != index) {
            viewPager.setCurrentItem(index, false)
        }
        val colors = themeStore.themeState.value.currentTheme.tokens.color
        updateSelectedTabColors(colors)
        updateStatusBarAreaColor(colors)
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars =
            appearanceLightStatusBarsOverride() ?: isColorLight(colors.bgColorOperate)
    }

    override fun appearanceLightStatusBarsOverride(): Boolean? {
        if (::bottomTabs.isInitialized &&
            bottomTabs.getOrNull(selectedTabIndex)?.tabId == R.id.demo_tab_calls
        ) {
            return true
        }
        return null
    }

    private fun updateStatusBarAreaColor(colors: ColorTokens) {
        if (!::bottomTabs.isInitialized) {
            return
        }
        val isCallsTab = bottomTabs.getOrNull(selectedTabIndex)?.tabId == R.id.demo_tab_calls
        if (isCallsTab) {
            mainContainer.setBackgroundColor(resolveCallsPageTopColor())
        } else {
            mainContainer.setBackgroundColor(colors.bgColorOperate)
        }
    }

    private fun resolveCallsPageTopColor(): Int {
        val typedValue = TypedValue()
        if (theme.resolveAttribute(com.tencent.qcloud.tuicore.R.attr.core_header_start_color, typedValue, true)) {
            if (typedValue.resourceId != 0) {
                return ContextCompat.getColor(this, typedValue.resourceId)
            }
            if (typedValue.type in TypedValue.TYPE_FIRST_COLOR_INT..TypedValue.TYPE_LAST_COLOR_INT) {
                return typedValue.data
            }
        }
        return ContextCompat.getColor(this, com.tencent.qcloud.tuikit.tuicallkit.R.color.callkit_color_white)
    }

    private fun updateSelectedTabColors(colors: ColorTokens) {
        if (!::bottomTabs.isInitialized) {
            return
        }
        bottomTabs.forEachIndexed { index, tab ->
            val selected = index == selectedTabIndex
            tab.icon.setImageDrawable(renderTabIcon(tab, colors, selected))
            tab.text.setTextColor(
                if (selected) {
                    colors.textColorLink
                } else {
                    colors.textColorTertiary
                }
            )
        }
    }

    private fun renderTabIcon(tab: BottomTab, colors: ColorTokens, selected: Boolean): Drawable {
        val sizePx = TAB_ICON_SIZE_DP.dpToPx()
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        ContextCompat.getDrawable(this, tab.iconResId)?.let { base ->
            base.setBounds(0, 0, sizePx, sizePx)
            base.draw(canvas)
        }
        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
            if (selected) {
                shader = LinearGradient(
                    sizePx * TAB_ICON_GRADIENT_LIGHT_X,
                    sizePx * TAB_ICON_GRADIENT_LIGHT_Y,
                    sizePx * TAB_ICON_GRADIENT_DARK_X,
                    sizePx * TAB_ICON_GRADIENT_DARK_Y,
                    colors.bgColorBubbleOwn,
                    colors.textColorLink,
                    Shader.TileMode.CLAMP
                )
            } else {
                color = colors.textColorTertiary
            }
        }
        canvas.drawRect(0f, 0f, sizePx.toFloat(), sizePx.toFloat(), fillPaint)
        if (tab.iconCutoutResId != 0) {
            ContextCompat.getDrawable(this, tab.iconCutoutResId)?.let { cutout ->
                val tinted = DrawableCompat.wrap(cutout)
                tinted.setBounds(0, 0, sizePx, sizePx)
                DrawableCompat.setTint(tinted, colors.bgColorBottomBar)
                DrawableCompat.setTintMode(tinted, PorterDuff.Mode.SRC_IN)
                tinted.draw(canvas)
            }
        }
        return BitmapDrawable(resources, bitmap)
    }

    private fun setupMessageBadgeDrag() {
        val badgeElevationPx = 24.dpToPx().toFloat()
        messageUnreadBadge.elevation = badgeElevationPx
        messageUnreadBadge.translationZ = badgeElevationPx
        messageUnreadBadge.isClickable = true
        messageUnreadBadge.isFocusable = true
        bottomNavContainer.clipChildren = false
        bottomNavContainer.clipToPadding = false
        mainContainer.clipChildren = false
        mainContainer.clipToPadding = false
        bottomNavContainer.bringChildToFront(messageUnreadBadge)
        bottomNavContainer.bringChildToFront(contactsUnreadBadge)
        messageUnreadBadge.setOnTouchListener { badgeView, event ->
            handleMessageBadgeTouch(badgeView, event)
        }
        bottomNav.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            if (messageUnreadBadge.visibility == View.VISIBLE && !isDraggingMessageBadge) {
                positionMessageUnreadBadge()
            }
            if (contactsUnreadBadge.visibility == View.VISIBLE) {
                positionContactsUnreadBadge()
            }
        }
    }

    private fun setupContactsBadgeLayout() {
        contactsUnreadBadge.elevation = 24.dpToPx().toFloat()
        contactsUnreadBadge.translationZ = 24.dpToPx().toFloat()
    }

    private fun handleMessageBadgeTouch(badgeView: View, event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (badgeView.visibility != View.VISIBLE) {
                    return false
                }
                isDraggingMessageBadge = true
                messageBadgeDragStartRawX = event.rawX
                messageBadgeDragStartRawY = event.rawY
                badgeView.parent?.requestDisallowInterceptTouchEvent(true)
                badgeView.animate().cancel()
                badgeView.animate().scaleX(1.08f).scaleY(1.08f).setDuration(120L).start()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (!isDraggingMessageBadge) {
                    return false
                }
                updateDraggingMessageBadge(badgeView, event.rawX, event.rawY)
                return true
            }
            MotionEvent.ACTION_UP -> {
                if (!isDraggingMessageBadge) {
                    return false
                }
                val shouldClear = BadgeDragPolicy.shouldClearUnread(
                    messageBadgeDragStartRawY,
                    event.rawY,
                    BADGE_CLEAR_DRAG_THRESHOLD_DP.dpToPx()
                )
                isDraggingMessageBadge = false
                badgeView.parent?.requestDisallowInterceptTouchEvent(false)
                if (shouldClear) {
                    clearAllUnreadByBadgeDrag(badgeView)
                } else {
                    resetMessageBadgeDrag(badgeView)
                }
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                if (!isDraggingMessageBadge) {
                    return false
                }
                isDraggingMessageBadge = false
                badgeView.parent?.requestDisallowInterceptTouchEvent(false)
                resetMessageBadgeDrag(badgeView)
                return true
            }
        }
        return false
    }

    private fun updateDraggingMessageBadge(badgeView: View, rawX: Float, rawY: Float) {
        val thresholdPx = BADGE_CLEAR_DRAG_THRESHOLD_DP.dpToPx()
        val dragOffset = BadgeDragPolicy.badgeDragOffset(
            messageBadgeDragStartRawX,
            messageBadgeDragStartRawY,
            rawX,
            rawY
        )
        val progress = (abs(dragOffset.y).toFloat() / thresholdPx).coerceIn(0f, 1f)
        val scale = 1f + progress * 0.2f
        badgeView.translationX = dragOffset.x.toFloat()
        badgeView.translationY = dragOffset.y.toFloat()
        badgeView.scaleX = scale
        badgeView.scaleY = scale
    }

    private fun resetMessageBadgeDrag(badgeView: View) {
        badgeView.animate().cancel()
        badgeView.animate()
            .translationX(0f)
            .translationY(0f)
            .scaleX(1f)
            .scaleY(1f)
            .alpha(1f)
            .setDuration(180L)
            .start()
    }

    private fun clearAllUnreadByBadgeDrag(badgeView: View) {
        badgeView.animate().cancel()
        badgeView.animate()
            .translationX(0f)
            .translationY(-BADGE_CLEAR_DRAG_THRESHOLD_DP.dpToPx().toFloat() * 1.5f)
            .scaleX(0.6f)
            .scaleY(0.6f)
            .alpha(0f)
            .setDuration(180L)
            .withEndAction {
                badgeView.visibility = View.INVISIBLE
                badgeView.translationX = 0f
                badgeView.translationY = 0f
                badgeView.scaleX = 1f
                badgeView.scaleY = 1f
                badgeView.alpha = 1f
            }
            .start()
        runCatching {
            conversationListStore.clearConversationUnreadCount("")
        }.onSuccess {
            Toast.makeText(this, R.string.demo_clear_all_unread_success, Toast.LENGTH_SHORT).show()
        }.onFailure {
            resetMessageBadgeDrag(badgeView)
        }
    }

    private fun refreshUnreadCounts() {
        groupStore.loadApplications(object : CompletionHandler {
            override fun onSuccess() {
            }

            override fun onFailure(code: Int, desc: String) {
            }
        })
        contactStore.loadFriendApplications(object : CompletionHandler {
            override fun onSuccess() {
            }

            override fun onFailure(code: Int, desc: String) {
            }
        })
    }

    private fun showGroupEventToast(groupID: String, messageResId: Int) {
        val fallbackName = groupID.ifEmpty { getString(R.string.demo_group_event_unknown_group) }
        groupStore.getGroupInfo(
            groupID,
            object : GetGroupInfoCompletionHandler {
                override fun onSuccess(groupInfo: GroupInfo) {
                    val groupName = groupInfo.groupName?.takeIf { it.isNotEmpty() } ?: fallbackName
                    Toast.makeText(this@MainActivity, getString(messageResId, groupName), Toast.LENGTH_LONG).show()
                }

                override fun onFailure(code: Int, desc: String) {
                    Toast.makeText(this@MainActivity, getString(messageResId, fallbackName), Toast.LENGTH_LONG).show()
                }
            }
        )
    }

    private fun updateTabBadge(tabId: Int, unreadCount: Int) {
        if (tabId == R.id.demo_tab_messages) {
            updateMessageTabBadge(unreadCount)
            return
        }
        if (tabId == R.id.demo_tab_contacts) {
            updateContactsTabBadge(unreadCount)
        }
    }

    private fun updateMessageTabBadge(unreadCount: Int) {
        if (unreadCount <= 0) {
            if (!isDraggingMessageBadge) {
                messageUnreadBadge.visibility = View.INVISIBLE
            }
            return
        }
        if (isDraggingMessageBadge) {
            return
        }
        val text = if (unreadCount > 99) "99+" else unreadCount.toString()
        messageUnreadBadge.setType(AvatarBadgeView.BadgeType.Text)
        messageUnreadBadge.setText(text)
        messageUnreadBadge.updateColors()
        messageUnreadBadge.alpha = 1f
        messageUnreadBadge.scaleX = 1f
        messageUnreadBadge.scaleY = 1f
        messageUnreadBadge.translationX = 0f
        messageUnreadBadge.translationY = 0f
        showMessageBadgeWhenReady()
    }

    private fun updateContactsTabBadge(unreadCount: Int) {
        if (unreadCount <= 0) {
            contactsUnreadBadge.visibility = View.INVISIBLE
            return
        }
        val text = if (unreadCount > 99) "99+" else unreadCount.toString()
        contactsUnreadBadge.setType(AvatarBadgeView.BadgeType.Text)
        contactsUnreadBadge.setText(text)
        contactsUnreadBadge.updateColors()
        showContactsBadgeWhenReady()
    }

    private fun showMessageBadgeWhenReady() {
        val anchorView = findTabIcon(R.id.demo_tab_messages)
        if (anchorView == null || anchorView.width == 0) {
            bottomNav.post { showMessageBadgeWhenReady() }
            return
        }
        attachMessageBadgeAnchorLayoutListener(anchorView)
        val unspec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        messageUnreadBadge.measure(unspec, unspec)
        applyOverlayBadgeLayoutParams(messageUnreadBadge, anchorView)
        if (messageUnreadBadge.visibility != View.VISIBLE) {
            messageUnreadBadge.visibility = View.VISIBLE
        }
    }

    private fun showContactsBadgeWhenReady() {
        val anchorView = findTabIcon(R.id.demo_tab_contacts)
        if (anchorView == null || anchorView.width == 0) {
            bottomNav.post { showContactsBadgeWhenReady() }
            return
        }
        attachContactsBadgeAnchorLayoutListener(anchorView)
        val unspec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        contactsUnreadBadge.measure(unspec, unspec)
        applyOverlayBadgeLayoutParams(contactsUnreadBadge, anchorView)
        if (contactsUnreadBadge.visibility != View.VISIBLE) {
            contactsUnreadBadge.visibility = View.VISIBLE
        }
    }

    private fun applyOverlayBadgeLayoutParams(badgeView: AvatarBadgeView, anchorView: View) {
        val anchorLoc = IntArray(2).also { anchorView.getLocationInWindow(it) }
        val containerLoc = IntArray(2).also { bottomNavContainer.getLocationInWindow(it) }
        val anchorLeftInContainer = anchorLoc[0] - containerLoc[0]
        val anchorTopInContainer = anchorLoc[1] - containerLoc[1]
        val isRtl = bottomNav.layoutDirection == View.LAYOUT_DIRECTION_RTL
        val badgeW = badgeView.width.takeIf { it > 0 }
            ?: badgeView.measuredWidth.takeIf { it > 0 }
            ?: return
        val badgeH = badgeView.height.takeIf { it > 0 }
            ?: badgeView.measuredHeight.takeIf { it > 0 }
            ?: return
        val badgePosition = BadgeDragPolicy.badgeTopEndPosition(
            anchorLeft = anchorLeftInContainer,
            anchorTop = anchorTopInContainer,
            anchorWidth = anchorView.width,
            badgeWidth = badgeW,
            badgeHeight = badgeH,
            horizontalOffsetPx = 0,
            verticalOffsetPx = 4.dpToPx(),
            isRtl = isRtl
        )
        val existing = badgeView.layoutParams as? FrameLayout.LayoutParams
        if (existing != null &&
            existing.leftMargin == badgePosition.left &&
            existing.topMargin == badgePosition.top &&
            existing.rightMargin == 0 &&
            existing.gravity == (Gravity.TOP or Gravity.LEFT) &&
            existing.width == FrameLayout.LayoutParams.WRAP_CONTENT &&
            existing.height == FrameLayout.LayoutParams.WRAP_CONTENT
        ) {
            return
        }
        val params = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.TOP or Gravity.LEFT
            leftMargin = badgePosition.left
            topMargin = badgePosition.top
            rightMargin = 0
            bottomMargin = 0
        }
        badgeView.layoutParams = params
    }

    private fun attachMessageBadgeAnchorLayoutListener(anchorView: View) {
        if (trackedMessageBadgeAnchorView === anchorView) return
        trackedMessageBadgeAnchorView?.let { prev ->
            messageBadgeAnchorLayoutListener?.let { prev.removeOnLayoutChangeListener(it) }
        }
        val listener = View.OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            if (messageUnreadBadge.visibility == View.VISIBLE && !isDraggingMessageBadge) {
                positionMessageUnreadBadge()
            }
        }
        anchorView.addOnLayoutChangeListener(listener)
        trackedMessageBadgeAnchorView = anchorView
        messageBadgeAnchorLayoutListener = listener
    }

    private fun attachContactsBadgeAnchorLayoutListener(anchorView: View) {
        if (trackedContactsBadgeAnchorView === anchorView) return
        trackedContactsBadgeAnchorView?.let { prev ->
            contactsBadgeAnchorLayoutListener?.let { prev.removeOnLayoutChangeListener(it) }
        }
        val listener = View.OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            if (contactsUnreadBadge.visibility == View.VISIBLE) {
                positionContactsUnreadBadge()
            }
        }
        anchorView.addOnLayoutChangeListener(listener)
        trackedContactsBadgeAnchorView = anchorView
        contactsBadgeAnchorLayoutListener = listener
    }

    private fun positionMessageUnreadBadge() {
        if (messageUnreadBadge.visibility != View.VISIBLE) return
        val anchorView = findTabIcon(R.id.demo_tab_messages) ?: return
        if (anchorView.width == 0) return
        attachMessageBadgeAnchorLayoutListener(anchorView)
        applyOverlayBadgeLayoutParams(messageUnreadBadge, anchorView)
    }

    private fun positionContactsUnreadBadge() {
        if (contactsUnreadBadge.visibility != View.VISIBLE) return
        val anchorView = findTabIcon(R.id.demo_tab_contacts) ?: return
        if (anchorView.width == 0) return
        attachContactsBadgeAnchorLayoutListener(anchorView)
        applyOverlayBadgeLayoutParams(contactsUnreadBadge, anchorView)
    }

    private fun findTabIcon(tabId: Int): ImageView? {
        return bottomTabs.firstOrNull { it.tabId == tabId }?.icon
    }

    private fun createConversationsPage(): View {
        val page = ConversationsPageView(this)
        val addButton = ImageView(this).apply {
            setImageResource(io.trtc.tuikit.chat.uikit.R.drawable.uikit_ic_add_circle)
            layoutParams = ViewGroup.LayoutParams(24.dpToPx(), 24.dpToPx())
        }
        conversationsAddButton = addButton
        addButton.setOnClickListener { anchor ->
            PopupMenuHelper(this).show(
                anchor,
                listOf(
                    PopupMenuItem(
                        title = getString(R.string.demo_start_chat),
                        onClick = {
                            ContactFlowLauncher.showStartSingleChatPage(this) { conversationId ->
                                ChatActivity.start(this, conversationId)
                            }
                        },
                        iconResId = io.trtc.tuikit.chat.uikit.R.drawable.uikit_ic_user_add
                    ),
                    PopupMenuItem(
                        title = getString(R.string.demo_create_group),
                        onClick = {
                            ContactFlowLauncher.showCreateGroupChatPage(this) { conversationId ->
                                ChatActivity.start(this, conversationId)
                            }
                        },
                        iconResId = io.trtc.tuikit.chat.uikit.R.drawable.uikit_ic_chat_add
                    )
                )
            )
        }
        page.setup(
            headerTitle = getString(R.string.demo_page_conversations_title),
            headerRightAction = addButton,
            onConversationClick = { conversationInfo ->
                val conversationID = conversationInfo.conversationID
                if (conversationID == AppConstants.CUSTOMER_SERVICE_CONVERSATION_ID) {
                    CustomerServiceManager.openCustomerServiceChat(this)
                } else {
                    ChatActivity.start(this, conversationID)
                }
            },
            onSearchClick = {
                SearchActivity.Companion.start(this)
            }
        )
        return page
    }

    private fun createContactsPage(): View {
        val page = ContactsPageView(this)
        val addButton = ImageView(this).apply {
            setImageResource(io.trtc.tuikit.chat.uikit.R.drawable.uikit_ic_add_circle)
            layoutParams = ViewGroup.LayoutParams(24.dpToPx(), 24.dpToPx())
        }
        contactsAddButton = addButton
        addButton.setOnClickListener { anchor ->
            PopupMenuHelper(this).show(
                anchor,
                listOf(
                    PopupMenuItem(
                        title = getString(R.string.demo_add_friend),
                        onClick = { ContactFlowLauncher.showAddFriendPage(this) },
                        iconResId = io.trtc.tuikit.chat.uikit.R.drawable.uikit_ic_user_add
                    ),
                    PopupMenuItem(
                        title = getString(R.string.demo_add_group),
                        onClick = { ContactFlowLauncher.showAddGroupPage(this) },
                        iconResId = io.trtc.tuikit.chat.uikit.R.drawable.uikit_ic_chat_add
                    )
                )
            )
        }
        page.setup(
            headerTitle = getString(R.string.demo_page_contacts_title),
            headerRightAction = addButton,
            onContactClick = { contactInfo ->
                ChatActivity.start(this, "c2c_${contactInfo.userID}")
            },
            onGroupClick = { contactInfo ->
                ChatActivity.start(this, "group_${contactInfo.userID}")
            }
        )
        return page
    }

    private fun createCallsPage(): View {
        val container = FragmentContainerView(this).apply {
            id = R.id.demo_calls_fragment_container
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
        // The container lives inside a ViewPager2 page, so it is not part of the view
        // hierarchy when onCreate/onStart run. Fragment transactions targeting it must
        // wait until it is attached to the window, otherwise FragmentManager crashes
        // with "No view found for id".
        container.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(view: View) {
                container.removeOnAttachStateChangeListener(this)
                val fragmentManager = supportFragmentManager
                val existing = fragmentManager.findFragmentById(container.id)
                if (existing != null && existing.view?.parent === container) {
                    return
                }
                // After recreation, FragmentManager restores the old fragment but its view
                // is never attached to this new container, so replace it with a fresh one.
                fragmentManager.beginTransaction().apply {
                    existing?.let { remove(it) }
                    add(container.id, RecentCallsFragment())
                }.commit()
            }

            override fun onViewDetachedFromWindow(view: View) {
            }
        })
        return container
    }

    private fun createMePage(): View {
        val page = SettingsPageView(this)
        page.setHeaderTitle(getString(R.string.demo_page_me_title))
        return page
    }

    private fun Int.dpToPx(): Int = (this * resources.displayMetrics.density).toInt()
}

class TabPagerAdapter(
    private val pages: List<View>
) : RecyclerView.Adapter<TabPagerAdapter.PageViewHolder>() {

    class PageViewHolder(val pageView: View) : RecyclerView.ViewHolder(pageView)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PageViewHolder {
        val view = pages[viewType]
        view.layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        return PageViewHolder(view)
    }

    override fun onBindViewHolder(holder: PageViewHolder, position: Int) {
    }

    override fun getItemCount(): Int = pages.size

    override fun getItemViewType(position: Int): Int = position
}
