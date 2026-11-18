package io.trtc.tuikit.chat.uikit.components.aiassistant.call.ui

import android.annotation.SuppressLint
import android.content.Context
import android.util.TypedValue
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import io.trtc.tuikit.chat.uikit.components.aiassistant.call.TranscriptEntry

/**
 * 通话页文本态的对话列表。
 *
 * 数据不来自 Store：用户侧文本来自 ASR 回调按 chunkIndex 覆盖后的拼接结果，
 * AI 侧文本来自流式消息的 markdown 增量，两者都由 AICallController 维护。
 * 这样文本态与音频链路共用同一份状态，也绕开了 Store 拿不到 V2TIMStreamElem 的限制。
 */
class TranscriptView(context: Context) : RecyclerView(context) {

    private val entries = mutableListOf<TranscriptEntry>()

    /** 为 true 时 AI 的回答去掉 Markdown 和参考链接后再显示，见 [SpokenText]。 */
    var plainText: Boolean = false

    private val internalAdapter = object : RecyclerView.Adapter<TranscriptViewHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TranscriptViewHolder {
            val textView = TextView(parent.context).apply {
                layoutParams = LayoutParams(
                    LayoutParams.MATCH_PARENT,
                    LayoutParams.WRAP_CONTENT
                )
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                setPadding(0, dp(6), 0, dp(6))
                setLineSpacing(dp(4).toFloat(), 1f)
            }
            return TranscriptViewHolder(textView)
        }

        override fun getItemCount(): Int = entries.size

        override fun onBindViewHolder(holder: TranscriptViewHolder, position: Int) {
            val entry = entries[position]
            holder.textView.text = if (plainText && !entry.isFromUser) SpokenText.from(entry.text) else entry.text
            // 用户说的话用灰色弱化，AI 的回答用白色强调，发送失败的标红
            holder.textView.setTextColor(
                when {
                    entry.isFailed -> COLOR_FAILED
                    entry.isFromUser -> COLOR_USER
                    else -> COLOR_ASSISTANT
                }
            )
        }
    }

    class TranscriptViewHolder(val textView: TextView) : ViewHolder(textView)

    init {
        layoutManager = LinearLayoutManager(context)
        adapter = internalAdapter
        clipToPadding = false
        setPadding(dp(24), dp(8), dp(24), dp(8))
        overScrollMode = OVER_SCROLL_NEVER
    }

    /**
     * 全量替换。对话条数很少（一次通话通常十几条）、且最后一条在流式推进中每来一个增量
     * 就变一次，做 diff 的收益不抵复杂度，所以这里直接 [notifyDataSetChanged]。
     */
    @SuppressLint("NotifyDataSetChanged")
    fun submit(newEntries: List<TranscriptEntry>) {
        entries.clear()
        entries.addAll(newEntries)
        internalAdapter.notifyDataSetChanged()
        if (entries.isNotEmpty()) {
            scrollToPosition(entries.lastIndex)
        }
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    companion object {
        private const val COLOR_USER = 0xFF8A9099.toInt()
        private const val COLOR_ASSISTANT = 0xFFF2F4F6.toInt()
        private const val COLOR_FAILED = 0xFFFF5A5F.toInt()
    }
}
