package com.unichat.app.sync

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import com.unichat.app.data.AppDatabase
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
            r.wechatNew = syncWechat(db)
        } catch (t: Throwable) {
            r.wechatError = t.message ?: t.javaClass.simpleName
            Log.w(TAG, "sync wechat failed", t)
        }
        // 抖音库路径差异大,先标记待适配
        r.douyinError = "待适配"
        r
    }

    // ==================== root 工具 ====================

    private fun runSu(command: String): String? {
        return try {
            val proc = Runtime.getRuntime().exec(arrayOf("su", "-c", command))
            val out = proc.inputStream.bufferedReader().readText()
            val err = proc.errorStream.bufferedReader().readText()
            proc.waitFor()
            (if (out.isNotBlank()) out else if (err.isNotBlank()) err else null)
        } catch (t: Throwable) {
            Log.w(TAG, "su 执行失败: $command -> $t")
            null
        }
    }

    // ==================== 微信 ====================

    private fun findWechatDb(): String? {
        val out = runSu("ls -d /data/data/com.tencent.mm/MicroMsg/*/EnMicroMsg.db 2>/dev/null") ?: return null
        return out.lineSequence()
            .map { it.trim() }
            .firstOrNull { it.endsWith("/EnMicroMsg.db") }
    }

    private suspend fun syncWechat(db: AppDatabase): Int {
        val srcDb = findWechatDb() ?: throw IllegalStateException("未找到微信数据库 EnMicroMsg.db")
        val cacheDir = File(appContext.cacheDir, "wx").apply { mkdirs() }
        val dbFile = File(cacheDir, "EnMicroMsg.db")
        val walPath = "$srcDb-wal"

        // WAL mtime 未变且已有缓存 -> 跳过拷贝
        val walMt = runSu("stat -c %Y \"$walPath\"")?.trim()?.toLongOrNull() ?: 0L
        val copiedMt = prefs.getLong(KEY_WECHAT_WAL_MT, 0L)
        if (!dbFile.exists() || walMt > copiedMt) {
            cacheDir.listFiles()?.forEach { it.delete() }
            // 只拷 db + wal(不拷 shm,由本地 SQLite 重建,避免锁状态不一致)
            runSu("cp -f \"$srcDb\" \"$walPath\" '${cacheDir.absolutePath}/' 2>/dev/null")
            prefs.edit().putLong(KEY_WECHAT_WAL_MT, walMt).apply()
        }
        if (!dbFile.exists() || dbFile.length() < 1000L) {
            throw IllegalStateException("微信数据库复制失败")
        }
        return parseWechatMessages(db, dbFile, prefs.getLong(KEY_WECHAT_LAST, 0L))
    }

    private suspend fun parseWechatMessages(db: AppDatabase, dbFile: File, lastMsgId: Long): Int {
        return try {
            doParse(db, dbFile, lastMsgId)
        } catch (t: Throwable) {
            // 可能是 wal 与主库拷贝瞬间不一致 -> 删 wal 后重试
            val wal = File(dbFile.absolutePath + "-wal")
            if (wal.exists()) wal.delete()
            doParse(db, dbFile, lastMsgId)
        }
    }

    private suspend fun doParse(db: AppDatabase, dbFile: File, lastMsgId: Long): Int {
        var inserted = 0
        var maxMsgId = lastMsgId
        // 用读写模式打开副本:WAL 需要写目录创建 shm(副本是应用自己的,可安全读写)
        // ?????????:WAL ??????? shm(????????,?????)
        // ????(???)???,???????????????
        val (msgRows, names) = SQLiteDatabase.openDatabase(dbFile.absolutePath, null, SQLiteDatabase.OPEN_READWRITE).use { sql ->
            val list = mutableListOf<MsgRow>()
            sql.rawQuery(
                "SELECT msgId, type, isSend, createTime, talker, content FROM message WHERE msgId > ? ORDER BY msgId ASC",
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
            if (IngestHelper.ingestMessage(db, m, r.talker, names[r.talker] ?: r.talker)) {
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
}
