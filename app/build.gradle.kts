// writing-with-ai · app module build.gradle.kts
// 见 docs/usage/development-setup.md 与 gradle/libs.versions.toml。

import com.yy.writingwithai.buildlogic.PreflightFailure
import com.yy.writingwithai.buildlogic.parseGrepOutput
import java.util.Properties
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
        versionCode = 5
        versionName = "0.4.1"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables { useSupportLibrary = true }

        // M4-4 onboarding-consent:consent 版本号(改隐私条款时 bump)与
        // gate 启用开关(回滚逃生口，设 false 即回到 M4-3 行为)。
        buildConfigField("Boolean", "CONSENT_GATE_ENABLED", "true")
        buildConfigField("int", "CONSENT_VERSION", "1")
    }

    // release-readiness:从 ~/.gradle/gradle.properties 读 4 凭据(keystore 不入库，本机维护)
    signingConfigs {
        create("release") {
            val props = Properties().apply {
                val f = file(System.getProperty("user.home") + "/.gradle/gradle.properties")
                if (f.exists()) f.inputStream().use { load(it) }
            }
            storeFile = props.getProperty("RELEASE_STORE_FILE")?.let { file(it) }
            storePassword = props.getProperty("RELEASE_STORE_PASSWORD")
            keyAlias = props.getProperty("RELEASE_KEY_ALIAS")
            keyPassword = props.getProperty("RELEASE_KEY_PASSWORD")
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
            // debug 双通道:不同包名可同装，独立检查更新
            applicationIdSuffix = ".debug"
            buildConfigField(
                "String",
                "UPDATE_MANIFEST_URL",
                "\"https://xiaozha.nananxue.cn/app/debug/version.json\""
            )
        }
        release {
            // release 双通道:独立检查更新
            buildConfigField(
                "String",
                "UPDATE_MANIFEST_URL",
                "\"https://xiaozha.nananxue.cn/app/release/version.json\""
            )
            // release-readiness:开启 R8 混淆 + 资源压缩 + 签名
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // C2 修:仅当本机 ~/.gradle/gradle.properties 配齐 4 凭据时挂 signingConfig,
            // 未配时 release 走 unsigned(AGP 在 configuration 阶段不会因 storeFile=null 崩溃),
            // 用户可在配齐凭据后单独跑 ./gradlew :app:assembleRelease 出包。
            signingConfigs.getByName("release").let { cfg ->
                if (cfg.storeFile != null) signingConfig = cfg
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        // M2 修:启用 BuildConfig 以便 DataModule 用 BuildConfig.DEBUG gate
        // `fallbackToDestructiveMigration()`，防止 release 升级 schema 时 wipe 用户笔记。
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    lint {
        // 占位字符串允许只用 default locale，关闭 MissingTranslation warning。
        disable += setOf("MissingTranslation")
        warningsAsErrors = false
        abortOnError = true
        // HardcodedText 升级为 error(见 app/lint.xml)，与 abortOnError=true 配合阻断 hardcoded 中文字符串。
        lintConfig = file("lint.xml")
        // entity-extraction-association:已记录 2 个 pre-existing 错误(AppNav.kt:109 FlowOperator +
        // ModelManagementScreen.kt:86 produceState)，与本 change 无关;Step 3 / 独立 polish 修。
        baseline = file("lint-baseline.xml")
    }

    // onboarding-apikey-prompt:jvm 单测走 isReturnDefaultValues=true，允许 android.util.Log.* 等
    // 未 mock Android 类返回默认值(0/null)而非抛 RuntimeException，降低 VM 内部日志对单测的耦合。
    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

// Room schema export:把 Room 生成的 schema JSON 输出到 app/schemas/,
// git 追踪后可以做 AutoMigration 或人工写 Migration。
// 路径是相对项目根，见 openspec/changes/quick-note-feature/specs/quick-note/spec.md
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

    // ----- Network(M0 占用，M2 真正用) -----
    implementation(libs.okhttp)

    // ----- Widget(M0 占用，M4 真正用) -----
    implementation(libs.androidx.glance.appwidget)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.documentfile)

    // ----- Security(M4-4 EncryptedSharedPreferences for AI apikey)-----
    implementation(libs.androidx.security.crypto)
    // feishu-user-oauth:CustomTabs 拉飞书 OAuth 授权页 + deep link 回跳
    implementation(libs.androidx.browser)

    // ----- Coroutines -----
    implementation(libs.kotlinx.coroutines.android)

    // ----- Serialization(navigation-compose 类型安全路由)-----
    implementation(libs.kotlinx.serialization.json)

    // ----- Test(JUnit5 + MockK + Turbine + Compose Test + Robolectric) -----
    testImplementation(libs.bundles.junit5)
    testRuntimeOnly(libs.junit.jupiter.engine)
    // fix-2026-06-26-review-r3-test:启用 JUnit Vintage engine。
    // 原因:Robolectric 的 @RunWith(RobolectricTestRunner::class) 是 JUnit4 注解，
    // JUnit5 platform 通过 Vintage engine 才能跑 JUnit4 写法的 Robolectric 测试。
    // 重写 NoteRepositoryDeleteOrderTest 用 Robolectric + Room.inMemoryDatabaseBuilder
    // 消除 MockK coEvery { throws ... } coAnswers { ... } 死锁。
    testRuntimeOnly(libs.junit.vintage.engine)
    testImplementation(libs.mockk)
    testImplementation(libs.turbine)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.runner)
    testImplementation(libs.robolectric.core)
    testImplementation(libs.androidx.compose.ui.test.junit4)
    testImplementation(libs.okhttp.mockwebserver)

    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.espresso.core)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    // fix-2026-06-25-review-r1 M9:MigrationTestHelper 验证 AppDatabase 1→9 AutoMigration。
    androidTestImplementation(libs.androidx.room.testing)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}

// JUnit5 启用 Jupiter 引擎;AGP 默认 JUnit 4，必须显式切换。
tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

ktlint {
    android = true
    // Compose 约定与 ktlint 默认规则冲突的项目级关闭清单;
    // 其余规则集中在 config/ktlint/.editorconfig。
    // 注意:本项目常量严格遵守 CLAUDE.md §"约定" UPPER_SNAKE_CASE,
    // 因此 standard:property-naming 不再禁用。
    // M5 fix:项目根 .editorconfig 用 ktlint 1.0 per-rule 下划线格式
    // (disabledRules SetProperty 在 1.0.x rule-engine 不生效，
    // 见 memory ktlint-compose-pascalcase-1.0)。
    disabledRules.set(
        setOf(
            // Composable 必须 PascalCase
            "standard:function-naming",
            // Color.kt 长表达式允许单行 / 自定义折行
            "standard:multiline-expression-wrapping"
        )
    )
}

// release-preflight-automation:release 前置 4 项校验。
// 见 openspec/changes/release-preflight-automation/{proposal,design}.md。
// check-1: values-en/strings.xml 残留 __TODO__ 占位
// check-2: 主源码出现明文 apikey 字面量(16+ 字母数字字符)
// check-3: res/xml/backup_rules.xml + data_extraction_rules.xml 存在
// check-4: 保留，见 design.md §R5 风险说明
// 失败格式:`Preflight FAILED [check-N]: file:line — message`
tasks.register("checkReleaseReadiness") {
    group = "verification"
    description = "Run 4 preflight checks before release builds"
    doLast {
        val failures = mutableListOf<PreflightFailure>()

        fun runGrep(args: List<String>): String {
            val proc = ProcessBuilder(args).redirectErrorStream(true).start()
            val text = proc.inputStream.bufferedReader().readText()
            proc.waitFor()
            return text
        }

        // check-1: __TODO__ 占位
        parseGrepOutput(
            runGrep(
                listOf(
                    "grep",
                    "-rn",
                    "__TODO__",
                    file("src/main/res/values-en/strings.xml").absolutePath
                )
            )
        ).forEach { failures += it.copy(checkId = "check-1") }

        // check-2: 明文 apikey 字面量
        parseGrepOutput(
            runGrep(
                listOf(
                    "grep",
                    "-rnE",
                    "\\bapikey\\s*=\\s*\"[a-zA-Z0-9_-]{16,}\"",
                    file("src/main/java/com/yy/writingwithai/").absolutePath
                )
            )
        ).forEach { failures += it.copy(checkId = "check-2") }

        // check-3: 备份规则 xml 必须存在
        val backupRules = file("src/main/res/xml/backup_rules.xml")
        val dataExtractionRules = file("src/main/res/xml/data_extraction_rules.xml")
        if (!backupRules.exists()) {
            failures += PreflightFailure(
                checkId = "check-3",
                file = "src/main/res/xml/backup_rules.xml",
                line = 0,
                message = "missing required file"
            )
        }
        if (!dataExtractionRules.exists()) {
            failures += PreflightFailure(
                checkId = "check-3",
                file = "src/main/res/xml/data_extraction_rules.xml",
                line = 0,
                message = "missing required file"
            )
        }

        if (failures.isNotEmpty()) {
            val msg = failures.joinToString("\n") { f ->
                "Preflight FAILED [${f.checkId}]: ${f.file}:${f.line} — ${f.message}"
            }
            throw GradleException("Preflight FAILED:\n$msg")
        }
    }
}

// release-preflight-automation:release 出包前必须先跑 preflight + ktlint。
// debug 出包不挂 preflight(快速迭代)。
// 用 afterEvaluate 避开 buildTypes DSL 嵌套 tasks 块的类型问题。
afterEvaluate {
    tasks.named("assembleRelease") {
        dependsOn("checkReleaseReadiness", "ktlintCheck")
    }
}
