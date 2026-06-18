// writing-with-ai · app module build.gradle.kts
// 见 docs/usage/development-setup.md 与 gradle/libs.versions.toml。

import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ktlint)
}

android {
    namespace = "com.yy.writingwithai"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.yy.writingwithai"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables { useSupportLibrary = true }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = false
            // M5 打磨阶段配 release 签名 / ProGuard 规则;M0 不开 minify。
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        // M2 修:启用 BuildConfig 以便 DataModule 用 BuildConfig.DEBUG gate
        // `fallbackToDestructiveMigration()`,防止 release 升级 schema 时 wipe 用户笔记。
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    lint {
        // 占位字符串允许只用 default locale,关闭 MissingTranslation warning。
        disable += setOf("MissingTranslation")
        warningsAsErrors = false
        abortOnError = true
        // HardcodedText 升级为 error(见 app/lint.xml),与 abortOnError=true 配合阻断 hardcoded 中文字符串。
        lintConfig = file("lint.xml")
    }
}

// Room schema export:把 Room 生成的 schema JSON 输出到 app/schemas/,
// git 追踪后可以做 AutoMigration 或人工写 Migration。
// 路径是相对项目根,见 openspec/changes/quick-note-feature/specs/quick-note/spec.md
// §"Note database schema is exportable"。
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.incremental", "true")
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    // ----- AndroidX core -----
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.activity.compose)
    implementation(libs.bundles.androidx.lifecycle)
    implementation(libs.androidx.navigation.compose)

    // ----- Compose (BOM 控制版本) -----
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.bundles.androidx.compose.ui)
    debugImplementation(libs.androidx.compose.ui.tooling)

    // ----- Persistence(M0 仅进 classpath,M1+ 真正用) -----
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.datastore.preferences)

    // ----- DI -----
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)

    // ----- Network(M0 占用,M2 真正用) -----
    implementation(libs.okhttp)

    // ----- Widget(M0 占用,M4 真正用) -----
    implementation(libs.androidx.glance.appwidget)

    // ----- Coroutines -----
    implementation(libs.kotlinx.coroutines.android)

    // ----- Serialization(navigation-compose 类型安全路由)-----
    implementation(libs.kotlinx.serialization.json)

    // ----- Test(JUnit5 + MockK + Turbine + Compose Test) -----
    testImplementation(libs.bundles.junit5)
    testRuntimeOnly(libs.junit.jupiter.engine)
    testImplementation(libs.mockk)
    testImplementation(libs.turbine)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.okhttp.mockwebserver)

    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.espresso.core)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}

// JUnit5 启用 Jupiter 引擎;AGP 默认 JUnit 4,必须显式切换。
tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

ktlint {
    android = true
    // Compose 约定与 ktlint 默认规则冲突的项目级关闭清单;
    // 其余规则集中在 config/ktlint/.editorconfig。
    // 注意:本项目常量严格遵守 CLAUDE.md §"约定" UPPER_SNAKE_CASE,
    // 因此 standard:property-naming 不再禁用。
    disabledRules.set(
        setOf(
            // Composable 必须 PascalCase
            "standard:function-naming",
            // Color.kt 长表达式允许单行 / 自定义折行
            "standard:multiline-expression-wrapping",
        ),
    )
}
