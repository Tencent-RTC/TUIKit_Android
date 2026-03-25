package com.tencent.uikit.app.main

import android.content.res.Resources
import android.text.TextUtils
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.recyclerview.widget.RecyclerView
import com.tencent.uikit.app.R

class TRTCMainAdapter(
    private val currentLanguage: String?,
    private val itemDataList: MutableList<MainItemData>,
    private val onItemClickListener: OnItemClickListener
) : RecyclerView.Adapter<RecyclerView.ViewHolder?>() {

    override fun getItemViewType(position: Int): Int {
        if (position == itemCount - 1) {
            return ITEM_TYPE_FOOTER
        }
        return ITEM_TYPE_ITEM
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val context = parent.context
        val inflater = LayoutInflater.from(context)
        if (viewType == ITEM_TYPE_ITEM) {
            val view: View = inflater.inflate(R.layout.app_main_item, parent, false)
            return ItemViewHolder(view)
        } else if (viewType == ITEM_TYPE_WEB_VIEW) {
            val view: View = inflater.inflate(R.layout.app_item_web_view, parent, false)
            return WebViewHolder(view)
        } else {
            val view = View(parent.context)
            return FooterViewHolder(view)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        if (holder is ItemViewHolder) {
            val item: MainItemData? = itemDataList[position]
            holder.bind(item, onItemClickListener)
        } else if (holder is WebViewHolder) {
            val item: MainItemData? = itemDataList[position]
            holder.bind(item, currentLanguage, onItemClickListener)
        }
    }

    override fun getItemCount(): Int {
        return itemDataList.size + 1
    }

    interface OnItemClickListener {
        fun onItemClick(mainItemData: MainItemData?)
    }

    class ItemViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val imageIcon: ImageView = itemView.findViewById(R.id.img_main_icon)
        private val textTitle: TextView = itemView.findViewById(R.id.tv_main_title)
        private val textSubTitle: TextView = itemView.findViewById(R.id.tv_main_subtitle)
        private val constraintItem: ConstraintLayout = itemView.findViewById(R.id.cl_main_item)

        fun bind(
            mainItemData: MainItemData?,
            onItemClickListener: OnItemClickListener
        ) {
            if (mainItemData == null) {
                return
            }
            itemView.setOnClickListener { v: View? ->
                onItemClickListener.onItemClick(
                    mainItemData
                )
            }

            constraintItem.setBackgroundResource(
                if (mainItemData.category === MainItemData.Category.KIT)
                    R.drawable.app_bg_main_kit_item
                else
                    R.drawable.app_bg_main_item
            )
            textTitle.setText(mainItemData.itemTitle)
            imageIcon.setImageResource(mainItemData.itemResId)
            textSubTitle.setText(mainItemData.itemSubTitle)
        }
    }

    class WebViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val textTitle: TextView = itemView.findViewById(R.id.tv_main_title)
        private val textSubTitle: TextView = itemView.findViewById(R.id.tv_main_subtitle)

        fun bind(
            mainItemData: MainItemData?,
            currentLanguage: String?,
            onItemClickListener: OnItemClickListener
        ) {
            if (mainItemData == null) {
                return
            }
            val layoutParams: ViewGroup.LayoutParams?
            if (TextUtils.equals(currentLanguage, ENGLISH_LANGUAGE_CODE)) {
                layoutParams = LinearLayout.LayoutParams(
                    dp2px(ENGLISH_TEXT_WIDTH_DP.toFloat()),
                    dp2px(ENGLISH_TEXT_HEIGHT_DP.toFloat())
                )
            } else {
                layoutParams = LinearLayout.LayoutParams(
                    dp2px(DEFAULT_TEXT_WIDTH_DP.toFloat()),
                    dp2px(DEFAULT_TEXT_HEIGHT_DP.toFloat())
                )
            }
            itemView.setOnClickListener(View.OnClickListener { v: View? ->
                onItemClickListener.onItemClick(
                    mainItemData
                )
            })
            textTitle.setText(mainItemData.itemTitle)
            textSubTitle.setText(mainItemData.itemSubTitle)
        }
    }

    class FooterViewHolder(view: View) : RecyclerView.ViewHolder(view)
    companion object {
        const val ITEM_TYPE_ITEM: Int = 0
        const val ITEM_TYPE_FOOTER: Int = 1
        const val ITEM_TYPE_WEB_VIEW: Int = 2
        private const val ENGLISH_LANGUAGE_CODE = "en"
        private const val ENGLISH_TEXT_WIDTH_DP = 36
        private const val ENGLISH_TEXT_HEIGHT_DP = 18
        private const val DEFAULT_TEXT_WIDTH_DP = 32
        private const val DEFAULT_TEXT_HEIGHT_DP = 18
        private fun dp2px(dp: Float): Int {
            return TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                dp,
                Resources.getSystem().displayMetrics
            )
                .toInt()
        }
    }
}