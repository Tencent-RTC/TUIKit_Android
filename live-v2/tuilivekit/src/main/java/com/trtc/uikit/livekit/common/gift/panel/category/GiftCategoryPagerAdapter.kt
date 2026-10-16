package com.trtc.uikit.livekit.common.gift.panel.category

import android.content.Context
import android.util.SparseArray
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.trtc.uikit.livekit.R
import com.trtc.uikit.livekit.common.gift.panel.gift.GiftPanelAdapter
import com.trtc.uikit.livekit.common.basic.ScreenUtil
import io.trtc.tuikit.atomicxcore.api.gift.Gift
import io.trtc.tuikit.atomicxcore.api.gift.GiftCategory
import androidx.core.util.forEach

class GiftCategoryPagerAdapter(
    private val context: Context,
    private val categories: List<GiftCategory>,
    private val columns: Int,
    private val onGiftSelectedListener: ((Gift) -> Unit)?,
    private val onGiftClick: (gift: Gift, count: Int) -> Unit,
    private val onAdvancedComboRequested: ((Gift) -> Unit)? = null,
) : RecyclerView.Adapter<GiftCategoryPagerAdapter.CategoryViewHolder>() {

    private val pageAdapters = SparseArray<GiftPanelAdapter>()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CategoryViewHolder {
        val container = FrameLayout(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
        return CategoryViewHolder(container)
    }

    override fun onBindViewHolder(holder: CategoryViewHolder, position: Int) {
        val category = categories[position]
        val (categoryView, pageAdapter) = createSingleCategoryView(category)
        pageAdapters.put(position, pageAdapter)
        holder.bind(categoryView)
    }

    override fun onViewRecycled(holder: CategoryViewHolder) {
        super.onViewRecycled(holder)
        val position = holder.bindingAdapterPosition
        if (position != RecyclerView.NO_POSITION) {
            pageAdapters.remove(position)
        }
        holder.detach()
    }

    override fun getItemCount(): Int = categories.size

    fun clearAllPageSelections() {
        pageAdapters.forEach { _, adapter -> adapter.clearSelection() }
    }

    private fun createSingleCategoryView(category: GiftCategory): Pair<View, GiftPanelAdapter> {
        val inflater = LayoutInflater.from(context)
        val contentView = inflater.inflate(R.layout.livekit_gift_layout_category_content, null)
        val recyclerView = contentView.findViewById<RecyclerView>(R.id.rv_gifts)

        recyclerView.layoutManager = GridLayoutManager(context, columns)
        recyclerView.itemAnimator = null
        val params = recyclerView.layoutParams
        params.height = ScreenUtil.dip2px(228f)
        recyclerView.layoutParams = params

        val adapter = GiftPanelAdapter(0, category.giftList.toMutableList(), context)
        adapter.onGiftSelectedListener = onGiftSelectedListener
        adapter.onAdvancedComboRequested = onAdvancedComboRequested
        recyclerView.adapter = adapter

        adapter.setOnItemClickListener(object : GiftPanelAdapter.OnItemClickListener {
            override fun onItemClick(view: View?, gift: Gift, position: Int, pageIndex: Int, count: Int) {
                onGiftClick(gift, count)
            }
        })

        return contentView to adapter
    }

    class CategoryViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val container = itemView as FrameLayout

        fun bind(view: View) {
            container.removeAllViews()
            container.addView(view)
        }

        fun detach() {
            stopInnerAnimations(container)
            container.removeAllViews()
        }

        private fun stopInnerAnimations(view: View?) {
            view ?: return
            if (view is RecyclerView) {
                view.stopScroll()
                view.itemAnimator?.endAnimations()
            }
            if (view is ViewGroup) {
                for (i in 0 until view.childCount) {
                    stopInnerAnimations(view.getChildAt(i))
                }
            }
        }
    }
}