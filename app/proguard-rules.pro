#  Clink ProGuard

# Kotlin 
-keep class kotlin.Metadata { *; }
-dontwarn kotlin.**
-dontwarn kotlinx.**

# Kotlin Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembernames class kotlinx.** {
    volatile <fields>;
}
-dontwarn kotlinx.coroutines.**

# Android 四大组件
-keep public class * extends android.app.Activity
-keep public class * extends android.app.Service
-keep public class * extends android.content.BroadcastReceiver
-keep public class * extends android.content.ContentProvider
-keep public class * extends android.app.Application

# TileService
-keep class * extends android.service.quicksettings.TileService { *; }

# 保留 SharedPreferences 相关的 key 字符串常量
-keepclassmembers class com.clink.app.data.StatsManager {
    public static final java.lang.String *;
}

# ClinkEngine 数据类（Lens 报告序列化）
-keep class com.clink.app.engine.CleanResult { *; }
-keep class com.clink.app.engine.RemovedParam { *; }

# 保留 R 文件
-keepclassmembers class **.R$* {
    public static <fields>;
}

# 通用安全规则
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# 移除 Log 调用（减少字符串常量体积）
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
}
