package io.trtc.tuikit.chat.demo.settings

import io.trtc.tuikit.chat.demo.common.BaseActivity

import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import io.trtc.tuikit.atomicx.theme.ThemeStore
import io.trtc.tuikit.atomicx.theme.tokens.ColorTokens
import io.trtc.tuikit.chat.app.R
import io.trtc.tuikit.chat.uikit.components.ai.AiMediaProcessManager
import io.trtc.tuikit.chat.uikit.components.ai.tts.CustomVoiceItem
import io.trtc.tuikit.chat.uikit.components.ai.tts.VoiceMessageConfig
import io.trtc.tuikit.chat.uikit.components.ai.tts.defaultVoiceList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class VoiceSelectActivity : BaseActivity() {

    private val themeStore by lazy { ThemeStore.shared(this) }
    private var activityScope: CoroutineScope? = null

    private lateinit var rootContainer: LinearLayout
    private lateinit var headerContainer: LinearLayout
    private lateinit var tvTitle: TextView
    private lateinit var btnBack: ImageView
    private lateinit var btnMore: ImageView
    private lateinit var headerDivider: View
    private lateinit var badgeContainer: View
    private lateinit var leftContainer: LinearLayout
    private lateinit var recyclerView: RecyclerView
    private lateinit var progressBar: ProgressBar

    private lateinit var colors: ColorTokens

    private val defaultVoices: List<CustomVoiceItem> by lazy { defaultVoiceList(this) }
    private val customVoices = mutableListOf<CustomVoiceItem>()
    private val items = mutableListOf<Item>()
    private var selectedId: String = ""
    private val adapter = VoiceAdapter()
    private var openRow: SwipeRevealRow? = null

    companion object {
        private const val TYPE_HEADER = 0
        private const val TYPE_DEFAULT_ROW = 1
        private const val TYPE_CUSTOM_ROW = 2
        private const val TYPE_EMPTY = 3

        fun start(context: Context) {
            context.startActivity(Intent(context, VoiceSelectActivity::class.java))
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (isFinishing) {
            return
        }
        setContentView(R.layout.demo_activity_voice_select)

        colors = themeStore.themeState.value.currentTheme.tokens.color

        rootContainer = findViewById(R.id.demo_voiceSelectRoot)
        headerContainer = findViewById(R.id.demo_chatHeaderContainer)
        tvTitle = findViewById(R.id.demo_tvChatTitle)
        btnBack = findViewById(R.id.demo_btnBack)
        btnMore = findViewById(R.id.demo_btnMore)
        headerDivider = findViewById(R.id.demo_headerDivider)
        badgeContainer = findViewById(R.id.demo_badgeContainer)
        leftContainer = findViewById(R.id.demo_leftContainer)
        recyclerView = findViewById(R.id.demo_voiceSelectRecycler)
        progressBar = findViewById(R.id.demo_voiceSelectProgress)

        ViewCompat.setOnApplyWindowInsetsListener(rootContainer) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.updatePadding(top = systemBars.top)
            recyclerView.updatePadding(bottom = systemBars.bottom)
            insets
        }

        leftContainer.contentDescription = btnBack.contentDescription
        leftContainer.setOnClickListener { finish() }
        btnMore.visibility = View.GONE
        badgeContainer.visibility = View.GONE
        tvTitle.text = getString(R.string.demo_voice_select)

        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter
        recyclerView.itemAnimator = null

        applyColors(colors)
        loadVoices()
    }

    override fun onStart() {
        super.onStart()
        activityScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
        activityScope?.launch {
            themeStore.themeState.collectLatest { state ->
                colors = state.currentTheme.tokens.color
                applyColors(colors)
                adapter.notifyDataSetChanged()
            }
        }
    }

    override fun onStop() {
        super.onStop()
        activityScope?.cancel()
        activityScope = null
    }

    private fun loadVoices() {
        selectedId = VoiceMessageConfig.getSelectedVoiceId(this)
        progressBar.visibility = View.VISIBLE
        AiMediaProcessManager.getCustomVoiceList(
            onSuccess = { list ->
                if (isFinishing || isDestroyed) return@getCustomVoiceList
                progressBar.visibility = View.GONE
                customVoices.clear()
                customVoices.addAll(list)
                rebuildItems()
                adapter.notifyDataSetChanged()
            },
            onFailure = { _, _ ->
                if (isFinishing || isDestroyed) return@getCustomVoiceList
                progressBar.visibility = View.GONE
                customVoices.clear()
                rebuildItems()
                adapter.notifyDataSetChanged()
            }
        )
    }

    private fun rebuildItems() {
        items.clear()
        items.add(Item.Header(getString(R.string.demo_voice_default_group)))
        defaultVoices.forEach { items.add(Item.Voice(it, custom = false)) }
        items.add(Item.Header(getString(R.string.demo_voice_custom_group)))
        if (customVoices.isEmpty()) {
            items.add(Item.Empty(getString(R.string.demo_voice_custom_empty)))
        } else {
            customVoices.forEach { items.add(Item.Voice(it, custom = true)) }
        }
    }

    private fun selectVoice(item: CustomVoiceItem) {
        VoiceMessageConfig.setSelectedVoice(this, item.voiceId, item.name)
        selectedId = item.voiceId
        adapter.notifyDataSetChanged()
    }

    private fun deleteVoice(item: CustomVoiceItem) {
        AiMediaProcessManager.deleteCustomVoice(
            voiceId = item.voiceId,
            onSuccess = {
                if (isFinishing || isDestroyed) return@deleteCustomVoice
                customVoices.removeAll { it.voiceId == item.voiceId }
                if (selectedId == item.voiceId) {
                    VoiceMessageConfig.setSelectedVoice(this, "", "")
                    selectedId = ""
                }
                if (openRow != null) openRow = null
                rebuildItems()
                adapter.notifyDataSetChanged()
            },
            onFailure = { _, _ ->
                if (isFinishing || isDestroyed) return@deleteCustomVoice
                Toast.makeText(this, R.string.demo_voice_delete_failed, Toast.LENGTH_SHORT).show()
            }
        )
    }

    private fun applyColors(colors: ColorTokens) {
        rootContainer.setBackgroundColor(colors.bgColorOperate)
        recyclerView.setBackgroundColor(colors.bgColorOperate)
        headerContainer.setBackgroundColor(colors.bgColorOperate)
        tvTitle.setTextColor(colors.textColorPrimary)
        btnBack.imageTintList = ColorStateList.valueOf(colors.textColorSecondary)
        headerDivider.setBackgroundColor(colors.strokeColorPrimary)
        progressBar.indeterminateTintList = ColorStateList.valueOf(colors.textColorLink)
    }

    private fun bindVoiceRowContent(content: View, item: CustomVoiceItem, custom: Boolean) {
        content.setBackgroundColor(colors.bgColorOperate)
        val name = content.findViewById<TextView>(R.id.demo_tvVoiceName)
        val badge = content.findViewById<TextView>(R.id.demo_tvVoiceBadge)
        val tvVoiceId = content.findViewById<TextView>(R.id.demo_tvVoiceId)
        val check = content.findViewById<ImageView>(R.id.demo_ivVoiceCheck)

        name.text = item.name
        name.setTextColor(colors.textColorPrimary)

        val selected = item.voiceId == selectedId
        check.visibility = if (selected) View.VISIBLE else View.INVISIBLE
        check.setColorFilter(colors.textColorLink)

        val nameParams = name.layoutParams as LinearLayout.LayoutParams
        if (custom) {
            nameParams.width = ViewGroup.LayoutParams.WRAP_CONTENT
            nameParams.weight = 0f
            name.layoutParams = nameParams

            badge.visibility = View.VISIBLE
            badge.text = getString(R.string.demo_voice_custom_badge)
            badge.setTextColor(colors.textColorButton)
            val badgeColor = colors.buttonColorPrimaryDefault
            badge.background = GradientDrawable().apply {
                cornerRadius = 4f * resources.displayMetrics.density
                setColor(badgeColor)
            }

            tvVoiceId.visibility = View.VISIBLE
            tvVoiceId.text = item.voiceId
            tvVoiceId.setTextColor(colors.textColorTertiary)
        } else {
            nameParams.width = 0
            nameParams.weight = 1f
            name.layoutParams = nameParams

            badge.visibility = View.GONE
            tvVoiceId.visibility = View.GONE
        }
    }

    private sealed class Item {
        data class Header(val title: String) : Item()
        data class Voice(val voice: CustomVoiceItem, val custom: Boolean) : Item()
        data class Empty(val text: String) : Item()
    }

    private class HeaderViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val title: TextView = view.findViewById(R.id.demo_tvVoiceSectionTitle)
    }

    private class DefaultRowViewHolder(view: View) : RecyclerView.ViewHolder(view)

    private class CustomRowViewHolder(
        val swipeRow: SwipeRevealRow,
        val content: View
    ) : RecyclerView.ViewHolder(swipeRow)

    private class EmptyViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val text: TextView = view.findViewById(R.id.demo_tvVoiceEmpty)
    }

    private inner class VoiceAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

        override fun getItemViewType(position: Int): Int {
            return when (val item = items[position]) {
                is Item.Header -> TYPE_HEADER
                is Item.Empty -> TYPE_EMPTY
                is Item.Voice -> if (item.custom) TYPE_CUSTOM_ROW else TYPE_DEFAULT_ROW
            }
        }

        override fun getItemCount(): Int = items.size

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val inflater = LayoutInflater.from(parent.context)
            return when (viewType) {
                TYPE_HEADER -> HeaderViewHolder(
                    inflater.inflate(R.layout.demo_item_voice_section_header, parent, false)
                )

                TYPE_EMPTY -> EmptyViewHolder(
                    inflater.inflate(R.layout.demo_item_voice_empty, parent, false)
                )

                TYPE_CUSTOM_ROW -> {
                    val swipeRow = SwipeRevealRow(parent.context).apply {
                        layoutParams = RecyclerView.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT
                        )
                    }
                    val content = inflater.inflate(R.layout.demo_item_voice_row, swipeRow, false)
                    swipeRow.setContent(content)
                    CustomRowViewHolder(swipeRow, content)
                }

                else -> DefaultRowViewHolder(
                    inflater.inflate(R.layout.demo_item_voice_row, parent, false)
                )
            }
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            when (val item = items[position]) {
                is Item.Header -> {
                    holder as HeaderViewHolder
                    holder.title.text = item.title
                    holder.title.setTextColor(colors.textColorSecondary)
                    holder.title.setBackgroundColor(colors.bgColorDefault)
                }

                is Item.Empty -> {
                    holder as EmptyViewHolder
                    holder.text.text = item.text
                    holder.text.setTextColor(colors.textColorTertiary)
                    holder.text.setBackgroundColor(colors.bgColorOperate)
                }

                is Item.Voice -> {
                    if (item.custom) {
                        holder as CustomRowViewHolder
                        holder.swipeRow.closeImmediate()
                        holder.swipeRow.setBackgroundColor(colors.textColorError)
                        holder.swipeRow.deleteButton.text = getString(R.string.demo_voice_delete)
                        holder.swipeRow.deleteButton.setTextColor(colors.textColorButton)
                        holder.swipeRow.deleteButton.setBackgroundColor(colors.textColorError)
                        holder.swipeRow.rowClickListener = { selectVoice(item.voice) }
                        holder.swipeRow.deleteClickListener = { deleteVoice(item.voice) }
                        holder.swipeRow.onOpenListener = { opened ->
                            if (openRow != null && openRow !== opened) {
                                openRow?.close()
                            }
                            openRow = opened
                        }
                        bindVoiceRowContent(holder.content, item.voice, custom = true)
                    } else {
                        holder as DefaultRowViewHolder
                        holder.itemView.setOnClickListener { selectVoice(item.voice) }
                        bindVoiceRowContent(holder.itemView, item.voice, custom = false)
                    }
                }
            }
        }
    }
}
