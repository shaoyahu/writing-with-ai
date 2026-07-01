# fix-2026-06-24-review-r1-critical Proposal

## Why

`docs/reviews/2026-06-24-full-project-code-review-r1.md` 全量扫描发现 **8 项 CRITICAL**(路径穿越 ×3、OAuth CSRF、Zip Slip、LLM 提示注入 ×2、FakeProvider 默认走 prod、SyncEngine stub 静默失败)+ **3 项 lint error**(导致 `./gradlew :app:check` 红)。任意一项都可被本地恶意 app / 构造 import zip / 构造笔记内容利用，或直接阻断 CI。HIGH/MEDIUM/LOW 暂不收，留后续 change 分批。

## What Changes

- **C1** UpdateDownloadReceiver:安装 Intent filename 从 manifest 的 `apkName` 派生(`Regex("^[A-Za-z0-9._-]+$")` allow-list)，不再从服务器 URL 派生
- **C2** Feishu OAuth:hosted callback 引入随机 `state`(PKCE/UUID),`OAuthCodeReceiver` 校验 `state` 与原值相等后再交换 code
- **C3** ZipHelper.readZip:resolve entry → canonical，必须以 `targetDir.canonicalPath` 为前缀，否则抛 `ImportRejected`
- **C4** AttachmentStore.getAttachmentFile/delete/deleteAllForNote:三段 id 走 `Regex("^[A-Za-z0-9_-]{1,64}$")`,resolve 后 canonical 必须 inside attachmentsDir
- **C5** WebDavSyncEngine:push/pull 改为 `SyncResult.Unsupported("B5b 未启用")`,UI 显示明确状态;不再无条件返回 Failure
- **C6** LlmNoteLinkExtractor + LlmEntityExtractor:用户笔记内容用 fenced block(`<<<USER_NOTE>>>...<<<END>>>`)包住，system prompt 写明"只解析 fenced 内容"
- **C7** LlmNoteLinkExtractor + LlmEntityExtractor:`gateway.streamWritingOp` 收集时 enforce `MAX_CHARS = 16384` 上限，超出抛 `TokenLimitExceeded`
- **C8** AiModule.provideFakeAiProvider:`@Provides` 加 `if (BuildConfig.DEBUG)` 分支，prod 不注册 fake;`ProviderPrefsStore.DEFAULT_PROVIDER_ID` 改为 `null`(首次启动走 onboarding 让用户选)
- **lint-1** UpdateDownloadReceiver.kt:59-60:`cursor.getColumnIndexOrThrow()` + null 检查
- **lint-2** SettingsDataScreen.kt:79:`settings_data_save_failed` 改为合法 format string(加 `%s` 占位符)

**BREAKING**:
- C5 改动 WebDavSyncEngine 返回类型(从 `Failure` 改 `Unsupported`)，任何调用方需要处理新 case
- C8 改 `DEFAULT_PROVIDER_ID` 默认值，从 `"fake"` 改 `null`，首次启动行为变化(从直接走 fake 改成 onboarding 引导)

## Capabilities

### New Capabilities

无。本次不引入新 capability，只补强已有 spec 的需求。

### Modified Capabilities

- **ai-gateway**:C8 — `FakeAiProvider` 不得在 prod 构建注册到 `Map<String, AiProvider>`;`ProviderPrefsStore.DEFAULT_PROVIDER_ID` 默认值改为 `null`
- **feishu-auth**:C2 — user OAuth flow 必须生成并校验 `state` 防 CSRF
- **data-export-import**:C3 — `ZipHelper.readZip` 必须防 Zip Slip(entry canonical 必须以 targetDir 为前缀)
- **feishu-bidir-sync**:C4 — `AttachmentStore` 三段 id 校验 + canonical 必须 inside root;C5 — `WebDavSyncEngine.push/pull` 必须返回 typed `Unsupported` 而非 `Failure`
- **note-entity-link**:C6 + C7 — `LlmNoteLinkExtractor` 用户内容 fenced + token/字符上限
- **note-entity-extraction**:C6 + C7 — `LlmEntityExtractor` 用户内容 fenced + token/字符上限
- **release-readiness**:C1 + lint-1 — UpdateDownloadReceiver safe filename + `getColumnIndex` Range 检查
- **localization**:lint-2 — `settings_data_save_failed` 改为合法 format string

## Impact

- **Code**:
  - `core/update/UpdateDownloadReceiver.kt`、`ApkDownloader.kt`、`AppUpdateManifest.kt`
  - `core/feishu/auth/OAuthLauncher.kt`、`OAuthCodeReceiver.kt`
  - `core/data/export/ZipHelper.kt`、`NoteImporter.kt`
  - `core/media/AttachmentStore.kt`
  - `core/sync/WebDavSyncEngine.kt`、`SyncTypes.kt`(新增 `Unsupported` case)
  - `core/note/impl/LlmNoteLinkExtractor.kt`
  - `core/note/entity/LlmEntityExtractor.kt`
  - `core/ai/di/AiModule.kt`、`core/ai/provider/ProviderPrefsStore.kt`
  - `feature/settings/data/SettingsDataScreen.kt`
  - `app/src/main/res/values/strings.xml`、`values-en/strings.xml`
- **APIs**: `SyncResult` sealed 加 `Unsupported(data: String)` case
- **Tests**: 补 ZipHelper / AttachmentStore / UpdateDownloadReceiver / OAuth state 单测;现有 `AppUpdateCheckerTest` + `AboutViewModelTest` 加 safe-name 断言
- **CI**: `./gradlew :app:check` 必须从 FAIL 转 PASS;lint baseline 不动(3 个 error 不能进 baseline，直接修)
- **Migration**: 无 DB schema 变更;无 Room migration