package com.unichat.app.data.provider

import android.content.ContentProvider
import android.content.ContentValues
import android.content.UriMatcher
import android.database.Cursor
import android.net.Uri
import android.os.Binder
import android.os.Bundle
import com.unichat.app.data.Contact
import com.unichat.app.data.ContactDao
import com.unichat.app.data.Direction
import com.unichat.app.data.Message
import com.unichat.app.data.MsgType
import com.unichat.app.data.Platform
import com.unichat.app.data.AppDatabase
import com.unichat.app.data.SyncStat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

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
            METHOD_HOOK_HELLO -> {
                // 模块已注入目标进程(心跳),记录接入状态
                val platform = extras?.getString(KEY_PLATFORM) ?: return null
                scope.launch {
                    db.syncStatDao().upsert(
                        SyncStat(
                            platform = platform,
                            hookInstalled = true,
                            lastSyncAt = System.currentTimeMillis()
                        )
                    )
                }
                Bundle().apply { putBoolean("ok", true) }
            }
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
                    bumpSync(db, msg.platform)
                }
                Bundle().apply { putBoolean("ok", true) }
            }
            METHOD_INGEST_CONTACT -> {
                val c = extras?.toContact() ?: return null
                scope.launch {
                    val dao = db.contactDao()
                    val existing = findMatchingContact(dao, c)
                    if (existing == null) dao.insert(c)
                    else {
                        val merged = mergeContact(existing, c)
                        dao.update(merged)
                    }
                    bumpSync(db, c.platforms)
                }
                Bundle().apply { putBoolean("ok", true) }
            }
            METHOD_MARK_READ -> {
                // 已读同步:优先按平台+联系人 id;若只有平台 peer id(Hook 场景)则解析为联系人 id
                val platform = extras?.getString(KEY_PLATFORM) ?: ""
                val peerId = extras?.getString(KEY_PEER_ID) ?: ""
                val directId = extras?.getLong(KEY_CONTACT_ID) ?: 0L
                if (platform.isNotBlank()) {
                    scope.launch {
                        var contactId = directId
                        if (contactId <= 0 && peerId.isNotBlank()) {
                            contactId = db.contactDao().findByPlatformId(peerId)?.id ?: 0L
                        }
                        if (contactId > 0) {
                            db.messageDao().markPlatformRead(contactId, platform)
                            db.contactDao().markRead(contactId, System.currentTimeMillis())
                        }
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
        val phone = extras.getString(KEY_PHONE) ?: ""
        val dao = db.contactDao()

        // 1. 该平台 ID 已存在 → 直接命中
        dao.findByPlatformId(peer)?.let { return it.id }

        // 2. 携带了手机号 → 跨平台归并(微信/抖音联系人资料通常带手机号)
        if (phone.isNotBlank()) {
            val byPhone = dao.findByPhone(phone)
            if (byPhone != null) {
                val updated = if (msg.platform == Platform.WECHAT)
                    byPhone.copy(wechatId = peer, platforms = mergePlatforms(byPhone.platforms, msg.platform))
                else
                    byPhone.copy(douyinId = peer, platforms = mergePlatforms(byPhone.platforms, msg.platform))
                dao.update(updated)
                return byPhone.id
            }
        }

        // 3. 平台 ID 本身就是手机号(如短信会话) → 按手机号归并
        if (isPhoneLike(peer)) {
            val byPhone = dao.findByPhone(peer)
            if (byPhone != null) {
                val updated = if (msg.platform == Platform.WECHAT)
                    byPhone.copy(wechatId = peer, platforms = mergePlatforms(byPhone.platforms, msg.platform))
                else
                    byPhone.copy(douyinId = peer, platforms = mergePlatforms(byPhone.platforms, msg.platform))
                dao.update(updated)
                return byPhone.id
            }
        }

        // 创建新联系人
        val contact = Contact(
            name = peerName,
            phone = phone,
            wechatId = if (msg.platform == Platform.WECHAT) peer else "",
            douyinId = if (msg.platform == Platform.DOUYIN) peer else "",
            platforms = msg.platform,
            lastTime = msg.timestamp
        )
        return dao.insert(contact)
    }

    /** 联系人入库时寻找可归并的既有联系人(同平台 ID / 同手机号) */
    private suspend fun findMatchingContact(
        dao: ContactDao,
        c: Contact
    ): Contact? {
        val platformId = if (c.wechatId.isNotBlank()) c.wechatId else c.douyinId
        if (platformId.isNotBlank()) {
            dao.findByPlatformId(platformId)?.let { return it }
        }
        if (c.phone.isNotBlank()) {
            dao.findByPhone(c.phone)?.let { return it }
        }
        return null
    }

    private fun mergeContact(old: Contact, incoming: Contact): Contact {
        return old.copy(
            name = incoming.name.ifBlank { old.name },
            phone = incoming.phone.ifBlank { old.phone },
            wechatId = incoming.wechatId.ifBlank { old.wechatId },
            douyinId = incoming.douyinId.ifBlank { old.douyinId },
            remark = incoming.remark.ifBlank { old.remark },
            avatarPath = incoming.avatarPath.ifBlank { old.avatarPath },
            platforms = mergePlatforms(old.platforms, incoming.platforms),
            updatedAt = System.currentTimeMillis()
        )
    }

    private fun mergePlatforms(old: String, new: String): String {
        return (old.split(",") + new.split(","))
            .filter { it.isNotBlank() }
            .distinct()
            .joinToString(",")
    }

    /** 平台 ID 形如手机号(纯数字 7~15 位) */
    private fun isPhoneLike(s: String): Boolean {
        val digits = s.filter { it.isDigit() }
        return digits.isNotEmpty() && digits == s && digits.length in 7..15
    }

    /** 记录平台同步统计(最近时间 + 累计条数) */
    private suspend fun bumpSync(db: AppDatabase, platform: String) {
        if (platform.isBlank()) return
        val dao = db.syncStatDao()
        val cur = dao.get(platform)
        dao.upsert(
            SyncStat(
                platform = platform,
                hookInstalled = cur?.hookInstalled ?: true,
                lastSyncAt = System.currentTimeMillis(),
                msgCount = (cur?.msgCount ?: 0) + 1
            )
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
        const val METHOD_HOOK_HELLO = "hook_hello"

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
