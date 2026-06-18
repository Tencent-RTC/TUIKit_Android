package com.tencent.rtcube.v2.main.domestic.views

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.paint
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.tencent.rtcube.assembly.EntranceCardStyle
import com.tencent.rtcube.v2.R
import com.tencent.rtcube.v2.main.model.ResolvedModule

@Composable
fun ModuleCard(
    module: ResolvedModule,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val config = module.config
    when (config.cardStyle) {
        EntranceCardStyle.STANDARD -> StandardCard(
            module = module,
            onClick = onClick,
            modifier = modifier,
        )

        EntranceCardStyle.UI_COMPONENT -> UIComponentCard(
            module = module,
            onClick = onClick,
            modifier = modifier,
        )

        EntranceCardStyle.BANNER -> BannerCard(
            module = module,
            onClick = onClick,
            modifier = modifier,
        )
    }
}

@Composable
private fun StandardCard(
    module: ResolvedModule,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BaseEntranceCard(
        module = module,
        onClick = onClick,
        modifier = modifier,
        backgroundBrush = null,
        trailingBadge = if (module.config.isHot) {
            { HotBadge() }
        } else {
            null
        },
    )
}

@Composable
private fun UIComponentCard(
    module: ResolvedModule,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val gradient = module.config.gradientColors.takeIf { it.size >= 2 }?.map { Color(it) }
        ?: listOf(Color(0xFFEEF4FF), Color(0xFFDCEBFF))
    BaseEntranceCard(
        module = module,
        onClick = onClick,
        modifier = modifier,
        backgroundBrush = Brush.verticalGradient(gradient),
        trailingBadge = { UIComponentBadge() },
    )
}

@Composable
private fun BaseEntranceCard(
    module: ResolvedModule,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    backgroundBrush: Brush? = null,
    trailingBadge: (@Composable () -> Unit)? = null,
) {
    val config = module.config
    Surface(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .fillMaxHeight(),
        shape = RoundedCornerShape(6.dp),
        color = if (backgroundBrush != null) Color.Transparent else Color.White,
        shadowElevation = 1.dp,
    ) {
        val contentModifier = Modifier
            .fillMaxSize()
            .let { if (backgroundBrush != null) it.background(backgroundBrush) else it }
            .padding(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 10.dp)
        Column(modifier = contentModifier) {
            Row(
                verticalAlignment = Alignment.Top,
                modifier = Modifier.fillMaxWidth(),
            ) {
                ModuleIcon(
                    iconName = config.iconName,
                    iconResId = config.iconResId,
                    iconUrl = config.iconUrl,
                    modifier = Modifier
                        .padding(top = 2.dp)
                        .size(20.dp),
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = config.title,
                    fontSize = 15.sp,
                    lineHeight = 20.sp,
                    fontWeight = FontWeight.Medium,
                    color = colorResource(R.color.main_card_title),
                    modifier = Modifier.weight(1f),
                )
                if (trailingBadge != null) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Box(modifier = Modifier.padding(top = 2.dp)) { trailingBadge() }
                    Spacer(modifier = Modifier.width(4.dp))
                }
                Icon(
                    painter = painterResource(id = R.drawable.main_entrance_pusharrow),
                    contentDescription = null,
                    tint = colorResource(R.color.main_card_arrow_tint),
                    modifier = Modifier
                        .padding(top = 2.dp)
                        .size(16.dp),
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = config.description,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                color = colorResource(R.color.main_card_description),
            )
        }
    }
}

@Composable
private fun BannerCard(
    module: ResolvedModule,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val config = module.config
    val gradient = config.gradientColors.takeIf { it.size >= 2 }?.map { Color(it) }
        ?: listOf(Color(0xFFEEF4FF), Color(0xFFDCEBFF))

    Surface(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp),
        shape = RoundedCornerShape(6.dp),
        color = Color.Transparent,
        shadowElevation = 1.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.horizontalGradient(gradient))
                .paint(
                    painter = painterResource(id = R.drawable.main_entrance_scenarios),
                    alignment = Alignment.CenterEnd,
                    contentScale = ContentScale.Inside,
                )
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = config.title,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = colorResource(R.color.main_banner_card_title),
                modifier = Modifier.weight(1f),
            )
            if (config.description.isNotEmpty()) {
                Text(
                    text = config.description,
                    fontSize = 13.sp,
                    color = colorResource(R.color.main_card_title),
                )
                Spacer(modifier = Modifier.width(4.dp))
            }
            Icon(
                painter = painterResource(id = R.drawable.main_entrance_pusharrow),
                contentDescription = null,
                tint = colorResource(R.color.main_banner_card_arrow_tint),
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

@Composable
private fun ModuleIcon(
    iconName: String,
    iconResId: Int,
    iconUrl: String,
    modifier: Modifier = Modifier,
) {
    when {
        iconUrl.isNotEmpty() -> AsyncImage(
            model = iconUrl,
            contentDescription = null,
            modifier = modifier,
        )

        iconResId != 0 -> Image(
            painter = painterResource(id = iconResId),
            contentDescription = null,
            modifier = modifier,
        )

        iconName.isNotEmpty() -> {
            val context = LocalContext.current
            val resId = runCatching {
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

@Composable
private fun HotBadge() {
    BadgeText(
        text = stringResource(id = R.string.main_hot_component),
        bgColor = colorResource(R.color.main_tag_hot_bg),
    )
}

@Composable
private fun UIComponentBadge() {
    BadgeText(
        text = stringResource(id = R.string.main_ui_component),
        bgColor = colorResource(R.color.main_tag_ui_component_bg),
    )
}

@Composable
private fun BadgeText(text: String, bgColor: Color) {
    Text(
        text = text,
        fontSize = 10.sp,
        fontWeight = FontWeight.Medium,
        color = Color.White,
        modifier = Modifier
            .clip(RoundedCornerShape(2.dp))
            .background(bgColor)
            .padding(horizontal = 4.dp, vertical = 1.dp),
    )
}
