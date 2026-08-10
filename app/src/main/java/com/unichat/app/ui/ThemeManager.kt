package com.unichat.app.ui

import android.content.Context
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

/** 主题模式 */
object ThemeMode {
    const val SYSTEM = "system"   // 跟随系统
    const val LIGHT = "light"     // 日间
    const val DARK = "dark"       // 夜间
}

/** 主题偏好管理(SharedPreferences) */
class ThemeManager(context: Context) {

    private val prefs = context.getSharedPreferences("unichat_settings", Context.MODE_PRIVATE)

    /** 当前主题模式(可观察) */
    var mode by mutableStateOf(prefs.getString(KEY_MODE, ThemeMode.SYSTEM) ?: ThemeMode.SYSTEM)
        private set

    fun updateMode(newMode: String) {
        mode = newMode
        prefs.edit().putString(KEY_MODE, newMode).apply()
    }

    companion object {
        private const val KEY_MODE = "theme_mode"
    }
}
