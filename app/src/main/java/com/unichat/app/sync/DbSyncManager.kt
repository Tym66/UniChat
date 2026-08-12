package com.unichat.app.sync

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import org.json.JSONObject
import com.unichat.app.data.AppDatabase
import com.unichat.app.data.Contact
import com.unichat.app.data.Direction
import com.unichat.app.data.IngestHelper
import com.unichat.app.data.Message
import com.unichat.app.data.MsgType
import com.unichat.app.data.Platform
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 直接读取平台数据库的同步器(root 方式)。
 *
 * 微信/抖音聊天数据最终都落在本地 SQLite,而 Hook 写 API 可能拦不到
 * (新版微信走 native 写库)。本类用 root 把平台数据库拷贝到应用缓存,
 * 再用 Android SQLite 增量读取 message/rcontact 表,写入聚合库。
 */
class DbSyncManager(private val appContext: Context) {

    companion object {
        private const val TAG = "UniChatSync"
        private const val PREFS = "sync_cursor"
        private const val KEY_WECHAT_LAST = "wechat_last_msgid"
        private const val KEY_WECHAT_WAL_MT = "wechat_copied_wal_mt"
        private const val KEY_DOUYIN_LAST = "douyin_last_msgid"
        /** 单次同步最多导入条数(首次全量很大,分批避免卡死) */
        private const val BATCH_LIMIT = 3000

        /** 微信内置账号的友好名称 */
        private val KNOWN_ACCOUNTS = mapOf(
            "filehelper" to "文件传输助手",
            "floatbottle" to "漂流瓶",
            "qqmail" to "QQ邮箱提醒",
            "tmessage" to "订阅号消息",
            "notification_messages" to "服务通知",
            "medianote" to "语音记事本",
            "newsapp" to "腾讯新闻",
            "weibo" to "腾讯微博",
            "shakeapp" to "摇一摇",
            "qqfriend" to "QQ好友"
        )
    }

    private val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    data class SyncResult(
        var wechatNew: Int = 0,
        var wechatError: String? = null,
        var douyinNew: Int = 0,
        var douyinError: String? = null,
        var rootAvailable: Boolean = false
    ) {
        val summary: String
            get() = buildString {
                if (!rootAvailable) { append("需要 root 权限"); return@buildString }
                if (wechatNew > 0) append("微信新增 $wechatNew 条")
                wechatError?.let { if (isNotEmpty()) append(" · "); append("微信: $it") }
                douyinError?.let { if (isNotEmpty()) append(" · "); append("抖音: $it") }
                if (isBlank()) append("已是最新")
            }
    }

    fun hasRoot(): Boolean = runSu("id")?.contains("uid=0") == true

    /** 同步所有平台,返回新增消息数 */
    suspend fun syncAll(db: AppDatabase): SyncResult = withContext(Dispatchers.IO) {
        val r = SyncResult()
        r.rootAvailable = hasRoot()
        if (!r.rootAvailable) {
            r.wechatError = "无 root"
            return@withContext r
        }
        try {
            // 微信库是 SQLCipher 加密,直接读不可行;保留 Hook 通道,这里标记说明
            r.wechatError = "微信库已加密,请用实时 Hook 同步"
        } catch (t: Throwable) {
            r.wechatError = t.message ?: t.javaClass.simpleName
            Log.w(TAG, "sync wechat failed", t)
        }
        try {
            r.douyinNew = syncDouyin(db) + syncDouyinInbox(db)
        } catch (t: Throwable) {
            r.douyinError = t.message ?: t.javaClass.simpleName
            Log.w(TAG, "sync douyin failed", t)
        }
        r
    }

    /** 读取抖音 Hook 写入的 inbox 文件(HyperOS 拦截跨进程 Provider,走文件通道兜底) */
    private suspend fun syncDouyinInbox(db: AppDatabase): Int {
        val inboxPath = "/data/data/com.ss.android.ugc.aweme/files/unichat_inbox.json"
        val out = runSu("cat $inboxPath") ?: return 0
        if (out.isBlank()) return 0
        var inserted = 0
        out.lineSequence().filter { it.isNotBlank() }.forEach { line ->
            try {
                val jo = JSONObject(line)
                val peer = jo.optString("peer")
                val msgId = jo.optString("msg_id")
                if (peer.isBlank() || msgId.isBlank()) return@forEach
                val m = Message(
                    platform = Platform.DOUYIN,
                    platformMsgId = msgId,
                    direction = jo.optString("direction", Direction.IN),
                    type = jo.optString("type", MsgType.TEXT),
                    content = jo.optString("content"),
                    timestamp = jo.optLong("timestamp")
                )
                if (IngestHelper.ingestMessage(db, m, peer, peer)) inserted++
            } catch (t: Throwable) {
                // 单条解析失败跳过
            }
        }
        // 处理完删除,避免重复(即使有失败的也会在下轮从新消息补)
        runSu("rm -f $inboxPath")
        if (inserted > 0) Log.i(TAG, "抖音 inbox 文件通道: 新入库 $inserted 条")
        return inserted
    }

    // ==================== root 工具 ====================

    private fun runSu(command: String): String? {
        return try {
            // KernelSU 的 su -c 直接执行命令(不走 shell 通配符/引号展开),
            // 因此调用方命令需避免通配符,路径用无空格形式。
            // -M(--mount-master): 切到全局挂载命名空间,否则 App 的 su 被隔离,
            //   只能看到自己 category 的 data 目录(读不到微信库)。
            val proc = Runtime.getRuntime().exec(arrayOf("su", "-M", "-c", command))
            val out = proc.inputStream.bufferedReader().readText()
            val err = proc.errorStream.bufferedReader().readText()
            proc.waitFor()
            Log.i(TAG, "su[$command] exit=${proc.exitValue()} out=${out.trim().take(120)} err=${err.trim().take(120)}")
            (if (out.isNotBlank()) out else if (err.isNotBlank()) err else null)
        } catch (t: Throwable) {
            Log.w(TAG, "su 执行失败: $command -> $t")
            null
        }
    }

    // ==================== 微信 ====================

    private fun findWechatDb(): String? {
        val out = runSu("find /data/data/com.tencent.mm/MicroMsg -maxdepth 2 -name EnMicroMsg.db") ?: return null
        return out.lineSequence()
            .map { it.trim() }
            .firstOrNull { it.endsWith("/EnMicroMsg.db") }
    }

    // ==================== 抖音 ====================

    private fun findDouyinDb(): String? {
        // 抖音未加密的 IM 库(encrypted_*_im.db 是加密的,读不了)
        val out = runSu("find /data/data/com.ss.android.ugc.aweme/databases -maxdepth 1 -name im_database_*") ?: return null
        val list = out.lineSequence().map { it.trim() }
            .filter { it.contains("/im_database_") }
            // 排除 -shm/-wal 后缀与无 uid 的空模板库
            .filter { !it.endsWith("-shm") && !it.endsWith("-wal") && !it.endsWith("/im_database_") }
            .toList()
        if (list.isEmpty()) return null
        // 多账号时选 mtime 最新的(当前活跃账号)
        var best = list.first()
        var bestMt = -1L
        for (c in list) {
            val mt = runSu("stat -c %Y $c")?.trim()?.toLongOrNull() ?: 0L
            if (mt > bestMt) { bestMt = mt; best = c }
        }
        return best
    }

    private suspend fun syncDouyin(db: AppDatabase): Int {
        val src = findDouyinDb() ?: throw IllegalStateException("未找到抖音聊天库 im_database")
        val cacheDir = File(appContext.cacheDir, "dy").apply { mkdirs() }
        val dbFile = File(cacheDir, "im.db")
        val cache = cacheDir.absolutePath
        // 抖音库较小,每次全量拷贝(先 root 清旧文件)
        runSu("rm -f $cache/im.db $cache/im.db-wal $cache/im.db-shm")
        // 主库 + wal(最新消息在 wal 里),分别重命名为 im.db / im.db-wal
        runSu("cp -f $src $cache/im.db")
        runSu("cp -f $src-wal $cache/im.db-wal")
        runSu("chmod 666 $cache/im.db $cache/im.db-wal")
        if (!dbFile.exists() || dbFile.length() < 1000L) {
            throw IllegalStateException("抖音库复制失败")
        }
        return parseDouyin(db, dbFile)
    }

    private suspend fun parseDouyin(db: AppDatabase, dbFile: File): Int {
        var inserted = 0
        val lastMsgId = prefs.getLong(KEY_DOUYIN_LAST, 0L)
        SQLiteDatabase.openDatabase(dbFile.absolutePath, null, SQLiteDatabase.OPEN_READONLY).use { sql ->
            // 会话名 + 头像 + 当前账号 uid(group_owner_id 即本机账号)
            val convNames = HashMap<String, String>()
            val convAvatars = HashMap<String, String>()
            var selfId: String? = null
            sql.rawQuery("SELECT conversation_id, name, group_owner_id, icon_image FROM im_conversation", null).use { c ->
                while (c.moveToNext()) {
                    val cid = c.getString(0)
                    val name = c.getString(1)
                    if (!name.isNullOrBlank()) convNames[cid] = name
                    val icon = c.getString(3)
                    if (!icon.isNullOrBlank()) {
                        try {
                            val url = JSONObject(icon).optJSONObject("image_thumb")
                                ?.optString("url", "")
                                ?.takeIf { it.isNotBlank() }
                            if (url != null) convAvatars[cid] = url
                        } catch (t: Throwable) {
                            // 头像解析失败不影响
                        }
                    }
                    if (selfId == null) selfId = c.getString(2)
                }
            }

            // 同步联系人资料(名称 + 头像,按会话 id 归并)
            val convIds = (convNames.keys + convAvatars.keys).toSet()
            for (cid in convIds) {
                IngestHelper.ingestContact(
                    db,
                    Contact(
                        name = convNames[cid] ?: cid,
                        douyinId = cid,
                        avatarPath = convAvatars[cid] ?: "",
                        platforms = Platform.DOUYIN
                    )
                )
            }

            // 增量读新消息(message_id 数字递增)
            val rows = mutableListOf<DyRow>()
            var maxMsgId = lastMsgId
            sql.rawQuery(
                "SELECT message_id, conversation_id, sender_id, content_type, create_time, content, brief " +
                    "FROM im_message WHERE message_id > ? ORDER BY message_id ASC LIMIT 2000",
                arrayOf(lastMsgId.toString())
            ).use { c ->
                while (c.moveToNext()) {
                    val mid = c.getLong(0)
                    val conv = c.getString(1) ?: ""
                    val sender = c.getString(2) ?: ""
                    val ctype = c.getInt(3)
                    val ct = c.getLong(4)
                    val content = c.getString(5) ?: ""
                    val brief = c.getString(6) ?: ""
                    if (conv.isBlank()) continue
                    rows.add(DyRow(mid, conv, sender, ctype, ct, content, brief))
                    if (mid > maxMsgId) maxMsgId = mid
                }
            }

            for (r in rows) {
                val msgType = mapDyType(r.contentType)
                val text = extractDyText(r.content, r.brief, msgType)
                val m = Message(
                    platform = Platform.DOUYIN,
                    platformMsgId = r.mid.toString(),
                    direction = if (r.sender == selfId) Direction.OUT else Direction.IN,
                    type = msgType,
                    content = text,
                    timestamp = if (r.createTime < 1_000_000_000_000L) r.createTime * 1000 else r.createTime
                )
                if (IngestHelper.ingestMessage(db, m, r.conv, convNames[r.conv] ?: r.conv)) {
                    inserted++
                }
            }
            prefs.edit().putLong(KEY_DOUYIN_LAST, maxMsgId).apply()
        }
        Log.i(TAG, "抖音增量同步完成: 新入库 $inserted 条")
        return inserted
    }

    /** 抖音 content_type -> 统一类型 */
    private fun mapDyType(contentType: Int): String = when (contentType) {
        1 -> MsgType.TEXT
        2 -> MsgType.IMAGE
        3 -> MsgType.VOICE
        4 -> MsgType.VIDEO
        5 -> MsgType.LINK
        else -> MsgType.TEXT
    }

    /** 抖音消息 content 是 JSON {"text":"..."},取 text;媒体消息给占位 */
    private fun extractDyText(content: String, brief: String, type: String): String {
        if (content.isNotBlank()) {
            try {
                val text = JSONObject(content).optString("text")
                if (text.isNotBlank()) return text
            } catch (t: Throwable) {
                // 忽略,走 brief
            }
        }
        if (brief.isNotBlank()) return brief
        return "[$type]"
    }

    private suspend fun syncWechat(db: AppDatabase): Int {
        val srcDb = findWechatDb() ?: throw IllegalStateException("未找到微信数据库 EnMicroMsg.db")
        val cacheDir = File(appContext.cacheDir, "wx").apply { mkdirs() }
        val dbFile = File(cacheDir, "EnMicroMsg.db")
        val walPath = "$srcDb-wal"

        // WAL mtime 未变且已有缓存 -> 跳过拷贝
        val walMt = runSu("stat -c %Y $walPath")?.trim()?.toLongOrNull() ?: 0L
        val copiedMt = prefs.getLong(KEY_WECHAT_WAL_MT, 0L)
        if (!dbFile.exists() || walMt > copiedMt) {
            val cache = cacheDir.absolutePath
            // root 删除旧副本(root 拷入的文件 App 删不掉)
            runSu("rm -f $cache/EnMicroMsg.db $cache/EnMicroMsg.db-wal $cache/EnMicroMsg.db-shm")
            // 拷主库 + wal(微信库是 WAL 模式,缺 wal 无法打开;shm 由本地重建)
            runSu("cp -f $srcDb $walPath $cache/")
            // 赋读写权限:App 打开时需写(重建 shm),且文件在 App 私有缓存里无风险
            runSu("chmod 666 $cache/EnMicroMsg.db $cache/EnMicroMsg.db-wal")
            prefs.edit().putLong(KEY_WECHAT_WAL_MT, walMt).apply()
        }
        if (!dbFile.exists() || dbFile.length() < 1000L) {
            throw IllegalStateException("微信数据库复制失败")
        }
        return parseWechatMessages(db, dbFile, prefs.getLong(KEY_WECHAT_LAST, 0L))
    }

    private suspend fun parseWechatMessages(db: AppDatabase, dbFile: File, lastMsgId: Long): Int {
        return doParse(db, dbFile, lastMsgId)
    }

    private suspend fun doParse(db: AppDatabase, dbFile: File, lastMsgId: Long): Int {
        var inserted = 0
        var maxMsgId = lastMsgId
        // 读写打开副本(WAL 模式需要写目录创建 shm;副本 666 在 App 私有缓存内)
        val (msgRows, names) = SQLiteDatabase.openDatabase(dbFile.absolutePath, null, SQLiteDatabase.OPEN_READWRITE).use { sql ->
            val list = mutableListOf<MsgRow>()
            sql.rawQuery(
                "SELECT msgId, type, isSend, createTime, talker, content FROM message WHERE msgId > ? ORDER BY msgId ASC LIMIT $BATCH_LIMIT",
                arrayOf(lastMsgId.toString())
            ).use { c ->
                while (c.moveToNext()) {
                    val id = c.getLong(0)
                    val type = c.getInt(1)
                    val isSend = c.getInt(2)
                    val createTime = c.getLong(3)
                    val talker = c.getString(4) ?: ""
                    val content = c.getString(5) ?: ""
                    if (talker.isBlank()) continue
                    list.add(MsgRow(id, type, isSend, createTime, talker, content))
                    if (id > maxMsgId) maxMsgId = id
                }
            }
            list to queryNames(sql, list.map { it.talker }.distinct())
        }
        for (r in msgRows) {
            val msgType = mapType(r.type)
            val content = normalize(r.type, r.content)
            val m = Message(
                platform = Platform.WECHAT,
                platformMsgId = r.id.toString(),
                direction = if (r.isSend == 1) Direction.OUT else Direction.IN,
                type = msgType,
                content = content,
                timestamp = if (r.createTime < 1_000_000_000_000L) r.createTime * 1000 else r.createTime
            )
            val displayName = names[r.talker] ?: KNOWN_ACCOUNTS[r.talker] ?: r.talker
            if (IngestHelper.ingestMessage(db, m, r.talker, displayName)) {
                inserted++
            }
        }
        prefs.edit().putLong(KEY_WECHAT_LAST, maxMsgId).apply()
        Log.i(TAG, "微信增量同步完成: 新入库 $inserted 条 (maxMsgId=$maxMsgId)")
        return inserted
    }

    /** 批量取联系人昵称(微信 rcontact) */
    private fun queryNames(sql: SQLiteDatabase, talkers: List<String>): Map<String, String> {
        if (talkers.isEmpty()) return emptyMap()
        val map = HashMap<String, String>()
        talkers.chunked(200).forEach { chunk ->
            val ph = chunk.joinToString(",") { "?" }
            sql.rawQuery(
                "SELECT username, nickname, conRemark, remark FROM rcontact WHERE username IN ($ph)",
                chunk.toTypedArray()
            ).use { c ->
                while (c.moveToNext()) {
                    val uname = c.getString(0)
                    val name = (c.getString(2) ?: "").ifBlank { c.getString(1) ?: "" }
                    if (name.isNotBlank()) map[uname] = name
                }
            }
        }
        return map
    }

    private fun mapType(type: Int): String = when (type) {
        1 -> MsgType.TEXT
        3, 47, 48 -> MsgType.IMAGE
        34 -> MsgType.VOICE
        43 -> MsgType.VIDEO
        49 -> MsgType.LINK
        10000, 10002 -> MsgType.SYSTEM
        else -> MsgType.TEXT
    }

    /** 微信 XML 消息(type=49)提取纯文本,空内容给占位 */
    private fun normalize(type: Int, content: String): String {
        if (type == 49 && content.startsWith("<") && content.endsWith(">")) {
            Regex("""<title>(.*?)</title>""", RegexOption.DOT_MATCHES_ALL)
                .find(content)?.groupValues?.get(1)?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
            Regex("""<des>(.*?)</des>""", RegexOption.DOT_MATCHES_ALL)
                .find(content)?.groupValues?.get(1)?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
            return content.replace(Regex("<[^>]+>"), "").trim().ifBlank { "[消息]" }
        }
        if (content.isBlank()) return "[${mapType(type)}]"
        return content
    }

    private data class MsgRow(
        val id: Long,
        val type: Int,
        val isSend: Int,
        val createTime: Long,
        val talker: String,
        val content: String
    )

    private data class DyRow(
        val mid: Long,
        val conv: String,
        val sender: String,
        val contentType: Int,
        val createTime: Long,
        val content: String,
        val brief: String
    )
}
