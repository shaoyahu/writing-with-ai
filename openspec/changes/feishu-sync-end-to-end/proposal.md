## Why

飞书双向同步的 6 个底层 change(`feishu-bidir-sync` / `feishu-oauth-flow` / `feishu-user-oauth` / `feishu-cli-analysis` / `feishu-openapi-kotlin-client` / `feishu-doc-service-refactor`)均已归档。`core/feishu/` 下的 sync 引擎、auth、converter、API client、DAO 都已落盘并通过单元测试。

**端到端现状(对齐 reality,2026-06-27 audit):**

| 入口 | 现状 | 来源 |
| --- | --- | --- |
| 详情页 push/pull 入口 | ✅ 已实现(`QuickNoteDetailScreen.kt:324-369` overflow menu 4 项:推送到飞书 / 从飞书拉取 / 在飞书中打开 / 另存为新文档) | `feishu-bidir-sync` change 内联 |
| 详情页 VM push/pull/conflict 状态机 | ✅ 已实现(`QuickNoteDetailViewModel.kt:127-247`) | 同上 |
| 详情页内联 URL 输入 dialog | ✅ 已实现(`QuickNoteDetailScreen.kt:624-653`,`OutlinedTextField` + 确认/取消) | 同上 |
| 冲突解决 dialog(3 选项:保留本地 / 保留飞书 / 取消) | ✅ 已实现(`feature/quicknote/detail/ConflictResolutionDialog.kt`,74 行) | 同上 |
| 列表项 feishu ref 状态 chip | ✅ 已实现(`QuickNoteListScreen.kt:124 + 196-216`,AssistChip + D6 状态色) | 同上 |
| 设置页同步日志 section 挂载 | ❌ **缺失**:`FeishuSyncLogSection` 已在 `feature/settings/feishu/`，但 `FeishuAuthScreen` 没拼装 | 本 change 补 |
| `FeishuSyncEventDao.listLast` 响应式 | ❌ **缺失**:目前是 `suspend fun` 一次性，不能驱动 `FeishuSyncLogSection` 自动刷新 | 本 change 补 |
| i18n 完整 12 key | ❌ **缺失 9/12**:仅 3 个 key 已存在(`quicknote_detail_pull_from_feishu` / `quicknote_feishu_pull_dialog_title` / `quicknote_feishu_conflict_remote_placeholder`) | 本 change 补 |
| `FeishuShareViewModel` 抽象层 | ❌ **缺失**:`QuickNoteDetailViewModel` 已包揽 push/pull 状态机，但**没**独立的可单测 wrapper | 本 change 补 |
| `FeishuShareViewModelTest` | ❌ **缺失** | 本 change 补 |

**结论**:详情 + 列表 + 内联 URL dialog + 冲突 dialog 4 个入口均已在 `feishu-bidir-sync` change 内落盘。本 change **不再重写这些入口**，只补 5 件真缺失:

1. 设置页挂载 `FeishuSyncLogSection`
2. 升级 `FeishuSyncEventDao.listLast` 为 `Flow`
3. 补完 9 个缺失 string key
4. 新建 `FeishuShareViewModel`(薄包装，给单测用，UI 已绑 detail VM 不迁移)
5. 新建 `FeishuShareViewModelTest`

## What Changes

- `feature/settings/feishu/FeishuAuthScreen.kt`:已连接状态下挂载 `FeishuSyncLogSection`
- `core/feishu/sync/FeishuSyncEventDao.kt`:新增 `observeLast(limit: Int = 20): Flow<List<FeishuSyncEventEntity>>`，沿用 `listLast` 不删(保留向后兼容)
- `feature/settings/feishu/FeishuSyncLogSection.kt`(已存在，本 change 不动 UI):改用 `observeLast` 替换 `listLast`
- 新建 `feature/quicknote/share/FeishuShareViewModel.kt`:`@HiltViewModel` 薄包装，暴露 `ShareState` sealed(Idle/Pushing/Pushed/Pulling/Pulled/Conflict/Error),`push(noteId)` / `pull(docUrl)` / `resolveConflictKeepLocal` / `resolveConflictKeepRemote` / `clearState` —— **不挂到 UI**(详情 VM 已用 inline state)
- `res/values/strings.xml` + `res/values-en/strings.xml`:新增 9 个 string key(已有 3 个，共 12)
- 新建 `app/src/test/java/com/yy/writingwithai/feature/quicknote/share/FeishuShareViewModelTest.kt`:5 用例覆盖 push 成功 / push 失败 / pull 成功 / pull 冲突 / pull 错误

## Capabilities

### Modified Capabilities

- `feishu-bidir-sync`:增加 1 个 Requirement "Sync engine is reachable from QuickNote detail UI"(已由底层 change 落地，本 change 不再写需求细节);增加 1 个 Scenario "Settings page renders sync log reactively when connected"(由本 change 落)
- `feishu-auth`:增加 1 个 Scenario "Settings page shows sync log section when connected"

## Impact

- 新增文件 2 个:`FeishuShareViewModel.kt` / `FeishuShareViewModelTest.kt`
- 修改 3 个:`FeishuAuthScreen.kt` / `FeishuSyncEventDao.kt` / `FeishuSyncLogSection.kt`
- 新增 9 个 string key(双语;3 个已存在，key 集合共 12)
- 不动 `FeishuSyncService` / `FeishuAuthStore` / `FeishuApiClient` / converter / DAO(除 `listLast` 加 Flow 版本)
- 不动详情 / 列表 / 冲突 dialog / 内联 URL dialog(已在底层 change 落地)
- 不引入新依赖(纯 Compose + Hilt + Room)
- 端到端验证:USER-OWNED，本 change 只列任务，不强制 AI 跑
