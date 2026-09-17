# ============================================================
# Venera Compose R8 规则
# 反射/桥接点清单（改代码时同步维护）：
#   - VeneraJsEngine.NativeBridge  : WebView @JavascriptInterface 桥
#   - ComicSourceParser/JsComicSource : Gson 序列化 JS<->原生 数据包
#   - source.model.* / download.* / stats.* / sync.* / data.db.* : Gson 模型
#   - kotlinx.serialization : 导航路由参数（ComicItem 等 @Serializable）
# ============================================================

# ---------- WebView JS 桥 ----------
# JS 侧按名字调用 _venera.xxx(...)，方法名/类名绝不能混淆
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
-keep class com.venera.compose.engine.VeneraJsEngine$NativeBridge { *; }

# ---------- Gson 反射模型 ----------
# JS 规则源返回的 JSON 经 Gson 反序列化，字段名即契约
-keep class com.venera.compose.source.model.** { *; }
-keep class com.venera.compose.download.** { *; }
-keep class com.venera.compose.stats.** { *; }
-keep class com.venera.compose.sync.** { *; }
-keep class com.venera.compose.data.db.** { *; }
-keep class com.venera.compose.feature.ComicItem { *; }
-keep class com.venera.compose.feature.favoriteimages.** { *; }
-keep class com.venera.compose.security.guard.** { *; }
-keep class com.venera.compose.feature.sourcemanage.** { *; }

# Gson 自身的 TypeToken/注解
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class * extends com.google.gson.reflect.TypeToken
-keep public class * implements java.lang.reflect.Type
-keep class sun.misc.Unsafe { *; }
-dontwarn sun.misc.**
-keepattributes Signature
-keepattributes *Annotation*
-keepattributes EnclosingMethod
-keepattributes InnerClasses
-keepattributes RuntimeVisibleAnnotations,RuntimeVisibleParameterAnnotations,AnnotationDefault

# ---------- kotlinx.serialization（导航路由） ----------
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class com.venera.compose.**$$serializer { *; }
-keepclassmembers class com.venera.compose.** { *** Companion; }
-keepclasseswithmembers class com.venera.compose.** { kotlinx.serialization.KSerializer serializer(...); }

# ---------- OkHttp / Okio ----------
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**
-keepclasseswithmembers class * { @retrofit2.http.* <methods>; }
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# ---------- Jsoup ----------
-dontwarn org.jsoup.**
-keep class org.jsoup.** { *; }

# ---------- Coil 3 ----------
-dontwarn coil3.**
-keep class coil3.** { *; }

# ---------- WebView / JS 引擎宿主 ----------
-keep class android.webkit.** { *; }
-dontwarn android.webkit.**

# ---------- Coroutines ----------
-dontwarn kotlinx.coroutines.**
-keepclassmembers class kotlinx.coroutines.** { volatile <fields>; }

# ---------- 移除 release 日志噪音 ----------
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
}
# 保留 w/e 级别（SourceHttpDiagnostics / 崩溃排查依赖）

# ---------- 防止 R8 对 Enum/values/valueOf 的隐式依赖报警 ----------
-dontwarn androidx.compose.**
