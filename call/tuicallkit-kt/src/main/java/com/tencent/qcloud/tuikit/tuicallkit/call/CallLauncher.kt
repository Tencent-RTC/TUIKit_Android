package com.tencent.qcloud.tuikit.tuicallkit.call

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.tencent.qcloud.tuikit.tuicallkit.R
import com.tencent.qcloud.tuikit.tuicallkit.call.callview.CallActivity
import com.tencent.qcloud.tuikit.tuicallkit.call.incoming.IncomingBannerView
import com.tencent.qcloud.tuikit.tuicallkit.common.CallKitContactLoader
import com.tencent.qcloud.tuikit.tuicallkit.common.CallKitContext
import com.tencent.qcloud.tuikit.tuicallkit.common.CallKitLogger
import com.tencent.qcloud.tuikit.tuicallkit.common.CallKitToast
import io.trtc.tuikit.atomicxcore.api.CompletionHandler
import io.trtc.tuikit.atomicxcore.api.ai.AITranscriberStore
import io.trtc.tuikit.atomicxcore.api.ai.SourceLanguage
import io.trtc.tuikit.atomicxcore.api.ai.TranscriptionConfig
import io.trtc.tuikit.atomicxcore.api.call.CallEndReason
import io.trtc.tuikit.atomicxcore.api.call.CallListener
import io.trtc.tuikit.atomicxcore.api.call.CallMediaType
import io.trtc.tuikit.atomicxcore.api.call.CallParticipantStatus
import io.trtc.tuikit.atomicxcore.api.call.CallStore
import io.trtc.tuikit.atomicxcore.api.device.DeviceStore
import io.trtc.tuikit.atomicxcore.api.login.LoginStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

internal object CallLauncher {

    private val logger = CallKitLogger.getLogger("CallLauncher")
    private const val INCOMING_CHANNEL_ID = "tuicallkit_incoming_call"
    private const val INCOMING_NOTIFICATION_ID = 4201

    private var isHangingUp = false
    private var isAnswering = false
    private var expandedIncomingCallId = ""
    private var preparedCallId = ""
    private var transcriber: AITranscriberStore? = null
    private var runningCallId = ""
    private var appliedLanguage: SourceLanguage? = null
    private var transcriptionGeneration = 0

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private val callListener = object : CallListener() {
        override fun onCallEnded(callId: String, mediaType: CallMediaType, reason: CallEndReason, userId: String) {
            scope.launch { handleCallEnded(reason, userId) }
        }
    }

    fun init() {
        scope.launch {
            combine(
                CallStore.shared.observerState.activeCall,
                CallStore.shared.observerState.selfInfo
            ) { call, self -> "${call.callId}|${self.status}|${call.inviterId}|${call.mediaType}" }
                .distinctUntilChanged()
                .collect { routeToCurrentState() }
        }
        CallStore.shared.addListener(callListener)
        Ringtone
        setupTranscriptionObserver()
    }

    private fun setupTranscriptionObserver() {
        scope.launch {
            CallKitConfig.shared.transcriptionChanged.collect { applyTranscriptionState() }
        }
        scope.launch {
            combine(
                CallStore.shared.observerState.activeCall,
                CallStore.shared.observerState.selfInfo
            ) { call, self -> "${call.callId}|${self.status}" }
                .distinctUntilChanged()
                .collect { applyTranscriptionState() }
        }
    }

    private val sourceLanguage: SourceLanguage
        get() = when (CallKitConfig.shared.transcriptionSourceLanguage) {
            "zh" -> SourceLanguage.CHINESE
            "en" -> SourceLanguage.ENGLISH
            "ar" -> SourceLanguage.ARABIC
            "ja" -> SourceLanguage.JAPANESE
            else -> SourceLanguage.CHINESE_ENGLISH
        }

    private fun applyTranscriptionState() {
        val state = CallStore.shared.observerState
        val callId = state.activeCall.value.callId
        val shouldRun = CallKitConfig.shared.enableAITranscriber
            && callId.isNotEmpty()
            && state.selfInfo.value.status == CallParticipantStatus.Accept

        if (!shouldRun) {
            stopTranscriber()
            return
        }
        if (transcriber == null || runningCallId != callId) {
            startTranscriber(callId)
        } else if (appliedLanguage != sourceLanguage) {
            updateTranscriberLanguages()
        }
    }

    private fun startTranscriber(callId: String) {
        stopTranscriber()
        val generation = ++transcriptionGeneration
        val store = AITranscriberStore.create(callId)
        transcriber = store
        runningCallId = callId
        appliedLanguage = sourceLanguage
        store.startTranscription(sourceLanguage, TranscriptionConfig(enableTranslation = true), object : CompletionHandler {
            override fun onSuccess() {}
            override fun onFailure(code: Int, desc: String) {
                if (generation != transcriptionGeneration || runningCallId != callId || transcriber !== store) {
                    return
                }
                logger.error("startTranscription failed: code=$code desc=$desc")
                val context = CallKitContext.requireContext().applicationContext
                CallKitToast.show(context.getString(R.string.tuicallkit_cc_start_failed, "$code $desc"))
                scope.launch {
                    CallKitConfig.shared.updateAITranscriber(false)
                }
            }
        })
    }

    private fun updateTranscriberLanguages() {
        appliedLanguage = sourceLanguage
        transcriber?.updateTranscription(sourceLanguage, TranscriptionConfig(enableTranslation = true), object : CompletionHandler {
            override fun onSuccess() {}
            override fun onFailure(code: Int, desc: String) {
                logger.error("updateTranscription failed: code=$code desc=$desc")
            }
        })
    }

    private fun stopTranscriber() {
        transcriptionGeneration += 1
        transcriber?.stopTranscription(object : CompletionHandler {
            override fun onSuccess() {}
            override fun onFailure(code: Int, desc: String) {
                logger.warn("stopTranscription failed: code=$code desc=$desc")
            }
        })
        transcriber = null
        runningCallId = ""
        appliedLanguage = null
    }

    fun hangup() {
        val callId = CallStore.shared.observerState.activeCall.value.callId
        if (isHangingUp || callId.isEmpty()) {
            return
        }
        isHangingUp = true
        CallStore.shared.hangup(object : CompletionHandler {
            override fun onSuccess() = resetHangingUp()
            override fun onFailure(code: Int, desc: String) {
                logger.error("hangup failed: code=$code desc=$desc")
                resetHangingUp()
            }
        })
    }

    fun acceptIncoming() {
        respondIncoming(accept = true)
    }

    fun rejectIncoming() {
        respondIncoming(accept = false)
    }

    private fun respondIncoming(accept: Boolean) {
        if (isAnswering) {
            return
        }
        isAnswering = true
        val handler = object : CompletionHandler {
            override fun onSuccess() = resetAnswering()
            override fun onFailure(code: Int, desc: String) {
                logger.error("respondIncoming(accept=$accept) failed: code=$code desc=$desc")
                resetAnswering()
            }
        }
        if (accept) {
            CallStore.shared.accept(handler)
        } else {
            CallStore.shared.reject(handler)
        }
    }

    private fun resetHangingUp() {
        scope.launch { isHangingUp = false }
    }

    private fun resetAnswering() {
        scope.launch { isAnswering = false }
    }

    private fun routeToCurrentState() {
        val call = CallStore.shared.observerState.activeCall.value
        val selfID = LoginStore.shared.loginState.loginUserInfo.value?.userID.orEmpty()
        val isOutgoing = call.inviterId.isNotEmpty() && call.inviterId == selfID
        val status = CallStore.shared.observerState.selfInfo.value.status

        if (call.callId.isEmpty()) {
            dismissAllCallUI()
            return
        }

        when {
            status == CallParticipantStatus.Waiting && isOutgoing -> {
                prepareCall(call.callId)
                showCallViewIfNeeded()
                Ringtone.startDialTone()
            }
            status == CallParticipantStatus.Waiting -> {
                val bannerEnabled = CallKitConfig.shared.enableIncomingBanner
                val isExpanded = expandedIncomingCallId == call.callId
                val hasFullScreen = CallWindow.isShowing
                val isForeground = CallKitContext.isForeground
                val wantsBanner = bannerEnabled && !isExpanded && !hasFullScreen && isForeground
                logger.info(
                    "incoming route: enabled=$bannerEnabled foreground=$isForeground " +
                        "expanded=$isExpanded fullScreen=$hasFullScreen target=${if (wantsBanner) "banner" else "fullscreen"}"
                )
                if (wantsBanner) {
                    showIncomingBanner()
                } else {
                    showCallViewIfNeeded()
                }
                Ringtone.startRinging()
                postIncomingNotificationIfBackground()
            }
            status == CallParticipantStatus.Accept -> {
                cancelIncomingNotification()
                CallWindow.dismissBanner()
                prepareCall(call.callId)
                showCallViewIfNeeded()
                Ringtone.stopAll()
            }
            else -> {}
        }
    }

    private fun showIncomingBanner() {
        if (CallWindow.isBannerShowing) {
            return
        }
        val context = CallKitContext.topActivity() ?: CallKitContext.requireContext()
        val mediaType = CallStore.shared.observerState.activeCall.value.mediaType
        val banner = IncomingBannerView(context, mediaType)
        banner.reload()
        banner.onAccept = { acceptIncoming() }
        banner.onReject = { rejectIncoming() }
        banner.onExpand = {
            expandedIncomingCallId = CallStore.shared.observerState.activeCall.value.callId
            CallWindow.dismissBanner()
            showCallViewIfNeeded()
        }
        CallWindow.showBanner(banner)
    }

    private fun showCallViewIfNeeded() {
        if (CallWindow.isShowing) {
            return
        }
        CallWindow.showFullScreen()
    }

    private fun dismissAllCallUI() {
        Ringtone.stopAll()
        CallWindow.dismissBanner()
        cancelIncomingNotification()
        if (CallWindow.isShowing) {
            CallWindow.dismiss()
        }
        CallKeepAliveService.stop(CallKitContext.requireContext())
        teardownCall()
        expandedIncomingCallId = ""
    }

    private fun handleCallEnded(reason: CallEndReason, userId: String) {
        dismissAllCallUI()
        val selfID = LoginStore.shared.loginState.loginUserInfo.value?.userID.orEmpty()
        if (userId == selfID) {
            return
        }
        val context = CallKitContext.requireContext()
        when (reason) {
            CallEndReason.Reject, CallEndReason.LineBusy ->
                CallKitToast.show(context.getString(R.string.tuicallkit_call_peer_rejected))
            CallEndReason.NoResponse ->
                CallKitToast.show(context.getString(R.string.tuicallkit_call_peer_no_answer))
            CallEndReason.Canceled ->
                CallKitToast.show(context.getString(R.string.tuicallkit_call_peer_canceled))
            CallEndReason.Hangup ->
                CallKitToast.show(context.getString(R.string.tuicallkit_call_call_ended))
            else -> {}
        }
    }

    private fun postIncomingNotificationIfBackground() {
        if (CallKitContext.isForeground) {
            return
        }
        val context = CallKitContext.requireContext()
        try {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                manager.createNotificationChannel(
                    NotificationChannel(
                        INCOMING_CHANNEL_ID,
                        context.getString(R.string.tuicallkit_incoming_channel_name),
                        NotificationManager.IMPORTANCE_HIGH
                    )
                )
            }
            val call = CallStore.shared.observerState.activeCall.value
            val base = context.getString(
                if (call.mediaType == CallMediaType.Video) R.string.tuicallkit_incoming_video
                else R.string.tuicallkit_incoming_audio
            )
            val content = if (CallKitConfig.shared.enableMuteMode) {
                base + context.getString(R.string.tuicallkit_incoming_muted_suffix)
            } else {
                base
            }
            CallKitContactLoader.load(listOf(call.inviterId)) { _, nickname, _, _ ->
                val title = nickname?.takeIf { it.isNotEmpty() } ?: call.inviterId
                notifyIncoming(context, title, content)
            }
        } catch (e: SecurityException) {
            logger.warn("incoming notification denied: ${e.message}")
        } catch (e: Exception) {
            logger.warn("incoming notification failed: ${e.javaClass.simpleName}")
        }
    }

    private fun notifyIncoming(context: Context, title: String, content: String) {
        try {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val fullScreenIntent = PendingIntent.getActivity(
                context,
                0,
                Intent(context, CallActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Notification.Builder(context, INCOMING_CHANNEL_ID)
            } else {
                @Suppress("DEPRECATION")
                Notification.Builder(context)
            }
            val notification = builder
                .setSmallIcon(R.drawable.tuicallkit_icon_accept)
                .setContentTitle(title)
                .setContentText(content)
                .setCategory(Notification.CATEGORY_CALL)
                .setOngoing(true)
                .setFullScreenIntent(fullScreenIntent, true)
                .setContentIntent(fullScreenIntent)
                .build()
            manager.notify(INCOMING_NOTIFICATION_ID, notification)
        } catch (e: SecurityException) {
            logger.warn("incoming notification denied: ${e.message}")
        } catch (e: Exception) {
            logger.warn("incoming notification failed: ${e.javaClass.simpleName}")
        }
    }

    private fun cancelIncomingNotification() {
        val context = CallKitContext.requireContext()
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.cancel(INCOMING_NOTIFICATION_ID)
    }

    private fun prepareCall(callId: String) {
        if (preparedCallId == callId) {
            return
        }
        preparedCallId = callId
        val isVideo = CallStore.shared.observerState.activeCall.value.mediaType == CallMediaType.Video
        setDefaultAudioRoute(earpiece = !isVideo)
    }

    private fun teardownCall() {
        preparedCallId = ""
        setDefaultAudioRoute(earpiece = false)
    }

    private fun setDefaultAudioRoute(earpiece: Boolean) {
        val targetId = if (earpiece) "speaker_0" else "speaker_1"
        val device = DeviceStore.shared().deviceState.speakerList.value.firstOrNull { it.deviceId == targetId } ?: return
        DeviceStore.shared().setCurrentSpeaker(device)
    }
}
