# 2026-06-24 feishu-doc-service-refactor code-review r1

Review scope: uncommitted changes in `app/src/main/java/com/yy/writingwithai/core/feishu/` + tests.
Covers `feishu-user-oauth`, `feishu-doc-service-refactor`, `feishu-bidir-sync` deltas (互相纠缠,合并 review)。

Diff size: `+263 / -604`. 删多增少 — 主线是 OAuth 链路从 `tenant_access_token` 切到 `user_access_token` + `FeishuSyncService` 拆 facade。

---

## CRITICAL (blocks production)

### C1 · OAuth 回调永远不会到达 app
- `app/src/main/java/com/yy/writingwithai/core/feishu/auth/OAuthLauncher.kt:41` —
  `REDIRECT_URI = "https://xiaozha.nananxue.cn/callback"`(HTTPS 页面)。
- `app/src/main/AndroidManifest.xml:91-94` — Activity 注册的 scheme 是
  `com.yy.writingwithai://feishu/callback`。
- Manifest 注释(80-81 行)宣称"HTTPS 页面 callback/index.html 用 JS 跳 custom scheme",
  **repo 里没有任何 `index.html` / 托管 callback 页面**(`find` 全仓 0 命中)。
- 后果:飞书 OAuth authorize → 重定向到 HTTPS 页面 → 用户卡死 → `OAuthCodeReceiver`
  永远收不到 `code`。整条 `feishu-user-oauth` change 在 prod 上无法完成首次授权。
- **Fix**:要么把 `REDIRECT_URI` 改成 `com.yy.writingwithai://feishu/callback`(纯 custom scheme,
  飞书开放平台需配该 URL);要么真的去 `xiaozha.nananxue.cn/callback/` 上传一个跳回 app 的
  HTML,并在 `docs/usage/` 写明托管契约。二选一,别同时存在两套不一致的 URI。

### C2 · `pull` 永远写入空 markdown
- `app/src/main/java/com/yy/writingwithai/core/feishu/sync/FeishuSyncService.kt:66` —
  `val markdown = docxConverter.convert(emptyList())`。
- `DocxToMarkdownConverter` 是真接口 + 真实现(`converter/FeishuConverterModule.kt:24`),
  但 caller 传的是 `emptyList()`。`pull` 不论飞书侧有什么,本地 note 的 `content` 永远被覆盖成空。
- 注释(64-65 行)自己也承认"仅作占位",但 **production code 真的跑了这条 path**,不是测试。
- **Fix**:真把 `content.rawBlocksJson` 解出 `FeishuBlock` 列表再 `docxConverter.convert(blocks)`。
  或者在 doc 上加 `// TODO(M4-3):` 并在调用前 `require(rawBlocksJson.isNotBlank())` 防止
  静默丢数据。

### C3 · `updateDoc` 第二次同步 URL 仍报 404(用户已报告,未修)
- `app/src/main/java/com/yy/writingwithai/core/feishu/sync/FeishuDocService.kt:55` —
  `feishuApi.batchDeleteChildren(ref.docId, ref.docId, 0, childCount)`。
- 把 `docId` 同时塞进 `docId` 和 `parentBlockId`,拼出 URL
  `/open-apis/docx/v1/documents/{docId}/blocks/{docId}/children/batch_delete` —
  这就是用户上一轮提到的错误信息原样。
- 飞书 docx v1 的 batch_delete 期望 `parent_block_id` 是文档根 page block 的 `block_id`,
  对新建文档通常等于 `document_id`,但**不是 API 契约保证**(服务端校验失败就 404)。
- **Fix**:从 `getBlocks(ref.docId)` 返回里找 `block_type=1`(page block)的 `block_id`,
  用它当 `parentBlockId`。或者直接走 v2 `docs_ai/v1` 的 `command=overwrite`(无索引问题)。
  建议加一个回归测试:`updateDoc` 第二次调用必须 200。

---

## HIGH

### H1 · `UserTokenProvider.exchangeCode` 死参数
- `app/src/main/java/com/yy/writingwithai/core/feishu/auth/UserTokenProvider.kt:75` —
  `suspend fun exchangeCode(appId, appSecret, code, redirectUri)`。
- `redirectUri` 在函数体里没出现过。`OAuthCodeReceiver.kt:63` 传了 `OAuthLauncher.REDIRECT_URI`
  但实际没用上。
- **Fix**:删参数,或者真用上(飞书 v1 不验 redirect_uri,但 v2 验,留着当契约更稳)。

### H2 · `FeishuAuthStore.setAppSecretTransient` / `getAppSecretTransient` 死方法
- `app/src/main/java/com/yy/writingwithai/core/feishu/auth/FeishuAuthStore.kt:44-45`
  接口声明 + `FeishuAuthStoreImpl.kt:120-123` 实现,**全仓无 caller**(grep 0 命中)。
- 实际用的是 `persistAppSecret` / `getAppSecretSnapshot` 走 EncryptedSharedPreferences。
- **Fix**:删 transient 那对方法,interface 减 2 行,impl 减 4 行,少一处认知负担。

### H3 · `FeishuAuthStore` 加密 prefs 初始化失败静默降级
- `app/src/main/java/com/yy/writingwithai/core/feishu/auth/FeishuAuthStore.kt:58-62` —
  `runCatching { openEncryptedPrefs() }.getOrElse { ... null }`。
- `prefs == null` 后,所有 read 返回 null、所有 write 不报错但不生效、`authState` 永远是
  `DISCONNECTED`。Android Keystore 不可用 / Keystore 被 wipe 时用户看着像"没登录过"。
- **Fix**:fail loud + 暴露 `authState = ERROR_KEYSTORE`。或者 retry 一次。CLAUDE.md 的
  "AI 调用失败必须 fallback"原则适用于 OAuth 凭据吗?不适用 — 凭据丢失必须显式。

### H4 · `FeishuDocService.readDoc` 元数据丢光
- `app/src/main/java/com/yy/writingwithai/core/feishu/sync/FeishuDocService.kt:48` —
  `FeishuDocContent(docId = docId, title = "", revisionId = "", rawBlocksJson = raw)`。
- `FeishuSyncService.pull` line 62:`content.title.ifBlank { titleHint }` → 永远走 `titleHint`。
  文档真标题(`createDoc` / `updateDoc` 都调 `getDocument` 拿 `title`)不显示。
- **Fix**:`readDoc` 调一次 `getDocument(docId)` 拿 title + revisionId,塞进 `FeishuDocContent`。

### H5 · `FeishuDocService.countPageChildren` 用正则解析 JSON
- `app/src/main/java/com/yy/writingwithai/core/feishu/sync/FeishuDocService.kt:129` —
  `Regex("\"block_type\"\\s*:\\s*1[^}]*\"children\"\\s*:\\s*\\[([^\\]]*)\\]")`。
- block 内任意 text run 出现 `"block_type":1`(比如代码块示例)就误中。
- **Fix**:用 `kotlinx.serialization.json` 解 envelope → `data.items` → 找
  `block_type=1` 的 page block → 读 `children` 数组长度。已经引入了 `kotlinx.serialization`,
  没理由再手撸正则。

### H6 · `updateDoc` 非原子:delete 成功 + append 失败 = 空文档
- `app/src/main/java/com/yy/writingwithai/core/feishu/sync/FeishuDocService.kt:52-58`。
- 顺序:`getBlocks` → `batchDeleteChildren(0..n)` → `convert` → `appendChildren`。
  任意一步失败,本地 ref 已不在,飞书侧内容也没了,用户下次同步会 `updateDoc` 写空。
- **Fix**:用 v2 `docs_ai/v1/{doc}` 的 `command=overwrite`(单次 PUT,无 race);
  或在 `batchDelete` 失败时不抛、直接 continue(保留旧内容 + 追加新内容 → 重复但安全)。

### H7 · `FeishuApiClient.getBlocks` 返回原始 envelope,caller 自己解析
- `app/src/main/java/com/yy/writingwithai/core/feishu/api/FeishuApiClientImpl.kt:72-78`。
- 返回类型 `String`,内容是 `{"code":0,"msg":"ok","data":{"items":[...]}}`。
- caller `FeishuDocService.countPageChildren` 又用 regex 解 envelope。接口形状错。
- **Fix**:接口改为 `suspend fun getBlocks(docId: String): List<FeishuBlock>`
  (caller 拿到结构化数据);envelope 解析留在 `executeRequest` 内部。

---

## MEDIUM

### M1 · `UserTokenProvider.httpClient` 修饰符错配
- `app/src/main/java/com/yy/writingwithai/core/feishu/auth/UserTokenProvider.kt:32` —
  `protected open val httpClient`。
- `UserTokenProvider` 不是 `open class`,`protected` 等价 `private`,`open` 无意义。
- **Fix**:改 `private val`,或真改成 `open class UserTokenProvider(...)`。

### M2 · `UserTokenProvider` POST body 字符串拼接,无 JSON 转义
- `app/src/main/java/com/yy/writingwithai/core/feishu/auth/UserTokenProvider.kt:62,79,112` —
  `"""{"app_id":"$appId","app_secret":"$appSecret"}"""` 等。
- app_secret / refresh_token / code 里如果含 `"` / `\` / 控制字符,生成的 JSON 损坏。
- **Fix**:用 `buildJsonObject { put("app_id", appId); put("app_secret", appSecret) }`。
  该文件已经引了 `kotlinx.serialization.json`,零增量依赖。

### M3 · `FeishuApiClientImpl.appendBlockV2` 抑制告警 + 冗余 `Unit`
- `app/src/main/java/com/yy/writingwithai/core/feishu/api/FeishuApiClientImpl.kt:229-231` —
  `@Suppress("UNUSED_EXPRESSION") executeRequest(request); Unit`。
- **Fix**:`executeRequest(request)` 当 statement 调用 → Kotlin 自动 Unit,不需要 `; Unit`。
  抑制注解也删。

### M4 · 硬编码飞书租户域
- `app/src/main/java/com/yy/writingwithai/core/feishu/api/FeishuApiClientImpl.kt:52,179` —
  fallback URL 用 `https://bytedance.feishu.cn/docx/{id}`。
- `BASE_URL = https://open.feishu.cn/open-apis`(line 235)。
- 非字节租户用会跳错域。CLAUDE.md 没强制多租户,但当前代码写死了 bytedance host。
- **Fix**:从 `FeishuAuthStore` 取 `tenant_domain` / `host`(飞书 OAuth 响应里有),
  URL 模板化。

### M5 · `OAuthCodeReceiver` 重复清理 appSecret
- `app/src/main/java/com/yy/writingwithai/core/feishu/auth/OAuthCodeReceiver.kt:65` —
  `authStore.clearAppSecret()`。
- `UserTokenProvider.exchangeCode` 内部 line 92 已经 `store.clearAppSecret()` 了。
- **Fix**:删 receiver 里的那行,信任 exchangeCode 的合约。

### M6 · URL 拼接未编码
- `app/src/main/java/com/yy/writingwithai/core/feishu/api/FeishuApiClientImpl.kt:83,94,192,211,225` —
  `parentBlockId` / `docToken` 直接拼路径。Feishu block_id 通常安全,但万一上游污染会 path traversal。
- **Fix**:`parentBlockId.addPathSegments(parentBlockId)` 用 HttpUrl builder。

### M7 · `OAuthLauncher.launch` 重复 FLAG_ACTIVITY_NEW_TASK
- `app/src/main/java/com/yy/writingwithai/core/feishu/auth/OAuthLauncher.kt:21` —
  `intent.intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)`。
- `CustomTabsIntent.launchUrl(context, ...)` 内部已处理启动 flag。
- **Fix**:删,或加注释说明此处是从 Application context 启动的兜底。

### M8 · 空内容判断太窄
- `app/src/main/java/com/yy/writingwithai/core/feishu/sync/FeishuSyncService.kt:59` —
  `content.rawBlocksJson.isBlank() || content.rawBlocksJson == "{}"`。
- 飞书真实空文档响应是 `{"code":0,"msg":"ok","data":{"items":[]}}` — 不等于 `"{}"`,
 也不 blank,守卫失效。
- **Fix**:要么改成 parsed 后判断 `items.isEmpty()`(配合 H7 一起),要么先正则匹配 `items\":\s*\[\s*\]`。

### M9 · `appendBlock` 不复用 `blockToJson` builder
- `app/src/main/java/com/yy/writingwithai/core/feishu/sync/FeishuDocService.kt:79-82`
  手搓 paragraph JSON,`blockToJson` 已经能产 paragraph。两套 escape 逻辑并存。
- **Fix**:复用 `blockToJson(FeishuBlock.Paragraph(listOf(Run(content))))`。

---

## LOW

### L1 · `FeishuDocService.escapeJson` 不完整
- `app/src/main/java/com/yy/writingwithai/core/feishu/sync/FeishuDocService.kt:172` —
  只转 `\\`, `"`, `\n`。漏 `\r` `\t` 控制字符 JSON 规范的 `\u00XX`。
- **Fix**:统一改用 `JsonPrimitive(s).toString().removeSurrounding("\"")`,或加 `\r`/`\t` 分支。

### L2 · `OAuthCodeReceiver` `catch (e: Throwable)`
- `app/src/main/java/com/yy/writingwithai/core/feishu/auth/OAuthCodeReceiver.kt:67`。
- 太宽。`CancellationException` 会吞掉,coroutine 取消语义被破坏。
- **Fix**:`catch (e: FeishuError)` + `catch (e: IOException)`,或至少
  `if (e is CancellationException) throw e`。

### L3 · `FeishuSyncService.disconnectAll` 只清 refs 不清 events
- `app/src/main/java/com/yy/writingwithai/core/feishu/sync/FeishuSyncService.kt:120` —
  `refDao.deleteAll()`,events 表保留。
- **Fix**:同时 `eventDao.trimTo(0)` 或显式 `eventDao.deleteAll()`(如果有该 API)。

### L4 · `appendBlock` fallback 只捕 NotFound
- `app/src/main/java/com/yy/writingwithai/core/feishu/sync/FeishuDocService.kt:89`。
- `appendChildren` 成功 + `recordEvent` 失败(比如 DB lock)→ ref 已更新,event 丢。
- **Fix**:把 `recordEvent` 放进 try/catch,记 warn,不阻塞主流程。

### L5 · `MarkdownToXmlConverter` 空白行产生 `<p>   </p>`
- `app/src/main/java/com/yy/writingwithai/core/feishu/converter/MarkdownToXmlConverter.kt:43-46`。
- **Fix**:加 `trimmed.isNotBlank()` 判断,纯空白行跳过。

### L6 · `FeishuSyncService.push` 注释撒谎
- `app/src/main/java/com/yy/writingwithai/core/feishu/sync/FeishuSyncService.kt:34-38`
  注释说有"冲突 → FeishuConflictResolver",代码里没看到 resolver 调用路径。
- **Fix**:要么实现,要么把注释删 / 改成 TODO。

### L7 · `AuthInterceptor.runBlocking` 未注释原因
- `app/src/main/java/com/yy/writingwithai/core/feishu/api/AuthInterceptor.kt:30,38`。
- OkHttp 拦截器是同步接口,`runBlocking` 是官方推荐做法,但本仓库习惯用 suspend,
  读代码的人会怀疑。
- **Fix**:加一行注释说明 OkHttp interceptor 是同步契约。

---

## Test coverage gap(同批报告)

- `FeishuDocService` 没有任何单测。`FeishuSyncServiceTest` 通过 4-arg 构造间接覆盖
  `createDoc` / `updateDoc` 成功路径,但:
  - 没覆盖 `updateDoc` 第二次同步的 404 路径(C3);
  - 没覆盖 `appendBlock` 触发 `NotFound` → fallback 到 `updateDoc` 的分支;
  - 没覆盖 `countPageChildren` 的边界(空 children / 多 page block)。
- `UserTokenProvider` 没有单测。exchangeCode / refresh / app token 三条路径全空。
- `FeishuAuthStoreImpl` 没有单测。`openEncryptedPrefs` 失败路径(H3)无验证。
- `OAuthCodeReceiver` 没有 instrumented test,custom-scheme callback 链路无端到端。

---

## Summary

3 个 CRITICAL 都阻塞 M5 user-OAuth 上线:
- **C1** OAuth 根本走不通;
- **C2** pull 丢数据;
- **C3** 用户上次报的问题还没修。

建议:
1. 立刻修 C1+C3,加单测防回归;
2. C2 在 facade 上加 TODO + 阻塞 throw,别静默丢;
3. HIGH 一组排入下一轮 review;
4. M3(appendBlockV2 抑制告警)随手清掉,ktlint check 大概率会报。