package com.example.atomicxcore.components

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.util.AttributeSet
import android.view.Gravity
import android.view.KeyEvent
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.atomicxcore.R
import io.trtc.tuikit.atomicxcore.api.barrage.Barrage
import io.trtc.tuikit.atomicxcore.api.barrage.BarrageStore
import io.trtc.tuikit.atomicxcore.api.gift.Gift
import io.trtc.tuikit.atomicxcore.api.gift.GiftListener
import io.trtc.tuikit.atomicxcore.api.gift.GiftStore
import io.trtc.tuikit.atomicxcore.api.live.LiveUserInfo

/**
 * Barrage (danmaku) interaction component
 *
 * Related APIs:
 * - BarrageStore.create(liveID) - Create a barrage manager instance
 * - BarrageStore.sendTextMessage(text, extensionInfo, completion) - Send a text barrage
 * - BarrageStore.appendLocalTip(message) - Insert a local tip message
 * - BarrageStore.state - Barrage state (StateFlow<BarrageState>, contains messageList)
 *
 * Features:
 * - Displays the barrage message list (auto-scrolls to the bottom)
 * - Bottom input button that opens an input dialog to send text barrages
 * - Listens for gift events and automatically inserts gift messages into the barrage list
 */
class BarrageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    private var liveID: String = ""
    private var barrageStore: BarrageStore? = null
    private var giftStore: GiftStore? = null
    private val messages = mutableListOf<Barrage>()
    private val adapter = BarrageAdapter(messages)
    private val recyclerView: RecyclerView

    private val giftListener = object : GiftListener() {
        override fun onReceiveGift(liveID: String, gift: Gift, count: Int, sender: LiveUserInfo) {
            insertGiftBarrage(gift, count, sender)
        }
    }

    init {
        orientation = VERTICAL

        // Barrage message list
        recyclerView = RecyclerView(context).apply {
            layoutManager = LinearLayoutManager(context)
            adapter = this@BarrageView.adapter
            setBackgroundColor(Color.TRANSPARENT)
            overScrollMode = OVER_SCROLL_NEVER
        }
        addView(recyclerView, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))

        // Bottom input placeholder button
        val inputPlaceholder = TextView(context).apply {
            text = context.getString(R.string.interactive_barrage_placeholder)
            setTextColor(Color.parseColor("#99FFFFFF"))
            textSize = 14f
            val dp12 = (12 * resources.displayMetrics.density).toInt()
            val dp16 = (16 * resources.displayMetrics.density).toInt()
            val dp40 = (40 * resources.displayMetrics.density).toInt()
            setPadding(dp16, 0, dp16, 0)
            gravity = Gravity.CENTER_VERTICAL
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(Color.parseColor("#66000000"))
                cornerRadius = 20 * resources.displayMetrics.density
            }
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, dp40).apply {
                topMargin = (8 * resources.displayMetrics.density).toInt()
                marginStart = dp12
                marginEnd = dp12
            }
            setOnClickListener { showInputDialog() }
        }
        addView(inputPlaceholder)
    }

    /**
     * Initialize the barrage component (called after entering the live stream in Activity)
     */
    fun initialize(liveID: String) {
        this.liveID = liveID
        barrageStore = BarrageStore.create(liveID)
        giftStore = GiftStore.create(liveID)
        giftStore?.addGiftListener(giftListener)
    }

    /**
     * Update the message list from BarrageStore.state
     */
    fun updateMessages(messageList: List<Barrage>) {
        messages.clear()
        messages.addAll(messageList)
        adapter.notifyDataSetChanged()
        if (messages.isNotEmpty()) {
            recyclerView.scrollToPosition(messages.size - 1)
        }
    }

    /** Insert a gift barrage message */
    private fun insertGiftBarrage(gift: Gift, count: Int, sender: LiveUserInfo) {
        val senderName = if (sender.userName.isNullOrEmpty()) sender.userID else sender.userName
        val barrage = Barrage().apply {
            textContent = "$senderName ${context.getString(R.string.interactive_gift_sent)} ${gift.name} x$count"
        }
        barrageStore?.appendLocalTip(barrage)
    }

    /** Show the input dialog */
    private fun showInputDialog() {
        val dialog = Dialog(context, android.R.style.Theme_Translucent_NoTitleBar)
        dialog.window?.apply {
            setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE or WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
            setDimAmount(0.3f)
            addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        }

        val root = FrameLayout(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setOnClickListener { dialog.dismiss() }
        }

        val dp12 = (12 * context.resources.displayMetrics.density).toInt()
        val dp52 = (52 * context.resources.displayMetrics.density).toInt()
        val dp36 = (36 * context.resources.displayMetrics.density).toInt()
        val dp44 = (44 * context.resources.displayMetrics.density).toInt()

        val inputBar = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(Color.parseColor("#F2262626"))
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, dp52
            ).apply {
                gravity = Gravity.BOTTOM
            }
            setPadding(dp12, 0, dp12, 0)
        }

        val editText = EditText(context).apply {
            hint = context.getString(R.string.interactive_barrage_placeholder)
            setHintTextColor(Color.parseColor("#80FFFFFF"))
            setTextColor(Color.WHITE)
            textSize = 15f
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(Color.parseColor("#1FFFFFFF"))
                cornerRadius = 18 * resources.displayMetrics.density
            }
            setPadding((14 * resources.displayMetrics.density).toInt(), 0, (14 * resources.displayMetrics.density).toInt(), 0)
            imeOptions = EditorInfo.IME_ACTION_SEND
            isSingleLine = true
        }
        inputBar.addView(editText, LayoutParams(0, dp36, 1f))

        val sendBtn = ImageButton(context).apply {
            setImageResource(R.drawable.ic_send)
            background = null
            layoutParams = LayoutParams(dp44, dp44)
            setOnClickListener {
                val text = editText.text?.toString()?.trim() ?: ""
                if (text.isNotEmpty()) {
                    barrageStore?.sendTextMessage(text, null, null)
                    editText.text?.clear()
                }
            }
        }
        inputBar.addView(sendBtn)

        editText.setOnEditorActionListener { _, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_SEND ||
                (event?.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN)) {
                sendBtn.performClick()
                true
            } else false
        }

        root.addView(inputBar)
        dialog.setContentView(root)
        dialog.show()
        editText.requestFocus()
    }

    fun release() {
        giftStore?.removeGiftListener(giftListener)
    }

    // MARK: - RecyclerView Adapter

    private class BarrageAdapter(
        private val messages: List<Barrage>
    ) : RecyclerView.Adapter<BarrageAdapter.ViewHolder>() {

        class ViewHolder(val textView: TextView) : RecyclerView.ViewHolder(textView)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val dp12 = (12 * parent.resources.displayMetrics.density).toInt()
            val dp6 = (6 * parent.resources.displayMetrics.density).toInt()
            val dp10 = (10 * parent.resources.displayMetrics.density).toInt()
            val dp2 = (2 * parent.resources.displayMetrics.density).toInt()

            val textView = TextView(parent.context).apply {
                setPadding(dp10, dp6, dp10, dp6)
                background = android.graphics.drawable.GradientDrawable().apply {
                    setColor(Color.parseColor("#4D000000"))
                    cornerRadius = 12 * resources.displayMetrics.density
                }
                layoutParams = RecyclerView.LayoutParams(
                    RecyclerView.LayoutParams.WRAP_CONTENT,
                    RecyclerView.LayoutParams.WRAP_CONTENT
                ).apply {
                    marginStart = dp12
                    topMargin = dp2
                    bottomMargin = dp2
                }
            }
            return ViewHolder(textView)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val barrage = messages[position]
            val sender = barrage.sender
            val senderName = if (sender?.userName.isNullOrEmpty()) (sender?.userID ?: "") else sender.userName

            val ssb = SpannableStringBuilder()
            val nameStr = "$senderName: "
            ssb.append(nameStr)
            ssb.setSpan(
                ForegroundColorSpan(Color.parseColor("#00BCD4")),
                0, nameStr.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            ssb.setSpan(
                StyleSpan(android.graphics.Typeface.BOLD),
                0, nameStr.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            ssb.append(barrage.textContent ?: "")
            ssb.setSpan(
                ForegroundColorSpan(Color.WHITE),
                nameStr.length, ssb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )

            holder.textView.text = ssb
            holder.textView.textSize = 13f
        }

        override fun getItemCount() = messages.size
    }
}
