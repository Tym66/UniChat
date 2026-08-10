# LSPosed API 是 compileOnly,不需要混淆规则
# Xposed 入口类
-keep class com.unichat.app.hook.XposedInit { *; }
-keep class com.unichat.app.hook.** { *; }

# 保留 Xposed 相关类
-keep class de.robv.android.xposed.** { *; }
-keep class org.lsposed.lsposed.** { *; }

# Room
-keep class * extends androidx.room.RoomDatabase { *; }
