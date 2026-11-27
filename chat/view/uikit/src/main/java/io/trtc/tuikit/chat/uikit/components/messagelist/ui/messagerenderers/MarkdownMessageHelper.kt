package io.trtc.tuikit.chat.uikit.components.messagelist.ui.messagerenderers

import android.content.Context
import android.widget.TextView
import io.noties.markwon.AbstractMarkwonPlugin
import io.noties.markwon.Markwon
import io.noties.markwon.core.MarkwonTheme
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin
import io.noties.markwon.ext.tables.TablePlugin
import java.util.WeakHashMap

/**
 * 把流式 / 机器人消息的 markdown 画进气泡 TextView。
 *
 * 流式过程中文档经常半截（未闭合围栏、半行表格），直接解析会把标记当正文闪一下，
 * 所以未结束时先做一次轻量补全。汉字紧贴的 `**加粗**` 也要先补空格，否则
 * CommonMark 侧翼规则不会开强调。
 */
internal object MarkdownMessageHelper {
    private val cache = WeakHashMap<Context, Markwon>()

    fun render(textView: TextView, markdown: String, isStreaming: Boolean, textColor: Int) {
        textView.setTextColor(textColor)
        if (markdown.isEmpty()) {
            textView.text = null
            return
        }
        val source = prepareMarkdown(markdown, isStreaming)
        val markwon = markwon(textView.context)
        runCatching { markwon.setMarkdown(textView, source) }
            .onFailure { textView.text = markdown }
    }

    private fun markwon(context: Context): Markwon {
        val app = context.applicationContext
        return cache.getOrPut(app) {
            Markwon.builder(app)
                .usePlugin(TablePlugin.create(app))
                .usePlugin(StrikethroughPlugin.create())
                .usePlugin(object : AbstractMarkwonPlugin() {
                    override fun configureTheme(builder: MarkwonTheme.Builder) {
                        builder.headingBreakHeight(0)
                    }

                    override fun afterSetText(textView: TextView) {
                        // Markwon 默认装的 LinkMovementMethod 会在 ACTION_DOWN 就消费事件，
                        // 气泡的长按菜单收不到手势。链接点击改由 MarkdownLinkTouchListener 处理，
                        // 它只在按下点落在链接上时才接管。
                        textView.movementMethod = null
                    }
                })
                .build()
        }
    }
}
