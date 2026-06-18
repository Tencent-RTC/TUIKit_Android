package com.tencent.rtcube.v2.main.overseas.views

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.tencent.rtcube.v2.R
import com.tencent.rtcube.v2.main.model.ResolvedModule

@Composable
fun OverSeasDiscoveryCard(
    module: ResolvedModule,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val config = module.config

    Surface(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .padding(5.dp),
        shape = RoundedCornerShape(10.dp),
        color = Color.White,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                DiscoveryModuleIcon(
                    iconName = config.iconName,
                    iconResId = config.iconResId,
                    iconUrl = config.iconUrl,
                    modifier = Modifier.size(24.dp),
                )
                Spacer(modifier = Modifier.width(5.dp))
                Text(
                    text = config.title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    painter = painterResource(id = R.drawable.main_entrance_pusharrow),
                    contentDescription = null,
                    tint = Color(0xFFBCC4D0),
                    modifier = Modifier.size(16.dp),
                )
            }
            Text(
                text = config.description,
                fontSize = 12.sp,
                color = Color(0xFF777777),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun DiscoveryModuleIcon(
    iconName: String,
    iconResId: Int,
    iconUrl: String,
    modifier: Modifier = Modifier,
) {
    when {
        iconUrl.isNotEmpty() -> {
            AsyncImage(
                model = iconUrl,
                contentDescription = null,
                modifier = modifier,
            )
        }

        iconResId != 0 -> {
            Image(
                painter = painterResource(id = iconResId),
                contentDescription = null,
                modifier = modifier,
            )
        }

        iconName.isNotEmpty() -> {
            val resId = runCatching {
                val context = LocalContext.current
                context.resources.getIdentifier(iconName, "drawable", context.packageName)
            }.getOrDefault(0)
            if (resId != 0) {
                Image(
                    painter = painterResource(id = resId),
                    contentDescription = null,
                    modifier = modifier,
                )
            } else {
                Box(modifier = modifier)
            }
        }

        else -> Box(modifier = modifier)
    }
}
