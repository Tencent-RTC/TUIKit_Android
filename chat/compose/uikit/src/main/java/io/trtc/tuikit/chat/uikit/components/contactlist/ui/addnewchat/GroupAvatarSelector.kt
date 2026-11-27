package io.trtc.tuikit.chat.uikit.components.contactlist.ui.addnewchat

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.trtc.tuikit.chat.uikit.compose.R
import io.trtc.tuikit.chat.uikit.components.widgets.Avatar
import io.trtc.tuikit.chat.uikit.components.widgets.AvatarContent
import io.trtc.tuikit.chat.uikit.components.widgets.AvatarShape
import io.trtc.tuikit.chat.uikit.components.widgets.AvatarSize
import io.trtc.tuikit.chat.uikit.components.theme.LocalTheme
import io.trtc.tuikit.chat.uikit.components.contactlist.viewmodel.getGroupAvatarUrls

private const val GRID_ROWS = 2

@Composable
internal fun GroupAvatarSelector(
    displayedGroupName: String,
    selectedAvatarUrl: String?,
    onAvatarSelected: (String?) -> Unit,
    modifier: Modifier = Modifier,
    avatarUrls: List<String> = getGroupAvatarUrls()
) {
    val colors = LocalTheme.current.colors

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.bgColorTopBar)
    ) {
        Text(
            text = stringResource(R.string.contact_list_group_avatar_text),
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = colors.textColorPrimary,
            modifier = Modifier.padding(
                start = 16.dp,
                top = 12.dp,
                end = 16.dp,
                bottom = 8.dp
            )
        )

        LazyHorizontalGrid(
            rows = GridCells.Fixed(GRID_ROWS),
            modifier = Modifier
                .fillMaxWidth()
                .height((AvatarSize.L.size * GRID_ROWS) + 20.dp)
                .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            item(key = "default_avatar") {
                SelectableAvatar(
                    isSelected = selectedAvatarUrl == null,
                    content = AvatarContent.Text(
                        displayedGroupName.ifBlank {
                            stringResource(R.string.contact_list_group_name)
                        }
                    ),
                    onClick = { onAvatarSelected(null) }
                )
            }
            items(avatarUrls, key = { it }) { url ->
                SelectableAvatar(
                    isSelected = selectedAvatarUrl == url,
                    content = AvatarContent.Image(url, displayedGroupName),
                    onClick = { onAvatarSelected(url) }
                )
            }
        }
    }
}

@Composable
private fun SelectableAvatar(
    isSelected: Boolean,
    content: AvatarContent,
    onClick: () -> Unit
) {
    val colors = LocalTheme.current.colors

    Avatar(
        modifier = Modifier.then(
            if (isSelected) {
                Modifier.border(
                    width = 2.dp,
                    color = colors.textColorLink,
                    shape = RoundedCornerShape(8.dp)
                )
            } else {
                Modifier
            }
        ),
        content = content,
        size = AvatarSize.L,
        shape = AvatarShape.RoundRectangle,
        onClick = onClick
    )
}
