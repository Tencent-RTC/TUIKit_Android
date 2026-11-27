package io.trtc.tuikit.chat.demo.login

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.Keep
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.tencent.mmkv.MMKV
import io.trtc.tuikit.atomicx.theme.ThemeStore
import io.trtc.tuikit.chat.app.R
import io.trtc.tuikit.chat.demo.common.AppConstants
import io.trtc.tuikit.chat.demo.common.BaseActivity
import java.io.InputStreamReader

class TelephoneAreaCodeSelectorActivity : BaseActivity() {

    override val requiresLogin: Boolean = false

    private lateinit var adapter: AreaCodeAdapter
    private val allData = mutableListOf<TelephoneAreaCode>()
    private val language by lazy {
        MMKV.defaultMMKV().decodeString(AppConstants.KEY_APP_LANGUAGE, "").orEmpty()
            .ifBlank { java.util.Locale.getDefault().language }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.demo_activity_telephone_area_code_selector)

        val colors = ThemeStore.shared(this).themeState.value.currentTheme.tokens.color
        findViewById<View>(R.id.demo_areaCodeRoot).setBackgroundColor(colors.bgColorOperate)
        findViewById<TextView>(R.id.demo_tvTitle).setTextColor(colors.textColorPrimary)
        val searchEdit = findViewById<EditText>(R.id.demo_searchEdit)
        searchEdit.setTextColor(colors.textColorPrimary)
        searchEdit.setHintTextColor(colors.textColorTertiary)

        findViewById<ImageView>(R.id.demo_btnBack).setOnClickListener { finish() }

        adapter = AreaCodeAdapter(language) { areaCode ->
            val intent = Intent()
            intent.putExtra("areaCodeName", areaCode.displayName(language))
            intent.putExtra("areaCode", areaCode.code)
            setResult(RESULT_OK, intent)
            finish()
        }

        val list = findViewById<RecyclerView>(R.id.demo_areaCodeList)
        list.layoutManager = LinearLayoutManager(this)
        list.adapter = adapter

        loadDataAsync()

        searchEdit.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val keyword = s?.toString()?.trim().orEmpty()
                if (keyword.isEmpty()) {
                    adapter.submit(allData)
                } else {
                    adapter.submit(allData.filter {
                        it.displayName(language).contains(keyword, ignoreCase = true) ||
                            it.code.contains(keyword) ||
                            it.codeName.contains(keyword, ignoreCase = true)
                    })
                }
            }
        })
    }

    private fun loadDataAsync() {
        Thread {
            val parsed = parseAreaCodes()
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                allData.clear()
                allData.addAll(parsed)
                adapter.submit(allData)
            }
        }.start()
    }

    private fun parseAreaCodes(): List<TelephoneAreaCode> {
        return try {
            assets.open("telephone_area_code.json").use { input ->
                val type = TypeToken.getParameterized(
                    MutableList::class.java, TelephoneAreaCode::class.java
                ).type
                Gson().fromJson<List<TelephoneAreaCode>>(InputStreamReader(input), type) ?: emptyList()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    private class AreaCodeAdapter(
        private val language: String,
        private val onClick: (TelephoneAreaCode) -> Unit
    ) : RecyclerView.Adapter<AreaCodeAdapter.Holder>() {

        private val data = mutableListOf<TelephoneAreaCode>()

        fun submit(items: List<TelephoneAreaCode>) {
            data.clear()
            data.addAll(items)
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.demo_telephone_area_item, parent, false)
            return Holder(view)
        }

        override fun onBindViewHolder(holder: Holder, position: Int) {
            val item = data[position]
            val colors = ThemeStore.shared(holder.itemView.context)
                .themeState.value.currentTheme.tokens.color
            holder.name.text = item.displayName(language)
            holder.name.setTextColor(colors.textColorPrimary)
            holder.code.text = "+" + item.code
            holder.code.setTextColor(colors.textColorSecondary)
            holder.itemView.setOnClickListener { onClick(item) }
        }

        override fun getItemCount(): Int = data.size

        class Holder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val name: TextView = itemView.findViewById(R.id.demo_name)
            val code: TextView = itemView.findViewById(R.id.demo_code)
        }
    }
}

@Keep
data class TelephoneAreaCode(
    val name: String = "",
    val chineseName: String = "",
    val arabicName: String = "",
    val codeName: String = "",
    val code: String = ""
) {
    fun displayName(language: String): String {
        return when {
            language.startsWith("zh", ignoreCase = true) -> chineseName.ifEmpty { name }
            language.startsWith("ar", ignoreCase = true) -> arabicName.ifEmpty { name }
            else -> name
        }
    }
}
