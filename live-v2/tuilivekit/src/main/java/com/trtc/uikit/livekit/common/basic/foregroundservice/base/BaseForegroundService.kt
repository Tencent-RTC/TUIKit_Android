package com.trtc.uikit.livekit.common.basic.foregroundservice.base

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import com.tencent.qcloud.tuicore.util.TUIBuild
import com.trtc.uikit.livekit.R
import com.trtc.uikit.livekit.common.basic.ContextProvider

abstract class BaseForegroundService(
    private val launcher: ServiceLauncher<*>
) : Service() {

    companion object {
        const val TITLE = "title"
        const val ICON = "icon"
        const val DESCRIPTION = "description"
        const val NOTIFICATION_ID = 1001
    }

    abstract fun provideForegroundServiceType(): Int
    
    abstract fun provideChannelId(): String

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) {
            stopSelf()
            launcher.updateState(ServiceState.IDLE)
            return START_NOT_STICKY
        }

        val appContext = ContextProvider.applicationContext
        val title = intent.getStringExtra(TITLE)
        val description = intent.getStringExtra(DESCRIPTION)
        val icon = intent.getIntExtra(ICON, appContext?.applicationInfo?.icon ?: 0)

        try {
            val notification = createNotification(title, description, icon)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                startForeground(NOTIFICATION_ID, notification, provideForegroundServiceType())
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (error: RuntimeException) {
            Log.e(javaClass.simpleName, "Unable to promote foreground service", error)
            launcher.updateState(ServiceState.IDLE)
            stopSelf()
            return START_NOT_STICKY
        }

        if (launcher.state == ServiceState.STOPPING) {
            stopSelf()
            launcher.updateState(ServiceState.IDLE)
        } else {
            launcher.updateState(ServiceState.RUNNING)
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        stopSelf()
        launcher.updateState(ServiceState.IDLE)
    }

    override fun onDestroy() {
        super.onDestroy()
        clearNotification()
        // A new start may already be queued while the old instance is destroyed.
        if (launcher.state == ServiceState.RUNNING) launcher.updateState(ServiceState.IDLE)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun createNotification(title: String?, desc: String?, icon: Int): Notification {
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        val channelId = provideChannelId()
        if (TUIBuild.getVersionInt() >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                applicationContext.getString(R.string.livekit_rtc_channel_name),
                NotificationManager.IMPORTANCE_LOW
            )
            manager.createNotificationChannel(channel)
        }
        return NotificationCompat.Builder(this, channelId)
            .setSmallIcon(icon)
            .setOngoing(true)
            .setContentTitle(title)
            .setContentText(desc)
            .build()
    }

    private fun clearNotification() {
        val manager = getSystemService(NOTIFICATION_SERVICE) as? NotificationManager
        manager?.cancel(NOTIFICATION_ID)
    }
}