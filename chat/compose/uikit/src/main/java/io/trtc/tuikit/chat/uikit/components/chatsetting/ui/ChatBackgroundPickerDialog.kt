package io.trtc.tuikit.chat.uikit.components.chatsetting.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import io.trtc.tuikit.chat.uikit.compose.R
import io.trtc.tuikit.chat.uikit.components.theme.LocalTheme
import io.trtc.tuikit.chat.uikit.components.chatsetting.background.ChatBackgroundPresetItem
import io.trtc.tuikit.chat.uikit.components.chatsetting.background.ChatBackgroundPresetProvider

@Composable
fun ChatBackgroundPickerDialog(
    isVisible: Boolean,
    selectedImageUri: String?,
    onDismiss: () -> Unit,
    onBackgroundSelected: (String?) -> Unit
) {
    val colors = LocalTheme.current.colors
    if (isVisible) {
        Dialog(
            onDismissRequest = onDismiss,
            properties = DialogProperties(
                dismissOnBackPress = true,
                dismissOnClickOutside = true,
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = false
            )
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(colors.bgColorMask)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { onDismiss() },
                contentAlignment = Alignment.BottomCenter
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(0.65f)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {},
                    shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
                    color = colors.bgColorOperate
                ) {
                    Column(modifier = Modifier.fillMaxSize().navigationBarsPadding()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 14.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.chat_setting_select_chat_background),
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.textColorPrimary,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Box(
                                modifier = Modifier
                                    .align(Alignment.CenterEnd)
                                    .size(48.dp)
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null
                                    ) { onDismiss() },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "×",
                                    fontSize = 20.sp,
                                    color = colors.textColorSecondary,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }

                        LazyVerticalGrid(
                            columns = GridCells.Fixed(2),
                            contentPadding = PaddingValues(12.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(ChatBackgroundPresetProvider.getPresetItems()) { item ->
                                ChatBackgroundGridItem(
                                    item = item,
                                    isSelected = item.imageUri == selectedImageUri ||
                                        (item.isDefault && selectedImageUri.isNullOrBlank()),
                                    onClick = {
                                        onBackgroundSelected(item.imageUri)
                                        onDismiss()
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChatBackgroundGridItem(
    item: ChatBackgroundPresetItem,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val colors = LocalTheme.current.colors
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp)
            .height(120.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(colors.bgColorTopBar)
            .then(
                if (isSelected) {
                    Modifier.border(
                        width = 2.dp,
                        color = colors.buttonColorPrimaryDefault,
                        shape = RoundedCornerShape(8.dp)
                    )
                } else {
                    Modifier
                }
            )
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        if (item.isDefault) {
            Text(
                text = stringResource(R.string.chat_setting_chat_background_default),
                fontSize = 14.sp,
                color = colors.textColorSecondary
            )
        } else {
            AsyncImage(
                model = item.thumbnailUri,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}
