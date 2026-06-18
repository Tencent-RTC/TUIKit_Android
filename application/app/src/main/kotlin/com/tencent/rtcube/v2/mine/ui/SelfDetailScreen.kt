package com.tencent.rtcube.v2.mine.ui

import android.app.DatePickerDialog
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.tencent.rtcube.v2.R
import com.tencent.rtcube.v2.mine.store.MineStore
import java.util.Calendar

private const val AVATAR_FACE_COUNT = 26
private const val AVATAR_URL_PREFIX = "https://im.sdk.qcloud.com/download/tuikit-resource/avatar/avatar_"

private val BgColor = Color(0xFFE6E9EB)
private val LabelColor = Color(0xFF444444)
private val ContentColor = Color(0xFF999999)
private val LineColor = Color(0xFFDDDDDD)
private val DialogLineColor = Color(0xFFEEEEEE)
private val DialogEditBg = Color(0xFFF5F5F5)
private val BlueColor = Color(0xFF006EFF)
private val ScrimColor = Color(0x80000000)

private fun Modifier.noRippleClick(onClick: () -> Unit) = composed {
    clickable(
        indication = null,
        interactionSource = remember { MutableInteractionSource() },
        onClick = onClick,
    )
}

@Composable
internal fun SelfDetailScreen(
    store: MineStore,
    userId: String,
    onBack: () -> Unit,
) {
    var faceUrl by remember { mutableStateOf("") }
    var nickname by remember { mutableStateOf("") }
    var signature by remember { mutableStateOf("") }
    var gender by remember { mutableStateOf(0) }
    var birthday by remember { mutableStateOf(0L) }
    var dialog by remember { mutableStateOf<DialogType>(DialogType.None) }

    LaunchedEffect(Unit) {
        store.loadSelfInfo()?.let {
            faceUrl = it.faceUrl.orEmpty()
            nickname = it.nickName.orEmpty()
            signature = it.selfSignature.orEmpty()
            gender = it.gender
            birthday = it.birthday
        }
    }

    fun commit() = store.commitSelfInfo(faceUrl, nickname, signature, gender, birthday)
    val close = { dialog = DialogType.None }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgColor),
    ) {
        TitleBar(stringResource(R.string.mine_profile_title), onBack)
        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
            ProfileBlock {
                ProfileItem(
                    label = stringResource(R.string.mine_profile_avatar),
                    height = 70.dp,
                    onClick = { dialog = DialogType.Avatar },
                ) {
                    AsyncImage(
                        model = faceUrl.ifEmpty { null },
                        placeholder = painterResource(R.drawable.mine_profile_ic_head),
                        error = painterResource(R.drawable.mine_profile_ic_head),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(46.dp)
                            .clip(RoundedCornerShape(4.6.dp)),
                    )
                    ItemArrow(size = 16.dp, startPadding = 10.dp)
                }
            }
            BlockGap()
            ProfileBlock {
                TextItem(stringResource(R.string.mine_profile_nickname), nickname, true) { dialog = DialogType.Nickname }
                Divider()
                TextItem(stringResource(R.string.mine_profile_account), userId, false, null)
            }
            BlockGap()
            ProfileBlock {
                TextItem(stringResource(R.string.mine_profile_signature), signature, true) { dialog = DialogType.Signature }
                Divider()
                TextItem(stringResource(R.string.mine_profile_gender), formatGender(gender), true) { dialog = DialogType.Gender }
                Divider()
                TextItem(stringResource(R.string.mine_profile_birthday), formatBirthday(birthday), true) { dialog = DialogType.Birthday }
            }
        }
    }

    when (dialog) {
        DialogType.Nickname -> InputBottomSheet(
            title = stringResource(R.string.mine_profile_modify_nickname),
            description = stringResource(R.string.mine_profile_input_rule),
            initial = nickname,
            onDismiss = close,
            onConfirm = { nickname = it; close(); commit() },
        )
        DialogType.Signature -> InputBottomSheet(
            title = stringResource(R.string.mine_profile_modify_signature),
            description = stringResource(R.string.mine_profile_input_rule),
            initial = signature,
            onDismiss = close,
            onConfirm = { signature = it; close(); commit() },
        )
        DialogType.Gender -> GenderBottomSheet(
            current = gender,
            onDismiss = close,
            onConfirm = { gender = it; close(); commit() },
        )
        DialogType.Birthday -> BirthdayPicker(
            birthday = birthday,
            onCancel = close,
            onConfirm = { y, m, d ->
                birthday = y * 10000L + (m + 1) * 100L + d
                close()
                commit()
            },
        )
        DialogType.Avatar -> AvatarBottomSheet(
            selected = faceUrl,
            onDismiss = close,
            onConfirm = { faceUrl = it; close(); commit() },
        )
        DialogType.None -> Unit
    }
}

private enum class DialogType { None, Nickname, Signature, Gender, Birthday, Avatar }

@Composable
private fun TitleBar(title: String, onBack: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White)
            .statusBarsPadding()
            .height(51.dp),
    ) {
        Image(
            painter = painterResource(R.drawable.mine_profile_ic_back),
            contentDescription = null,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 17.dp)
                .size(24.dp)
                .clickable(onClick = onBack),
        )
        Text(
            text = title,
            color = Color.Black,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.align(Alignment.Center),
        )
    }
}

@Composable
private fun ProfileBlock(content: @Composable ColumnScope.() -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().background(Color.White), content = content)
}

@Composable
private fun BlockGap() = Box(modifier = Modifier.fillMaxWidth().height(10.dp))

@Composable
private fun Divider() = Box(
    modifier = Modifier
        .fillMaxWidth()
        .padding(start = 16.dp)
        .height(1.dp)
        .background(LineColor),
)

@Composable
private fun ProfileItem(
    label: String,
    height: androidx.compose.ui.unit.Dp = 46.dp,
    onClick: (() -> Unit)? = null,
    trailing: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }
            .height(height)
            .padding(start = 16.dp, end = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = LabelColor, fontSize = 15.sp, modifier = Modifier.weight(1f))
        trailing()
    }
}

@Composable
private fun TextItem(label: String, value: String, showArrow: Boolean, onClick: (() -> Unit)?) {
    ProfileItem(label = label, onClick = onClick) {
        Text(value, color = ContentColor, fontSize = 15.sp, textAlign = TextAlign.End, maxLines = 1)
        if (showArrow) ItemArrow(size = 12.dp, startPadding = 10.dp)
    }
}

@Composable
private fun ItemArrow(size: androidx.compose.ui.unit.Dp, startPadding: androidx.compose.ui.unit.Dp) {
    Image(
        painter = painterResource(R.drawable.mine_info_ic_arrow),
        contentDescription = null,
        modifier = Modifier.padding(start = startPadding).size(size),
    )
}

@Composable
private fun BottomSheet(
    title: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    confirmEnabled: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            usePlatformDefaultWidth = false,
        ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(ScrimColor)
                .noRippleClick(onDismiss),
            contentAlignment = Alignment.BottomCenter,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight()
                    .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
                    .background(Color.White)
                    .noRippleClick {}
                    .navigationBarsPadding(),
            ) {
                BottomSheetHeader(title, onDismiss)
                content()
                ConfirmButton(enabled = confirmEnabled, onClick = onConfirm)
            }
        }
    }
}

@Composable
private fun BottomSheetHeader(title: String, onClose: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(50.dp)
            .padding(horizontal = 20.dp),
    ) {
        Text(
            text = title,
            color = Color(0xFF000000),
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.align(Alignment.Center),
        )
        Image(
            painter = painterResource(R.drawable.mine_profile_ic_close),
            contentDescription = null,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .size(24.dp)
                .clickable(onClick = onClose),
        )
    }
    Box(modifier = Modifier.fillMaxWidth().height(0.5.dp).background(DialogLineColor))
}

@Composable
private fun ConfirmButton(enabled: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 22.dp, end = 22.dp, top = 18.dp, bottom = 24.dp)
            .height(44.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(if (enabled) BlueColor else BlueColor.copy(alpha = 0.5f))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(R.string.mine_profile_btn_confirm),
            color = Color.White,
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun InputBottomSheet(
    title: String,
    description: String,
    initial: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var text by remember { mutableStateOf(initial) }
    BottomSheet(title = title, onDismiss = onDismiss, onConfirm = { onConfirm(text) }) {
        Column(modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 16.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(DialogEditBg)
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                BasicTextField(
                    value = text,
                    onValueChange = { text = it },
                    singleLine = true,
                    cursorBrush = SolidColor(BlueColor),
                    textStyle = TextStyle(color = Color(0xFF030303), fontSize = 15.sp),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Text(
                text = description,
                color = ContentColor,
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 10.dp),
            )
        }
    }
}

@Composable
private fun GenderBottomSheet(current: Int, onDismiss: () -> Unit, onConfirm: (Int) -> Unit) {
    var selected by remember { mutableStateOf(if (current in 1..2) current else 1) }
    BottomSheet(
        title = stringResource(R.string.mine_profile_modify_gender),
        onDismiss = onDismiss,
        onConfirm = { onConfirm(selected) },
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 8.dp)) {
            GenderOption(stringResource(R.string.mine_profile_gender_male), selected == 1) { selected = 1 }
            GenderOption(stringResource(R.string.mine_profile_gender_female), selected == 2) { selected = 2 }
        }
    }
}

@Composable
private fun GenderOption(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .height(48.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = label, color = LabelColor, fontSize = 15.sp, modifier = Modifier.weight(1f))
        Box(
            modifier = Modifier
                .size(18.dp)
                .clip(CircleShape)
                .background(if (selected) BlueColor else Color(0xFFE5E5E5)),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) {
                Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(Color.White))
            }
        }
    }
}

@Composable
private fun BirthdayPicker(birthday: Long, onCancel: () -> Unit, onConfirm: (Int, Int, Int) -> Unit) {
    val context = LocalContext.current
    val (year, month, day) = remember(birthday) { birthday.toYMD() }
    val dialog = remember {
        DatePickerDialog(context, { _, y, m, d -> onConfirm(y, m, d) }, year, month, day).apply {
            setOnCancelListener { onCancel() }
        }
    }
    DisposableEffect(dialog) {
        dialog.show()
        onDispose { dialog.dismiss() }
    }
}

@Composable
private fun AvatarBottomSheet(selected: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var current by remember { mutableStateOf(selected) }
    val avatars = remember { (1..AVATAR_FACE_COUNT).map { "$AVATAR_URL_PREFIX$it.png" } }
    BottomSheet(
        title = stringResource(R.string.mine_profile_choose_avatar),
        onDismiss = onDismiss,
        onConfirm = { onConfirm(current) },
    ) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(4),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 320.dp)
                .padding(start = 16.dp, end = 16.dp, top = 12.dp),
        ) {
            items(avatars.size) { i ->
                AvatarItem(avatars[i], avatars[i] == current) { current = avatars[i] }
            }
        }
    }
}

@Composable
private fun AvatarItem(url: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(64.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(if (selected) BlueColor else Color.Transparent)
            .padding(if (selected) 3.dp else 0.dp)
            .clickable(onClick = onClick),
    ) {
        AsyncImage(
            model = url,
            placeholder = painterResource(R.drawable.mine_profile_ic_head),
            error = painterResource(R.drawable.mine_profile_ic_head),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(4.dp)),
        )
    }
}

@Composable
private fun formatGender(gender: Int): String = stringResource(
    when (gender) {
        1 -> R.string.mine_profile_gender_male
        2 -> R.string.mine_profile_gender_female
        else -> R.string.mine_profile_gender_unknown
    },
)

private fun formatBirthday(birthday: Long): String {
    if (birthday < 19000101L) return "1970-01-01"
    val s = birthday.toString().padStart(8, '0')
    return "${s.substring(0, 4)}-${s.substring(4, 6)}-${s.substring(6, 8)}"
}

private fun Long.toYMD(): Triple<Int, Int, Int> = if (this >= 19000101L) {
    Triple((this / 10000L).toInt(), ((this / 100L) % 100L).toInt() - 1, (this % 100L).toInt())
} else {
    val c = Calendar.getInstance()
    Triple(c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH))
}
