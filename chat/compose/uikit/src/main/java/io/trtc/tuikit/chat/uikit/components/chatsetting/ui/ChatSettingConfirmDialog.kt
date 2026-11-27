package io.trtc.tuikit.chat.uikit.components.chatsetting.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.trtc.tuikit.chat.uikit.compose.R
import io.trtc.tuikit.chat.uikit.components.theme.LocalTheme

@Composable
internal fun ChatSettingConfirmDialog(
    isVisible: Boolean,
    message: String?,
    cancelText: String = stringResource(R.string.base_component_cancel),
    confirmText: String = stringResource(R.string.base_component_confirm),
    isConfirmDestructive: Boolean = false,
    onDismiss: () -> Unit,
    onCancel: () -> Unit,
    onConfirm: () -> Unit
) {
    val colors = LocalTheme.current.colors
    if (isVisible) {
        Dialog(
            onDismissRequest = onDismiss,
            properties = DialogProperties(
                dismissOnBackPress = false,
                dismissOnClickOutside = false
            )
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier
                        .width(327.dp)
                        .heightIn(min = 190.dp, max = 480.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(color = colors.bgColorDialog),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    val scrollState = rememberScrollState()
                    Column(
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .fillMaxWidth()
                            .heightIn(min = 134.dp, max = 424.dp)
                            .padding(start = 24.dp, end = 24.dp, top = 32.dp, bottom = 20.dp)
                            .verticalScroll(scrollState),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        message?.takeIf { it.isNotEmpty() }?.let { messageText ->
                            Text(
                                text = messageText,
                                style = TextStyle(
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Normal,
                                    color = colors.textColorSecondary,
                                    lineHeight = 24.sp
                                ),
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = false)
                            .height(56.dp)
                    ) {
                        HorizontalDivider(
                            thickness = 0.5.dp,
                            color = colors.strokeColorSecondary
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = cancelText,
                                style = TextStyle(
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Normal,
                                    color = colors.textColorSecondary,
                                    lineHeight = 24.sp
                                ),
                                textAlign = TextAlign.Center,
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .clickable { onCancel() }
                                    .padding(vertical = 16.dp, horizontal = 20.dp)
                            )

                            VerticalDivider(
                                thickness = 0.5.dp,
                                color = colors.strokeColorSecondary,
                                modifier = Modifier.height(56.dp)
                            )
                            Text(
                                text = confirmText,
                                style = TextStyle(
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Normal,
                                    color = if (isConfirmDestructive) colors.textColorError else colors.textColorLink,
                                    lineHeight = 24.sp
                                ),
                                textAlign = TextAlign.Center,
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .clickable { onConfirm() }
                                    .padding(vertical = 16.dp, horizontal = 20.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
