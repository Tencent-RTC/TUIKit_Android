package com.tencent.rtcube.v2.login.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.tencent.rtcube.v2.login.R
import com.tencent.rtcube.v2.login.profile.store.AvatarConstants

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AvatarSelectDialog(
    selectedAvatarUrl: String,
    onAvatarSelect: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colorResource(R.color.login_color_white),
        shape = RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp),
        dragHandle = null,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 32.dp, start = 20.dp, end = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.login_profile_avatar_dialog_title),
                color = colorResource(R.color.login_main_text),
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = stringResource(R.string.login_profile_avatar_dialog_confirm),
                color = colorResource(R.color.login_menu_icon_tint),
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.clickable { onConfirm() },
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        LazyVerticalGrid(
            columns = GridCells.Fixed(4),
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 40.dp),
        ) {
            items(AvatarConstants.USER_AVATAR_ARRAY) { url ->
                AvatarGridItem(
                    avatarUrl = url,
                    isSelected = url == selectedAvatarUrl,
                    onClick = { onAvatarSelect(url) },
                )
            }
        }
    }
}

@Composable
private fun AvatarGridItem(
    avatarUrl: String,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        AsyncImage(
            model = avatarUrl,
            placeholder = painterResource(id = R.drawable.login_ic_head),
            error = painterResource(id = R.drawable.login_ic_head),
            contentDescription = null,
            modifier = Modifier
                .size(68.dp)
                .clip(CircleShape)
                .clickable { onClick() },
            contentScale = ContentScale.Crop,
        )
        if (isSelected) {
            Box(
                modifier = Modifier
                    .size(68.dp)
                    .clip(CircleShape)
                    .background(Color.Transparent)
                    .border(
                        width = 3.dp,
                        color = colorResource(R.color.login_menu_icon_tint),
                        shape = CircleShape,
                    ),
            )
        }
    }
}
