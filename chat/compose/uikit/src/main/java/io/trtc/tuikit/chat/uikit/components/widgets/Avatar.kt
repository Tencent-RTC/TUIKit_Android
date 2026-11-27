package io.trtc.tuikit.chat.uikit.components.widgets

import androidx.annotation.DrawableRes
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import io.trtc.tuikit.chat.uikit.compose.R
import io.trtc.tuikit.chat.uikit.components.config.AppBuilderConfig
import io.trtc.tuikit.chat.uikit.components.config.GlobalAvatarShape
import io.trtc.tuikit.chat.uikit.components.theme.Colors
import io.trtc.tuikit.chat.uikit.components.theme.LocalTheme
import java.util.Locale

private val supportedAvatarImageUrlSchemes = setOf("http", "https", "content", "file", "android.resource")

private const val INVALID_IMAGE_URL_CACHE_MAX_SIZE = 100

private val invalidImageUrlCache = LinkedHashSet<String>()

internal fun normalizeAvatarImageUrlKey(url: Any?): String? {
    return when (url) {
        is String -> {
            val trimmedUrl = url.trim()
            trimmedUrl.takeIf { it.isNotEmpty() && hasSupportedAvatarImageUrlScheme(it) }
        }
        null -> null
        else -> url.toString().takeIf { it.isNotEmpty() }
    }
}

private fun hasSupportedAvatarImageUrlScheme(url: String): Boolean {
    val colonIndex = url.indexOf(':')
    if (colonIndex <= 0 || url.length <= colonIndex + 2) return false
    if (url[colonIndex + 1] != '/' || url[colonIndex + 2] != '/') return false
    val scheme = url.substring(0, colonIndex).lowercase(Locale.US)
    return scheme in supportedAvatarImageUrlSchemes
}

private fun isInvalidImageUrlCached(urlKey: String): Boolean {
    synchronized(invalidImageUrlCache) {
        return invalidImageUrlCache.contains(urlKey)
    }
}

private fun cacheInvalidImageUrl(urlKey: String) {
    synchronized(invalidImageUrlCache) {
        if (invalidImageUrlCache.contains(urlKey)) {
            return
        }
        if (invalidImageUrlCache.size >= INVALID_IMAGE_URL_CACHE_MAX_SIZE) {
            val oldestUrlKey = invalidImageUrlCache.firstOrNull()
            if (oldestUrlKey != null) {
                invalidImageUrlCache.remove(oldestUrlKey)
            }
        }
        invalidImageUrlCache.add(urlKey)
    }
}

@DrawableRes
private fun defaultAvatarResId(isGroup: Boolean): Int {
    val fieldName = if (isGroup) {
        "base_component_avatar_group_default_icon"
    } else {
        "base_component_avatar_user_default_icon"
    }
    return try {
        R.drawable::class.java.getField(fieldName).getInt(null)
    } catch (_: Exception) {
        R.drawable.base_component_avatar_default_icon
    }
}

sealed class AvatarContent {
    data class Image(val url: Any?, val fallbackName: String = "") : AvatarContent()
    data class Text(val name: String) : AvatarContent()
    data class Icon(val painter: Painter? = null) : AvatarContent()
    data class Default(val isGroup: Boolean = false) : AvatarContent()
}

sealed class AvatarBadge {
    object None : AvatarBadge()
    object Dot : AvatarBadge()
    data class Text(val text: String) : AvatarBadge()
    data class Count(val count: Int) : AvatarBadge()
}

enum class AvatarSize(
    val size: Dp,
    val textSize: TextUnit,
    val borderRadius: Dp
) {
    XS(size = 24.dp, textSize = 12.sp, borderRadius = 4.dp),
    S(size = 32.dp, textSize = 14.sp, borderRadius = 4.dp),
    M(size = 40.dp, textSize = 16.sp, borderRadius = 4.dp),
    L(size = 48.dp, textSize = 18.sp, borderRadius = 8.dp),
    L52(size = 52.dp, textSize = 20.sp, borderRadius = 8.dp),
    XL(size = 64.dp, textSize = 28.sp, borderRadius = 12.dp),
    XXL(size = 96.dp, textSize = 36.sp, borderRadius = 12.dp)
}

enum class AvatarShape {
    Round,
    RoundRectangle,
    Rectangle
}

enum class AvatarStatus {
    None,
    Online,
    Offline,
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Avatar(
    content: AvatarContent,
    modifier: Modifier = Modifier,
    size: AvatarSize = AvatarSize.M,
    shape: AvatarShape? = null,
    status: AvatarStatus = AvatarStatus.None,
    badge: AvatarBadge = AvatarBadge.None,
    onLongClick: (() -> Unit)? = null,
    onClick: (() -> Unit)? = null
) {
    val colors = LocalTheme.current.colors
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val interactionSource = remember { MutableInteractionSource() }
    val clickModifier = when {
        onLongClick != null -> Modifier.combinedClickable(
            onClick = { onClick?.invoke() },
            onLongClick = onLongClick,
            indication = null,
            interactionSource = interactionSource
        )
        onClick != null -> Modifier.clickable(
            onClick = onClick,
            indication = null,
            interactionSource = interactionSource
        )
        else -> Modifier
    }

    Box(
        modifier = modifier
            .avatarPreferredSize(size.size)
            .then(clickModifier),
        contentAlignment = Alignment.Center
    ) {
        BasicAvatar(
            content = content,
            modifier = Modifier.fillMaxSize(),
            size = size,
            shape = shape
        )

        if (status == AvatarStatus.Online || status == AvatarStatus.Offline) {
            val dotColor = when (status) {
                AvatarStatus.Offline -> Colors.GrayLight7
                AvatarStatus.Online -> colors.textColorSuccess
                else -> Color.Transparent
            }
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .offset(x = 2.dp, y = 2.dp)
                    .size(8.dp)
                    .border(1.dp, colors.bgColorDefault, CircleShape)
                    .background(dotColor, CircleShape)
            )
        }
        if (badge != AvatarBadge.None) {
            val badgeText = when (badge) {
                is AvatarBadge.Text -> badge.text
                is AvatarBadge.Count -> badge.count.toString()
                else -> ""
            }
            Badge(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .avatarBadgeCornerOffset(isRtl),
                text = badgeText
            )
        }
    }
}

@Composable
fun Avatar(
    modifier: Modifier = Modifier,
    url: Any? = null,
    name: String = "",
    size: AvatarSize = AvatarSize.M,
    onClick: (() -> Unit)? = null
) {
    Avatar(
        content = AvatarContent.Image(url, name),
        modifier = modifier,
        size = size,
        onClick = onClick
    )
}

@Composable
private fun BasicAvatar(
    content: AvatarContent,
    modifier: Modifier = Modifier,
    size: AvatarSize = AvatarSize.M,
    shape: AvatarShape? = null
) {
    val colors = LocalTheme.current.colors
    val avatarShape: Shape = getAvatarShape(shape, size)

    Box(
        modifier = modifier
            .background(color = colors.bgColorAvatar, shape = avatarShape)
            .clip(avatarShape),
        contentAlignment = Alignment.Center
    ) {
        when (content) {
            is AvatarContent.Image -> {
                AvatarImageContent(
                    url = content.url,
                    fallbackName = content.fallbackName,
                    textSize = size.textSize,
                    avatarShape = avatarShape,
                    textColor = colors.textColorPrimary
                )
            }

            is AvatarContent.Text -> {
                val title = if (content.name.isEmpty()) "" else content.name.first().uppercase()
                AvatarFallbackText(
                    text = title,
                    textSize = size.textSize,
                    textColor = colors.textColorPrimary
                )
            }

            is AvatarContent.Icon -> {
                val painter = content.painter ?: painterResource(R.drawable.base_component_avatar_default_icon)
                Image(
                    painter = painter,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(0.5f),
                    colorFilter = ColorFilter.tint(colors.textColorPrimary)
                )
            }

            is AvatarContent.Default -> {
                Image(
                    painter = painterResource(defaultAvatarResId(content.isGroup)),
                    contentDescription = "",
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(avatarShape),
                    contentScale = ContentScale.Crop
                )
            }
        }
    }
}

@Composable
private fun AvatarImageContent(
    url: Any?,
    fallbackName: String,
    textSize: TextUnit,
    avatarShape: Shape,
    textColor: Color
) {
    val urlKey = remember(url) { normalizeAvatarImageUrlKey(url) }
    val isLoadError = remember(urlKey) { mutableStateOf(false) }
    val cachedInvalid = urlKey != null && isInvalidImageUrlCached(urlKey)
    val showFallback = urlKey == null || isLoadError.value || cachedInvalid
    val fallbackTitle = if (fallbackName.isEmpty()) "" else fallbackName.first().uppercase()

    if (showFallback) {
        AvatarFallbackText(
            text = fallbackTitle,
            textSize = textSize,
            textColor = textColor
        )
    } else {
        val imageModel = if (url is String) urlKey else url
        AsyncImage(
            modifier = Modifier
                .fillMaxSize()
                .clip(avatarShape),
            model = imageModel,
            contentDescription = "",
            contentScale = ContentScale.Crop,
            onError = {
                if (urlKey != null) {
                    cacheInvalidImageUrl(urlKey)
                    isLoadError.value = true
                }
            }
        )
    }
}

@Composable
private fun AvatarFallbackText(
    text: String,
    textSize: TextUnit,
    textColor: Color
) {
    BasicText(
        text = text,
        modifier = Modifier.fillMaxWidth(),
        style = TextStyle(
            color = textColor,
            fontSize = textSize,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        ),
        maxLines = 1
    )
}

private fun Modifier.avatarPreferredSize(defaultSize: Dp): Modifier {
    return this.layout { measurable, constraints ->
        val defaultPx = defaultSize.roundToPx()
        val sizePx = when {
            constraints.hasFixedWidth && constraints.hasFixedHeight -> {
                minOf(constraints.maxWidth, constraints.maxHeight)
            }
            constraints.hasFixedWidth -> constraints.maxWidth
            constraints.hasFixedHeight -> constraints.maxHeight
            else -> defaultPx
        }.coerceAtLeast(0)
        val placeable = measurable.measure(Constraints.fixed(sizePx, sizePx))
        layout(sizePx, sizePx) {
            placeable.place(0, 0)
        }
    }
}

private fun Modifier.avatarBadgeCornerOffset(isRtl: Boolean): Modifier {
    return this.layout { measurable, constraints ->
        val placeable = measurable.measure(constraints)
        val width = placeable.width
        val height = placeable.height
        layout(width, height) {
            val x = if (isRtl) {
                -width / 2
            } else {
                width / 2
            }
            placeable.place(x, -height / 2)
        }
    }
}

private fun getAvatarShape(shape: AvatarShape? = null, size: AvatarSize): Shape {
    return shape?.let {
        when (shape) {
            AvatarShape.Round -> CircleShape
            AvatarShape.RoundRectangle -> RoundedCornerShape(size.borderRadius)
            AvatarShape.Rectangle -> RectangleShape
        }
    } ?: when (AppBuilderConfig.avatarShape) {
        GlobalAvatarShape.CIRCULAR -> CircleShape
        GlobalAvatarShape.ROUNDED -> RoundedCornerShape(size.borderRadius)
        GlobalAvatarShape.SQUARE -> RectangleShape
    }
}
