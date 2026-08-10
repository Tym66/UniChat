package com.unichat.app.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** 消息类型 */
object MsgType {
    const val TEXT = "text"
    const val IMAGE = "image"
    const val VOICE = "voice"
    const val VIDEO = "video"
    const val FILE = "file"
    const val LINK = "link"
    const val SYSTEM = "system"
}

/** 方向 */
object Direction {
    const val IN = "in"   // 收到
    const val OUT = "out" // 发出
}

/** 聚合消息(微信 + 抖音统一格式) */
@Entity(
    tableName = "messages",
    indices = [
        Index(value = ["contactId", "timestamp"]),
        Index(value = ["platform", "platformMsgId"], unique = true)
    ]
)
data class Message(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** 关联的聚合联系人 id */
    val contactId: Long = 0,
    /** 来源平台:wechat / douyin */
    val platform: String = "",
    /** 平台原始消息 id(去重) */
    val platformMsgId: String = "",
    /** 方向:in / out */
    val direction: String = Direction.IN,
    /** 消息类型:text / image / voice ... */
    val type: String = MsgType.TEXT,
    /** 文本内容(非文本类型存描述或文件路径) */
    val content: String = "",
    /** 消息时间戳(毫秒) */
    val timestamp: Long = 0,
    /** 是否已读 */
    val isRead: Boolean = false,
    /** 原始数据 JSON(备用) */
    val rawJson: String = ""
)
