package com.unichat.app.data.repo

import com.google.gson.Gson
import com.google.gson.JsonParser
import com.unichat.app.data.ModuleCategory
import com.unichat.app.data.ModuleInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * 模块仓库聚合服务。
 * 通过 GitHub Search API 聚合:
 *  - Magisk 模块 (topic: magisk-module)
 *  - LSPosed / Xposed 模块 (topic: xposed-module / lsposed)
 * 后续可扩展其他仓库接口(如 Magisk 模块仓库官方列表、酷安等)。
 */
class ModuleRepoService(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build(),
    private val gson: Gson = Gson()
) {

    private val queries = mapOf(
        ModuleCategory.MAGISK to listOf(
            "topic:magisk-module",
            "topic:magisk",
            "magisk module in:name,description"
        ),
        ModuleCategory.LSPOSED to listOf(
            "topic:xposed-module",
            "topic:lsposed",
            "topic:lsposed-module"
        )
    )

    /** 从 GitHub 搜索指定分类的模块,每类取前 n 个 */
    suspend fun searchCategory(category: String, perPage: Int = 30): List<ModuleInfo> =
        withContext(Dispatchers.IO) {
            val qs = queries[category].orEmpty()
            val results = mutableListOf<ModuleInfo>()
            for (q in qs) {
                if (results.size >= perPage) break
                results += searchGitHub(q, category, perPage)
            }
            results.distinctBy { it.sourceUrl }.take(perPage)
        }

    /** 关键词搜索(跨分类) */
    suspend fun search(keyword: String, perPage: Int = 30): List<ModuleInfo> =
        withContext(Dispatchers.IO) {
            if (keyword.isBlank()) return@withContext emptyList()
            val q = "$keyword in:name,description"
            searchGitHub(q, ModuleCategory.OTHER, perPage)
        }

    private fun searchGitHub(query: String, category: String, perPage: Int): List<ModuleInfo> {
        val url = "https://api.github.com/search/repositories" +
            "?q=${java.net.URLEncoder.encode(query, "UTF-8")}" +
            "&sort=stars&order=desc&per_page=$perPage"
        val req = Request.Builder()
            .url(url)
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "UniChat")
            .build()

        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return emptyList()
            val body = resp.body?.string() ?: return emptyList()
            val arr = JsonParser.parseString(body).asJsonObject.getAsJsonArray("items")
                ?: return emptyList()
            return arr.mapNotNull { el ->
                try {
                    val obj = el.asJsonObject
                    val name = obj.get("name")?.asString ?: return@mapNotNull null
                    val fullName = obj.get("full_name")?.asString ?: return@mapNotNull null
                    val desc = obj.get("description")?.asString ?: ""
                    val stars = obj.get("stargazers_count")?.asLong ?: 0
                    val html = obj.get("html_url")?.asString ?: "https://github.com/$fullName"
                    val updated = obj.get("updated_at")?.asString ?: ""
                    ModuleInfo(
                        name = name,
                        packageName = fullName, // 仓库名作为包名展示
                        author = fullName.substringBefore("/"),
                        description = desc,
                        category = category,
                        sourceUrl = html,
                        stars = stars,
                        lastUpdate = parseIsoTime(updated)
                    )
                } catch (t: Throwable) { null }
            }
        }
    }

    private fun parseIsoTime(iso: String): Long = try {
        java.time.Instant.parse(iso).toEpochMilli()
    } catch (t: Throwable) { 0L }

    companion object {
        val instance by lazy { ModuleRepoService() }
    }
}
