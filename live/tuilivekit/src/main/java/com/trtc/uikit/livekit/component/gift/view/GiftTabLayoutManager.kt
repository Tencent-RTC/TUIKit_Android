package com.trtc.uikit.livekit.component.gift.view

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.viewpager2.widget.ViewPager2
import com.trtc.uikit.livekit.R
import com.trtc.uikit.livekit.component.gift.view.adapter.GiftCategoryPagerAdapter
import com.trtc.uikit.livekit.component.gift.view.cell.GiftBaseCell
import io.trtc.tuikit.atomicxcore.api.gift.Gift
import io.trtc.tuikit.atomicxcore.api.gift.GiftCategory

class GiftTabLayoutManager(private val context: Context) {
    private var giftClickListener: GiftCategoryViewPagerManager.GiftClickListener? = null
    private var tabManager: GiftCategoryTabManager? = null
    private var categoryViewManager: GiftCategoryViewPagerManager? = null

    private var viewPager: ViewPager2? = null
    private var contentContainer: FrameLayout? = null
    private var giftCategories: List<GiftCategory> = emptyList()
    private var dividerView: View? = null
    private var pagerAdapter: GiftCategoryPagerAdapter? = null

    var cellFactory: ((ViewGroup, Gift) -> GiftBaseCell?)? = null
    var onGiftSelectedListener: ((Gift) -> Unit)? = null

    fun setGiftClickListener(listener: GiftCategoryViewPagerManager.GiftClickListener) {
        giftClickListener = listener
    }

    fun createLayout(categories: List<GiftCategory>, columns: Int, rows: Int): View {
        giftCategories = categories

        val inflater = LayoutInflater.from(context)
        val mainLayout = inflater.inflate(R.layout.gift_layout_main_layout, null)

        tabManager = GiftCategoryTabManager(context)
        categoryViewManager = GiftCategoryViewPagerManager().apply {
            this.cellFactory = this@GiftTabLayoutManager.cellFactory
            this.onGiftSelectedListener = this@GiftTabLayoutManager.onGiftSelectedListener
            setGiftClickListener(object : GiftCategoryViewPagerManager.GiftClickListener {
                override fun onClick(position: Int, gift: Gift, count: Int) {
                    giftClickListener?.onClick(position, gift, count)
                }
            })
        }

        val tabLayoutContainer = mainLayout.findViewById<LinearLayout>(R.id.ll_tab_container)
        dividerView = mainLayout.findViewById(R.id.view_divider)
        contentContainer = mainLayout.findViewById(R.id.fl_content_container)

        viewPager = ViewPager2(context).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }
        contentContainer?.addView(viewPager)

        pagerAdapter = GiftCategoryPagerAdapter(context, categories, columns, categoryViewManager!!)
        viewPager?.adapter = pagerAdapter

        val tabLayout = tabManager?.createTabLayout(categories)
        tabLayoutContainer.addView(tabLayout)

        tabManager?.setOnCategorySelectedListener(object :
            GiftCategoryTabManager.OnCategorySelectedListener {
            override fun onCategorySelected(categoryIndex: Int, category: GiftCategory?) {
                viewPager?.setCurrentItem(categoryIndex, true)
            }
        })

        viewPager?.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                tabManager?.selectTab(position)
            }
        })

        return mainLayout
    }

    fun selectCategory(index: Int) {
        if (index in giftCategories.indices) {
            viewPager?.setCurrentItem(index, true)
            tabManager?.selectTab(index)
        }
    }
}