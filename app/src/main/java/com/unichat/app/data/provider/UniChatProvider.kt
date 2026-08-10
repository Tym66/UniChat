package com.unichat.app.data.provider

import android.content.ContentProvider
import android.content.ContentValues
import android.content.UriMatcher
import android.database.Cursor
import android.net.Uri
import android.os.Binder
import android.os.Bundle
import com.unichat.app.data.Contact
import com.unichat.app.data.Direction
import com.unichat.app.data.Message
import com.unichat.app.data.MsgType
import com.unichat.app.data.Platform
import com.unichat.app.data.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/**
 * 跨进程数据通道。
 *
 * LSPosed Hook 注入到微信/抖音进程后,无法直接访问本 App 的数据库,
 * 通过本 Provider 将消息/联系人/已读状态写入。
 *
 * 安全:只允许微信、抖音、本应用调用,其余包名一律拒绝。
 */
class UniChatProvider : ContentProvider() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate(): Boolean = true

    // ============ 方法通道(推荐,避免 ContentValues 拼装) ============

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
        if (!checkCaller()) return null
        val db = AppDatabase.get(context!!)
        return when (method) {
            METHOD_INGEST_MESSAGE -> {
                val msg = extras?.toMessage() ?: return null
                scope.launch {
                    val contactId = resolveContactId(db, msg, extras)
                    if (contactId > 0) {
                        val m = msg.copy(contactId = contactId)
                        val id = db.messageDao().insert(m)
                        if (id > 0 && m.direction == Direction.IN) {
                            db.contactDao().bump(contactId, m.content.take(50), m.timestamp, System.currentTimeMillis())
                        }
                    }
                }
                Bundle().apply { putBoolean("ok", true) }
            }
            METHOD_INGEST_CONTACT -> {
                val c = extras?.toContact() ?: return null
                scope.launch {
                    val dao = db.contactDao()
                    val existing = dao.findByPlatformId(
                        if (c.wechatId.isNotBlank()) c.wechatId else c.douyinId
                    )
                    if (existing == null) dao.insert(c)
                    else {
                        val merged = mergeContact(existing, c)
                        dao.update(merged)
                    }
                }
                Bundle().apply { putBoolean("ok", true) }
            }
            METHOD_MARK_READ -> {
                val contactId = extras?.getLong(KEY_CONTACT_ID) ?: 0L
                val platform = extras?.getString(KEY_PLATFORM) ?: ""
                if (contactId > 0) {
                    scope.launch {
                        db.messageDao().markPlatformRead(contactId, platform)
                        db.contactDao().markRead(contactId, System.currentTimeMillis())
                    }
                }
                Bundle().apply { putBoolean("ok", true) }
            }
            METHOD_MARK_READ_BY_PLATFORM_MSG -> {
                // 对方已读了我发的消息(抖音/微信回执),这里预留
                Bundle().apply { putBoolean("ok", true) }
            }
            else -> null
        }
    }

    /** 根据消息解析/归并联系人,返回聚合联系人 id */
    private suspend fun resolveContactId(
        db: AppDatabase,
        msg: Message,
        extras: Bundle
    ): Long {
        val peer = extras.getString(KEY_PEER_ID) ?: return 0L
        val peerName = extras.getString(KEY_PEER_NAME) ?: peer
        val dao = db.contactDao()
        val existing = dao.findByPlatformId(peer)
        if (existing != null) {
            return existing.id
        }
        // 创建新联系人
        val contact = Contact(
            name = peerName,
            wechatId = if (msg.platform == Platform.WECHAT) peer else "",
            douyinId = if (msg.platform == Platform.DOUYIN) peer else "",
            platforms = msg.platform,
            lastTime = msg.timestamp
        )
        return dao.insert(contact)
    }

    private fun mergeContact(old: Contact, incoming: Contact): Contact {
        val platforms = (old.platforms.split(",") + incoming.platforms.split(","))
            .filter { it.isNotBlank() }.distinct().joinToString(",")
        return old.copy(
            name = incoming.name.ifBlank { old.name },
            phone = incoming.phone.ifBlank { old.phone },
            wechatId = incoming.wechatId.ifBlank { old.wechatId },
            douyinId = incoming.douyinId.ifBlank { old.douyinId },
            remark = incoming.remark.ifBlank { old.remark },
            avatarPath = incoming.avatarPath.ifBlank { old.avatarPath },
            platforms = platforms,
            updatedAt = System.currentTimeMillis()
        )
    }

    /** 校验调用方:微信 / 抖音 / 本应用 */
    private fun checkCaller(): Boolean {
        val uid = Binder.getCallingUid()
        val pm = context?.packageManager ?: return false
        val pkgs = pm.getPackagesForUid(uid) ?: return false
        return pkgs.any {
            it == "com.tencent.mm" ||
                it == "com.ss.android.ugc.aweme" ||
                it == "com.unichat.app" ||
                it == "com.unichat.app.debug"
        }
    }

    // ============ 占位实现(不使用,仅满足抽象) ============

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = null
    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0

    companion object {
        const val AUTHORITY = "com.unichat.app.data"
        val CONTENT_URI: Uri = Uri.parse("content://$AUTHORITY")

        const val METHOD_INGEST_MESSAGE = "ingest_message"
        const val METHOD_INGEST_CONTACT = "ingest_contact"
        const val METHOD_MARK_READ = "mark_read"
        const val METHOD_MARK_READ_BY_PLATFORM_MSG = "mark_read_by_platform_msg"

        // Bundle keys
        const val KEY_PLATFORM = "platform"
        const val KEY_PLATFORM_MSG_ID = "platform_msg_id"
        const val KEY_PEER_ID = "peer_id"
        const val KEY_PEER_NAME = "peer_name"
        const val KEY_DIRECTION = "direction"
        const val KEY_TYPE = "type"
        const val KEY_CONTENT = "content"
        const val KEY_TIMESTAMP = "timestamp"
        const val KEY_CONTACT_ID = "contact_id"
        const val KEY_PHONE = "phone"
        const val KEY_REMARK = "remark"
        const val KEY_AVATAR = "avatar"

        /** hook 侧便捷方法:写入一条消息 */
        fun Bundle.toMessage(): Message? {
            val platform = getString(KEY_PLATFORM) ?: return null
            val platformMsgId = getString(KEY_PLATFORM_MSG_ID) ?: return null
            val content = getString(KEY_CONTENT) ?: ""
            return Message(
                platform = platform,
                platformMsgId = platformMsgId,
                direction = getString(KEY_DIRECTION) ?: Direction.IN,
                type = getString(KEY_TYPE) ?: MsgType.TEXT,
                content = content,
                timestamp = getLong(KEY_TIMESTAMP),
                isRead = getBoolean("is_read", false)
            )
        }

        fun Bundle.toContact(): Contact? {
            val peerId = getString(KEY_PEER_ID) ?: return null
            val platform = getString(KEY_PLATFORM) ?: return null
            return Contact(
                name = getString(KEY_PEER_NAME) ?: peerId,
                phone = getString(KEY_PHONE) ?: "",
                wechatId = if (platform == Platform.WECHAT) peerId else "",
                douyinId = if (platform == Platform.DOUYIN) peerId else "",
                remark = getString(KEY_REMARK) ?: "",
                avatarPath = getString(KEY_AVATAR) ?: "",
                platforms = platform
            )
        }
    }
}
