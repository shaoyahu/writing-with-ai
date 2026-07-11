# 修 review-r1 全量问题

## Why

2026-07-11 6 个 archived change(`ai-regenerate-versions` / `ai-usage-statistics` / `attachment-inline-render` / `markdown-live-preview` / `morning-freewrite` / `note-graph-view`)通过 code review 暴露一批问题,集中在:

- **CRITICAL(2)**: `attachment-inline-render` 任务清单虚假完成 — 详情屏未接入 + lightbox 三文件 + nav route 全缺,用户看不到内联缩略图也点不进 lightbox
- **HIGH(7)**: 数据丢失 / 统计曲线错位 / 数值异常 / a11y 静默 / 测试虚假 done
- **MEDIUM(13)**: 注释 vs 实现漂移 / 时区 / 测试覆盖空缺 / BackHandler 漏态 / exact-alarm 权限 / BootReceiver cancel 缺失 / LaunchedEffect key 错位 / `Log.d` release 裸露 / `NoteDao.getByIds` 假 TODO

LOW(16)推到下一轮 polish。本 change 只修 CRITICAL + HIGH + MEDIUM。

## What changes

- `attachment`: 详情屏正文替换为 `InlineMarkdownText`,补 `AttachmentLightboxScreen` / `AttachmentLightboxViewModel` / `AttachmentLightbox` route;图片 / lightbox contentDescription 走 `stringResource`
- `quick-note`: detail 屏 `Log.d` 加 `BuildConfig.DEBUG` 守卫;preview-related TODO(en) i18n 补完
- `note-entity-link`: graph 屏 contentDescription / Canvas a11y 描述 / `ForceLayout` tolerance 默认值改回 0.05 / `CircularLayout` hash 负值修复 / `GraphDataLoader` 2-hop 真用 `NoteDao.getByIds`
- `core/ui` (markdown-renderer, markdown-text): `MarkdownRenderer` 删无条件 `dropLast(1)` 兜底
- `ai-history`: 新增 `AiHistoryDaoTest`(in-memory Room,3 case)
- `core/data/repo` (usage): `UsagePeriod.startOfDayMillis` 改 UTC epoch day,与 SQL `GROUP BY (createdAt / 86400000)` 对齐;`AiUsageScreen` a11y 描述补齐
- `core/prefs` (provider-cost): `setCostRate` 头加 `isFinite()` 校验
- `core/notification` (morning-freewrite): `BackHandler` 三态统一 `showExitDialog`;`NoProvider` 跳 `SettingsModelManagement` route;`BootReceiver` enabled=false 分支调 `scheduler.cancel()`;`Scheduler` 检测到 `canScheduleExactAlarms()==false` 弹 `AlertDialog` 跳 `Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM`;`Notifier` channel description 与 body 分开
- `core/note/graph`: `ForceLayout` 排斥力公式改成 paper 一致;`CircularLayout` 角度稳定在 `[0, 2π)`
- `feature/aiwriting/usage` / `quicknote/graph` / `aiwriting/streaming`: `UsageBarChart` / `NoteGraphScreen` / `NoteGraphCanvas` contentDescription / liveRegion 公告;`StreamingPanel` LaunchedEffect key 用 `position + accumulatedLength`
- `app` (androidTest): `AppDatabaseMigrationTest` 加 fresh-install v14 用例

## Impact

- 用户可见: 附件详情屏可见内联缩略图 + 点开 lightbox / AI 用量图表坐标轴对得上日期 / 早 freewrite 写作中 back 不丢内容
- 安全/隐私: 不变(`ProviderCostStore` 加固浮点 + `Log.d` 加固 release)
- 性能: 不变(`ForceLayout` 收敛判定不变,只是数值常量统一)
- 测试: 新增 in-memory DAO test + fresh-install migration test