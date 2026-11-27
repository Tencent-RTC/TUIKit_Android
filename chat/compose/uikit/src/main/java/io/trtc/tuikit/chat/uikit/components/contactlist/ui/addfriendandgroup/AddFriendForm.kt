package io.trtc.tuikit.chat.uikit.components.contactlist.ui.addfriendandgroup

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.trtc.tuikit.chat.uikit.compose.R
import io.trtc.tuikit.chat.uikit.components.theme.LocalTheme
import io.trtc.tuikit.chat.uikit.components.contactlist.viewmodel.AddType
import io.trtc.tuikit.atomicxcore.api.contact.ContactInfo

@Composable
fun AddFriendForm(
    contactInfo: ContactInfo,
    wording: String,
    remark: String,
    isAdding: Boolean,
    onWordingChange: (String) -> Unit,
    onRemarkChange: (String) -> Unit,
    onSend: (wording: String, remark: String) -> Unit
) {
    val colors = LocalTheme.current.colors
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.bgColorDefault)
            .verticalScroll(rememberScrollState())
    ) {
        AddContactInfoCard(addType = AddType.CONTACT, result = contactInfo)

        AddContactSectionTitle(text = stringResource(R.string.contact_list_fill_validation_message))

        AddContactMultilineInputCard(
            value = wording,
            onValueChange = onWordingChange
        )

        AddContactSectionSpacer()

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .background(colors.bgColorOperate)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.contact_list_remark),
                color = colors.textColorPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.W400
            )

            Spacer(modifier = Modifier.width(16.dp))

            BasicTextField(
                value = remark,
                onValueChange = onRemarkChange,
                modifier = Modifier.weight(1f),
                textStyle = TextStyle(
                    color = colors.textColorPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.W400,
                    textAlign = TextAlign.End
                ),
                cursorBrush = SolidColor(colors.textColorLink),
                singleLine = true
            )
        }

        AddContactSectionSpacer()

        AddContactActionCard(
            text = stringResource(R.string.contact_list_send),
            enabled = !isAdding,
            onClick = { onSend(wording, remark) }
        )
    }
}
