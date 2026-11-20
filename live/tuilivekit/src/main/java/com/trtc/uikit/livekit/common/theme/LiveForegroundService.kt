package com.trtc.uikit.livekit.common.theme

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.trtc.uikit.livekit.R
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class LiveForegroundService : Service() {

    companion object {

        private const val CHANNEL_ID = "livekit_foreground_service"
        private const val NOTIFICATION_ID = 1001
        private const val EXTRA_TYPE = "foreground_service_type"

        private val running = AtomicBoolean(false)
        private val lastType = AtomicInteger(0)

        fun start(context: Context) {
            val appContext = context.applicationContext
            val type = serviceType(appContext)
            if (type == 0) return
            if (running.get() && lastType.get() == type) return
            running.set(true)
            lastType.set(type)
            val intent = Intent(appContext, LiveForegroundService::class.java).putExtra(EXTRA_TYPE, type)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                appContext.startForegroundService(intent)
            } else {
                appContext.startService(intent)
            }
        }

        fun stop(context: Context) {
            if (!running.compareAndSet(true, false)) return
            context.applicationContext.stopService(Intent(context, LiveForegroundService::class.java))
        }

        private fun serviceType(context: Context): Int {
            val microphone = ContextCompat.checkSelfPermission(
                context, Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
            return if (microphone) ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE else 0
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val appName = applicationInfo.loadLabel(packageManager).toString()
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, appName, NotificationManager.IMPORTANCE_LOW)
            )
        }
        val notification =
            NotificationCompat.Builder(this, CHANNEL_ID).setSmallIcon(applicationInfo.icon).setContentTitle(appName)
                .setContentText(getString(R.string.livekit_coguest_service_description)).build()
        val type = intent?.getIntExtra(EXTRA_TYPE, 0) ?: 0
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && type != 0) {
            startForeground(NOTIFICATION_ID, notification, type)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        if (!running.get()) stopSelf()
        return START_NOT_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        stopSelf()
        running.set(false)
    }
}
