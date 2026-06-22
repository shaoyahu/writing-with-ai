## 1. 数据层 — Room 实体与 DAO

- [x] 1.1 新增 `FeishuRefEntity`(`feishu_ref` 表,PK `noteId`,FK CASCADE)
- [x] 1.2 新增 `FeishuSyncEventEntity`(`feishu_sync_event` 表,索引 `createdAt`)
- [x] 1.3 新增 `FeishuRefDao`:upsert / getByNoteId / getByDocId / deleteByNoteId / listAllWithRef()
- [x] 1.4 新增 `FeishuSyncEventDao`:insert / listLast20 / deleteOldestExceeding(100)
- [x] 1.5 Room version `4 → 5` + AutoMigration
- [x] 1.6 `AppDatabase` 注册新 entity / DAO
- [x] 1.7 枚举 `SyncDirection` / `FeishuRefStatus` 单独文件

## 2. Repository + Service

- [x] 2.1 新建 `core/feishu/sync/FeishuSyncRepository.kt`:`push(noteId)` / `pull(docUrl)` / `pullByDocId(docId)`;每个动作 idempotent
- [x] 2.2 新建 `core/feishu/sync/FeishuSyncService.kt`:核心工作流编排 — push / pull 详细步骤按 design.md D2 / D3 实现
- [x] 2.3 新建 `core/feishu/sync/FeishuConflictResolver.kt`:detectConflict(localRev, storedRemoteRev, newRemoteRev) → ConflictResult
- [x] 2.4 同步完成后 insert `FeishuSyncEventEntity` 行

## 3. 列表页状态展示

- [x] 3.1 `feature/quicknote/list/QuickNoteListViewModel` 增 `NoteWithFeishuRef` 数据结构(运行时组装 note + ref)
- [x] 3.2 `QuickNoteListScreen` row 加飞书状态 chip:无 ref → 不显示;SYNCED → 灰图标;DIRTY → 黄「待同步」;CONFLICT → 红「冲突」;REMOTE_DELETED → 灰删除线「远程已删」

## 4. 详情页触发入口

- [x] 4.1 `feature/quicknote/detail/QuickNoteDetailScreen.kt` 增「...」菜单:
  - 同步到飞书
  - 从飞书链接拉取(弹 dialog 输入 docUrl)
  - 在飞书中打开(有 ref 时才显示,跳 Custom Tabs 到 docUrl)
- [x] 4.2 `QuickNoteDetailViewModel` 加 push / pull / openFeishu actions

## 5. 冲突解决 UI

- [x] 5.1 新建 `feature/quicknote/detail/ConflictResolutionDialog.kt`:三选一(保留本地 / 保留飞书 / 取消),默认焦点「保留飞书」
- [x] 5.2 `QuickNoteDetailViewModel` 监听 `feishu_ref.status = CONFLICT` → 自动弹 dialog
- [x] 5.3 用户选择后调 `FeishuConflictResolver.resolve(...)` 写 ref + 触发 sync event

## 6. 远程已删保护

- [x] 6.1 push / pull 遇 404 → 标 `REMOTE_DELETED`
- [x] 6.2 列表页 chip 显示「远程已删」
- [x] 6.3 详情页菜单「重新同步为新文档」入口 → 删 ref 行 + 调 push 创新文档

## 7. 同步日志

- [x] 7.1 设置页「同步日志」section:`FeishuSyncEventDao.listLast20()` → list desc by createdAt
- [x] 7.2 顶部 disclaimer:「同步不消耗 AI token,只调飞书 API」
- [x] 7.3 每条显示:时间 / 方向 / 状态 / 错误信息(若有)

## 8. Hilt 注入

- [x] 8.1 `core/feishu/di/FeishuModule.kt` 增 `FeishuSyncRepository` / `FeishuSyncService` / `FeishuConflictResolver` providers
- [x] 8.2 ViewModel 注入

## 9. i18n

- [x] 9.1 `values/strings.xml` 增:菜单项 / 状态 chip 文案 / 冲突 dialog / 远程已删 / 同步日志 / disclaimer
- [x] 9.2 `values-en/strings.xml` 同步

## 10. 测试

- [x] 10.1 `FeishuSyncRepositoryTest`:push 流程(无 ref / 有 ref / 冲突 / 远程已删 / 空内容)
- [x] 10.2 `FeishuConflictResolverTest`:本地新 / 远程新 / 双向脏 / 空内容 四场景
- [x] 10.3 `FeishuRefDaoTest`:cascade delete + revision 比对
- [x] 10.4 `FeishuSyncEventDaoTest`:insert + listLast20 + cap 100 自动清理
- [x] 10.5 集成测试:用 `FakeFeishuApiClient` + `FakeConverter` 跑端到端 push / pull / conflict UI 流程

## 11. 编译 + ktlint

- [x] 11.1 `./gradlew :app:assembleDebug` 通过
- [x] 11.2 `./gradlew :app:ktlintCheck` 通过
- [x] 11.3 `./gradlew :app:testDebugUnitTest` 全绿
- [x] 11.4 `./gradlew :app:check` 全绿