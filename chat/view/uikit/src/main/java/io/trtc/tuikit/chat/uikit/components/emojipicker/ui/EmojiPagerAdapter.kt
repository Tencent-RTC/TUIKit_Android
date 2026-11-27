package io.trtc.tuikit.chat.uikit.components.emojipicker.ui
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.recyclerview.widget.RecyclerView

class EmojiPagerAdapter(
    private var pageCount: Int,
    private val pageFactory: (Int) -> RecyclerView
) : RecyclerView.Adapter<EmojiPagerAdapter.PageViewHolder>() {

    class PageViewHolder(val container: FrameLayout) : RecyclerView.ViewHolder(container)

    fun submitPageCount(count: Int) {
        if (pageCount == count) return
        pageCount = count
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PageViewHolder {
        val container = FrameLayout(parent.context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
        return PageViewHolder(container)
    }

    override fun onBindViewHolder(holder: PageViewHolder, position: Int) {
        for (index in 0 until holder.container.childCount) {
            (holder.container.getChildAt(index) as? RecyclerView)?.adapter = null
        }
        holder.container.removeAllViews()
        holder.container.addView(pageFactory(position), ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ))
    }

    override fun getItemCount(): Int = pageCount
}
