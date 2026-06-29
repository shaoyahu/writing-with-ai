## 1. 设置页同步日志挂载

- [x] 1.1 `core/feishu/sync/FeishuSyncEventDao.kt` 新增 `observeLast(limit: Int = 20): Flow<List<FeishuSyncEventEntity>>`,内部用 `@Query("SELECT * FROM feishu_sync_events ORDER BY created_at DESC LIMIT :limit")` 包装;**沿用**现有 `suspend fun listLast(limit: Int = 20)` 不删
- [x] 1.2 `feature/settings/feishu/FeishuSyncLogSection.kt`(已存在)将内部 `listLast(20)` 调用改为 `observeLast(20).collectAsState(emptyList())`;首屏空 list 显 `R.string.feishu_sync_log_disclaimer` + 空态文案
- [x] 1.3 `feature/settings/feishu/FeishuAuthScreen.kt`(已存在)已连接状态(检查 `FeishuAuthState.connected`)时挂载 `FeishuSyncLogSection(eventDao)`;未连接时不渲染

## 2. ViewModel(薄包装,供单测)

- [x] 2.1 新建 `feature/quicknote/share/FeishuShareViewModel.kt`:`@HiltViewModel` 包装 `FeishuSyncService` + `FeishuRefDao`,暴露 `state: StateFlow<ShareState>`(`ShareState` sealed:Idle/Pushing/Pushed(docUrl)/Pulling/Pulled(noteId)/Conflict(noteId)/Error(msg))
- [x] 2.2 `push(noteId)`:调 `service.push(id)` → Pushing → Pushed(docUrl via ref.docUrl) 或 Error(throws FeishuError 转 msg)
- [x] 2.3 `pull(docUrl)`:VM 内 regex 提取 docId(同 `QuickNoteDetailViewModel.extractDocId` 写法)→ 调 `service.pull(docId, docUrl, titleHint="来自飞书")` → Pulled(ref.noteId) 或 Error
- [x] 2.4 `resolveConflictKeepLocal(noteId)` / `resolveConflictKeepRemote(noteId)`:重置 ref 状态 + 触发对应 push/pull,完成后 `clearState()`
- [x] 2.5 `clearState()`:重置 `state.value = Idle`

## 3. i18n

- [x] 3.1 `res/values/strings.xml` 新增 9 个 key:`feishu_chip_synced` / `feishu_chip_conflict` / `feishu_chip_remote_deleted` / `feishu_chip_pending` / `feishu_conflict_title` / `feishu_conflict_keep_local` / `feishu_conflict_keep_remote` / `share_to_feishu` / `feishu_sync_log_disclaimer`
- [x] 3.2 `res/values-en/strings.xml` 同步 9 key 英文翻译(参考 D4 表)
- [x] 3.3 验证 key 集合双侧完全一致(用 grep 双向 diff,无差异)

## 4. JVM 单测

- [x] 4.1 新建 `app/src/test/java/com/yy/writingwithai/feature/quicknote/share/FeishuShareViewModelTest.kt`
- [x] 4.2 测 1:`push(noteId)` service 成功 → state 转 Pushed(docUrl="https://bytedance.feishu.cn/docx/abc")
- [x] 4.3 测 2:`push(noteId)` service 抛 FeishuError → state 转 Error(msg)
- [x] 4.4 测 3:`pull(docUrl="https://bytedance.feishu.cn/docx/abc")` service 成功 → state 转 Pulled(noteId)
- [x] 4.5 测 4:`pull(...)` service 抛 FeishuError(冲突场景)→ state 转 Error(msg)(注:本 VM 简化,不弹 Conflict 状态,统一 Error)
- [x] 4.6 测 5:`clearState()` 后 state == Idle

## 5. 端到端验证(USER-OWNED)

- [ ] 5.1 真机连飞书开放平台自建 app(用户已有 `appId` / `appSecret`)
- [ ] 5.2 设置页 → "连接飞书" → 填 appId/appSecret → OAuth 走通 → 跳回设置页 → "已连接"
- [ ] 5.3 设置页 → 飞书授权页 → 同步日志 section 显示最近 20 条 sync event
- [ ] 5.4 详情页 → 菜单 → "推送到飞书" → 飞书后台看到新 doc → 列表 chip 变 SYNCED
- [ ] 5.5 飞书后台改 doc → 详情页 → "从飞书拉取 URL" → 弹冲突 → 选"保留飞书" → 本地更新
- [ ] 5.6 列表项 chip 状态显示符合 D6 颜色

## 6. 收口

- [x] 6.1 `./gradlew :app:assembleDebug` 全绿
- [x] 6.2 `./gradlew :app:ktlintCheck` 全绿
- [x] 6.3 `./gradlew :app:testDebugUnitTest` 全绿(含 5 个新 FeishuShareViewModelTest 用例)
- [x] 6.4 `docs/progress.md` 顶部追加本 change 收口条目
