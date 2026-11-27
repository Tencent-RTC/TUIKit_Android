package io.trtc.tuikit.chat.uikit.components.messagelist.ui.widgets

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.trtc.tuikit.chat.uikit.compose.R
import io.trtc.tuikit.chat.uikit.components.theme.LocalTheme

@Composable
fun MultiSelectBottomBar(
    selectedCount: Int,
    onCancel: () -> Unit = {},
    onDelete: () -> Unit = {},
    onForward: () -> Unit = {},
    onForwardSeparate: () -> Unit = onForward,
    onForwardMerge: () -> Unit = onForward
) {
    val colors = LocalTheme.current.colors
    val enabled = selectedCount > 0

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(color = colors.bgColorBottomBar)
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        MultiSelectActionItem(
            iconRes = R.drawable.message_list_multi_forward_separate_icon,
            label = stringResource(R.string.message_list_forward_by_separate),
            enabled = enabled,
            onClick = onForwardSeparate,
            modifier = Modifier.weight(1f)
        )
        MultiSelectActionItem(
            iconRes = R.drawable.message_list_multi_forward_merge_icon,
            label = stringResource(R.string.message_list_forward_by_merge),
            enabled = enabled,
            onClick = onForwardMerge,
            modifier = Modifier.weight(1f)
        )
        MultiSelectActionItem(
            iconRes = R.drawable.message_list_multi_delete_icon,
            label = stringResource(R.string.message_list_multi_select_delete),
            enabled = enabled,
            onClick = onDelete,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun MultiSelectActionItem(
    iconRes: Int,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalTheme.current.colors
    Column(
        modifier = modifier
            .alpha(if (enabled) 1f else 0.4f)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = label,
            tint = colors.textColorSecondary,
            modifier = Modifier.size(40.dp)
        )
        Text(
            text = label,
            fontSize = 12.sp,
            color = colors.textColorSecondary,
            maxLines = 1,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 6.dp)
        )
    }
}
