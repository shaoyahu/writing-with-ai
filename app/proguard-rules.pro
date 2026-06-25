# writing-with-ai · release-readiness · R8 / ProGuard keep 规则
# 5 段分组:Hilt / Room / Compose / Glance / kotlinx-serialization
# 引入方式:AGP `proguard-android-optimize.txt` 默认 + 本文件叠加(见 app/build.gradle.kts release{})
# 凭据 / 签名配 ~/.gradle/gradle.properties(不入库),见 docs/usage/signing.md

# ----- Hilt (DI 反射构造 @HiltViewModel) -----
-keep class dagger.hilt.** { *; }
-keep class * extends dagger.hilt.android.lifecycle.HiltViewModel
-keep,allowobfuscation @interface dagger.hilt.android.lifecycle.HiltViewModel
-keep @dagger.hilt.android.AndroidEntryPoint class *

# ----- Room (DAO / Entity / Database 反射) -----
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao class * { *; }

# ----- Compose (M3 编译器默认 + @Composable 反射) -----
-keep class androidx.compose.runtime.** { *; }
-keepclassmembers class * {
    @androidx.compose.runtime.Composable <methods>;
}

# ----- Glance (widget 反射调用 provideContent / GlanceAppWidget) -----
-keep class androidx.glance.** { *; }
-keep class * extends androidx.glance.appwidget.GlanceAppWidgetReceiver { *; }
-keep class * extends androidx.glance.appwidget.GlanceAppWidget { *; }

# ----- kotlinx-serialization (@Serializable 反射 + KSerializer 静态方法) -----
# R8 默认会剥 @Serializable 类的 $Companion / serializer() 静态方法,
# 运行期抛 "Serializer for class 'X' is not found" → 闪退。
# 见 https://github.com/Kotlin/kotlinx.serialization#android
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
# kotlinx-serialization 编译器生成的 serializer() / $Companion 必须保留
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
# 官方推荐:keep 所有 @Serializable 标注的类(包级粒度),
# 比 -if 嵌套规则更稳。
-keep @kotlinx.serialization.Serializable class com.yy.writingwithai.** { *; }
# 业务侧用到 @Serializable 的类 + 包含 $Companion 的引用
-keep class com.yy.writingwithai.core.ai.api.** { *; }
-keep class com.yy.writingwithai.core.ai.provider.** { *; }
-keep class com.yy.writingwithai.core.feishu.converter.** { *; }
-keep class com.yy.writingwithai.core.widget.** { *; }
-keep class com.yy.writingwithai.core.update.** { *; }
-keep class com.yy.writingwithai.core.data.export.** { *; }
-keep class com.yy.writingwithai.core.note.impl.** { *; }
-keep class com.yy.writingwithai.app.AppNavKt { *; }
-keep class com.yy.writingwithai.app.AppShellKt { *; }
-keep class com.yy.writingwithai.feature.my.MeTabTargetKt { *; }

# ----- App 入口(应用类 + Activity 不能混淆) -----
-keep class com.yy.writingwithai.app.WritingApp
-keep class com.yy.writingwithai.app.MainActivity
# Hilt 在编译期生成 Hilt_WritingApp / Hilt_MainActivity 父类,
# Application.attachBaseContext 走 Hilt 入口,父类被混淆或剥离会导致 release 启动崩。
# 见 https://dagger.dev/hilt/gradle-setup (R8 keep 段落)。
-keep class * extends android.app.Application
-keep class * extends android.app.Activity
-keep class * extends androidx.fragment.app.Fragment
-keep class * extends android.app.Service
-keep class * extends android.content.BroadcastReceiver

# ----- Tink 间接引用(errorprone annotations 仅编译期,R8 可安全跳过) -----
-dontwarn com.google.errorprone.annotations.**
# Conscrypt / okhttp 间接引用(R8 默认不报)
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# ----- Hilt 生成的 *_HiltModules / *_GeneratedInjector(父类链必需)-----
-keep class **_HiltModules { *; }
-keep class **_HiltModules$* { *; }
-keep class **_GeneratedInjector { *; }
-keep class hilt_aggregated_deps.** { *; }
