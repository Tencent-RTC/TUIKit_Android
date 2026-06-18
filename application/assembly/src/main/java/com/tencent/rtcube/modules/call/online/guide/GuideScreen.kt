package com.tencent.rtcube.modules.call.online.guide

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.os.ConfigurationCompat
import com.tencent.rtcube.modules.R
import org.json.JSONArray

enum class GuidePageType {
    SINGLE_PLAYER,
    MULTI_PLAYER_WITH_WEB,
    MULTI_PLAYER_WITH_APP,
}

@Composable
fun GuideScreen(
    pageType: GuidePageType,
    homeModel: GuideHomeModel,
    copyUrl: String,
    copyUrlEn: String,
) {
    val context = LocalContext.current
    val isZh = ConfigurationCompat.getLocales(context.resources.configuration)[0]?.language == "zh"

    // Currently selected multi-player type (Web / App)
    var currentPageType by remember { mutableStateOf(pageType) }

    // Load step data from JSON
    val guideItems by remember(currentPageType) {
        derivedStateOf { loadGuideItems(context, homeModel, currentPageType, isZh) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White),
    ) {
        val reminderText = if (currentPageType == GuidePageType.SINGLE_PLAYER) {
            stringResource(R.string.assembly_call_guide_reminder_single)
        } else {
            stringResource(R.string.assembly_call_guide_reminder_multi)
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(colorResource(R.color.call_color_call_tip_bg))
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "⚠",
                fontSize = 12.sp,
                color = colorResource(R.color.call_color_call_tip_text),
                modifier = Modifier.padding(end = 6.dp),
            )
            Text(
                text = reminderText,
                fontSize = 12.sp,
                color = colorResource(R.color.call_color_call_tip_text),
            )
        }

        if (currentPageType != GuidePageType.SINGLE_PLAYER) {
            GuideTypeSelector(
                currentType = currentPageType,
                onTypeSelected = { currentPageType = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            )
            HorizontalDivider(color = Color(0xFFF0F0F0), thickness = 1.dp)
        }

        Text(
            text = stringResource(R.string.assembly_call_guide_steps),
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            color = Color(0xFF262B32),
            modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 8.dp),
        )

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 10.dp),
        ) {
            items(guideItems) { item ->
                GuideStepItem(
                    model = item,
                    onCopyClick = {
                        val urlToCopy = if (isZh) copyUrl else copyUrlEn
                        copyToClipboard(context, urlToCopy)
                        Toast.makeText(
                            context,
                            context.getString(R.string.assembly_call_guide_copy_success),
                            Toast.LENGTH_SHORT,
                        ).show()
                    },
                )
            }
        }
    }
}

@Composable
private fun GuideTypeSelector(
    currentType: GuidePageType,
    onTypeSelected: (GuidePageType) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }

    val options = listOf(
        stringResource(R.string.assembly_call_guide_others_use_web) to GuidePageType.MULTI_PLAYER_WITH_WEB,
        stringResource(R.string.assembly_call_guide_others_use_app) to GuidePageType.MULTI_PLAYER_WITH_APP,
    )
    val currentLabel = options.firstOrNull { it.second == currentType }?.first
        ?: options[0].first

    Box(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
                .clip(RoundedCornerShape(8.dp))
                .border(1.dp, Color(0xFFDDE2EB), RoundedCornerShape(8.dp))
                .clickable { expanded = true }
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = currentLabel,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFF22262E),
            )
            Text(
                text = if (expanded) "∧" else "∨",
                fontSize = 14.sp,
                color = Color(0xFF8A9099),
            )
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .background(Color.White),
        ) {
            options.forEachIndexed { index, (label, type) ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = label,
                            fontSize = 15.sp,
                            color = if (type == currentType) Color(0xFF006CFF) else Color(0xFF22262E),
                            fontWeight = if (type == currentType) FontWeight.Medium else FontWeight.Normal,
                        )
                    },
                    onClick = {
                        onTypeSelected(type)
                        expanded = false
                    },
                )
                if (index < options.size - 1) {
                    HorizontalDivider(color = Color(0xFFF0F0F0), thickness = 1.dp)
                }
            }
        }
    }
}

private fun loadGuideItems(
    context: Context,
    homeModel: GuideHomeModel,
    pageType: GuidePageType,
    isZh: Boolean,
): List<GuideModel> {
    val rawResName = when (pageType) {
        GuidePageType.SINGLE_PLAYER -> homeModel.singlePlayerJsonName ?: "call_guide_single_data"
        GuidePageType.MULTI_PLAYER_WITH_WEB -> homeModel.withWebJsonName ?: "call_guide_with_web_data"
        GuidePageType.MULTI_PLAYER_WITH_APP -> homeModel.withAppJsonName ?: "call_guide_with_app_data"
    }
    val resId = context.resources.getIdentifier(rawResName, "raw", context.packageName)
    if (resId == 0) return emptyList()

    return try {
        val jsonStr = context.resources.openRawResource(resId).bufferedReader().readText()
        val array = JSONArray(jsonStr)
        (0 until array.length()).map { i ->
            val obj = array.getJSONObject(i)
            fun loc(key: String) = if (isZh) obj.optString(key, "") else obj.optString("${key}_en", "")
            GuideModel(
                avatarType = AvatarType.fromValue(obj.optInt("avartarType", 1)),
                avatarImageName = obj.optString("avartarImageName", ""),
                name = loc("name"),
                text = loc("text"),
                hasCopyButton = obj.optBoolean("hasCopyButton", false),
                leftContextImageName = loc("leftContextImageName"),
                rightContextImageName = obj.optString("rightContextImageName", ""),
            )
        }
    } catch (e: Exception) {
        emptyList()
    }
}

private fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("guide_url", text))
}
