## 1. Gradle Task 落地

- [x] 1.1 在 `app/build.gradle.kts` 末尾新增 `tasks.register("checkReleaseReadiness")` 块,`group = "verification"`,`description = "Run 4 preflight checks before release builds"`
- [x] 1.2 Task 实现 check-1:`grep -rn '__TODO__' app/src/main/res/values-en/strings.xml`,非空输出 → 抛 GradleException 含 `Preflight FAILED [check-1]`
- [x] 1.3 Task 实现 check-2:`grep -rnE '\bapikey\s*=\s*"[a-zA-Z0-9_-]{16,}"' app/src/main/java/com/yy/writingwithai/`,非空 → `Preflight FAILED [check-2]`
- [x] 1.4 Task 实现 check-3:`file("app/src/main/res/xml/backup_rules.xml").exists() && file(".../data_extraction_rules.xml").exists()`,任一不存在 → `Preflight FAILED [check-3]`
- [x] 1.5 把 check-1~3 内部 grep 输出解析逻辑抽到 `internal fun parseGrepOutput(text: String): List<PreflightFailure>`(同 `app/build.gradle.kts` script body 或同级 .kts),Task 内调用;为可单测放 `app/src/main/java/com/yy/writingwithai/buildlogic/PreflightCheck.kt`,build.gradle.kts `import` 进来

## 2. release buildType 钩子

- [x] 2.1 `app/build.gradle.kts` `release` buildType 加 `dependsOn("checkReleaseReadiness", "ktlintCheck")`(用 `afterEvaluate { tasks.named("assembleRelease") { dependsOn(...) } }` 挂载,buildTypes DSL 无直接 tasks 子块)
- [x] 2.2 `debug` buildType 不动(不挂 preflight)
- [x] 2.3 验证 `./gradlew :app:tasks --all | grep checkReleaseReadiness` 输出 Task 名 + group

## 3. JVM 单测

- [x] 3.1 新建 `app/src/test/java/com/yy/writingwithai/buildlogic/CheckReleaseReadinessTaskTest.kt`(实际放 `buildSrc/src/test/kotlin/...`,见 §1.5 说明)
- [x] 3.2 `parseGrepOutput("")` 返回空 list
- [x] 3.3 `parseGrepOutput("file1.kt:10: match1\nfile2.kt:20: match2")` 返回 2 条记录
- [x] 3.4 跑 `./gradlew :buildSrc:test` 全绿(4 用例通过)

## 4. 端到端验证

- [x] 4.1 临时往 `app/src/main/res/values-en/strings.xml` 加一行 `__TODO__`,跑 `./gradlew :app:checkReleaseReadiness` 应失败 + 输出 `Preflight FAILED [check-1]`(✓ 命中 strings.xml:446)
- [x] 4.2 撤销 4.1 改动
- [x] 4.3 临时往某 Kotlin 文件加 `val apikey = "sk-abcdef1234567890abcdef12"`,跑 checkReleaseReadiness 应失败 + 输出 `Preflight FAILED [check-2]`(✓ 命中 ApikeyPoisonProbe.kt:4)
- [x] 4.4 撤销 4.3 改动
- [x] 4.5 临时 `mv app/src/main/res/xml/backup_rules.xml /tmp/`,跑 checkReleaseReadiness 应失败 + 输出 `Preflight FAILED [check-3]`(✓ 命中 backup_rules.xml:0)
- [x] 4.6 恢复 backup_rules.xml
- [x] 4.7 跑 `./gradlew :app:assembleDebug` 应成功(不触发 preflight)(✓ BUILD SUCCESSFUL in 52s)
- [x] 4.8 跑 `./gradlew :app:assembleRelease`(本机未配 signingConfig 时 unsigned 也应过 preflight,APK 生成 OK)(✓ BUILD SUCCESSFUL in 1m 10s,preflight + ktlintCheck 双重通过,unsigned APK 输出到 app/build/outputs/apk/release/)

## 5. 收口

- [x] 5.1 `./gradlew :app:ktlintCheck` 全绿(✓)
- [x] 5.2 `./gradlew :buildSrc:test` 全绿(4 用例通过;task 3.4 路径从 `:app:testDebugUnitTest` 调整到 `:buildSrc:test`,因 `parseGrepOutput` 物理位置在 buildSrc)
- [x] 5.3 `docs/usage/known-issues.md` KI-012 状态 `[open]` → `[resolved]`(本 change 修),保留条目;归档到 `docs/reviews/` 待连续 2 个 release 未复发后按维护说明 §108 处理
- [x] 5.4 `docs/progress.md` 顶部追加本 change 收口条目