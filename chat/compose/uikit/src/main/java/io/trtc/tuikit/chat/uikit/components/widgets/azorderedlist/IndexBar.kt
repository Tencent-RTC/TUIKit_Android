package io.trtc.tuikit.chat.uikit.components.widgets.azorderedlist

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.trtc.tuikit.chat.uikit.components.theme.LocalTheme

private val LetterWidth = 24.dp
private val LetterHeight = 20.dp
private val HighlightedSize = 20.dp
private val VerticalPadding = 3.dp

@Composable
fun IndexBar(
    letters: List<String>,
    modifier: Modifier = Modifier,
    currentLetter: String? = null,
    onLetterSelected: (String) -> Unit,
    onLetterPressed: (String) -> Unit = {},
    onLetterReleased: () -> Unit = {},
    onDragStart: () -> Unit = {},
    onDragEnd: () -> Unit = {},
) {
    val colors = LocalTheme.current.colors

    var isDragging by remember { mutableStateOf(false) }
    var draggedLetter by remember { mutableStateOf<String?>(null) }

    Box(
        modifier = modifier
            .wrapContentHeight()
            .width(LetterWidth)
            .pointerInput(letters) {
                if (letters.isEmpty()) return@pointerInput

                val verticalPaddingPx = VerticalPadding.toPx()
                val letterHeightPx = LetterHeight.toPx()

                fun letterAt(y: Float): String? {
                    if (letterHeightPx <= 0f) return null
                    val index = ((y - verticalPaddingPx) / letterHeightPx).toInt()
                        .coerceIn(0, letters.lastIndex)
                    return letters[index]
                }

                awaitEachGesture {
                    val down = awaitFirstDown(
                        requireUnconsumed = false,
                        pass = PointerEventPass.Initial
                    )
                    down.consume()

                    var started = false
                    var lastLetter: String? = null
                    try {
                        isDragging = true
                        onDragStart()
                        started = true

                        letterAt(down.position.y)?.let { letter ->
                            lastLetter = letter
                            draggedLetter = letter
                            onLetterSelected(letter)
                            onLetterPressed(letter)
                        }

                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull() ?: break
                            change.consume()
                            if (change.changedToUpIgnoreConsumed() || !change.pressed) {
                                break
                            }
                            letterAt(change.position.y)?.let { letter ->
                                if (lastLetter != letter) {
                                    lastLetter = letter
                                    draggedLetter = letter
                                    onLetterSelected(letter)
                                    onLetterPressed(letter)
                                }
                            }
                        }
                    } finally {
                        if (started) {
                            isDragging = false
                            draggedLetter = null
                            onDragEnd()
                            onLetterReleased()
                        }
                    }
                }
            }
    ) {
        Column(
            modifier = Modifier
                .width(LetterWidth)
                .padding(vertical = VerticalPadding),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            letters.forEach { letter ->
                val isDraggedLetter = isDragging && draggedLetter == letter
                val isCurrent = !isDragging && currentLetter == letter
                val isHighlighted = isDraggedLetter || isCurrent

                Box(
                    modifier = Modifier.size(width = LetterWidth, height = LetterHeight),
                    contentAlignment = Alignment.Center
                ) {
                    if (isHighlighted) {
                        Box(
                            modifier = Modifier
                                .size(HighlightedSize)
                                .clip(CircleShape)
                                .background(color = colors.textColorLink)
                        )
                    }
                    Text(
                        text = letter,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (isHighlighted) {
                            colors.textColorButton
                        } else {
                            colors.textColorSecondary
                        },
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}
