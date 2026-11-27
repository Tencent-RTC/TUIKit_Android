package io.trtc.tuikit.chat.uikit.components.chatsetting.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.trtc.tuikit.chat.uikit.compose.R
import io.trtc.tuikit.chat.uikit.components.widgets.Switch
import io.trtc.tuikit.chat.uikit.components.widgets.SwitchSize
import io.trtc.tuikit.chat.uikit.components.theme.LocalTheme
import io.trtc.tuikit.chat.uikit.components.common.uicustom.EditorContext
import io.trtc.tuikit.chat.uikit.components.chatsetting.model.ChatSettingCustomItem

enum class SettingRowButtonStyle {
    NORMAL,
    DANGER,
    LINK
}

@Composable
fun SettingRowNavigate(
    title: String,
    modifier: Modifier = Modifier,
    value: String = "",
    showArrow: Boolean = true,
    @DrawableRes customAccessoryResId: Int? = null,
    onClick: (() -> Unit)? = null
) {
    val colors = LocalTheme.current.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .then(
                if (onClick != null) {
                    Modifier.clickable { onClick() }
                } else {
                    Modifier
                }
            )
            .background(color = colors.bgColorOperate)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            fontSize = 16.sp,
            fontWeight = FontWeight.W400,
            color = colors.textColorSecondary,
            maxLines = 1
        )

        Text(
            modifier = Modifier
                .weight(1f)
                .padding(start = 16.dp),
            textAlign = TextAlign.End,
            text = value,
            fontSize = 16.sp,
            fontWeight = FontWeight.W400,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = colors.textColorPrimary
        )

        val accessoryResId = customAccessoryResId
            ?: if (showArrow) R.drawable.chat_setting_ic_arrow_right else null
        if (accessoryResId != null) {
            Icon(
                painter = painterResource(accessoryResId),
                contentDescription = null,
                tint = if (customAccessoryResId != null) colors.textColorPrimary else colors.textColorTertiary,
                modifier = Modifier
                    .padding(start = 8.dp)
                    .size(14.dp)
            )
        }
    }
}

@Composable
fun SettingRowToggle(
    title: String,
    checked: Boolean,
    modifier: Modifier = Modifier,
    onCheckedChange: (Boolean) -> Unit = {}
) {
    val colors = LocalTheme.current.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(color = colors.bgColorOperate)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            fontSize = 16.sp,
            fontWeight = FontWeight.W400,
            color = colors.textColorSecondary,
            maxLines = 1,
            modifier = Modifier.weight(1f)
        )

        Switch(
            checked = checked,
            size = SwitchSize.L,
            onCheckedChange = onCheckedChange
        )
    }
}

@Composable
fun SettingRowButton(
    title: String,
    modifier: Modifier = Modifier,
    style: SettingRowButtonStyle = SettingRowButtonStyle.NORMAL,
    onClick: () -> Unit = {}
) {
    val colors = LocalTheme.current.colors
    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable { onClick() }
            .background(color = colors.bgColorOperate)
            .padding(vertical = 15.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = title,
            fontSize = 17.sp,
            fontWeight = FontWeight.W400,
            textAlign = TextAlign.Center,
            color = when (style) {
                SettingRowButtonStyle.DANGER -> colors.textColorError
                SettingRowButtonStyle.LINK -> colors.textColorLink
                SettingRowButtonStyle.NORMAL -> colors.textColorPrimary
            }
        )
    }
}

@Composable
fun <C : EditorContext> ChatSettingItemColumn(
    itemContext: C,
    items: List<ChatSettingCustomItem<C>>,
    modifier: Modifier = Modifier,
    itemAvailability: Map<String, Boolean> = emptyMap()
) {
    val colors = LocalTheme.current.colors
    val dividerHeight = with(LocalDensity.current) { maxOf(0.5.dp, 1.toDp()) }
    val visibleItems = items.filter { itemAvailability[it.ID] != false }

    Column(modifier = modifier.fillMaxWidth()) {
        var index = 0
        var blockIndex = 0
        while (index < visibleItems.size) {
            if (blockIndex > 0) {
                Spacer(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(10.dp)
                        .background(colors.bgColorInput)
                )
            }

            val current = visibleItems[index]
            val sectionID = current.sectionID
            if (sectionID == null) {
                current.contentFactory(itemContext)
                index++
                blockIndex++
                continue
            }

            val sectionItems = mutableListOf<ChatSettingCustomItem<C>>()
            while (index < visibleItems.size && visibleItems[index].sectionID == sectionID) {
                sectionItems.add(visibleItems[index])
                index++
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.bgColorOperate)
            ) {
                sectionItems.forEachIndexed { sectionItemIndex, sectionItem ->
                    sectionItem.contentFactory(itemContext)
                    if (sectionItemIndex != sectionItems.lastIndex) {
                        Spacer(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(dividerHeight)
                                .background(colors.strokeColorPrimary)
                        )
                    }
                }
            }
            blockIndex++
        }
    }
}

@Composable
fun ChatSettingCheckbox(
    checked: Boolean,
    modifier: Modifier = Modifier,
    onCheckedChange: (Boolean) -> Unit,
) {
    val colors = LocalTheme.current.colors
    Box(
        modifier = modifier
            .size(16.dp)
            .clickable { onCheckedChange(!checked) },
        contentAlignment = Alignment.Center
    ) {
        if (checked) {
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .background(
                        color = colors.textColorLink,
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Checked",
                    tint = colors.textColorButton,
                    modifier = Modifier.size(12.dp)
                )
            }
        } else {
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .border(
                        width = 1.dp,
                        color = colors.scrollbarColorDefault,
                        shape = CircleShape
                    )
            )
        }
    }
}

@Composable
fun ChatSettingDialogTopBar(
    title: String,
    modifier: Modifier = Modifier,
    showConfirm: Boolean = false,
    confirmEnabled: Boolean = true,
    confirmText: String? = null,
    dividerColor: Color? = null,
    onBackClick: () -> Unit = {},
    onConfirmClick: () -> Unit = {}
) {
    val colors = LocalTheme.current.colors
    val topBarDividerColor = dividerColor ?: colors.strokeColorSecondary
    Column(modifier = modifier) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .background(colors.bgColorOperate)
                .padding(horizontal = 16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .clickable { onBackClick() }
            ) {
                Icon(
                    painter = painterResource(R.drawable.uikit_ic_back),
                    contentDescription = null,
                    tint = colors.textColorSecondary,
                    modifier = Modifier.size(16.dp)
                )
            }

            Text(
                text = title,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = colors.textColorPrimary,
                modifier = Modifier.align(Alignment.Center)
            )

            if (showConfirm) {
                Text(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .then(
                            if (confirmEnabled) {
                                Modifier.clickable { onConfirmClick() }
                            } else {
                                Modifier
                            }
                        ),
                    text = confirmText ?: "",
                    fontSize = 16.sp,
                    color = if (confirmEnabled) colors.textColorLink else colors.textColorDisable
                )
            }
        }

        Spacer(
            modifier = Modifier
                .fillMaxWidth()
                .height(0.5.dp)
                .background(topBarDividerColor)
        )
    }
}
