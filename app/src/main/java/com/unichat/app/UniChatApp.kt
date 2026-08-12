package com.unichat.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.unichat.app.data.AppDatabase
import com.unichat.app.sync.KeepAliveService

class UniChatApp : Application() {

    companion object {
        const val CHANNEL_MSG = "unichat_messages"
        lateinit var instance: UniChatApp
            private set
    }

    val database: AppDatabase by lazy { AppDatabase.get(this) }

    override fun onCreate() {
        super.onCreate()
        instance = this
        createNotificationChannel()
        startKeepAlive()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_MSG,
            "聚合消息",
            NotificationManager.IMPORTANCE_HIGH
        ).apply { description = "微信/抖音聚合后的新消息提醒" }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    /** 启动后台保活前台服务(避免被 HyperOS 冻结) */
    private fun startKeepAlive() {
        KeepAliveService.ensureChannel(getSystemService(NotificationManager::class.java))
        ContextCompat.startForegroundService(
            this,
            Intent(this, KeepAliveService::class.java)
        )
    }
}
