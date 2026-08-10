package io.trtc.tuikit.chat.uikit.components.contactlist.ui
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.TextView
import android.widget.Toast
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.lifecycle.ViewModelProvider
import com.google.android.material.appbar.AppBarLayout
import io.trtc.tuikit.chat.uikit.R
import io.trtc.tuikit.chat.uikit.components.common.displayName
import io.trtc.tuikit.chat.uikit.components.common.findViewModelStoreOwner
import io.trtc.tuikit.chat.uikit.components.contactlist.utils.matchesSearchQuery
import io.trtc.tuikit.chat.uikit.components.contactlist.viewmodel.BlacklistSubViewModel
import io.trtc.tuikit.chat.uikit.components.contactlist.viewmodel.BlacklistSubViewModelFactory
import io.trtc.tuikit.atomicx.theme.ThemeStore
import io.trtc.tuikit.atomicx.theme.tokens.ColorTokens
import io.trtc.tuikit.chat.uikit.components.widgets.azorderedlist.AZOrderedListItem
import io.trtc.tuikit.chat.uikit.components.widgets.azorderedlist.AtomicAZOrderedList
import io.trtc.tuikit.atomicxcore.api.CompletionHandler
import io.trtc.tuikit.atomicxcore.api.contact.ContactInfo
import io.trtc.tuikit.atomicxcore.api.contact.ContactStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

internal class BlacklistDialog(
    context: Context,
    private val contactStore: ContactStore,
    private val onContactClick: ((ContactInfo) -> Unit)? = null
) : ContactSubPageDialog(context) {

    private val viewModel: BlacklistSubViewModel by lazy {
        val owner = context.findViewModelStoreOwner()
            ?: error("BlacklistDialog requires a ViewModelStoreOwner host context.")
        val key = "${BlacklistSubViewModel::class.java.name}:${System.identityHashCode(this)}"
        ViewModelProvider(owner, BlacklistSubViewModelFactory(contactStore))
            .get(key, BlacklistSubViewModel::class.java)
    }
    private var scope: CoroutineScope? = null
    private lateinit var searchBarView: ContactListSearchBarView
    private lateinit var appBarLayout: AppBarLayout
    private lateinit var azOrderedList: AtomicAZOrderedList
    private lateinit var emptyTextView: TextView
    private var currentPopup: BlacklistPopupMenu? = null
    private var searchQuery: String = ""
    private var allBlacklistUsers: List<ContactInfo> = emptyList()
    private var highlightedAnchor: View? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setTitle(context.getString(R.string.contact_list_blacklist))

        val colors = getColors()

        val coordinatorLayout = CoordinatorLayout(context).apply {
            layoutDirection = View.LAYOUT_DIRECTION_LOCALE
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }

        appBarLayout = AppBarLayout(context).apply {
            layoutDirection = View.LAYOUT_DIRECTION_LOCALE
            fitsSystemWindows = false
            stateListAnimator = null
            setBackgroundColor(colors.bgColorOperate)
        }
        coordinatorLayout.addView(
            appBarLayout,
            CoordinatorLayout.LayoutParams(
                CoordinatorLayout.LayoutParams.MATCH_PARENT,
                CoordinatorLayout.LayoutParams.WRAP_CONTENT
            )
        )

        searchBarView = ContactListSearchBarView(context).apply {
            onQueryChange = { query ->
                searchQuery = query
                renderBlacklist()
            }
        }
        val searchBarParams = AppBarLayout.LayoutParams(
            AppBarLayout.LayoutParams.MATCH_PARENT,
            AppBarLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            scrollFlags = AppBarLayout.LayoutParams.SCROLL_FLAG_SCROLL or
                AppBarLayout.LayoutParams.SCROLL_FLAG_ENTER_ALWAYS or
                AppBarLayout.LayoutParams.SCROLL_FLAG_SNAP
        }
        appBarLayout.addView(searchBarView, searchBarParams)

        val listContainer = FrameLayout(context).apply {
            layoutDirection = View.LAYOUT_DIRECTION_LOCALE
            setOnTouchListener { _, _ ->
                searchBarView.hideKeyboard()
                false
            }
        }
        val listContainerParams = CoordinatorLayout.LayoutParams(
            CoordinatorLayout.LayoutParams.MATCH_PARENT,
            CoordinatorLayout.LayoutParams.MATCH_PARENT
        ).apply {
            behavior = AppBarLayout.ScrollingViewBehavior()
        }
        coordinatorLayout.addView(listContainer, listContainerParams)

        emptyTextView = TextView(context).apply {
            text = context.getString(R.string.contact_list_no_blacklist_users)
            setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 17f)
            setTextColor(colors.textColorSecondary)
            gravity = Gravity.CENTER
            visibility = View.GONE
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }
        listContainer.addView(emptyTextView)

        azOrderedList = AtomicAZOrderedList(context).apply {
            layoutDirection = View.LAYOUT_DIRECTION_LOCALE
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            onUserInteraction = {
                searchBarView.hideKeyboard()
            }
        }
        azOrderedList.setOnItemClickListener<ContactInfo> { item ->
            onContactClick?.invoke(item.extraData)
        }
        azOrderedList.setOnItemLongClickListener<ContactInfo> { item, anchorView ->
            showPopupMenu(item.extraData, anchorView)
        }
        listContainer.addView(azOrderedList)

        contentContainer.addView(coordinatorLayout)
    }

    override fun onStart() {
        super.onStart()
        val newScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        scope = newScope

        newScope.launch {
            viewModel.blacklistUsers.collectLatest { users ->
                allBlacklistUsers = users
                renderBlacklist()
            }
        }

        newScope.launch {
            ThemeStore.shared(context).themeState.collectLatest {
                val colors = it.currentTheme.tokens.color
                refreshNavBarColors(colors)
                appBarLayout.setBackgroundColor(colors.bgColorOperate)
                searchBarView.applyTheme()
                emptyTextView.setTextColor(colors.textColorSecondary)
            }
        }
    }

    override fun onStop() {
        super.onStop()
        currentPopup?.dismissImmediately()
        scope?.cancel()
        scope = null
    }

    private fun renderBlacklist() {
        val filteredUsers = allBlacklistUsers.filter { user ->
            user.matchesSearchQuery(searchQuery)
        }
        if (filteredUsers.isEmpty()) {
            emptyTextView.visibility = View.VISIBLE
            emptyTextView.text = context.getString(
                if (searchQuery.isBlank()) {
                    R.string.contact_list_no_blacklist_users
                } else {
                    R.string.contact_list_cannot_found_blacklist_user
                }
            )
            azOrderedList.visibility = View.GONE
            return
        }

        emptyTextView.visibility = View.GONE
        azOrderedList.visibility = View.VISIBLE
        azOrderedList.setDataSource(
            filteredUsers.map { user ->
                AZOrderedListItem(
                    key = user.userID,
                    label = user.displayName,
                    avatarUrl = user.avatarURL,
                    extraData = user
                )
            }
        )
    }

    private fun showPopupMenu(contactInfo: ContactInfo, anchorView: View) {
        searchBarView.hideKeyboard()
        setAnchorHighlighted(anchorView, true)
        val colors = getColors()
        val popupView = buildPopupMenuView(colors) {
            removeFromBlacklist(contactInfo)
        }
        popupView.measure(
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )
        val popupWidth = popupView.measuredWidth
        val popupHeight = popupView.measuredHeight

        val popupHostView = contentContainer
        val hostLocation = IntArray(2)
        popupHostView.getLocationInWindow(hostLocation)
        val anchorLocation = IntArray(2)
        anchorView.getLocationInWindow(anchorLocation)
        val anchorX = anchorLocation[0] - hostLocation[0]
        val anchorY = anchorLocation[1] - hostLocation[1]
        val anchorWidth = anchorView.width
        val anchorHeight = anchorView.height
        val hostWidth = popupHostView.width
        val hostHeight = popupHostView.height

        val margin = dp2px(context, 4f)
        val horizontalInset = dp2px(context, 16f)
        val aboveOverlap = dp2px(context, 8f)
        val belowOverlap = (anchorHeight * 3 / 5).coerceAtLeast(dp2px(context, 24f))
        val isRtl = anchorView.layoutDirection == View.LAYOUT_DIRECTION_RTL
        val preferredX = if (isRtl) {
            anchorX + horizontalInset
        } else {
            anchorX + anchorWidth - popupWidth - horizontalInset
        }
        val maxX = (hostWidth - popupWidth - margin).coerceAtLeast(margin)
        val xOffset = preferredX.coerceIn(margin, maxX)
        val belowY = anchorY + anchorHeight - belowOverlap
        val aboveY = anchorY - popupHeight + aboveOverlap
        val showBelow = belowY + popupHeight <= hostHeight - margin || aboveY < margin
        val yOffset = if (showBelow) {
            belowY.coerceAtMost(hostHeight - popupHeight - margin)
        } else {
            aboveY.coerceAtLeast(margin)
        }

        currentPopup?.dismissImmediately()
        currentPopup = BlacklistPopupMenu(
            anchorView = anchorView,
            popupHostView = popupHostView,
            popupWindowX = hostLocation[0],
            popupWindowY = hostLocation[1],
            popupWindowWidth = hostWidth,
            popupWindowHeight = hostHeight,
            menuView = popupView,
            popupX = xOffset,
            popupY = yOffset,
            showBelow = showBelow
        ).also {
            it.show()
        }
    }

    private fun setAnchorHighlighted(anchorView: View, highlighted: Boolean) {
        if (highlighted) {
            if (highlightedAnchor != null && highlightedAnchor !== anchorView) {
                highlightedAnchor?.foreground = null
            }
            anchorView.foreground = ColorDrawable(Color.argb(20, 0, 0, 0))
            highlightedAnchor = anchorView
        } else {
            anchorView.foreground = null
            if (highlightedAnchor === anchorView) {
                highlightedAnchor = null
            }
        }
    }

    private fun buildPopupMenuView(colors: ColorTokens, onRemoveClick: () -> Unit): View {
        val horizontalPadding = dp2px(context, 16f)
        val verticalPadding = dp2px(context, 12f)

        val menuItem = TextView(context).apply {
            text = context.getString(R.string.contact_list_blacklist_remove)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            setTextColor(colors.textColorError)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            layoutDirection = View.LAYOUT_DIRECTION_LOCALE
            textAlignment = View.TEXT_ALIGNMENT_VIEW_START
            textDirection = View.TEXT_DIRECTION_LOCALE
            gravity = Gravity.START or Gravity.CENTER_VERTICAL
            setPadding(horizontalPadding, verticalPadding, horizontalPadding, verticalPadding)
            background = createMenuItemRipple(colors)
            isClickable = true
            setOnClickListener {
                currentPopup?.dismiss {
                    onRemoveClick()
                } ?: onRemoveClick()
            }
        }
        menuItem.measure(
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )

        val menuWidth = menuItem.measuredWidth

        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_LOCALE
            background = GradientDrawable().apply {
                setColor(colors.bgColorOperate)
                cornerRadius = dp2px(context, 8f).toFloat()
            }
            elevation = dp2px(context, 8f).toFloat()
            isClickable = true
            isFocusable = true
            setOnClickListener { }
            addView(
                menuItem,
                LinearLayout.LayoutParams(
                    menuWidth,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            )
        }
    }

    private fun createMenuItemRipple(colors: ColorTokens): RippleDrawable {
        val rippleColor = Color.argb(
            30,
            Color.red(colors.textColorPrimary),
            Color.green(colors.textColorPrimary),
            Color.blue(colors.textColorPrimary)
        )
        val menuCornerRadius = dp2px(context, 8f).toFloat()
        val mask = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = menuCornerRadius
            setColor(Color.WHITE)
        }
        return RippleDrawable(ColorStateList.valueOf(rippleColor), null, mask)
    }

    private fun removeFromBlacklist(contactInfo: ContactInfo) {
        contactStore.removeFromBlacklist(contactInfo.userID, object : CompletionHandler {
            override fun onSuccess() {}

            override fun onFailure(code: Int, desc: String) {
                if (desc.isNotBlank()) {
                    Toast.makeText(context, desc, Toast.LENGTH_SHORT).show()
                }
            }
        })
    }

    private inner class BlacklistPopupMenu(
        private val anchorView: View,
        private val popupHostView: View,
        private val popupWindowX: Int,
        private val popupWindowY: Int,
        private val popupWindowWidth: Int,
        private val popupWindowHeight: Int,
        private val menuView: View,
        private val popupX: Int,
        private val popupY: Int,
        private val showBelow: Boolean
    ) {

        private val popupWindow: PopupWindow
        private var isDismissed = false
        private var isDismissAnimating = false

        init {
            val overlayView = FrameLayout(context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                setOnClickListener {
                    dismiss()
                }
                addView(
                    menuView,
                    FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    ).apply {
                        leftMargin = popupX
                        topMargin = popupY
                    }
                )
            }
            popupWindow = PopupWindow(
                overlayView,
                popupWindowWidth,
                popupWindowHeight,
                true
            ).apply {
                setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
                isOutsideTouchable = false
                isFocusable = true
                animationStyle = 0
                elevation = 0f
                setOnDismissListener {
                    if (currentPopup == this@BlacklistPopupMenu) {
                        currentPopup = null
                    }
                    setAnchorHighlighted(anchorView, false)
                }
            }
        }

        fun show() {
            popupWindow.showAtLocation(
                popupHostView,
                Gravity.NO_GRAVITY,
                popupWindowX,
                popupWindowY
            )
            startShowAnimation()
        }

        fun dismiss(afterDismiss: (() -> Unit)? = null) {
            if (isDismissed) {
                afterDismiss?.invoke()
                return
            }
            if (isDismissAnimating) {
                return
            }
            isDismissAnimating = true
            val dismissTranslation = if (showBelow) {
                -dp2px(context, 6f).toFloat()
            } else {
                dp2px(context, 6f).toFloat()
            }
            menuView.animate()
                .alpha(0f)
                .scaleX(0.92f)
                .scaleY(0.92f)
                .translationY(dismissTranslation)
                .setDuration(160L)
                .setListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationCancel(animation: Animator) {
                        finishDismiss(afterDismiss)
                    }

                    override fun onAnimationEnd(animation: Animator) {
                        finishDismiss(afterDismiss)
                    }
                })
                .start()
        }

        fun dismissImmediately() {
            finishDismiss(afterDismiss = null)
        }

        private fun startShowAnimation() {
            menuView.pivotX = if (popupHostView.layoutDirection == View.LAYOUT_DIRECTION_RTL) {
                dp2px(context, 16f).toFloat()
            } else {
                (menuView.measuredWidth - dp2px(context, 16f)).toFloat()
            }
            menuView.pivotY = if (showBelow) {
                0f
            } else {
                menuView.measuredHeight.toFloat()
            }
            val enterTranslation = if (showBelow) {
                -dp2px(context, 8f).toFloat()
            } else {
                dp2px(context, 8f).toFloat()
            }
            menuView.alpha = 0f
            menuView.scaleX = 0.92f
            menuView.scaleY = 0.92f
            menuView.translationY = enterTranslation
            menuView.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .translationY(0f)
                .setDuration(180L)
                .setListener(null)
                .start()
        }

        private fun finishDismiss(afterDismiss: (() -> Unit)?) {
            if (isDismissed) {
                afterDismiss?.invoke()
                return
            }
            isDismissed = true
            isDismissAnimating = false
            menuView.animate().setListener(null)
            popupWindow.dismiss()
            afterDismiss?.invoke()
        }
    }
}

fun dp2px(context: Context, dpValue: Float): Int {
    return TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, dpValue, context.resources.displayMetrics
    ).toInt()
}