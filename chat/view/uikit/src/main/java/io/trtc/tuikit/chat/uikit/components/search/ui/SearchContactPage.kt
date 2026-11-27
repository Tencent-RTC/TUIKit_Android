package io.trtc.tuikit.chat.uikit.components.search.ui
import android.content.Context
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import io.trtc.tuikit.chat.uikit.R
import io.trtc.tuikit.chat.uikit.components.search.viewmodel.SearchContactViewModel
import io.trtc.tuikit.atomicx.theme.ThemeStore
import io.trtc.tuikit.atomicxcore.api.search.FriendSearchInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class SearchContactPage(
    context: Context,
    private val viewModel: SearchContactViewModel
) : LinearLayout(context) {

    private val searchBar = SubSearchBarView(context)
    private val recyclerView = RecyclerView(context)
    private val loadingView: ProgressBar
    private val emptyText: TextView
    private val headerText: TextView
    private var viewScope: CoroutineScope? = null
    private var searchQuery = ""
    private val themeStore = ThemeStore.shared(context)

    var onContactClick: ((FriendSearchInfo) -> Unit)? = null
    var onQueryChange: ((String) -> Unit)? = null
    var onBack: (() -> Unit)? = null

    init {
        orientation = VERTICAL
        layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )

        addView(searchBar, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))

        headerText = TextView(context).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 24f)
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            text = context.getString(R.string.search_category_contact)
            val dp16 = dpToPx(16)
            setPadding(dp16, dpToPx(8), dp16, dpToPx(4))
            visibility = View.GONE
        }
        addView(headerText)

        loadingView = ProgressBar(context).apply {
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                topMargin = dpToPx(16)
            }
            visibility = View.GONE
        }
        addView(loadingView)

        emptyText = TextView(context).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            text = context.getString(R.string.search_cannot_found_contact)
            gravity = Gravity.CENTER
            val dp16 = dpToPx(16)
            setPadding(dp16, dp16, dp16, dp16)
            visibility = View.GONE
        }
        addView(emptyText)

        recyclerView.layoutManager = LinearLayoutManager(context)
        recyclerView.setOnTouchListener { _, _ ->
            searchBar.hideKeyboard()
            false
        }
        recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(rv: RecyclerView, dx: Int, dy: Int) {
                if (dx != 0 || dy != 0) {
                    searchBar.hideKeyboard()
                }
                val lm = rv.layoutManager as LinearLayoutManager
                val total = lm.itemCount
                val lastVisible = lm.findLastVisibleItemPosition()
                if (total > 0 && lastVisible >= total - 1) {
                    viewModel.searchMore()
                }
            }
        })
        addView(recyclerView, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))

        searchBar.onQueryChange = { query ->
            searchQuery = query
            onQueryChange?.invoke(query)
            if (query.isBlank()) {
                viewModel.updateSearchQuery(query)
                updateResults(emptyList())
            } else {
                viewModel.updateSearchQuery(query)
            }
        }
        searchBar.onBack = { onBack?.invoke() }
        searchBar.onCancel = { onBack?.invoke() }

        applyTheme()
    }

    fun start(keywords: String) {
        searchQuery = keywords
        searchBar.setQuery(keywords)
        if (viewScope != null) {
            viewModel.updateSearchQuery(keywords)
            searchBar.requestFocusAndShowKeyboard()
            return
        }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        viewScope = scope

        scope.launch {
            viewModel.contactSearchResults.collectLatest { results ->
                if (!viewModel.isSearching.value) {
                    updateResults(results)
                }
            }
        }
        scope.launch {
            viewModel.isSearching.collectLatest { searching ->
                loadingView.visibility = if (searching) View.VISIBLE else View.GONE
                if (searching) {
                    emptyText.visibility = View.GONE
                    headerText.visibility = View.GONE
                    recyclerView.visibility = View.GONE
                } else {
                    updateResults(viewModel.contactSearchResults.value)
                }
            }
        }
        scope.launch {
            themeStore.themeState.collectLatest {
                applyTheme()
                recyclerView.adapter?.notifyDataSetChanged()
            }
        }

        viewModel.updateSearchQuery(keywords)
        searchBar.requestFocusAndShowKeyboard()
    }

    private fun stopCollectingUiState() {
        viewScope?.cancel()
        viewScope = null
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        stopCollectingUiState()
    }

    private fun updateResults(results: List<FriendSearchInfo>) {
        if (results.isEmpty() && searchQuery.isNotBlank()) {
            emptyText.visibility = View.VISIBLE
            headerText.visibility = View.GONE
            recyclerView.visibility = View.GONE
        } else {
            emptyText.visibility = View.GONE
            headerText.visibility = if (results.isNotEmpty()) View.VISIBLE else View.GONE
            recyclerView.visibility = View.VISIBLE
            recyclerView.adapter = ContactListAdapter(context, results, searchQuery) {
                onContactClick?.invoke(it)
            }
        }
    }

    private fun applyTheme() {
        val colors = themeStore.themeState.value.currentTheme.tokens.color
        setBackgroundColor(colors.bgColorOperate)
        headerText.setTextColor(colors.textColorPrimary)
        emptyText.setTextColor(colors.textColorSecondary)
        searchBar.applyTheme()
    }

    private fun dpToPx(dp: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, dp.toFloat(), resources.displayMetrics
        ).toInt()
    }
}

private class ContactListAdapter(
    private val context: Context,
    private val contacts: List<FriendSearchInfo>,
    private val keywords: String,
    private val onClick: (FriendSearchInfo) -> Unit
) : RecyclerView.Adapter<ContactListAdapter.VH>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = VH(SearchResultItemView(context))
    override fun getItemCount() = contacts.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val colors = ThemeStore.shared(context).themeState.value.currentTheme.tokens.color
        holder.view.bindContact(contacts[position], keywords, colors) {
            onClick(contacts[position])
        }
    }

    class VH(val view: SearchResultItemView) : RecyclerView.ViewHolder(view)
}
