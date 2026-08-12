package com.unichat.app.data.provider

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.util.Log
import android.os.Binder
import android.os.Bundle
import com.unichat.app.data.Contact
import com.unichat.app.data.Direction
import com.unichat.app.data.Message
import com.unichat.app.data.MsgType
import com.unichat.app.data.Platform
import com.unichat.app.data.AppDatabase
import com.unichat.app.data.IngestHelper
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

    private val tag = "UniChatProvider"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate(): Boolean = true

    // ============ 方法通道(推荐,避免 ContentValues 拼装) ============

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
        if (!checkCaller()) {
            Log.w(tag, "call[$method] rejected by caller check")
            return null
        }
        Log.i(tag, "call[$method] from ${Binder.getCallingUid()} platform=${extras?.getString(KEY_PLATFORM)}")
        val db = try {
            AppDatabase.get(context!!)
        } catch (t: Throwable) {
            Log.e(tag, "db init failed", t)
            return null
        }
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
                    IngestHelper.ingestMessage(
                        db = db,
                        msg = msg,
                        peer = extras.getString(KEY_PEER_ID) ?: "",
                        peerName = extras.getString(KEY_PEER_NAME) ?: "",
                        phone = extras.getString(KEY_PHONE) ?: ""
                    )
                }
                Bundle().apply { putBoolean("ok", true) }
            }
            METHOD_INGEST_CONTACT -> {
                val c = extras?.toContact() ?: return null
                scope.launch {
                    IngestHelper.ingestContact(db, c)
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
                            contactId = IngestHelper.resolveContactIdForPeer(db, peerId)
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

    /** 校验调用方:微信 / 抖音 / 本应用 */
    private fun checkCaller(): Boolean {
        val uid = Binder.getCallingUid()
        val pm = context?.packageManager ?: return false
        val pkgs = pm.getPackagesForUid(uid) ?: return false
        val ok = pkgs.any {
            it == "com.tencent.mm" ||
                it == "com.ss.android.ugc.aweme" ||
                it == "com.unichat.app" ||
                it == "com.unichat.app.debug"
        }
        if (!ok) Log.w(tag, "checkCaller rejected uid=$uid pkgs=$pkgs")
        return ok
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
