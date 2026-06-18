package com.tencent.rtcube.v2.login.components.views

import android.content.Context
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.os.ConfigurationCompat
import com.tencent.rtcube.v2.login.R

@Composable
fun LoginTopBackground(
    modifier: Modifier = Modifier,
    onLogoHiddenTrigger: (() -> Unit)? = null,
) {
    var lastClickMillis by remember { mutableLongStateOf(0L) }
    var clickCount by remember { mutableIntStateOf(0) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(200.dp)
    ) {
        Image(
            painter = painterResource(id = R.drawable.login_bg_login_top),
            contentDescription = null,
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp),
            contentScale = ContentScale.FillBounds
        )
        val logoModifier = Modifier
            .width(213.dp)
            .height(80.dp)
            .align(Alignment.BottomCenter)
            .padding(bottom = 20.dp)
            .let { base ->
                if (onLogoHiddenTrigger != null) {
                    base.clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() }
                    ) {
                        val now = System.currentTimeMillis()
                        clickCount = if (now - lastClickMillis > 2000L) 1 else clickCount + 1
                        lastClickMillis = now
                        if (clickCount >= 5) {
                            clickCount = 0
                            onLogoHiddenTrigger.invoke()
                        }
                    }
                } else base
            }
        Image(
            painter = painterResource(id = resolveLogoRes(LocalContext.current)),
            contentDescription = null,
            modifier = logoModifier
        )
    }
}

@Composable
fun OtherLoginMethodsDivider(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        HorizontalDivider(
            modifier = Modifier.weight(1f),
            color = Color(0xFFF0F2F7),
            thickness = 1.dp
        )
        Text(
            text = stringResource(R.string.login_other_methods_divider),
            color = Color(0x8C000000),
            fontSize = 12.sp,
            modifier = Modifier.padding(horizontal = 10.dp)
        )
        HorizontalDivider(
            modifier = Modifier.weight(1f),
            color = Color(0xFFF0F2F7),
            thickness = 1.dp
        )
    }
}

@Composable
fun IOALoginButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Image(
        painter = painterResource(id = R.drawable.login_ic_ioa),
        contentDescription = stringResource(R.string.login_menu_ioa),
        modifier = modifier
            .size(40.dp)
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            ) { onClick() }
    )
}

private fun resolveLogoRes(context: Context): Int {
    val language = ConfigurationCompat.getLocales(context.resources.configuration)[0]?.language
    return if (language == "zh") {
        R.drawable.login_title_app
    } else {
        R.drawable.login_title_logo_en
    }
}
