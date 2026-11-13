package com.tencent.qcloud.tuikit.tuicallkit.call

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.tencent.qcloud.tuikit.tuicallkit.R

internal class CallKeepAliveService : Service() {

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val typeMask = intent?.getIntExtra(EXTRA_TYPE_MASK, TYPE_MASK_MICROPHONE) ?: TYPE_MASK_MICROPHONE
        createChannel()
        val notification = Notification.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.tuicallkit_keepalive_notification))
            .setSmallIcon(R.drawable.tuicallkit_icon_mic)
            .setOngoing(true)
            .build()
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, typeMask)
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.tuicallkit_keepalive_notification),
                NotificationManager.IMPORTANCE_LOW
            )
            manager?.createNotificationChannel(channel)
        }
    }

    companion object {
        private const val CHANNEL_ID = "tuicallkit_call_keepalive"
        private const val NOTIFICATION_ID = 4101
        private const val EXTRA_TYPE_MASK = "tuicallkit.extra.keepaliveType"
        private const val TYPE_MASK_MICROPHONE = ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE

        fun start(context: Context, isVideo: Boolean) {
            val micGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
            if (!micGranted) {
                return
            }
            var typeMask = TYPE_MASK_MICROPHONE
            val cameraGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
            if (isVideo && cameraGranted) {
                typeMask = typeMask or ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
            }
            val intent = Intent(context, CallKeepAliveService::class.java)
                .putExtra(EXTRA_TYPE_MASK, typeMask)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, CallKeepAliveService::class.java))
        }
    }
}
