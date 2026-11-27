package io.trtc.tuikit.chat.uikit.components.messageinput.ui

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp


@Composable
fun InputIconButton(modifier: Modifier = Modifier, onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(
        modifier
            .clickable(
                onClick = onClick, role = Role.Button, indication = LocalIndication.current,
                interactionSource = remember { MutableInteractionSource() })
            .padding(8.dp),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}
