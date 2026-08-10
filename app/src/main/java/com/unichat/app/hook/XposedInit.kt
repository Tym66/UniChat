package com.unichat.app.hook

import android.os.Bundle
import com.unichat.app.data.Platform
import com.unichat.app.data.provider.UniChatProvider
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage
import java.util.concurrent.ConcurrentHashMap

/**
 * Xposed 入口。
 * 通过 android.database.sqlite.SQLiteDatabase 写入层统一拦截,
 * 不依赖微信/抖音内部类名,版本升级兼容性好。
 */
class XposedInit : IXposedHookLoadPackage {

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        val pkg = lpparam.packageName
        val platform = when (pkg) {
            "com.tencent.mm" -> Platform.WECHAT
            "com.ss.android.ugc.aweme" -> Platform.DOUYIN
            else -> return
        }
        // 只处理主进程(微信/抖音多进程,避免重复 hook)
        if (lpparam.processName != pkg) {
            XposedBridge.log("[UniChat] 跳过子进程: ${lpparam.processName}")
            return
        }

        XposedBridge.log("[UniChat] ===== 模块已加载: $pkg (${lpparam.processName}) =====")
        XposedBridge.log("[UniChat] classLoader: ${lpparam.classLoader}")
        try {
            SqliteHook.install(platform)
            XposedBridge.log("[UniChat] SQLite Hook 安装成功")
        } catch (t: Throwable) {
            XposedBridge.log("[UniChat] SQLite Hook 安装失败: ${t.message}")
            XposedBridge.log("[UniChat] ${t.stackTraceToString()}")
        }
    }
}

/**
 * 通用 SQLite 拦截引擎。
 *
 * 原理:微信/抖音聊天数据最终都写入 SQLite。
 * Hook SQLiteDatabase.insertWithOnConflict / updateWithOnConflict,
 * 通过表名 + 字段映射,把消息转为统一格式,经 UniChatProvider 上报给主应用。
 */
object SqliteHook {

    private val installed = ConcurrentHashMap.newKeySet<String>()

    /** 各平台:数据库文件特征 -> 消息表名集合 */
    private val messageTables = mapOf(
        Platform.WECHAT to setOf("message"),
        Platform.DOUYIN to setOf("im_msg", "message", "msg")
    )

    /** 各平台:联系人表名集合 */
    private val contactTables = mapOf(
        Platform.WECHAT to setOf("rcontact"),
        Platform.DOUYIN to setOf("im_user", "user_profile")
    )

    /** 消息表字段 -> 统一语义 */
    private val msgFieldMap = mapOf(
        Platform.WECHAT to mapOf(
            "id" to UniChatProvider.KEY_PLATFORM_MSG_ID,
            "msgId" to UniChatProvider.KEY_PLATFORM_MSG_ID,
            "talker" to UniChatProvider.KEY_PEER_ID,
            "createTime" to UniChatProvider.KEY_TIMESTAMP,
            "isSend" to "is_send_raw",
            "content" to UniChatProvider.KEY_CONTENT
        ),
        Platform.DOUYIN to mapOf(
            "id" to UniChatProvider.KEY_PLATFORM_MSG_ID,
            "conversation_id" to UniChatProvider.KEY_PEER_ID,
            "conversationShortId" to UniChatProvider.KEY_PEER_ID,
            "create_time" to UniChatProvider.KEY_TIMESTAMP,
            "createTime" to UniChatProvider.KEY_TIMESTAMP,
            "content" to UniChatProvider.KEY_CONTENT,
            "is_self" to "is_send_raw"
        )
    )

    fun install(platform: String) {
        if (!installed.add(platform)) {
            XposedBridge.log("[UniChat] $platform 已安装过 Hook,跳过")
            return
        }
        XposedBridge.log("[UniChat] 开始安装 SQLite Hook: $platform")
        XposedBridge.log("[UniChat] 消息表: ${messageTables[platform]}, 联系人表: ${contactTables[platform]}")

        // ---------- 消息写入 ----------
        XposedHelpers.findAndHookMethod(
            "android.database.sqlite.SQLiteDatabase",
            null,
            "insertWithOnConflict",
            String::class.java,
            String::class.java,
            android.content.ContentValues::class.java,
            Int::class.javaPrimitiveType,
            object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    try {
                        val table = param.args[0] as String
                        val values = param.args[2] as? android.content.ContentValues ?: return
                        if (table in messageTables[platform].orEmpty()) {
                            XposedBridge.log("[UniChat] 捕获消息写入: 表=$table 字段=${values.keySet()}")
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
        )

        // ---------- 消息写入(备选入口:insert 不带 conflict 参数的重载) ----------
        try {
            XposedHelpers.findAndHookMethod(
                "android.database.sqlite.SQLiteDatabase",
                null,
                "insert",
                String::class.java,
                String::class.java,
                android.content.ContentValues::class.java,
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        try {
                            val table = param.args[0] as String
                            val values = param.args[2] as? android.content.ContentValues ?: return
                            if (table in messageTables[platform].orEmpty()) {
                                XposedBridge.log("[UniChat] 捕获消息写入(insert): 表=$table")
                                reportMessage(platform, values)
                            }
                        } catch (t: Throwable) { }
                    }
                }
            )
            XposedBridge.log("[UniChat] 备选 insert Hook 安装成功")
        } catch (t: Throwable) {
            XposedBridge.log("[UniChat] 备选 insert Hook 安装失败(不影响): ${t.message}")
        }

        // ---------- 消息更新(如已读回执) ----------
        XposedHelpers.findAndHookMethod(
            "android.database.sqlite.SQLiteDatabase",
            null,
            "updateWithOnConflict",
            String::class.java,
            android.content.ContentValues::class.java,
            String::class.java,
            Array<String>::class.java,
            Int::class.javaPrimitiveType,
            object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    try {
                        val table = param.args[0] as String
                        if (table in messageTables[platform].orEmpty()) {
                            val values = param.args[1] as? android.content.ContentValues ?: return
                            // 已读相关字段变化时上报
                            val readKey = values.keySet().firstOrNull {
                                it.contains("read", true) || it == "status"
                            }
                            if (readKey != null) {
                                reportRead(platform, values)
                            }
                        }
                    } catch (t: Throwable) {
                        // 静默
                    }
                }
            }
        )
    }

    /** 把 ContentValues 按字段映射转成统一 Bundle 上报 */
    private fun reportMessage(platform: String, values: android.content.ContentValues) {
        val map = msgFieldMap[platform] ?: return
        val bundle = Bundle()
        bundle.putString(UniChatProvider.KEY_PLATFORM, platform)

        var peerId: String? = null
        var msgId: String? = null
        var ts = System.currentTimeMillis()
        var content = ""
        var isSendRaw: Any? = null

        for ((col, semantic) in map) {
            val v = values.get(col) ?: continue
            when (semantic) {
                UniChatProvider.KEY_PEER_ID -> peerId = v.toString()
                UniChatProvider.KEY_PLATFORM_MSG_ID -> msgId = v.toString()
                UniChatProvider.KEY_TIMESTAMP -> ts = (v as? Number)?.toLong()?.let { if (it < 1_000_000_000_000L) it * 1000 else it } ?: ts
                UniChatProvider.KEY_CONTENT -> content = v.toString()
                "is_send_raw" -> isSendRaw = v
            }
        }

        if (peerId.isNullOrBlank() || msgId.isNullOrBlank()) {
            XposedBridge.log("[UniChat] 消息字段不完整,跳过: peer=$peerId msgId=$msgId")
            return
        }
        // 跳过空内容系统消息
        if (content.isBlank()) {
            XposedBridge.log("[UniChat] 空内容消息,跳过: $msgId")
            return
        }

        bundle.putString(UniChatProvider.KEY_PEER_ID, peerId)
        bundle.putString(UniChatProvider.KEY_PLATFORM_MSG_ID, msgId)
        bundle.putLong(UniChatProvider.KEY_TIMESTAMP, ts)
        bundle.putString(UniChatProvider.KEY_CONTENT, normalizeContent(platform, content))
        bundle.putString(
            UniChatProvider.KEY_DIRECTION,
            if (isSendRaw?.toString() == "1") "out" else "in"
        )
        bundle.putString(UniChatProvider.KEY_TYPE, "text")
        XposedBridge.log("[UniChat] 上报消息: $platform peer=$peerId type=${bundle.getString(UniChatProvider.KEY_DIRECTION)} ts=$ts content=${content.take(30)}")

        sendToApp(bundle, UniChatProvider.METHOD_INGEST_MESSAGE)
    }

    private fun reportContact(platform: String, values: android.content.ContentValues) {
        val bundle = Bundle()
        bundle.putString(UniChatProvider.KEY_PLATFORM, platform)
        val peerId = values.get("username")
            ?: values.get("user_id")
            ?: values.get("userId")
            ?: values.get("sec_uid")
            ?: return
        val name = values.get("nickname")
            ?: values.get("nickName")
            ?: values.get("remark")
            ?: values.get("alias")
            ?: peerId.toString()
        bundle.putString(UniChatProvider.KEY_PEER_ID, peerId.toString())
        bundle.putString(UniChatProvider.KEY_PEER_NAME, name.toString())
        sendToApp(bundle, UniChatProvider.METHOD_INGEST_CONTACT)
    }

    private fun reportRead(platform: String, values: android.content.ContentValues) {
        val peerId = values.get("talker")?.toString()
            ?: values.get("conversation_id")?.toString()
            ?: return
        val bundle = Bundle()
        bundle.putString(UniChatProvider.KEY_PLATFORM, platform)
        bundle.putString(UniChatProvider.KEY_PEER_ID, peerId)
        sendToApp(bundle, UniChatProvider.METHOD_MARK_READ)
    }

    /** 微信 XML 消息(type=49)提取纯文本 */
    private fun normalizeContent(platform: String, raw: String): String {
        if (platform != Platform.WECHAT) return raw
        if (!raw.startsWith("<") || !raw.endsWith(">")) return raw
        return Regex("""<title>(.*?)</title>""").find(raw)?.groupValues?.get(1)
            ?: raw.replace(Regex("<[^>]+>"), "").trim().ifBlank { "[消息]" }
    }

    private fun sendToApp(bundle: Bundle, method: String) {
        try {
            val ctx = XposedHelpers.callStaticMethod(
                XposedHelpers.findClass("android.app.ActivityThread", null),
                "currentApplication"
            ) as android.content.Context
            ctx.contentResolver.call(
                UniChatProvider.CONTENT_URI, method, null, bundle
            )
        } catch (t: Throwable) {
            // 主应用未安装或 provider 拒绝,静默
        }
    }
}
