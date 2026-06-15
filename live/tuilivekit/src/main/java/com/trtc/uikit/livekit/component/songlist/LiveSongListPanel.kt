package com.trtc.uikit.livekit.component.songlist

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.tabs.TabLayout
import com.trtc.uikit.livekit.R
import com.trtc.uikit.livekit.component.karaoke.store.utils.PlaybackState
import io.trtc.tuikit.atomicx.widget.basicwidget.popover.AtomicPopover
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class LiveSongListPanel(
    context: Context,
    private val store: LiveMusicStore,
) : AtomicPopover(context) {

    private lateinit var recyclerLibrary: RecyclerView
    private lateinit var recyclerSelected: RecyclerView
    private var orderedTabView: TextView? = null
    private val adapterLibrary = LiveSongListAdapter(store)
    private val adapterSelected = LiveSelectedSongAdapter(store)
    private var subscribeJob: Job? = null

    init {
        initView()
        initCallbacks()
    }

    private fun initView() {
        val view: View = LayoutInflater.from(context)
            .inflate(R.layout.live_song_list_panel, null)

        setPanelHeight(PanelHeight.Ratio(0.6F))
        initTabLayout(view)
        initLibraryView(view)
        initSelectedView(view)
        configDialogHeight(view)
        setContent(view)
    }

    private fun initCallbacks() {
        adapterSelected.onPlayPause = {
            val isPlaying = store.playStatus.value == PlaybackState.START
                    || store.playStatus.value == PlaybackState.RESUME
            if (isPlaying) {
                store.pause()
            } else {
                store.resume()
            }
        }
        adapterSelected.onNext = {
            store.playNext()
        }
        adapterSelected.onTop = { song ->
            store.topSong(song.songId)
        }
        adapterSelected.onRemove = { song ->
            store.removeSong(song.songId)
        }
    }

    private fun addObserve() {
        subscribeJob = CoroutineScope(Dispatchers.Main).launch {
            launch {
                store.librarySongs.collect { songs ->
                    adapterLibrary.submitList(songs.toList())
                }
            }
            launch {
                store.selectedSongs.collect { songs ->
                    orderedTabView?.text = context.getString(R.string.karaoke_ordered_count, songs.size)
                    adapterSelected.submitList(songs.toList())
                    // Refresh library list to update "Ordered" state
                    adapterLibrary.submitList(store.librarySongs.value.toList())
                }
            }
            launch {
                store.playStatus.collect {
                    adapterSelected.submitList(store.selectedSongs.value.toList())
                }
            }
            launch {
                store.currentSongId.collect {
                    adapterSelected.submitList(store.selectedSongs.value.toList())
                }
            }
        }
    }

    private fun removeObserve() {
        subscribeJob?.cancel()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        addObserve()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        removeObserve()
    }

    private fun initLibraryView(view: View) {
        recyclerLibrary = view.findViewById(R.id.rv_song_library)
        recyclerLibrary.layoutManager = LinearLayoutManager(context)
        recyclerLibrary.adapter = adapterLibrary
        recyclerLibrary.visibility = View.VISIBLE
    }

    private fun initSelectedView(view: View) {
        recyclerSelected = view.findViewById(R.id.rv_selected_songs)
        recyclerSelected.layoutManager = LinearLayoutManager(context)
        recyclerSelected.adapter = adapterSelected
        recyclerSelected.visibility = View.GONE
    }

    private fun initTabLayout(view: View) {
        val tabLayout = view.findViewById<TabLayout>(R.id.tab)
        tabLayout.removeAllTabs()
        val tabTitles = listOf(
            R.string.karaoke_order_song,
            R.string.karaoke_ordered_count
        )
        val tabColors = listOf(
            R.color.karaoke_color_white,
            R.color.karaoke_text_color_grey_4d
        )

        tabTitles.forEachIndexed { index, titleRes ->
            tabLayout.addTab(createTab(view, titleRes, tabColors[index], index), index == 0)
        }

        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                setTabTextColor(tab, R.color.karaoke_color_white)
                when (tab.position) {
                    0 -> {
                        recyclerLibrary.visibility = View.VISIBLE
                        recyclerSelected.visibility = View.GONE
                    }
                    1 -> {
                        recyclerLibrary.visibility = View.GONE
                        recyclerSelected.visibility = View.VISIBLE
                    }
                }
            }

            override fun onTabUnselected(tab: TabLayout.Tab) {
                setTabTextColor(tab, R.color.karaoke_text_color_grey_4d)
            }

            override fun onTabReselected(tab: TabLayout.Tab) {}
        })
    }

    private fun createTab(view: View, titleRes: Int, textColorRes: Int, index: Int): TabLayout.Tab {
        val tabView = LayoutInflater.from(context)
            .inflate(R.layout.karaoke_tab_item, null) as TextView
        tabView.text = context.getString(titleRes)
        tabView.setTextColor(ContextCompat.getColor(context, textColorRes))
        if (index == 1) {
            orderedTabView = tabView
        }
        return view.findViewById<TabLayout>(R.id.tab).newTab().setCustomView(tabView)
    }

    private fun setTabTextColor(tab: TabLayout.Tab, colorRes: Int) {
        val tabView = tab.customView as? TextView ?: return
        tabView.setTextColor(ContextCompat.getColor(tabView.context, colorRes))
    }

    private fun configDialogHeight(view: View) {
        view.layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            (context.resources.displayMetrics.heightPixels * 0.6).toInt()
        )
    }
}
