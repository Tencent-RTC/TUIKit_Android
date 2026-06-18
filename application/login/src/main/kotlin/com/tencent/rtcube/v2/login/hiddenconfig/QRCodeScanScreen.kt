package com.tencent.rtcube.v2.login.hiddenconfig

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.journeyapps.barcodescanner.BarcodeCallback
import com.journeyapps.barcodescanner.BarcodeResult
import com.journeyapps.barcodescanner.DecoratedBarcodeView
import com.tencent.rtcube.v2.login.R

@Composable
fun QRCodeScanScreen(
    onResult: (String) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val activity = context as Activity

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }
    var showPermissionDialog by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
        if (!granted) {
            showPermissionDialog = true
        }
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasCameraPermission = ContextCompat.checkSelfPermission(
                    context, Manifest.permission.CAMERA
                ) == PackageManager.PERMISSION_GRANTED
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        if (hasCameraPermission) {
            ScannerView(
                activity = activity,
                onResult = onResult
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 0.dp)
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .statusBarsPadding()
                    .padding(start = 16.dp, top = 8.dp)
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.3f))
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() }
                    ) { onCancel() },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "✕",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Text(
                text = stringResource(R.string.login_hidden_config_scan_qr),
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = 20.dp)
            )

            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(250.dp)
                    .border(2.dp, Color.White, RoundedCornerShape(12.dp))
            )

            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(top = 320.dp)
            ) {
                Text(
                    text = stringResource(R.string.login_hidden_config_scan_hint),
                    color = Color.White,
                    fontSize = 14.sp
                )
            }
        }
    }

    if (showPermissionDialog) {
        AlertDialog(
            onDismissRequest = { showPermissionDialog = false },
            title = { Text(text = stringResource(R.string.login_hidden_config_camera_permission_title)) },
            text = { Text(text = stringResource(R.string.login_hidden_config_camera_permission_msg)) },
            confirmButton = {
                TextButton(onClick = {
                    showPermissionDialog = false
                    runCatching {
                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.fromParts("package", context.packageName, null)
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(intent)
                    }
                }) {
                    Text(text = stringResource(R.string.login_hidden_config_go_settings))
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showPermissionDialog = false
                    onCancel()
                }) {
                    Text(text = stringResource(R.string.login_hidden_config_cancel))
                }
            }
        )
    }
}

@Composable
private fun ScannerView(
    activity: Activity,
    onResult: (String) -> Unit,
) {
    val barcodeView = remember { DecoratedBarcodeView(activity) }
    val handledRef = remember { java.util.concurrent.atomic.AtomicBoolean(false) }
    val currentOnResult by rememberUpdatedState(onResult)

    DisposableEffect(Unit) {
        barcodeView.decodeContinuous(object : BarcodeCallback {
            override fun barcodeResult(result: BarcodeResult) {
                val text = result.text
                if (!text.isNullOrEmpty() && handledRef.compareAndSet(false, true)) {
                    barcodeView.pause()
                    currentOnResult(text)
                }
            }
        })
        barcodeView.resume()
        onDispose {
            barcodeView.barcodeView.stopDecoding()
            barcodeView.pause()
        }
    }

    AndroidView(
        factory = { barcodeView },
        modifier = Modifier.fillMaxSize()
    )
}
