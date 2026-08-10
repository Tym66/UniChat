package com.unichat.app.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** 模块分类 */
object ModuleCategory {
    const val MAGISK = "magisk"   // Magisk 模块
    const val LSPOSED = "lsposed" // LSPosed/Xposed 模块
    const val OTHER = "other"
}

/** 搜索到的模块信息(缓存自 GitHub 等仓库) */
@Entity(
    tableName = "modules",
    indices = [Index(value = ["sourceUrl"], unique = true), Index(value = ["category"])]
)
data class ModuleInfo(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** 模块名 */
    val name: String = "",
    /** 应用包名(如 com.example.module) */
    val packageName: String = "",
    /** 作者/仓库所有者 */
    val author: String = "",
    /** 描述 */
    val description: String = "",
    /** 分类:magisk / lsposed / other */
    val category: String = ModuleCategory.LSPOSED,
    /** 仓库地址(GitHub) */
    val sourceUrl: String = "",
    /** 下载量 */
    val downloads: Long = 0,
    /** 星标 */
    val stars: Long = 0,
    /** 最后更新时间 */
    val lastUpdate: Long = 0,
    /** 版本 */
    val version: String = "",
    /** 已缓存到本地(下载) */
    val cached: Boolean = false
)
