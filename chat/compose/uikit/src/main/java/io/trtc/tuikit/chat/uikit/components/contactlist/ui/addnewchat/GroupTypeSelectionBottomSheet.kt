package io.trtc.tuikit.chat.uikit.components.contactlist.ui.addnewchat

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.trtc.tuikit.chat.uikit.compose.R
import io.trtc.tuikit.chat.uikit.components.theme.LocalTheme
import io.trtc.tuikit.chat.uikit.components.contactlist.viewmodel.AddNewChatViewModel
import io.trtc.tuikit.chat.uikit.components.contactlist.viewmodel.GroupTypeOption
import io.trtc.tuikit.chat.uikit.components.contactlist.viewmodel.getGroupTypeOptionList

@Composable
fun GroupTypeSelectionBottomSheet(
    viewModel: AddNewChatViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    onTypeSelected: (GroupTypeOption) -> Unit
) {
    val colors = LocalTheme.current.colors
    val currentSelectedType by viewModel.currentSelectedGroupType.collectAsState()
    val groupTypes = getGroupTypeOptionList()

    Column(modifier = modifier.fillMaxSize()) {
        AddNewChatHeader(
            title = stringResource(R.string.contact_list_group_type_select_text),
            onBack = onBack
        )

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(colors.bgColorTopBar),
            contentPadding = PaddingValues(
                start = 16.dp,
                top = 12.dp,
                end = 16.dp,
                bottom = 12.dp
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(groupTypes, key = { it.type.value }) { groupType ->
                GroupTypeOptionCard(
                    groupType = groupType,
                    isSelected = currentSelectedType.type == groupType.type,
                    onClick = { onTypeSelected(groupType) }
                )
            }
        }
    }
}

@Composable
private fun GroupTypeOptionCard(
    groupType: GroupTypeOption,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val colors = LocalTheme.current.colors
    val cardShape = RoundedCornerShape(12.dp)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(cardShape)
            .background(colors.bgColorOperate)
            .then(
                if (isSelected) {
                    Modifier.border(
                        width = 1.5.dp,
                        color = colors.textColorLink,
                        shape = cardShape
                    )
                } else {
                    Modifier
                }
            )
            .clickable { onClick() }
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(
                painter = painterResource(GroupTypeIconMapper.iconResId(groupType)),
                contentDescription = stringResource(groupType.displayNameResID),
                contentScale = ContentScale.Fit,
                modifier = Modifier.size(40.dp)
            )

            Spacer(modifier = Modifier.width(10.dp))

            Text(
                text = stringResource(groupType.displayNameResID),
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = colors.textColorPrimary
            )
        }

        Text(
            text = stringResource(groupType.descriptionResID),
            fontSize = 13.sp,
            lineHeight = 17.sp,
            color = colors.textColorSecondary,
            modifier = Modifier.padding(top = 10.dp)
        )
    }
}
