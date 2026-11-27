package io.trtc.tuikit.chat.login

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.annotation.Keep
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.tencent.mmkv.MMKV
import io.trtc.tuikit.chat.uikit.components.theme.LocalTheme
import io.trtc.tuikit.chat.BaseActivity
import io.trtc.tuikit.chat.R
import io.trtc.tuikit.chat.common.AppConstants
import io.trtc.tuikit.chat.uikit.compose.R as UiKitR
import java.io.InputStreamReader
import java.util.Locale
import kotlin.concurrent.thread

class TelephoneAreaCodeSelectorActivity : BaseActivity() {

    override val requiresLogin: Boolean = false

    private var items by mutableStateOf<List<TelephoneAreaCode>>(emptyList())
    private var keyword by mutableStateOf("")

    private val language: String
        get() = MMKV.defaultMMKV().decodeString(AppConstants.KEY_APP_LANGUAGE, "").orEmpty()
            .ifBlank { Locale.getDefault().language }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AreaCodeScreen()
        }
        loadDataAsync()
    }

    @Composable
    private fun AreaCodeScreen() {
        val colors = LocalTheme.current.colors
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(colors.bgColorOperate)
                .statusBarsPadding()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    painter = painterResource(UiKitR.drawable.uikit_ic_back),
                    contentDescription = stringResource(R.string.compose_demo_back),
                    tint = colors.textColorPrimary,
                    modifier = Modifier
                        .size(16.dp)
                        .clickable { finish() }
                )
                Text(
                    text = stringResource(R.string.compose_demo_area_code_title),
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.textColorPrimary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )
                Box(modifier = Modifier.size(16.dp))
            }

            BasicTextField(
                value = keyword,
                onValueChange = { keyword = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 8.dp)
                    .height(40.dp),
                textStyle = TextStyle(
                    fontSize = 15.sp,
                    color = colors.textColorPrimary
                ),
                singleLine = true,
                cursorBrush = SolidColor(colors.textColorLink),
                decorationBox = { innerTextField ->
                    Box(
                        modifier = Modifier.padding(horizontal = 8.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        if (keyword.isEmpty()) {
                            Text(
                                text = stringResource(R.string.compose_demo_area_code_search_hint),
                                fontSize = 15.sp,
                                color = colors.textColorTertiary
                            )
                        }
                        innerTextField()
                    }
                }
            )

            val filtered = remember(items, keyword, language) {
                val key = keyword.trim()
                if (key.isEmpty()) {
                    items
                } else {
                    items.filter {
                        it.displayName(language).contains(key, ignoreCase = true) ||
                            it.code.contains(key) ||
                            it.codeName.contains(key, ignoreCase = true)
                    }
                }
            }

            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(filtered) { item ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .clickable { onAreaCodeSelected(item) }
                            .padding(horizontal = 24.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = item.displayName(language),
                            fontSize = 16.sp,
                            color = colors.textColorPrimary,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = "+${item.code}",
                            fontSize = 16.sp,
                            color = colors.textColorSecondary
                        )
                    }
                }
            }
        }
    }

    private fun onAreaCodeSelected(areaCode: TelephoneAreaCode) {
        val intent = Intent()
        intent.putExtra("areaCodeName", areaCode.displayName(language))
        intent.putExtra("areaCode", areaCode.code)
        setResult(RESULT_OK, intent)
        finish()
    }

    private fun loadDataAsync() {
        thread {
            val parsed = parseAreaCodes()
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                items = parsed
            }
        }
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
