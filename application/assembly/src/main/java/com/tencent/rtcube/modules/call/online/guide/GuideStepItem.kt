package com.tencent.rtcube.modules.call.online.guide

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.tencent.rtcube.modules.R

@Composable
fun GuideStepItem(
    model: GuideModel,
    onCopyClick: () -> Unit = {},
) {
    val isRight = model.avatarType == AvatarType.RIGHT

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp),
        horizontalArrangement = if (isRight) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Top,
    ) {
        if (isRight) {
            GuideBubble(
                model = model,
                modifier = Modifier.weight(1f, fill = false),
                onCopyClick = onCopyClick,
            )
            Spacer(modifier = Modifier.width(8.dp))
            GuideAvatar(model = model)
        } else {
            GuideAvatar(model = model)
            Spacer(modifier = Modifier.width(8.dp))
            GuideBubble(
                model = model,
                modifier = Modifier.weight(1f, fill = false),
                onCopyClick = onCopyClick,
            )
        }
    }
}


@Composable
private fun GuideAvatar(model: GuideModel) {
    val context = LocalContext.current
    val resId = context.getDrawableResId(model.avatarImageName)
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        if (resId != 0) {
            AsyncImage(
                model = ImageRequest.Builder(context).data(resId).build(),
                contentDescription = model.name,
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape),
                contentScale = ContentScale.Crop,
            )
        } else {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFE0E0E0)),
                contentAlignment = Alignment.Center,
            ) {
                Text(text = model.name.take(1), fontSize = 14.sp, color = Color.Gray)
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(text = model.name, fontSize = 12.sp, color = Color(0xFF757575))
    }
}

@Composable
private fun GuideBubble(
    model: GuideModel,
    modifier: Modifier = Modifier,
    onCopyClick: () -> Unit,
) {
    val context = LocalContext.current
    val hasExtra = model.leftContextImageName.isNotEmpty() || model.hasCopyButton

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFFF0F4FA))
            .widthIn(max = 280.dp),
    ) {
        Text(
            text = model.text,
            fontSize = 14.sp,
            color = colorResource(R.color.call_color_main_text),
            modifier = Modifier.padding(
                start = 12.dp, end = 12.dp, top = 12.dp,
                bottom = if (hasExtra) 0.dp else 12.dp,
            ),
        )

        val imgResId = context.getDrawableResId(model.leftContextImageName)
        if (imgResId != 0) {
            AsyncImage(
                model = ImageRequest.Builder(context).data(imgResId).build(),
                contentDescription = null,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                contentScale = ContentScale.FillWidth,
            )
        }

        if (model.hasCopyButton) {
            HorizontalDivider(
                color = Color(0xFFDDE2EB),
                thickness = 1.dp,
                modifier = Modifier.padding(horizontal = 12.dp),
            )
            TextButton(
                onClick = onCopyClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp),
            ) {
                Text(
                    text = stringResource(R.string.assembly_call_guide_copy_url),
                    fontSize = 14.sp,
                    color = Color(0xFF0C59F2),
                    fontWeight = FontWeight.Medium,
                )
            }
        } else {
            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

private fun Context.getDrawableResId(name: String): Int {
    if (name.isEmpty()) return 0
    return resources.getIdentifier(name, "drawable", packageName)
}
