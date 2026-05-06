package com.trtc.uikit.livekit.voiceroom.interaction.common

import android.annotation.SuppressLint
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.widget.NestedScrollView
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.tabs.TabLayout
import com.trtc.uikit.livekit.R
import com.trtc.uikit.livekit.common.ErrorLocalized
import com.trtc.uikit.livekit.voiceroom.interaction.battle.BattleInviteAdapter
import com.trtc.uikit.livekit.voiceroom.interaction.cohost.CoHostInviteAdapter
import com.trtc.uikit.livekit.voiceroom.interaction.cohost.InteractionManagerView
import com.trtc.uikit.livekit.voiceroom.store.ViewStore
import io.trtc.tuikit.atomicx.widget.basicwidget.popover.AtomicPopover
import io.trtc.tuikit.atomicxcore.api.CompletionHandler
import io.trtc.tuikit.atomicxcore.api.live.CoHostStatus
import io.trtc.tuikit.atomicxcore.api.live.CoHostStore
import io.trtc.tuikit.atomicxcore.api.live.LiveInfo
import io.trtc.tuikit.atomicxcore.api.live.LiveListStore
import io.trtc.tuikit.atomicxcore.api.live.SeatUserInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

@SuppressLint("ViewConstructor")
class InteractionInvitePanel(context: Context, private val viewStore: ViewStore? = null) : AtomicPopover(context) ,
    DefaultLifecycleObserver {
    private val liveID: String = LiveListStore.shared().liveState.currentLive.value.liveID
    private lateinit var tabLayout: TabLayout
    private lateinit var recyclerBattleRecommendView: RecyclerView
    private lateinit var coHostRecyclerRecommendView: RecyclerView
    private lateinit var swipeRefreshLayout: SwipeRefreshLayout
    private val coHostStore: CoHostStore = CoHostStore.create(liveID)
    private val liveListStore: LiveListStore = LiveListStore.shared()
    private lateinit var nestedScrollView: NestedScrollView
    private val jobs = mutableListOf<Job>()
    private lateinit var coHostInviteAdapter: CoHostInviteAdapter
    private lateinit var battleRecommendedAdapter: BattleInviteAdapter
    private lateinit var rootView: View
    private lateinit var inConnectionOrPKView: InteractionManagerView
    private lateinit var layoutRecommendRootView: LinearLayout
    private var recommendedCursor = ""
    private var dialogScope: CoroutineScope? = null

    companion object {
        private const val FETCH_LIST_COUNT = 20
    }

    init {
        initView()
    }

    private fun initView() {
        rootView = LayoutInflater.from(context)
            .inflate(R.layout.livekit_voiceroom_interaction_invite_panel, null)
        bindViewId(rootView)
        initTabView()
        initRefresh()
        initBattleRecommendList()
        initCoHostRecommendList()
        initNestedScrollView()
        val initialStatus = coHostStore.coHostState.coHostStatus.value
        onConnectionStatusChange(initialStatus)
        refreshData()
        setContent(rootView)
    }

    override fun onStart() {
        super<AtomicPopover>.onStart()
        dialogScope = CoroutineScope(Dispatchers.Main)
        addObserver()
    }

    override fun dismiss() {
        super.dismiss()
        dialogScope?.cancel()
        dialogScope = null
        jobs.forEach { it.cancel() }
        jobs.clear()
    }

    private fun bindViewId(view: View) {
        tabLayout = view.findViewById(R.id.tab)
        recyclerBattleRecommendView = view.findViewById(R.id.rv_battle_recommend_list)
        coHostRecyclerRecommendView = view.findViewById(R.id.rv_connection_recommend_list)
        swipeRefreshLayout = view.findViewById(R.id.refresh_recommendation_list)
        inConnectionOrPKView = view.findViewById(R.id.in_connection_pk_view)
        viewStore?.let { inConnectionOrPKView.setViewStore(it) }
        layoutRecommendRootView = view.findViewById(R.id.ll_recommend_root)
        nestedScrollView = view.findViewById(R.id.scroll_view)
    }

    private fun initRefresh() {
        swipeRefreshLayout.setOnRefreshListener {
            fetchLiveList(true)
            swipeRefreshLayout.isRefreshing = false
        }
    }

    private fun refreshData() {
        fetchLiveList(true)
    }

    private fun fetchLiveList(isRefresh: Boolean) {
        if (isRefresh) {
            recommendedCursor = ""
        }
        LiveListStore.shared()
            .fetchLiveList(recommendedCursor, FETCH_LIST_COUNT, object : CompletionHandler {
                override fun onSuccess() {
                    recommendedCursor = liveListStore.liveState.liveListCursor.value
                }
                override fun onFailure(code: Int, desc: String) {
                    ErrorLocalized.onError(code)
                }
            })
    }

    private fun initBattleRecommendList() {
        recyclerBattleRecommendView.layoutManager =
            LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false)
        battleRecommendedAdapter = BattleInviteAdapter(context)
        recyclerBattleRecommendView.adapter = battleRecommendedAdapter
    }

    private fun initCoHostRecommendList() {
        coHostRecyclerRecommendView.layoutManager =
            LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false)
        coHostInviteAdapter = CoHostInviteAdapter(context)
        coHostRecyclerRecommendView.adapter = coHostInviteAdapter
    }

    private fun initNestedScrollView() {
        nestedScrollView.setOnScrollChangeListener(
            NestedScrollView.OnScrollChangeListener
            { v, _, scrollY, _, _ ->
                if (scrollY >= (v.getChildAt(0).measuredHeight - v.measuredHeight)) {
                    if (recommendedCursor.isNotEmpty()) {
                        fetchLiveList(false)
                    }
                }
            })
    }

    private fun initTabView() {
        tabLayout.removeAllTabs()
        val tabTitles = intArrayOf(
            R.string.seat_request_battle,
            R.string.common_connection
        )
        val tabColors = intArrayOf(
            R.color.common_text_color_primary,
            R.color.common_text_color_grey
        )

        for (i in tabTitles.indices) {
            tabLayout.addTab(createTab(tabLayout, tabTitles[i], tabColors[i]), i == 0)
        }
        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                setTabTextColor(tab, R.color.common_text_color_primary)
                if (tab.position == 0) {
                    recyclerBattleRecommendView.visibility = View.VISIBLE
                    coHostRecyclerRecommendView.visibility = View.GONE
                } else {
                    recyclerBattleRecommendView.visibility = View.GONE
                    coHostRecyclerRecommendView.visibility = View.VISIBLE
                }
            }

            override fun onTabUnselected(tab: TabLayout.Tab) {
                setTabTextColor(tab, R.color.common_text_color_grey)
            }

            override fun onTabReselected(tab: TabLayout.Tab) {}
        })
    }

    private fun createTab(tabLayout: TabLayout, titleRes: Int, colorRes: Int): TabLayout.Tab {
        val context = tabLayout.context
        val tabView = LayoutInflater.from(context)
            .inflate(R.layout.livekit_voiceroom_interaction_invite_tab, null) as TextView
        tabView.text = context.getString(titleRes)
        tabView.setTextColor(ContextCompat.getColor(context, colorRes))
        return tabLayout.newTab().setCustomView(tabView)
    }

    private fun setTabTextColor(tab: TabLayout.Tab, colorRes: Int) {
        val customView = tab.customView
        if (customView is TextView) {
            customView.setTextColor(ContextCompat.getColor(customView.context, colorRes))
        }
    }

    private fun addObserver() {
        val scope = dialogScope ?: return
        liveListStore.liveState.liveList
            .onEach { onLiveListChange(it) }
            .launchIn(scope)
            .let { jobs.add(it) }

        coHostStore.coHostState.connected
            .onEach { onConnectedUserChange(it) }
            .launchIn(scope)
            .let { jobs.add(it) }

        coHostStore.coHostState.invitees
            .onEach { onConnectedUserChange(it) }
            .launchIn(scope)
            .let { jobs.add(it) }

        coHostStore.coHostState.coHostStatus
            .onEach { onConnectionStatusChange(it) }
            .launchIn(scope)
            .let { jobs.add(it) }
    }

    private fun onConnectedUserChange(connectedList: List<SeatUserInfo>) {
        notifyVisibleItemsChanged(coHostRecyclerRecommendView, coHostInviteAdapter)
        notifyVisibleItemsChanged(recyclerBattleRecommendView, battleRecommendedAdapter)
    }

    private fun onLiveListChange(liveList: List<LiveInfo>?) {
        val filteredList = liveList?.filter {
            it.liveOwner.userID != liveListStore.liveState.currentLive.value.liveOwner.userID
        } ?: emptyList()

        coHostInviteAdapter.submitList(ArrayList(filteredList))
        battleRecommendedAdapter.submitList(ArrayList(filteredList))
    }

    private fun onConnectionStatusChange(status: CoHostStatus) {
        configDialogHeight(status)
        if (status == CoHostStatus.CONNECTED) {
            inConnectionOrPKView.visibility = View.VISIBLE
            layoutRecommendRootView.visibility = View.GONE
        } else {
            inConnectionOrPKView.visibility = View.GONE
            layoutRecommendRootView.visibility = View.VISIBLE
        }
    }

    private fun notifyVisibleItemsChanged(
        recyclerView: RecyclerView?,
        adapter: RecyclerView.Adapter<*>?,
    ) {
        val layoutManager = recyclerView?.layoutManager as? LinearLayoutManager ?: return
        adapter ?: return
        val firstVisible = layoutManager.findFirstVisibleItemPosition()
        val lastVisible = layoutManager.findLastVisibleItemPosition()
        if (firstVisible == RecyclerView.NO_POSITION) {
            return
        }
        for (i in firstVisible..lastVisible) {
            adapter.notifyItemChanged(i)
        }
    }

    private fun configDialogHeight(status: CoHostStatus) {
        val height = if (status == CoHostStatus.CONNECTED) {
            ViewGroup.LayoutParams.WRAP_CONTENT
        } else {
            (context.resources.displayMetrics.heightPixels * 0.8).toInt()
        }
        var params = rootView.layoutParams
        if (params == null) {
            params = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, height)
        } else {
            params.height = height
        }
        rootView.layoutParams = params
    }

    override fun onDestroy(owner: LifecycleOwner) {
        if (isShowing) {
            dismiss()
        }
        owner.lifecycle.removeObserver(this)
    }
}