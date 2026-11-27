package io.trtc.tuikit.chat.uikit.components.messageinput.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.trtc.tuikit.chat.uikit.components.theme.LocalTheme
import io.trtc.tuikit.chat.uikit.compose.R
import io.trtc.tuikit.chat.uikit.components.messageinput.data.MessageInputMenuAction


@Composable
fun ActionsDialog(
    isVisible: Boolean,
    actions: List<MessageInputMenuAction>,
    onDismiss: () -> Unit,
    onActionClick: (MessageInputMenuAction) -> Unit
) {
    if (isVisible) {
        val colors = LocalTheme.current.colors

        Dialog(
            onDismissRequest = onDismiss,
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { onDismiss() }
                    .padding(16.dp),
                verticalArrangement = Arrangement.Bottom
            ) {
                LazyColumn(
                    modifier = Modifier
                        .background(
                            color = colors.bgColorBubbleReciprocal,
                            shape = RoundedCornerShape(16.dp)
                        )
                        .clip(RoundedCornerShape(16.dp))
                        .fillMaxWidth(),
                ) {
                    itemsIndexed(actions) { index, action ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onActionClick(action)
                                    onDismiss()
                                }) {
                            Row(
                                Modifier.padding(vertical = 16.dp, horizontal = 16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    modifier = Modifier.size(30.dp),
                                    painter = painterResource(action.iconResID),
                                    tint = if (action.isEnabled) colors.textColorSecondary else colors.textColorDisable,
                                    contentDescription = action.title
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = action.title,
                                    color = if (action.isEnabled) colors.textColorLink else colors.textColorDisable,
                                    fontSize = 17.sp
                                )
                            }
                            if (index != actions.lastIndex) {
                                HorizontalDivider(
                                    thickness = 0.5.dp,
                                    color = colors.strokeColorPrimary
                                )
                            }
                        }
                    }
                }
                Box(
                    Modifier
                        .padding(top = 20.dp)
                        .background(
                            color = colors.bgColorOperate,
                            shape = RoundedCornerShape(16.dp)
                        )
                        .clip(RoundedCornerShape(16.dp))
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.message_input_cancel),
                        color = colors.textColorLink,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.W600,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .clickable { onDismiss() }
                            .padding(vertical = 16.dp, horizontal = 16.dp)
                            .fillMaxWidth()
                    )
                }
            }
        }
    }
}
