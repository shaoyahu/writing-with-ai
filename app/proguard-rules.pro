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
-keepattributes *Annotation*, InnerClasses
-keepclassmembers class * implements kotlinx.serialization.KSerializer {
    static **$* *;
}
-keep class com.yy.writingwithai.core.ai.api.** { *; }
-keep class com.yy.writingwithai.app.AppNavKt { *; }

# ----- App 入口(应用类 + Activity 不能混淆) -----
-keep class com.yy.writingwithai.app.WritingApp
-keep class com.yy.writingwithai.app.MainActivity
