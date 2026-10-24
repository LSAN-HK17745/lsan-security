# ============================================================
# LSAN-信息防护终端 · 发布构建混淆与防逆向配置
# ============================================================

# 由系统按类名实例化的组件必须保留（Activity / Service / Receiver / AccessibilityService）
-keep class com.lsan.sec.ui.MainActivity { *; }
-keep class com.lsan.sec.ui.LauncherWhite { *; }
-keep class com.lsan.sec.ui.LauncherBlack { *; }
-keep class com.lsan.sec.service.** { *; }
-keep class * extends android.accessibilityservice.AccessibilityService
-keep class * extends android.service.notification.NotificationListenerService
-keep class * extends android.app.Service
-keep class * extends android.content.BroadcastReceiver
-keep class * extends android.app.Activity

# 组件类保留可读名，其余全部混淆并压缩到根包
-repackageclasses ''
-allowaccessmodification

# 移除调试与日志痕迹
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
    public static int w(...);
    public static int e(...);
}
-assumenosideeffects class java.io.PrintStream {
    public void println(...);
    public void print(...);
}

# 行号信息仅保留源文件名（避免暴露原始路径）
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
-keepattributes Signature,Exceptions

# 混淆字典：使用无意义短名，提高可读性破坏
-obfuscationdictionary proguard-dict.txt
-classobfuscationdictionary proguard-dict.txt
-packageobfuscationdictionary proguard-dict.txt

# 抑制无害告警
-dontwarn org.json.**
-dontwarn java.lang.invoke.**
