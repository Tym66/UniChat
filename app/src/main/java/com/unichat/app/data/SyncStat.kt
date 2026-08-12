package com.unichat.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 各平台接入同步状态(诊断用)。
 *
 * Hook 注入微信/抖音进程后,会通过 Provider 上报心跳与同步统计,
 * 用于在 UI 上直观显示"哪个平台已接入、最近一次同步是什么时候"。
 */
@Entity(tableName = "sync_stats")
data class SyncStat(
    /** 平台:wechat / douyin */
    @PrimaryKey val platform: String,
    /** 模块是否已成功注入该平台进程 */
    val hookInstalled: Boolean = false,
    /** 最近一次同步时间(毫秒) */
    val lastSyncAt: Long = 0,
    /** 累计同步的消息条数 */
    val msgCount: Int = 0
)
