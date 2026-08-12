package com.unichat.app.sync

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.unichat.app.R

/**
 * 后台保活前台服务。
 *
 * HyperOS 会冻结后台进程,导致微信/抖音无法通过 ContentProvider 上报数据。
 * 本服务常驻前台(持续通知),让 UniChat 退到后台也不被冻结。
 */
class KeepAliveService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startForeground(NOTIFICATION_ID, buildNotification())
    }

    private fun buildNotification() = NotificationCompat.Builder(this, CHANNEL)
        .setContentTitle("UniChat")
        .setContentText("正在同步微信/抖音消息,请勿关闭")
        .setSmallIcon(R.mipmap.ic_launcher)
        .setOngoing(true)
        .setPriority(NotificationCompat.PRIORITY_LOW)
        .build()

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    companion object {
        private const val CHANNEL = "unichat_keepalive"
        private const val NOTIFICATION_ID = 2

        /** 确保通知渠道存在(幂等) */
        fun ensureChannel(nm: NotificationManager) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                nm.createNotificationChannel(
                    NotificationChannel(CHANNEL, "后台同步", NotificationManager.IMPORTANCE_LOW).apply {
                        description = "保持 UniChat 后台运行,持续同步消息"
                    }
                )
            }
        }
    }
}
