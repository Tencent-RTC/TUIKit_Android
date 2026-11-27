package io.trtc.tuikit.chat.settings

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import io.trtc.tuikit.chat.BaseActivity
import io.trtc.tuikit.chat.R
import io.trtc.tuikit.chat.uikit.components.ai.AiMediaProcessManager
import io.trtc.tuikit.chat.uikit.components.ai.tts.CustomVoiceItem
import io.trtc.tuikit.chat.uikit.components.ai.tts.VoiceMessageConfig
import io.trtc.tuikit.chat.uikit.components.ai.tts.defaultVoiceList
import io.trtc.tuikit.chat.uikit.components.common.SetActivitySystemBarAppearance
import io.trtc.tuikit.chat.uikit.components.theme.LocalTheme
import io.trtc.tuikit.chat.uikit.components.widgets.Toast
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

class VoiceSelectActivity : BaseActivity() {

    companion object {
        fun start(context: Context) {
            context.startActivity(Intent(context, VoiceSelectActivity::class.java))
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SetActivitySystemBarAppearance()
            VoiceSelectScreen(onBackClick = { finish() })
        }
    }
}

@Composable
private fun VoiceSelectScreen(onBackClick: () -> Unit) {
    val colors = LocalTheme.current.colors
    val context = LocalContext.current

    val defaultVoices = remember { defaultVoiceList(context) }
    var customVoices by remember { mutableStateOf<List<CustomVoiceItem>>(emptyList()) }
    var selectedId by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(true) }
    var refreshTrigger by remember { mutableIntStateOf(0) }
    var openVoiceId by remember { mutableStateOf<String?>(null) }

    val deleteFailedTip = stringResource(R.string.compose_demo_voice_delete_failed)
    val deleteLabel = stringResource(R.string.compose_demo_voice_delete)

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                refreshTrigger++
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    DisposableEffect(refreshTrigger) {
        selectedId = VoiceMessageConfig.getSelectedVoiceId(context)
        loading = true
        AiMediaProcessManager.getCustomVoiceList(
            onSuccess = { list ->
                loading = false
                customVoices = list
            },
            onFailure = { _, _ ->
                loading = false
                customVoices = emptyList()
            }
        )
        onDispose { }
    }

    fun selectVoice(item: CustomVoiceItem) {
        VoiceMessageConfig.setSelectedVoice(context, item.voiceId, item.name)
        selectedId = item.voiceId
    }

    fun deleteVoice(item: CustomVoiceItem) {
        AiMediaProcessManager.deleteCustomVoice(
            voiceId = item.voiceId,
            onSuccess = {
                customVoices = customVoices.filterNot { it.voiceId == item.voiceId }
                if (selectedId == item.voiceId) {
                    VoiceMessageConfig.setSelectedVoice(context, "", "")
                    selectedId = ""
                }
                if (openVoiceId == item.voiceId) {
                    openVoiceId = null
                }
            },
            onFailure = { _, _ ->
                Toast.error(context, deleteFailedTip)
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.bgColorOperate)
            .navigationBarsPadding()
    ) {
        DemoPageHeader(
            title = stringResource(R.string.compose_demo_voice_select),
            onBackClick = onBackClick
        )

        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                item {
                    VoiceSectionHeader(stringResource(R.string.compose_demo_voice_default_group))
                }
                items(
                    count = defaultVoices.size,
                    key = { index -> "default_${defaultVoices[index].voiceId}" }
                ) { index ->
                    val voice = defaultVoices[index]
                    VoiceRow(
                        voice = voice,
                        custom = false,
                        selected = voice.voiceId == selectedId,
                        modifier = Modifier.clickable { selectVoice(voice) }
                    )
                }

                item {
                    VoiceSectionHeader(stringResource(R.string.compose_demo_voice_custom_group))
                }
                if (customVoices.isEmpty() && !loading) {
                    item {
                        Text(
                            text = stringResource(R.string.compose_demo_voice_custom_empty),
                            fontSize = 14.sp,
                            color = colors.textColorTertiary,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(colors.bgColorOperate)
                                .padding(horizontal = 16.dp, vertical = 16.dp)
                        )
                    }
                } else {
                    items(
                        count = customVoices.size,
                        key = { index -> "custom_${customVoices[index].voiceId}" }
                    ) { index ->
                        val voice = customVoices[index]
                        SwipeRevealRow(
                            deleteLabel = deleteLabel,
                            opened = openVoiceId == voice.voiceId,
                            onOpenedChange = { open ->
                                openVoiceId = if (open) voice.voiceId else {
                                    if (openVoiceId == voice.voiceId) null else openVoiceId
                                }
                            },
                            onClick = { selectVoice(voice) },
                            onDelete = { deleteVoice(voice) }
                        ) {
                            VoiceRow(
                                voice = voice,
                                custom = true,
                                selected = voice.voiceId == selectedId
                            )
                        }
                    }
                }
            }

            if (loading) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = colors.textColorLink,
                    strokeWidth = 2.dp
                )
            }
        }
    }
}

@Composable
private fun VoiceSectionHeader(title: String) {
    val colors = LocalTheme.current.colors
    Text(
        text = title,
        fontSize = 13.sp,
        color = colors.textColorSecondary,
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.bgColorDefault)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    )
}

@Composable
private fun SwipeRevealRow(
    deleteLabel: String,
    opened: Boolean,
    onOpenedChange: (Boolean) -> Unit,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    content: @Composable () -> Unit
) {
    val colors = LocalTheme.current.colors
    val density = LocalDensity.current
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val deleteWidthPx = with(density) { 80.dp.toPx() }
    var visualOffset by remember { mutableFloatStateOf(0f) }
    val scope = rememberCoroutineScope()
    val openOffset = if (isRtl) deleteWidthPx else -deleteWidthPx

    LaunchedEffect(opened, openOffset) {
        val target = if (opened) openOffset else 0f
        animate(visualOffset, target, animationSpec = tween(180)) { value, _ ->
            visualOffset = value
        }
    }

    Box(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(colors.textColorError)
        ) {
            Box(
                modifier = Modifier
                    .align(if (isRtl) Alignment.CenterStart else Alignment.CenterEnd)
                    .width(80.dp)
                    .fillMaxHeight()
                    .background(colors.textColorError)
                    .clickable { onDelete() },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = deleteLabel,
                    fontSize = 14.sp,
                    color = colors.textColorButton,
                    maxLines = 1
                )
            }
        }

        Box(
            modifier = Modifier
                .offset { IntOffset(visualOffset.roundToInt(), 0) }
                .fillMaxWidth()
                .background(colors.bgColorOperate)
                .pointerInput(deleteWidthPx, isRtl, opened) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            val pastHalf = if (isRtl) {
                                visualOffset >= deleteWidthPx / 2f
                            } else {
                                visualOffset <= -deleteWidthPx / 2f
                            }
                            if (pastHalf != opened) {
                                onOpenedChange(pastHalf)
                            } else {
                                val target = if (opened) openOffset else 0f
                                scope.launch {
                                    animate(
                                        visualOffset,
                                        target,
                                        animationSpec = tween(180)
                                    ) { value, _ ->
                                        visualOffset = value
                                    }
                                }
                            }
                        },
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            val raw = visualOffset + dragAmount
                            visualOffset = if (isRtl) {
                                raw.coerceIn(0f, deleteWidthPx)
                            } else {
                                raw.coerceIn(-deleteWidthPx, 0f)
                            }
                        }
                    )
                }
                .clickable {
                    if (opened) onOpenedChange(false) else onClick()
                }
        ) {
            content()
        }
    }
}

@Composable
private fun VoiceRow(
    voice: CustomVoiceItem,
    custom: Boolean,
    selected: Boolean,
    modifier: Modifier = Modifier
) {
    val colors = LocalTheme.current.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.bgColorOperate)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = voice.name,
            fontSize = 15.sp,
            color = colors.textColorPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = if (custom) Modifier else Modifier.weight(1f)
        )

        if (custom) {
            Text(
                text = stringResource(R.string.compose_demo_voice_custom_badge),
                fontSize = 10.sp,
                color = colors.textColorButton,
                modifier = Modifier
                    .padding(start = 8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(colors.buttonColorPrimaryDefault)
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            )
            Text(
                text = voice.voiceId,
                fontSize = 10.sp,
                color = colors.textColorTertiary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .padding(start = 6.dp)
                    .weight(1f)
            )
        }

        if (selected) {
            Icon(
                painter = painterResource(R.drawable.demo_ic_voice_check),
                contentDescription = null,
                tint = colors.textColorLink,
                modifier = Modifier
                    .padding(start = 6.dp)
                    .size(20.dp)
            )
        } else {
            Spacer(
                modifier = Modifier
                    .padding(start = 6.dp)
                    .size(20.dp)
            )
        }
    }
}
