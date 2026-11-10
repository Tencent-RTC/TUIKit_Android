package com.tencent.uikit.app.setting

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.tencent.uikit.app.R

class LanguageSelectActivity : AppCompatActivity() {
    private var titleTextView: TextView? = null
    private var itemClickListener: OnItemClickListener? = null
    private var currentLanguage: String? = null
    private val languages: MutableList<LanguageItem> = ArrayList()
    private var adapter: SelectAdapter? = null

    @SuppressLint("UseCompatLoadingForDrawables")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.app_activity_language_select)
        initStatusBar()

        titleTextView = findViewById(R.id.tv_language_select_title)
        titleTextView?.text = getString(R.string.app_login_language)
        val imageView = findViewById<ImageView?>(R.id.img_language_select_back)
        imageView?.setOnClickListener { v: View? -> finish() }

        currentLanguage = LocaleManager.getLanguage()
        adapter = SelectAdapter(
            languages = languages
        ) { languageCode -> itemClickListener?.onClick(languageCode) }
        initData()

        val recyclerView = findViewById<RecyclerView?>(R.id.login_recycler_view)
        recyclerView?.setAdapter(adapter)
        recyclerView?.setLayoutManager(LinearLayoutManager(this))
        val dividerItemDecoration = DividerItemDecoration(this, DividerItemDecoration.VERTICAL)
        dividerItemDecoration.setDrawable(getResources().getDrawable(R.drawable.app_core_list_divider))
        recyclerView?.addItemDecoration(dividerItemDecoration)

        itemClickListener = object : OnItemClickListener {
            override fun onClick(language: String?) {
                currentLanguage = language
                val index = if (TextUtils.equals(language, "zh")) 0 else 1
                adapter?.selectedItem = index
                adapter?.notifyDataSetChanged()
                LocaleManager.setLanguage(language ?: LocaleManager.LANGUAGE_EN)
                changeTitleLanguage()
                notifyLanguageChanged()
                finish()
            }
        }
    }

    private fun notifyLanguageChanged() {
        val intent = Intent()
        intent.setAction(LANGUAGE_CHANGED_ACTION)
        intent.putExtra(LANGUAGE, currentLanguage)
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
    }

    private fun changeTitleLanguage() {
        titleTextView?.text = getString(R.string.app_login_language)
    }

    private fun initData() {
        languages.clear()
        languages.add(LanguageItem(label = "简体中文", code = "zh"))
        languages.add(LanguageItem(label = "English", code = "en"))
        val idx = languages.indexOfFirst { TextUtils.equals(currentLanguage, it.code) }
        adapter?.selectedItem = if (idx >= 0) idx else 0
    }

    private data class LanguageItem(val label: String, val code: String)

    private class SelectAdapter(
        private val languages: List<LanguageItem>,
        private val onItemClick: (String?) -> Unit
    ) : RecyclerView.Adapter<SelectAdapter.SelectViewHolder>() {
        var selectedItem: Int = -1

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SelectViewHolder {
            val view: View = LayoutInflater.from(parent.context)
                .inflate(R.layout.app_login_select_item_layout, parent, false)
            return SelectViewHolder(view)
        }

        override fun onBindViewHolder(holder: SelectViewHolder, position: Int) {
            val item = languages[position]
            holder.name.setText(item.label)
            if (selectedItem == position) {
                holder.selectedIcon.setVisibility(View.VISIBLE)
            } else {
                holder.selectedIcon.setVisibility(View.GONE)
            }
            holder.itemView.setOnClickListener { onItemClick(item.code) }
        }

        override fun getItemCount(): Int {
            return languages.size
        }

        class SelectViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            var name: TextView = itemView.findViewById(R.id.name)
            var selectedIcon: ImageView = itemView.findViewById(R.id.selected_icon)
        }
    }

    interface OnItemClickListener {
        fun onClick(language: String?)
    }

    private fun initStatusBar() {
        val window = getWindow()
        window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)
        window.decorView.setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
        )
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
        window.setStatusBarColor(Color.TRANSPARENT)
    }

    companion object {
        const val LANGUAGE: String = "language"
        const val LANGUAGE_CHANGED_ACTION: String = "demoLanguageChangedAction"

        fun startSelectLanguage(activity: Activity) {
            val intent = Intent(activity, LanguageSelectActivity::class.java)
            activity.startActivity(intent)
        }
    }
}