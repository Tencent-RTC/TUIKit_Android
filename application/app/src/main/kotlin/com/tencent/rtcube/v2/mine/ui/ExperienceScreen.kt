package com.tencent.rtcube.v2.mine.ui

import android.Manifest
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.journeyapps.barcodescanner.BarcodeCallback
import com.journeyapps.barcodescanner.BarcodeResult
import com.journeyapps.barcodescanner.CaptureManager
import com.journeyapps.barcodescanner.DecoratedBarcodeView
import com.tencent.qcloud.tuicore.TUICore
import com.tencent.rtcube.v2.R
import com.tencent.rtcube.v2.mine.store.MineStore
import kotlinx.coroutines.launch

private const val TRTC_CONSOLE_ADDRESS = "https://console.cloud.tencent.com/trtc"
private const val DEFAULT_NUMBER_ROOM_ID = "12345"
private const val DEFAULT_STRING_ROOM_ID = "room_12345"

internal data class ScanResult(
    val sdkAppId: String = "",
    val userSig: String = "",
    val roomId: String = "",
    val roomType: String = "",
    val userId: String = "",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ExperienceScreen(
    store: MineStore,
) {
    val context = LocalContext.current

    var sdkAppId by remember { mutableStateOf("") }
    var userSig by remember { mutableStateOf("") }
    var roomId by remember { mutableStateOf(DEFAULT_NUMBER_ROOM_ID) }
    var isNumberRoomId by remember { mutableStateOf(true) }

    var showScanner by remember { mutableStateOf(false) }

    val onScanResult: (ScanResult) -> Unit = { parsed ->
        if (parsed.sdkAppId.isNotEmpty()) sdkAppId = parsed.sdkAppId
        if (parsed.userSig.isNotEmpty()) userSig = parsed.userSig
        if (parsed.roomId.isNotEmpty()) {
            isNumberRoomId = parsed.roomType == "number"
            roomId = parsed.roomId
        }
        showScanner = false
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            showScanner = true
        } else {
            Toast.makeText(context, context.getString(R.string.mine_experience_camera_permission_denied), Toast.LENGTH_SHORT)
                .show()
        }
    }

    val startScan = {
        cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
    }

    var showRoomIdTypeSheet by remember { mutableStateOf(false) }
    var showHowToGetSheet by remember { mutableStateOf(false) }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val howToGetSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFEBEDF5)),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .height(50.dp),
        ) {
            Image(
                painter = painterResource(id = R.drawable.mine_info_ic_back),
                contentDescription = stringResource(R.string.mine_info_back_desc),
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 20.dp)
                    .size(36.dp)
                    .padding(4.dp)
                    .clickable { store.hideExperience() },
            )
            Text(
                text = stringResource(R.string.mine_experience_title),
                color = Color(0xFF000000),
                fontSize = 16.sp,
                fontWeight = FontWeight.Normal,
                modifier = Modifier.align(Alignment.Center),
            )
            Image(
                painter = painterResource(id = R.drawable.mine_experience_ic_scanner),
                contentDescription = null,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 20.dp)
                    .size(24.dp)
                    .clickable { startScan() },
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(70.dp)
                .background(Color(0xFFD8DFF1))
                .padding(horizontal = 16.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            Text(
                text = stringResource(R.string.mine_experience_hint),
                color = Color(0xFF22262E),
                fontSize = 12.sp,
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 21.dp, end = 16.dp, top = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.mine_experience_select_test_app),
                    color = Color(0xFF727A8A),
                    fontSize = 14.sp,
                    modifier = Modifier.weight(1f),
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable { showHowToGetSheet = true },
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.mine_experience_ic_get_login_info),
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = stringResource(R.string.mine_experience_how_to_get_it),
                        color = Color(0xFF1C66E5),
                        fontSize = 14.sp,
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            ExperienceInputRow(
                label = stringResource(R.string.mine_experience_sdk_app_id),
                value = sdkAppId,
                hint = stringResource(R.string.mine_experience_scan_hint),
                keyboardType = KeyboardType.Number,
                onValueChange = { sdkAppId = it },
            )

            Spacer(modifier = Modifier.height(16.dp))

            ExperienceInputRow(
                label = stringResource(R.string.mine_experience_user_sig),
                value = userSig,
                hint = stringResource(R.string.mine_experience_scan_hint),
                keyboardType = KeyboardType.Text,
                isPassword = true,
                onValueChange = { userSig = it },
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = stringResource(R.string.mine_experience_enter_test_room),
                color = Color(0xFF727A8A),
                fontSize = 14.sp,
                modifier = Modifier.padding(start = 21.dp),
            )

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .height(48.dp)
                    .background(Color.White, RoundedCornerShape(8.dp)),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    modifier = Modifier
                        .clickable { showRoomIdTypeSheet = true }
                        .padding(start = 20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = if (isNumberRoomId) stringResource(R.string.mine_experience_number_room_id_type)
                        else stringResource(R.string.mine_experience_string_room_id_type),
                        color = Color(0xFF000000),
                        fontSize = 16.sp,
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Image(
                        painter = painterResource(id = R.drawable.mine_experience_ic_down_arrows),
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                }
                BasicTextField(
                    value = roomId,
                    onValueChange = { roomId = it },
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 4.dp, end = 16.dp),
                    textStyle = TextStyle(
                        color = Color(0xFF000000),
                        fontSize = 16.sp,
                        textAlign = TextAlign.End,
                    ),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = if (isNumberRoomId) KeyboardType.Number else KeyboardType.Text,
                    ),
                    cursorBrush = SolidColor(Color(0xFF1C66E5)),
                    decorationBox = { innerTextField ->
                        Box(contentAlignment = Alignment.CenterEnd) {
                            if (roomId.isEmpty()) {
                                Text(
                                    text = stringResource(R.string.mine_experience_scan_hint),
                                    color = Color(0xFFACB6C5),
                                    fontSize = 16.sp,
                                    textAlign = TextAlign.End,
                                )
                            }
                            innerTextField()
                        }
                    },
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .height(48.dp)
                    .background(Color.White, RoundedCornerShape(8.dp))
                    .padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.mine_experience_user_id),
                    color = Color(0xFF000000),
                    fontSize = 16.sp,
                )
                Text(
                    text = store.getCurrentUserId(),
                    color = Color(0xFF0F1014),
                    fontSize = 16.sp,
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 4.dp),
                    textAlign = TextAlign.End,
                )
            }

            Spacer(modifier = Modifier.weight(1f))
            Spacer(modifier = Modifier.height(32.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 16.dp)
                    .height(52.dp)
                    .background(Color(0xFF1C66E5), RoundedCornerShape(8.dp))
                    .clickable {
                        enterRoom(
                            context = context,
                            sdkAppId = sdkAppId,
                            userSig = userSig,
                            roomId = roomId,
                            isNumberRoomId = isNumberRoomId,
                            store = store,
                        )
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.mine_experience_enter_room),
                    color = Color(0xFFF2F5FC),
                    fontSize = 16.sp,
                )
            }
        }
    }

    if (showRoomIdTypeSheet) {
        ModalBottomSheet(
            onDismissRequest = { showRoomIdTypeSheet = false },
            sheetState = sheetState,
            containerColor = Color.White,
        ) {
            Column(modifier = Modifier.padding(bottom = 32.dp)) {
                RoomIdTypeItem(
                    text = stringResource(R.string.mine_experience_number_room_id_type),
                    isSelected = isNumberRoomId,
                    onClick = {
                        isNumberRoomId = true
                        roomId = DEFAULT_NUMBER_ROOM_ID
                        scope.launch { sheetState.hide() }.invokeOnCompletion {
                            showRoomIdTypeSheet = false
                        }
                    },
                )
                RoomIdTypeItem(
                    text = stringResource(R.string.mine_experience_string_room_id_type),
                    isSelected = !isNumberRoomId,
                    onClick = {
                        isNumberRoomId = false
                        roomId = DEFAULT_STRING_ROOM_ID
                        scope.launch { sheetState.hide() }.invokeOnCompletion {
                            showRoomIdTypeSheet = false
                        }
                    },
                )
            }
        }
    }

    if (showHowToGetSheet) {
        ModalBottomSheet(
            onDismissRequest = { showHowToGetSheet = false },
            sheetState = howToGetSheetState,
            containerColor = Color.White,
        ) {
            HowToGetContent(
                context = context,
                onScanClick = {
                    scope.launch { howToGetSheetState.hide() }.invokeOnCompletion {
                        showHowToGetSheet = false
                    }
                    startScan()
                },
            )
        }
    }

    if (showScanner) {
        ScannerOverlay(
            onResult = { onScanResult(it) },
            onBack = { showScanner = false },
        )
    }
}

@Composable
private fun ExperienceInputRow(
    label: String,
    value: String,
    hint: String,
    keyboardType: KeyboardType,
    isPassword: Boolean = false,
    onValueChange: (String) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .height(48.dp)
            .background(Color.White, RoundedCornerShape(8.dp))
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            color = Color(0xFF000000),
            fontSize = 16.sp,
        )
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .weight(1f)
                .padding(start = 4.dp),
            textStyle = TextStyle(
                color = Color(0xFF000000),
                fontSize = 16.sp,
                textAlign = TextAlign.End,
            ),
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            visualTransformation = if (isPassword) PasswordVisualTransformation() else VisualTransformation.None,
            cursorBrush = SolidColor(Color(0xFF1C66E5)),
            decorationBox = { innerTextField ->
                Box(contentAlignment = Alignment.CenterEnd) {
                    if (value.isEmpty()) {
                        Text(
                            text = hint,
                            color = Color(0xFFACB6C5),
                            fontSize = 16.sp,
                            textAlign = TextAlign.End,
                        )
                    }
                    innerTextField()
                }
            },
        )
    }
}

@Composable
private fun RoomIdTypeItem(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(50.dp)
            .clickable { onClick() }
            .padding(horizontal = 20.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = if (isSelected) Color(0xFF1C66E5) else Color(0xFF000000),
            fontSize = 16.sp,
        )
    }
}

@Composable
private fun HowToGetContent(
    context: Context,
    onScanClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(bottom = 16.dp),
    ) {
        Text(
            text = stringResource(R.string.mine_experience_get_app_info_title),
            color = Color(0xFF000000),
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 20.dp),
            textAlign = TextAlign.Center,
        )

        Text(
            text = stringResource(R.string.mine_experience_computer_create_app_title),
            color = Color(0xFF000000),
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
        )

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
        ) {
            Text(
                text = stringResource(R.string.mine_experience_computer_create_app_hint),
                color = Color(0xFF000000),
                fontSize = 14.sp,
                modifier = Modifier.weight(1f),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Row(
                modifier = Modifier
                    .clickable {
                        copyToClipboard(
                            context = context,
                            content = TRTC_CONSOLE_ADDRESS,
                            toastMessage = context.getString(R.string.mine_experience_copy_success),
                        )
                    },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Image(
                    painter = painterResource(id = R.drawable.mine_experience_ic_copy),
                    contentDescription = null,
                    modifier = Modifier.size(15.dp),
                )
                Text(
                    text = stringResource(R.string.mine_experience_copy),
                    color = Color(0xFF1C66E5),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = stringResource(R.string.mine_experience_get_free_resource_title),
            color = Color(0xFF000000),
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = stringResource(R.string.mine_experience_get_free_resource_hint),
            color = Color(0xFF000000),
            fontSize = 14.sp,
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = stringResource(R.string.mine_experience_get_app_info_for_scan_title),
            color = Color(0xFF000000),
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = stringResource(R.string.mine_experience_get_app_info_for_scan_hint),
            color = Color(0xFF000000),
            fontSize = 14.sp,
        )

        Spacer(modifier = Modifier.height(32.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .background(Color(0xFFEBF2FF), RoundedCornerShape(8.dp))
                .clickable { onScanClick() },
            contentAlignment = Alignment.Center,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(
                    painter = painterResource(id = R.drawable.mine_experience_ic_blue_scan),
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = stringResource(R.string.mine_experience_auto_write_for_scan_title),
                    color = Color(0xFF1C66E5),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
    }
}

private fun enterRoom(
    context: Context,
    sdkAppId: String,
    userSig: String,
    roomId: String,
    isNumberRoomId: Boolean,
    store: MineStore,
) {
    if (sdkAppId.isEmpty() || userSig.isEmpty() || roomId.isEmpty()) {
        Toast.makeText(
            context,
            context.getString(R.string.mine_experience_please_fill_all),
            Toast.LENGTH_SHORT,
        ).show()
        return
    }
    val sdkAppIdInt = sdkAppId.toIntOrNull()
    if (sdkAppIdInt == null) {
        Toast.makeText(
            context,
            context.getString(R.string.mine_experience_invalid_sdk_app_id),
            Toast.LENGTH_SHORT,
        ).show()
        return
    }
    if (isNumberRoomId) {
        val roomIdInt = roomId.toIntOrNull()
        if (roomIdInt == null) {
            Toast.makeText(
                context,
                context.getString(R.string.mine_experience_invalid_room_id),
                Toast.LENGTH_SHORT,
            ).show()
            return
        }
    }
    val params = android.os.Bundle().apply {
        putInt("sdkAppId", sdkAppIdInt)
        putString("userId", store.getCurrentUserId())
        putString("userSig", userSig)
        putInt("roomId", if (isNumberRoomId) roomId.toInt() else 0)
        putString("strRoomId", if (isNumberRoomId) "" else roomId)
    }
    TUICore.startActivity("ExperienceHomeActivity", params)
}

private fun copyToClipboard(context: Context, content: String, toastMessage: String) {
    val cm = context.applicationContext.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clipData = ClipData.newPlainText("Label", content)
    cm.setPrimaryClip(clipData)
    Toast.makeText(context.applicationContext, toastMessage, Toast.LENGTH_SHORT).show()
}

private fun parseScanResult(jsonString: String?): ScanResult? {
    if (jsonString.isNullOrEmpty()) return null
    return try {
        Gson().fromJson(jsonString, ScanResult::class.java)
    } catch (e: JsonSyntaxException) {
        null
    }
}

@Composable
internal fun ScannerOverlay(
    onResult: (ScanResult) -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val activity = context as Activity
    val barcodeView = remember { DecoratedBarcodeView(context) }
    val captureManager = remember {
        CaptureManager(activity, barcodeView).also { manager ->
            manager.initializeFromIntent(activity.intent, null)
        }
    }

    DisposableEffect(Unit) {
        barcodeView.decodeContinuous(object : BarcodeCallback {
            override fun barcodeResult(result: BarcodeResult) {
                parseScanResult(result.text)?.let { onResult(it) }
            }
        })
        captureManager.onResume()
        onDispose {
            captureManager.onPause()
            captureManager.onDestroy()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = { barcodeView },
            modifier = Modifier.fillMaxSize(),
        )

        Image(
            painter = painterResource(id = R.drawable.mine_info_ic_back),
            contentDescription = stringResource(R.string.mine_info_back_desc),
            modifier = Modifier
                .align(Alignment.TopStart)
                .statusBarsPadding()
                .padding(start = 16.dp, top = 16.dp)
                .size(52.dp)
                .clickable { onBack() },
        )

        Text(
            text = stringResource(R.string.mine_experience_scan_title),
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 28.dp),
        )
    }
}
