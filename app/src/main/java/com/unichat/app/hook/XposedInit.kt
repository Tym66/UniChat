package com.unichat.app.hook

import android.content.ContentValues
import android.os.Bundle
import com.unichat.app.data.Direction
import com.unichat.app.data.MsgType
import com.unichat.app.data.Platform
import com.unichat.app.data.provider.UniChatProvider
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.io.File
import org.json.JSONObject

/**
 * Xposed 入口。
 *
 * 微信写库走的是自家 WCDB(com.tencent.wcdb.database.SQLiteDatabase),
 * 不再只 Hook 系统框架的 android.database.sqlite.SQLiteDatabase。
 * 这里对「框架 SQLite + 微信 WCDB + 编译语句」三层做统一拦截,
 * 通过表名 + 字段语义映射,把消息/联系人/已读回执经 UniChatProvider 上报给主应用。
 */
class XposedInit : IXposedHookLoadPackage {

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        val pkg = lpparam.packageName
        val platform = when (pkg) {
            "com.tencent.mm" -> Platform.WECHAT
            "com.ss.android.ugc.aweme" -> Platform.DOUYIN
            else -> return
        }
        // 只处理主进程(微信/抖音多进程,避免重复 hook 与误伤子进程)
        if (lpparam.processName != pkg) {
            XposedBridge.log("[UniChat] 跳过子进程: ${lpparam.processName}")
            return
        }

        XposedBridge.log("[UniChat] ===== 模块已加载: $pkg (${lpparam.processName}) =====")
        try {
            SqliteHook.install(platform, lpparam.classLoader)
            XposedBridge.log("[UniChat] SQLite Hook 安装成功")
            // 向主应用上报心跳,确认模块已注入目标进程(用于 UI 诊断)
            SqliteHook.reportHello(platform)
        } catch (t: Throwable) {
            XposedBridge.log("[UniChat] SQLite Hook 安装失败: ${t.message}")
            XposedBridge.log("[UniChat] ${t.stackTraceToString()}")
        }
    }
}

/**
 * 通用 SQLite 拦截引擎。
 *
 * 覆盖三种写入路径:
 * 1. 系统框架 SQLiteDatabase(抖音/多数 App 使用)
 * 2. 微信 WCDB SQLiteDatabase(微信实际写库路径)
 * 3. SQLiteStatement 编译语句(兜底)
 */
object SqliteHook {

    /** 诊断模式:记录微信/抖音所有数据库写入路径(用于定位真实写库 API,上线置 false) */
    private const val DIAG = true

    private val installed = ConcurrentHashMap.newKeySet<String>()

    /** 应用上下文(在 Application.attach 时捕获,避免初始化早期拿不到) */
    @Volatile
    private var appContext: android.content.Context? = null

    /** 应用上下文未就绪前暂存的上报(attach 后补发) */
    private val pendingReports = ConcurrentLinkedQueue<Pair<Bundle, String>>()

    /** SQLiteStatement 绑定参数(线程局部):statement 实例 -> (index -> value) */
    private val stmtBindings = ThreadLocal.withInitial {
        object : LinkedHashMap<Any, HashMap<Int, Any?>>() {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Any, HashMap<Int, Any?>>): Boolean {
                // 防止 statement 长期不 execute 导致泄漏
                return size > 256
            }
        }
    }

    /** 上报失败后的重试调度(UniChat 被 HyperOS 冻结时,解冻后补上报) */
    private val retryExecutor = Executors.newSingleThreadScheduledExecutor { r ->
        Thread(r, "UniChat-retry").apply { isDaemon = true }
    }

    /** 各平台:消息表名集合 */
    private val messageTables = mapOf(
        Platform.WECHAT to setOf("message", "message_pending"),
        Platform.DOUYIN to setOf("im_msg", "message", "msg", "conversation_message", "chat_message")
    )

    /** 各平台:联系人表名集合 */
    private val contactTables = mapOf(
        Platform.WECHAT to setOf("rcontact"),
        Platform.DOUYIN to setOf("im_user", "user_profile", "conversation")
    )

    /** 需要拦截的数据库实现类 */
    private val databaseClasses = listOf(
        "android.database.sqlite.SQLiteDatabase",       // 系统框架(抖音)
        "com.tencent.wcdb.database.SQLiteDatabase",     // 微信 WCDB 主类
        "com.tencent.wcdb.compat.SQLiteDatabase"        // 微信 WCDB 兼容层(WeKit 也 hook 这里)
    )

    /** 需要拦截的编译语句类 */
    private val statementClasses = listOf(
        "android.database.sqlite.SQLiteStatement",
        "com.tencent.wcdb.database.SQLiteStatement",
        "com.tencent.wcdb.compat.SQLiteStatement"
    )

    /** 消息 ID 候选列(优先大字段,避免 HashSet 顺序不确定导致选到行 id) */
    private val msgIdColumns = listOf(
        "msg_uuid", "msg_server_id", "msgId", "msgSvrId", "msg_id", "messageId", "clientMsgId", "id"
    )

    /** 会话(对端)ID 候选列 */
    private val peerIdColumns = listOf(
        "talker", "conversation_id", "conversationShortId", "sessionId", "session_id", "toId", "to_user_id"
    )

    /** 时间戳候选列 */
    private val tsColumns = listOf("created_time", "createTime", "create_time", "timestamp", "msgTime")

    /** 内容候选列 */
    private val contentColumns = listOf("content", "text", "summary")

    /** 消息类型候选列 */
    private val typeColumns = listOf("type", "msgType", "messageType")

    fun install(platform: String, classLoader: ClassLoader) {
        if (!installed.add(platform)) {
            XposedBridge.log("[UniChat] $platform 已安装过 Hook,跳过")
            return
        }
        XposedBridge.log("[UniChat] 开始安装 Hook: $platform 消息表=${messageTables[platform]} 联系人表=${contactTables[platform]}")

        // 捕获应用 Context(Application.attach 触发),并补发初始化早期未能上报的数据
        try {
            XposedHelpers.findAndHookMethod(
                "android.app.Application",
                null,
                "attach",
                android.content.Context::class.java,
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val ctx = param.thisObject as? android.content.Context ?: return
                        appContext = ctx
                        var item = pendingReports.poll()
                        while (item != null) {
                            try {
                                doSend(ctx, item.second, item.first)
                            } catch (t: Throwable) {
                                XposedBridge.log("[UniChat] 补发失败 ${item.second}: $t")
                            }
                            item = pendingReports.poll()
                        }
                    }
                }
            )
        } catch (t: Throwable) {
            XposedBridge.log("[UniChat] Application.attach Hook 失败(忽略): ${t.message}")
        }

        for (cls in databaseClasses) {
            try {
                hookDatabaseClass(platform, cls, if (cls.startsWith("android.")) null else classLoader)
            } catch (t: Throwable) {
                XposedBridge.log("[UniChat] Hook 数据库类 $cls 失败(忽略): ${t.message}")
            }
        }
        for (cls in statementClasses) {
            try {
                hookStatementClass(platform, cls, if (cls.startsWith("android.")) null else classLoader)
            } catch (t: Throwable) {
                XposedBridge.log("[UniChat] Hook 语句类 $cls 失败(忽略): ${t.message}")
            }
        }
    }

    /** 拦截 SQLiteDatabase 的 insert/update/execSQL 系列方法 */
    private fun hookDatabaseClass(platform: String, className: String, classLoader: ClassLoader?) {
        val cl = XposedHelpers.findClass(className, classLoader)

        // ---------- 写入(insert 系列,可拿到 ContentValues) ----------
        val insertHook = object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                try {
                    val table = param.args[0] as? String ?: return
                    val values = param.args.firstOrNull { it is ContentValues } as? ContentValues ?: return
                    if (DIAG) {
                        val keys = values.keySet().joinToString(",") { k ->
                            val v = values.get(k)?.toString()?.take(60) ?: "null"
                            "$k=$v"
                        }
                        XposedBridge.log("[UniChat][w] INSERT ${cl.simpleName} 表=$table ${keys.take(900)}")
                    }
                    if (table in messageTables[platform].orEmpty()) {
                        XposedBridge.log("[UniChat] 捕获消息写入: ${cl.simpleName} 表=$table")
                        reportMessage(platform, values)
                    } else if (table in contactTables[platform].orEmpty()) {
                        XposedBridge.log("[UniChat] 捕获联系人写入: 表=$table")
                        reportContact(platform, values)
                    }
                } catch (t: Throwable) {
                    // 静默,不干扰原应用
                }
            }
        }
        hookIfPresent(cl, "insertWithOnConflict",
            arrayOf<Class<*>>(String::class.java, String::class.java, ContentValues::class.java, Int::class.javaPrimitiveType!!), insertHook)
        hookIfPresent(cl, "insert",
            arrayOf<Class<*>>(String::class.java, String::class.java, ContentValues::class.java), insertHook)
        hookIfPresent(cl, "insertOrThrow",
            arrayOf<Class<*>>(String::class.java, String::class.java, ContentValues::class.java), insertHook)
        hookIfPresent(cl, "replace",
            arrayOf<Class<*>>(String::class.java, String::class.java, ContentValues::class.java), insertHook)
        hookIfPresent(cl, "replaceOrThrow",
            arrayOf<Class<*>>(String::class.java, String::class.java, ContentValues::class.java), insertHook)

        // ---------- 更新(已读回执等) ----------
        val updateHook = object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                try {
                    val table = param.args[0] as? String ?: return
                    if (table !in messageTables[platform].orEmpty()) return
                    val values = param.args.firstOrNull { it is ContentValues } as? ContentValues ?: return
                    if (!isReadUpdate(values)) return
                    val whereClause = param.args.getOrNull(2) as? String ?: ""
                    val whereArgs = (param.args.getOrNull(3) as? Array<*>) ?: emptyArray<Any>()
                    XposedBridge.log("[UniChat] 捕获已读更新: 表=$table where=$whereClause")
                    reportRead(platform, values, whereClause, whereArgs)
                } catch (t: Throwable) {
                    // 静默
                }
            }
        }
        hookIfPresent(cl, "updateWithOnConflict",
            arrayOf<Class<*>>(String::class.java, ContentValues::class.java, String::class.java, Array<String>::class.java, Int::class.javaPrimitiveType!!), updateHook)
        hookIfPresent(cl, "update",
            arrayOf<Class<*>>(String::class.java, ContentValues::class.java, String::class.java, Array<String>::class.java), updateHook)

        // ---------- execSQL(拿不到 ContentValues,仅解析字面量 SQL) ----------
        val execHook = object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                try {
                    val sql = param.args[0] as? String ?: return
                    if (DIAG) {
                        val s = sql.trimStart()
                        val prefix = s.take(6).uppercase()
                        if (prefix.startsWith("INSERT") || prefix.startsWith("UPDATE") || prefix.startsWith("REPLACE")) {
                            XposedBridge.log("[UniChat][w] EXECSQL ${cl.simpleName}: ${s.take(140)}")
                        }
                    }
                    if (!isTargetTableMentioned(sql, platform)) return
                    val table = parseWriteTable(sql) ?: return
                    if (table in messageTables[platform].orEmpty()) {
                        val bind = (param.args.getOrNull(1) as? Array<*>) ?: emptyArray<Any>()
                        val values = contentValuesFromSql(sql, bind) ?: return
                        XposedBridge.log("[UniChat] 捕获消息写入(execSQL): 表=$table")
                        reportMessage(platform, values)
                    }
                } catch (t: Throwable) {
                    // 静默
                }
            }
        }
        hookIfPresent(cl, "execSQL", arrayOf<Class<*>>(String::class.java), execHook)
        hookIfPresent(cl, "execSQL", arrayOf<Class<*>>(String::class.java, Array<Any>::class.java), execHook)

        XposedBridge.log("[UniChat] 数据库类 Hook 完成: $className")
    }

    /** 拦截 SQLiteStatement 编译语句:捕获 bind* 绑定参数,组装成消息上报 */
    private fun hookStatementClass(platform: String, className: String, classLoader: ClassLoader?) {
        val cl = XposedHelpers.findClass(className, classLoader)

        // ---------- 绑定参数捕获(参数在 native 层,必须 hook bind* 才能拿到值) ----------
        val bindHook = object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                try {
                    val stmt = param.thisObject
                    val idx = (param.args[0] as? Int) ?: return
                    val map = stmtBindings.get().getOrPut(stmt) { HashMap() }
                    map[idx] = param.args.getOrNull(1)
                } catch (t: Throwable) {
                    // 静默
                }
            }
        }
        hookIfPresent(cl, "bindLong",
            arrayOf<Class<*>>(Int::class.javaPrimitiveType!!, Long::class.javaPrimitiveType!!), bindHook)
        hookIfPresent(cl, "bindDouble",
            arrayOf<Class<*>>(Int::class.javaPrimitiveType!!, Double::class.javaPrimitiveType!!), bindHook)
        hookIfPresent(cl, "bindString",
            arrayOf<Class<*>>(Int::class.javaPrimitiveType!!, String::class.java), bindHook)
        hookIfPresent(cl, "bindNull",
            arrayOf<Class<*>>(Int::class.javaPrimitiveType!!), bindHook)
        hookIfPresent(cl, "bindBlob",
            arrayOf<Class<*>>(Int::class.javaPrimitiveType!!, ByteArray::class.java), bindHook)

        // ---------- 执行时用绑定参数组装消息 ----------
        val execHook = object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                try {
                    val sql = XposedHelpers.getObjectField(param.thisObject, "mSql") as? String ?: return
                    if (DIAG) {
                        XposedBridge.log("[UniChat][w] STMT ${cl.simpleName}: ${sql.take(140)}")
                    }
                    if (!isTargetTableMentioned(sql, platform)) return
                    val table = parseWriteTable(sql) ?: return
                    if (table in messageTables[platform].orEmpty()) {
                        val bindings = stmtBindings.get().remove(param.thisObject) ?: HashMap()
                        val values = contentValuesFromStmt(sql, bindings) ?: return
                        XposedBridge.log("[UniChat] 捕获消息写入(语句绑定): 表=$table keys=${values.keySet()}")
                        reportMessage(platform, values)
                    }
                } catch (t: Throwable) {
                    // 静默
                }
            }
        }
        hookIfPresent(cl, "executeInsert", emptyArray<Class<*>>(), execHook)
        hookIfPresent(cl, "executeUpdateDelete", emptyArray<Class<*>>(), execHook)
        hookIfPresent(cl, "execute", emptyArray<Class<*>>(), execHook)
        XposedBridge.log("[UniChat] 语句类 Hook 完成: $className")
    }

    /** 安全地 hook 某个方法;方法不存在则跳过(不同平台/版本 API 差异大) */
    private fun hookIfPresent(
        cl: Class<*>,
        method: String,
        paramTypes: Array<Class<*>>,
        hook: XC_MethodHook
    ) {
        try {
            XposedHelpers.findAndHookMethod(cl, method, *paramTypes, hook)
        } catch (t: Throwable) {
            XposedBridge.log("[UniChat] 跳过 ${cl.simpleName}.$method: ${t.message}")
        }
    }

    // ==================== 消息 ====================

    private fun reportMessage(platform: String, values: ContentValues) {
        // 抖音 msg 表字段差异大,单独处理(真实私信)
        if (platform == Platform.DOUYIN && values.containsKey("msg_uuid")) {
            reportDouyinMsg(values)
            return
        }
        val peerId = firstValue(values, peerIdColumns)?.toString() ?: return
        val msgId = firstValue(values, msgIdColumns)?.toString() ?: return
        val ts = toTimestamp(firstValue(values, tsColumns))
        var content = firstValue(values, contentColumns)?.toString() ?: ""
        val typeRaw = firstValue(values, typeColumns)?.toString()
        val msgType = mapMsgType(platform, typeRaw, content)

        if (content.isBlank() && msgType == MsgType.TEXT) {
            XposedBridge.log("[UniChat] 空文本消息,跳过: $msgId")
            return
        }
        if (content.isBlank()) content = "[$msgType]"
        content = normalizeContent(platform, typeRaw, content)

        val isSend = parseBool(values.get("isSend") ?: values.get("is_self") ?: values.get("self"))
        val direction = if (isSend == true) Direction.OUT else Direction.IN

        val bundle = Bundle()
        bundle.putString(UniChatProvider.KEY_PLATFORM, platform)
        bundle.putString(UniChatProvider.KEY_PEER_ID, peerId)
        bundle.putString(UniChatProvider.KEY_PLATFORM_MSG_ID, msgId)
        bundle.putLong(UniChatProvider.KEY_TIMESTAMP, ts)
        bundle.putString(UniChatProvider.KEY_DIRECTION, direction)
        bundle.putString(UniChatProvider.KEY_TYPE, msgType)
        bundle.putString(UniChatProvider.KEY_CONTENT, content)
        XposedBridge.log("[UniChat] 上报消息: $platform peer=$peerId type=$msgType dir=$direction ts=$ts content=${content.take(30)}")

        sendToApp(bundle, UniChatProvider.METHOD_INGEST_MESSAGE)
    }

    /** 抖音真实私信(msg 表):字段与微信差异大,单独解析 */
    private fun reportDouyinMsg(values: ContentValues) {
        val msgId = firstValue(values, listOf("msg_uuid", "msg_server_id", "index_in_conversation"))?.toString() ?: return
        val convId = firstValue(values, listOf("conversation_id", "conversation_short_id"))?.toString() ?: return
        val sender = values.get("sender")?.toString()
        val created = values.get("created_time")?.toString()?.toLongOrNull()
            ?: values.get("create_time")?.toString()?.toLongOrNull()
            ?: System.currentTimeMillis()
        val rawContent = values.get("content")?.toString() ?: ""
        val text = extractDouyinText(rawContent)
        val msgType = mapDyType(values.get("type")?.toString())

        if (text.isBlank() && msgType == MsgType.TEXT) return

        // 方向:conversation_id 格式 "0:1:<对端>:<自己>",sender == 自己 → OUT
        val selfId = convId.split(":").lastOrNull()
        val isOut = !sender.isNullOrBlank() && sender == selfId

        val bundle = Bundle()
        bundle.putString(UniChatProvider.KEY_PLATFORM, Platform.DOUYIN)
        bundle.putString(UniChatProvider.KEY_PEER_ID, convId)
        bundle.putString(UniChatProvider.KEY_PLATFORM_MSG_ID, msgId)
        bundle.putLong(UniChatProvider.KEY_TIMESTAMP, if (created < 1_000_000_000_000L) created * 1000 else created)
        bundle.putString(UniChatProvider.KEY_DIRECTION, if (isOut) Direction.OUT else Direction.IN)
        bundle.putString(UniChatProvider.KEY_TYPE, msgType)
        bundle.putString(UniChatProvider.KEY_CONTENT, text.ifBlank { "[$msgType]" })
        XposedBridge.log("[UniChat] 上报抖音消息: peer=$convId type=$msgType dir=${if (isOut) "OUT" else "IN"} content=${text.take(30)}")

        // 文件通道:同时写入抖音私有目录,供 UniChat root 读取(绕过 HyperOS 跨进程 Provider 限制)
        writeDouyinInbox(bundle)
        sendToApp(bundle, UniChatProvider.METHOD_INGEST_MESSAGE)
    }

    /** 抖音消息写入本地 inbox 文件(UniChat 周期同步用 root 读取) */
    private fun writeDouyinInbox(bundle: Bundle) {
        try {
            val ctx = appContext ?: return
            val file = File(ctx.filesDir, "unichat_inbox.json")
            val line = JSONObject().apply {
                put("peer", bundle.getString(UniChatProvider.KEY_PEER_ID))
                put("msg_id", bundle.getString(UniChatProvider.KEY_PLATFORM_MSG_ID))
                put("direction", bundle.getString(UniChatProvider.KEY_DIRECTION))
                put("type", bundle.getString(UniChatProvider.KEY_TYPE))
                put("content", bundle.getString(UniChatProvider.KEY_CONTENT))
                put("timestamp", bundle.getLong(UniChatProvider.KEY_TIMESTAMP))
            }.toString() + "\n"
            file.parentFile?.mkdirs()
            file.appendText(line)
            XposedBridge.log("[UniChat] 抖音消息已写入 inbox: ${file.absolutePath}")
        } catch (t: Throwable) {
            // 写失败不影响上报
        }
    }

    /** 抖音消息 content 是 JSON,取 text 字段 */
    private fun extractDouyinText(raw: String): String {
        if (raw.isBlank()) return ""
        return try {
            JSONObject(raw).optString("text")
        } catch (t: Throwable) {
            ""
        }
    }

    /** 抖音消息 type 映射 */
    private fun mapDyType(typeRaw: String?): String {
        val n = typeRaw?.toIntOrNull() ?: return MsgType.TEXT
        return when (n) {
            1 -> MsgType.TEXT
            2 -> MsgType.IMAGE
            3 -> MsgType.VOICE
            4 -> MsgType.VIDEO
            5 -> MsgType.LINK
            else -> MsgType.TEXT
        }
    }

    // ==================== 联系人 ====================

    private fun reportContact(platform: String, values: ContentValues) {
        val peerId = firstValue(
            values,
            listOf("username", "userName", "user_id", "userId", "sec_uid", "uid", "userid", "id")
        )?.toString() ?: return
        val name = firstValue(
            values,
            listOf("nickname", "nickName", "remark", "alias", "displayName", "name")
        )?.toString() ?: peerId
        val phone = firstValue(values, listOf("mobile", "phone", "phoneNumber", "telephone"))?.toString() ?: ""
        val avatar = firstValue(values, listOf("headimgurl", "avatar", "avatarUrl", "icon", "avatarUrl"))?.toString() ?: ""

        val bundle = Bundle()
        bundle.putString(UniChatProvider.KEY_PLATFORM, platform)
        bundle.putString(UniChatProvider.KEY_PEER_ID, peerId)
        bundle.putString(UniChatProvider.KEY_PEER_NAME, name)
        if (phone.isNotBlank()) bundle.putString(UniChatProvider.KEY_PHONE, phone)
        if (avatar.isNotBlank()) bundle.putString(UniChatProvider.KEY_AVATAR, avatar)
        XposedBridge.log("[UniChat] 上报联系人: $platform peer=$peerId name=$name phone=${phone.take(4)}****")

        sendToApp(bundle, UniChatProvider.METHOD_INGEST_CONTACT)
    }

    // ==================== 已读回执 ====================

    private fun reportRead(
        platform: String,
        values: ContentValues,
        whereClause: String,
        whereArgs: Array<*>
    ) {
        val peerId = firstValue(values, peerIdColumns)?.toString()
            ?: extractPeerFromWhere(platform, whereClause, whereArgs)
            ?: return
        val bundle = Bundle()
        bundle.putString(UniChatProvider.KEY_PLATFORM, platform)
        bundle.putString(UniChatProvider.KEY_PEER_ID, peerId)
        XposedBridge.log("[UniChat] 上报已读: $platform peer=$peerId")
        sendToApp(bundle, UniChatProvider.METHOD_MARK_READ)
    }

    /** 从 update 的 where 子句解析对端 ID(如 talker=? / conversation_id=?) */
    private fun extractPeerFromWhere(platform: String, whereClause: String, whereArgs: Array<*>): String? {
        if (whereClause.isBlank()) return null
        val keys = if (platform == Platform.WECHAT)
            listOf("talker")
        else
            listOf("conversation_id", "conversationShortId", "talker")
        for (k in keys) {
            val m = Regex("\\b" + Regex.escape(k) + "\\s*=\\s*\\?").find(whereClause) ?: continue
            val idx = whereClause.substring(0, m.range.first).count { it == '?' }
            val v = whereArgs.getOrNull(idx)?.toString()
            if (!v.isNullOrBlank()) return v
        }
        return null
    }

    /** 判断一次 update 是否与已读相关 */
    private fun isReadUpdate(values: ContentValues): Boolean {
        return values.keySet().any {
            it.contains("read", true) || it.equals("status", true) || it.equals("hasRead", true)
        }
    }

    // ==================== 心跳(诊断) ====================

    fun reportHello(platform: String) {
        val bundle = Bundle()
        bundle.putString(UniChatProvider.KEY_PLATFORM, platform)
        bundle.putLong(UniChatProvider.KEY_TIMESTAMP, System.currentTimeMillis())
        sendToApp(bundle, UniChatProvider.METHOD_HOOK_HELLO)
    }

    // ==================== 工具 ====================

    /** 按候选列顺序取第一个非空值(确定性,避免 keySet 顺序问题) */
    private fun firstValue(values: ContentValues, cols: List<String>): Any? {
        for (c in cols) {
            val v = values.get(c) ?: continue
            val s = v.toString()
            if (s.isNotBlank() && s != "null") return v
        }
        return null
    }

    /** 时间戳统一转毫秒(兼容秒级) */
    private fun toTimestamp(v: Any?): Long {
        val raw = (v as? Number)?.toLong()
            ?: v?.toString()?.toLongOrNull()
            ?: System.currentTimeMillis()
        return if (raw > 0 && raw < 1_000_000_000_000L) raw * 1000 else raw
    }

    /** isSend / is_self 可能是 int/long/string/boolean */
    private fun parseBool(v: Any?): Boolean? = when (v) {
        is Boolean -> v
        is Number -> v.toInt() == 1
        is String -> v == "1" || v.equals("true", true)
        else -> null
    }

    /** 平台消息类型 -> 统一类型 */
    private fun mapMsgType(platform: String, typeRaw: String?, content: String): String {
        val n = typeRaw?.toIntOrNull() ?: return MsgType.TEXT
        return if (platform == Platform.WECHAT) {
            when (n) {
                1 -> MsgType.TEXT
                3, 47, 48 -> MsgType.IMAGE
                34 -> MsgType.VOICE
                43 -> MsgType.VIDEO
                49 -> MsgType.LINK
                10000, 10002 -> MsgType.SYSTEM
                else -> MsgType.TEXT
            }
        } else {
            when (n) {
                1 -> MsgType.TEXT
                2 -> MsgType.IMAGE
                3 -> MsgType.VOICE
                4 -> MsgType.VIDEO
                5 -> MsgType.LINK
                else -> MsgType.TEXT
            }
        }
    }

    /** 微信 XML 消息(type=49)提取纯文本 */
    private fun normalizeContent(platform: String, typeRaw: String?, content: String): String {
        if (content.isBlank()) return content
        val isXml = platform == Platform.WECHAT &&
            (typeRaw == "49" || (content.startsWith("<") && content.endsWith(">")))
        if (!isXml) return content
        Regex("""<title>(.*?)</title>""", RegexOption.DOT_MATCHES_ALL)
            .find(content)?.groupValues?.get(1)?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
        Regex("""<des>(.*?)</des>""", RegexOption.DOT_MATCHES_ALL)
            .find(content)?.groupValues?.get(1)?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
        return content.replace(Regex("<[^>]+>"), "").trim().ifBlank { "[消息]" }
    }

    /** SQL 语句是否命中目标表名(快速预过滤,避免解析大量无关 SQL) */
    private fun isTargetTableMentioned(sql: String, platform: String): Boolean {
        val tables = messageTables[platform].orEmpty() + contactTables[platform].orEmpty()
        return tables.any { Regex("\\b" + Regex.escape(it) + "\\b").containsMatchIn(sql) }
    }

    /** 从写语句中解析表名(INSERT INTO / REPLACE INTO) */
    private fun parseWriteTable(sql: String): String? {
        val m = Regex("""(?is)^\s*(?:insert|replace)\s+(?:or\s+\w+\s+)?into\s+[`"\[]?([\w]+)[`"\]]?""").find(sql)
            ?: return null
        return m.groupValues[1]
    }

    /** 从字面量 INSERT 语句构建 ContentValues(带 ? 时用绑定参数补齐) */
    private fun contentValuesFromSql(sql: String, bind: Array<*>): ContentValues? {
        val m = Regex(
            """(?is)^\s*(?:insert|replace)\s+(?:or\s+\w+\s+)?into\s+`?(\w+)`?\s*\(([^)]*)\)\s*values\s*\(([^)]*)\)\s*$"""
        ).find(sql.trim()) ?: return null
        val table = m.groupValues[1]
        val cols = m.groupValues[2].split(',').map { it.trim().trim('`') }.filter { it.isNotEmpty() }
        val vals = m.groupValues[3].split(',').map { it.trim() }
        if (cols.isEmpty() || cols.size != vals.size) return null
        val cv = ContentValues()
        var bindIdx = 0
        for (i in cols.indices) {
            val raw = vals[i]
            when {
                raw == "?" -> {
                    if (bindIdx < bind.size) putValue(cv, cols[i], bind[bindIdx])
                    bindIdx++
                }
                raw.equals("null", true) -> {}
                raw.startsWith("'") && raw.endsWith("'") && raw.length >= 2 ->
                    cv.put(cols[i], raw.substring(1, raw.length - 1))
                else -> {
                    raw.toLongOrNull()?.let { cv.put(cols[i], it) }
                        ?: raw.toDoubleOrNull()?.let { cv.put(cols[i], it) }
                }
            }
        }
        if (cv.size() == 0) return null
        return cv
    }

    /** 从 SQLiteStatement 的 INSERT 语句 + 绑定参数构建 ContentValues */
    private fun contentValuesFromStmt(sql: String, bindings: Map<Int, Any?>): ContentValues? {
        val m = Regex(
            """(?is)^\s*(?:insert|replace)\s+(?:or\s+\w+\s+)?into\s+`?(\w+)`?\s*\(([^)]*)\)\s*values\s*\([^)]*\)\s*$"""
        ).find(sql.trim()) ?: return null
        val cols = m.groupValues[2].split(',').map { it.trim().trim('`') }.filter { it.isNotEmpty() }
        if (cols.isEmpty()) return null
        val cv = ContentValues()
        for ((idx, col) in cols.withIndex()) {
            // bind 索引从 1 开始
            val v = bindings[idx + 1] ?: continue
            putValue(cv, col, v)
        }
        if (cv.size() == 0) return null
        return cv
    }

    private fun putValue(cv: ContentValues, key: String, value: Any?) {
        when (value) {
            null -> {}
            is String -> cv.put(key, value)
            is Int -> cv.put(key, value)
            is Long -> cv.put(key, value)
            is Double -> cv.put(key, value)
            is Float -> cv.put(key, value)
            is Boolean -> cv.put(key, value)
            is ByteArray -> cv.put(key, value)
            else -> cv.put(key, value.toString())
        }
    }

    /** 跨进程上报:经 ContentResolver.call 写入主应用 Provider */
    private fun sendToApp(bundle: Bundle, method: String) {
        val ctx = appContext ?: run {
            // 兜底:尝试从 ActivityThread 取(应用运行中通常已就绪)
            try {
                XposedHelpers.callStaticMethod(
                    XposedHelpers.findClass("android.app.ActivityThread", null),
                    "currentApplication"
                ) as? android.content.Context
            } catch (t: Throwable) {
                null
            }
        }
        if (ctx == null) {
            // 应用上下文尚未就绪,暂存待 Application.attach 后补发
            if (pendingReports.size < 64) {
                pendingReports.add(bundle to method)
            } else {
                XposedBridge.log("[UniChat] 待发队列已满,丢弃: $method")
            }
            return
        }
        try {
            val ret = ctx.contentResolver.call(UniChatProvider.CONTENT_URI, method, null, bundle)
            if (ret != null) {
                XposedBridge.log("[UniChat] 上报成功: $method ok=${ret.getBoolean("ok")}")
            } else {
                XposedBridge.log("[UniChat] 上报被拒(返回 null): $method")
                scheduleRetry(bundle, method, 0)
            }
        } catch (t: Throwable) {
            XposedBridge.log("[UniChat] 上报失败: $method -> ${t}")
            // HyperOS 冻结 UniChat 时 Provider 不可达,延迟重试等解冻后补上报
            scheduleRetry(bundle, method, 0)
        }
    }

    /** 上报失败后延迟重试(最多 6 次,间隔 3 秒),覆盖 UniChat 被冻结/未启动场景 */
    private fun scheduleRetry(bundle: Bundle, method: String, attempt: Int) {
        if (attempt >= 6) {
            XposedBridge.log("[UniChat] 上报重试放弃: $method")
            return
        }
        retryExecutor.schedule({
            try {
                val ctx = appContext ?: return@schedule
                val ret = ctx.contentResolver.call(UniChatProvider.CONTENT_URI, method, null, bundle)
                if (ret != null) {
                    XposedBridge.log("[UniChat] 上报成功(重试$attempt): $method ok=${ret.getBoolean("ok")}")
                } else {
                    scheduleRetry(bundle, method, attempt + 1)
                }
            } catch (t: Throwable) {
                scheduleRetry(bundle, method, attempt + 1)
            }
        }, 3, TimeUnit.SECONDS)
    }

    /** 实际发送(供 attach 补发复用) */
    private fun doSend(ctx: android.content.Context, method: String, bundle: Bundle) {
        val ret = ctx.contentResolver.call(UniChatProvider.CONTENT_URI, method, null, bundle)
        XposedBridge.log(
            "[UniChat] 上报成功(补发): $method ok=${ret?.getBoolean("ok")}"
        )
    }
}
