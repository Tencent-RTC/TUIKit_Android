package io.trtc.tuikit.chat.uikit.components.contactlist.ui
import android.content.Context
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.lifecycle.ViewModelProvider
import com.google.android.material.appbar.AppBarLayout
import io.trtc.tuikit.chat.uikit.R
import io.trtc.tuikit.chat.uikit.components.common.displayName
import io.trtc.tuikit.chat.uikit.components.common.findViewModelStoreOwner
import io.trtc.tuikit.chat.uikit.components.contactlist.utils.matchesSearchQuery
import io.trtc.tuikit.chat.uikit.components.contactlist.viewmodel.MyGroupSubViewModel
import io.trtc.tuikit.chat.uikit.components.contactlist.viewmodel.MyGroupSubViewModelFactory
import io.trtc.tuikit.atomicx.theme.ThemeStore
import io.trtc.tuikit.chat.uikit.components.widgets.azorderedlist.AZOrderedListItem
import io.trtc.tuikit.chat.uikit.components.widgets.azorderedlist.AtomicAZOrderedList
import io.trtc.tuikit.atomicxcore.api.contact.ContactInfo
import io.trtc.tuikit.atomicxcore.api.group.GroupStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

internal class MyGroupDialog(
    context: Context,
    private val groupStore: GroupStore,
    private val onGroupClick: ((ContactInfo) -> Unit)? = null
) : ContactSubPageDialog(context) {

    private val viewModel: MyGroupSubViewModel by lazy {
        val owner = context.findViewModelStoreOwner()
            ?: error("MyGroupDialog requires a ViewModelStoreOwner host context.")
        val key = "${MyGroupSubViewModel::class.java.name}:${System.identityHashCode(this)}"
        ViewModelProvider(owner, MyGroupSubViewModelFactory(groupStore))
            .get(key, MyGroupSubViewModel::class.java)
    }
    private var scope: CoroutineScope? = null
    private lateinit var searchBarView: ContactListSearchBarView
    private lateinit var appBarLayout: AppBarLayout
    private lateinit var azOrderedList: AtomicAZOrderedList
    private lateinit var emptyTextView: TextView
    private var searchQuery: String = ""
    private var allGroups: List<ContactInfo> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setTitle(context.getString(R.string.contact_list_my_group))

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
                renderGroupList()
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
            text = context.getString(R.string.contact_list_no_group)
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
            onGroupClick?.invoke(item.extraData)
        }
        listContainer.addView(azOrderedList)

        contentContainer.addView(coordinatorLayout)
    }

    override fun onStart() {
        super.onStart()
        val newScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        scope = newScope

        newScope.launch {
            viewModel.groups.collectLatest { groups ->
                allGroups = groups
                renderGroupList()
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
        scope?.cancel()
        scope = null
    }

    private fun renderGroupList() {
        val filteredGroups = allGroups.filter { group ->
            group.matchesSearchQuery(searchQuery)
        }
        if (filteredGroups.isEmpty()) {
            emptyTextView.visibility = View.VISIBLE
            emptyTextView.text = context.getString(
                if (searchQuery.isBlank()) {
                    R.string.contact_list_no_group
                } else {
                    R.string.contact_list_cannot_found_group
                }
            )
            azOrderedList.visibility = View.GONE
            return
        }

        emptyTextView.visibility = View.GONE
        azOrderedList.visibility = View.VISIBLE
        azOrderedList.setDataSource(
            filteredGroups.map { groupInfo ->
                AZOrderedListItem(
                    key = groupInfo.userID,
                    label = groupInfo.displayName,
                    avatarUrl = groupInfo.avatarURL,
                    extraData = groupInfo
                )
            }
        )
    }
}
