package com.tencent.rtcube.v2.launchanim

import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.VideoView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.net.toUri
import kotlinx.coroutines.delay

private const val FADE_OUT_DURATION_MS = 300
private const val PLAYBACK_TIMEOUT_MILLIS = 8_000L

@Composable
fun LaunchAnimationOverlay(onAnimationFinished: () -> Unit) {
    val context = LocalContext.current
    val rawResId = LaunchAnimationCoordinator.videoRawResId(context)
    if (rawResId == 0) {
        LaunchedEffect(Unit) { onAnimationFinished() }
        return
    }

    val visibilityState = remember { MutableTransitionState(initialState = true) }
    val finish = {
        if (visibilityState.targetState) {
            LaunchAnimationCoordinator.markPlayedForCurrentVersion(context)
            visibilityState.targetState = false
        }
    }

    LaunchedEffect(Unit) {
        delay(PLAYBACK_TIMEOUT_MILLIS)
        finish()
    }

    LaunchedEffect(visibilityState.currentState, visibilityState.isIdle) {
        if (!visibilityState.targetState && !visibilityState.currentState && visibilityState.isIdle) {
            onAnimationFinished()
        }
    }

    AnimatedVisibility(
        visibleState = visibilityState,
        exit = fadeOut(animationSpec = tween(FADE_OUT_DURATION_MS)),
    ) {
        LaunchAnimationVideoView(rawResId, onFinished = finish)
    }
}

@Composable
private fun LaunchAnimationVideoView(
    videoRawResId: Int,
    onFinished: () -> Unit,
) {
    val context = LocalContext.current
    val videoUri = remember { "android.resource://${context.packageName}/$videoRawResId".toUri() }
    Box(modifier = Modifier
        .fillMaxSize()
        .background(Color.White)
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                VideoView(ctx).apply {
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT,
                    )
                    setVideoURI(videoUri)
                    setOnPreparedListener { mp ->
                        mp.isLooping = false
                        start()
                    }
                    setOnCompletionListener { onFinished() }
                    setOnErrorListener { _, _, _ ->
                        onFinished()
                        true
                    }
                }
            },
        )
    }
}
