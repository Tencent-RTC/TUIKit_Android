package com.trtc.uikit.roomkit.base.service

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
import com.trtc.uikit.roomkit.R
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/// 会中采集前台服务（对齐老工程 `KeepAliveService` + atomic-x `VideoForegroundService`）：
/// Android 14+ 切后台继续摄像头/麦克风采集必须由对应类型的前台服务承载，服务本身同时保证进程后台存活。
///
/// 类型按当前权限组合动态决定并做类型级幂等：已运行且类型未变不重复投递；
/// 权限组合变化（如仅麦克风 → 补授权摄像头）时通过 startForeground 原地升级，无需重启服务。
class RoomForegroundService : Service() {

    companion object {

        private const val CHANNEL_ID = "roomkit_foreground_service"
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
            val intent = Intent(appContext, RoomForegroundService::class.java)
                .putExtra(EXTRA_TYPE, type)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                appContext.startForegroundService(intent)
            } else {
                appContext.startService(intent)
            }
        }

        fun stop(context: Context) {
            if (!running.compareAndSet(true, false)) return
            context.applicationContext.stopService(Intent(context, RoomForegroundService::class.java))
        }

        /// Android 14 要求服务类型必须有对应运行时权限，否则抛 ForegroundServiceTypeException：
        /// 按权限组合降级（双权限 → camera|microphone；仅麦克风 → microphone；仅摄像头 → camera；都无 → 0 不启动）。
        private fun serviceType(context: Context): Int {
            val camera = hasPermission(context, Manifest.permission.CAMERA)
            val microphone = hasPermission(context, Manifest.permission.RECORD_AUDIO)
            return when {
                camera && microphone ->
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE

                camera -> ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
                microphone -> ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                else -> 0
            }
        }

        private fun hasPermission(context: Context, permission: String): Boolean =
            ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
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
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(applicationInfo.icon)
            .setContentTitle(appName)
            .setContentText(getString(R.string.roomkit_foreground_service_running))
            .build()
        val type = intent?.getIntExtra(EXTRA_TYPE, 0) ?: 0
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && type != 0) {
            startForeground(NOTIFICATION_ID, notification, type)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        // start 后立刻 stop 的竞态：stop 先翻转了标记，这里直接结束，避免空跑的前台通知
        if (!running.get()) stopSelf()
        return START_NOT_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        stopSelf()
        running.set(false)
    }
}
