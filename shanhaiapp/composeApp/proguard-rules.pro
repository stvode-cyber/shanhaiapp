# ====================================================================
# 山海妖志 · ProGuard / R8 混淆规则（Compose Multiplatform + Kotlin 2.0）
# ====================================================================

# ── 通用 ──────────────────────────────────────────────────────────────
-dontwarn org.jetbrains.annotations.**
-keep class kotlin.Metadata { *; }
-keepclassmembers class kotlin.Metadata { public <methods>; }
-keepattributes InnerClasses

# Kotlin 反射
-keep class kotlin.reflect.** { *; }
-dontwarn kotlin.reflect.jvm.internal.**

# ── Kotlin 2.0 / K2 编译器 ────────────────────────────────────────────
-dontwarn kotlin.contracts.**

# ── Compose Multiplatform ─────────────────────────────────────────────
# Compose 编译器生成的 @Composable 函数必须保留签名
-keepclassmembers class ** {
    @androidx.compose.runtime.Composable <methods>;
}
-keepnames class androidx.compose.runtime.Composable

# Compose State 保留
-keep class androidx.compose.runtime.** { *; }
-keepclassmembers class androidx.compose.runtime.** { *; }

# Material3
-keep class androidx.compose.material3.** { *; }

# ── Kotlin Coroutines ─────────────────────────────────────────────────
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembernames class kotlinx.** {
    volatile <fields>;
}
-dontwarn kotlinx.coroutines.**

# ── kotlinx.serialization（shared 模块用了 Serialization） ─────────────
-keepattributes *Annotation*, Signature, InnerClasses
-keep class kotlinx.serialization.** { *; }
-keepclassmembers class **$$serializer { *; }
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @kotlinx.serialization.SerialName <methods>;
}
# shared 模块 data class（用了 @Serializable）
-keep class com.shanhai.core.data.** { *; }
-keep class com.shanhai.core.logic.** { *; }
-keep class com.shanhai.core.persistence.** { *; }

# ── Shared 模块 expect/actual 接口（关键！混淆会破坏跨端反射） ─────────
# expect/actual 声明必须保留完整签名
-keep class com.shanhai.core.platform.** { *; }
-keep interface com.shanhai.core.platform.** { *; }
-keep class com.shanhai.core.presentation.** { *; }
-keep interface com.shanhai.core.presentation.** { *; }

# ── Data Class 保留（toString/equals/hashCode 不能丢） ─────────────────
-keepclassmembers class ** {
    public boolean equals(java.lang.Object);
    public int hashCode();
    public java.lang.String toString();
}

# ── osmdroid（OpenStreetMap，Apache 2.0，已由 osmdroid-android:6.1.20 自动处理） ──

# ── AndroidX 保留（debuggable=false 时有些规则冲突） ──────────────────
-keep class androidx.core.** { *; }
-dontwarn androidx.core.**
-keep class androidx.activity.** { *; }
-dontwarn androidx.activity.**

# ── 不混淆 Compose 的 modifier chain（Compose 调试） ──────────────────
# 生产 release 可以删除此行减小体积
# -keep class androidx.compose.ui.Modifier { *; }

