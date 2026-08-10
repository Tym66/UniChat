package com.unichat.app.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** 平台标识 */
object Platform {
    const val WECHAT = "wechat"
    const val DOUYIN = "douyin"
}

/** 聚合联系人:同一人跨平台资料归并 */
@Entity(
    tableName = "contacts",
    indices = [Index(value = ["name"]), Index(value = ["phone"]), Index(value = ["wechatId"]), Index(value = ["douyinId"])]
)
data class Contact(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** 显示名 */
    val name: String = "",
    /** 手机号(用于跨平台匹配) */
    val phone: String = "",
    /** 微信 ID 或 微信号 */
    val wechatId: String = "",
    /** 抖音 ID */
    val douyinId: String = "",
    /** 备注 */
    val remark: String = "",
    /** 头像(本地文件路径或 URI) */
    val avatarPath: String = "",
    /** 最近一条消息预览 */
    val lastMessage: String = "",
    /** 最近消息时间 */
    val lastTime: Long = 0,
    /** 未读数 */
    val unreadCount: Int = 0,
    /** 已接入平台,如 "wechat" / "douyin" / "wechat,douyin" */
    val platforms: String = "",
    /** 更新时间戳 */
    val updatedAt: Long = System.currentTimeMillis()
)
