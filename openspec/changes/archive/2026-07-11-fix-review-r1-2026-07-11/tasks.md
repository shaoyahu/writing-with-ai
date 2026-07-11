# Tasks · fix-review-r1-2026-07-11

## CRITICAL · attachment Phase 2 + 3

- [ ] T1 `feature/quicknote/detail/QuickNoteDetailScreen.kt` 正文 `Text(note.content)` → `InlineMarkdownText`,传 `attachmentDao` + `onAttachmentClick = navigate("attachment_lightbox/{id}")`
- [ ] T2 新建 `feature/quicknote/lightbox/AttachmentLightboxScreen.kt`(@Composable 全屏 + 关闭按钮 + footer)
- [ ] T3 新建 `feature/quicknote/lightbox/AttachmentLightboxViewModel.kt`(从 `NoteAttachmentDao` 查 `localPath` + StateFlow)
- [ ] T4 `app/AppNav.kt` 新增 `composable("attachment_lightbox/{id}")` 入口
- [ ] T5 双语 `strings.xml` + `values-en/strings.xml`: `quicknote_attachment_lightbox_close` / `quicknote_attachment_lightbox_size_fmt` / `quicknote_attachment_image_load_failed` / `quicknote_attachment_image_a11y`

## HIGH · 核心正确性

- [ ] T6 `core/ui/MarkdownRenderer.kt:331` 删无条件 `if (s.endsWith(")")) s = s.dropLast(1)`
- [ ] T7 `core/prefs/ProviderCostStore.kt:42 setCostRate` 入口加 `isFinite() && >= 0` `require` 双校验
- [ ] T8 `core/data/repo/UsagePeriod.kt:43 startOfDayMillis` 改 UTC epoch day `(ms / 86_400_000L) * 86_400_000L`
- [ ] T9 新建 `app/src/androidTest/java/.../core/data/db/AiHistoryDaoTest.kt`(in-memory Room + 3 case)

## HIGH · a11y

- [ ] T10 `feature/aiwriting/usage/UsageBarChart.kt` Canvas 加 `semantics { contentDescription = stringResource(aiwriting_usage_chart_a11y) }`,每桶非 0 加桶级 contentDescription
- [ ] T11 `feature/quicknote/graph/NoteGraphCanvas.kt` Canvas `mergeDescendants = true` + 节点 / 边列表 contentDescription
- [ ] T12 `core/ui/MarkdownText.kt` image contentDescription 走 `R.string.quicknote_attachment_image_a11y` + 原名
- [ ] T13 双语加新 strings: `aiwriting_usage_chart_a11y` / `aiwriting_usage_chart_bucket_fmt` / `note_graph_a11y_summary` / `note_graph_node_fmt` / `note_graph_edge_fmt`

## MEDIUM · 杂项

- [ ] T14 `core/note/graph/ForceLayout.kt:32` `tolerance = 0.5` → `0.05`
- [ ] T15 `core/note/graph/GraphDataLoader.kt:81` 2-hop 真用 `noteDao.getByIds(ids)`,删 TODO 占位
- [ ] T16 `feature/quicknote/detail/QuickNoteDetailScreen.kt` `Log.d` 加 `BuildConfig.DEBUG` 守卫
- [ ] T17 `core/notification/BootReceiver.kt` `enabled=false` 分支调 `scheduler.cancel()`
- [ ] T18 `feature/morningfreewrite/MorningFreewriteScreen.kt` `BackHandler` 三态统一 `showExitDialog`,`enabled = state.isDirty`
- [ ] T19 `core/notification/Scheduler.kt` `canScheduleExactAlarms()==false` 弹 `AlertDialog` 跳 `Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM`
- [ ] T20 `core/notification/Notifier.kt` channel description 走 `setDescription(...)`,body 每次设
- [ ] T21 `feature/aiwriting/streaming/StreamingPanel.kt` LaunchedEffect key 用 `position + accumulatedLength`
- [ ] T22 `app/src/androidTest/java/.../AppDatabaseMigrationTest.kt` 加 `freshInstall_v14_matchesLatestSchema` case
- [ ] T23 `core/note/graph/ForceLayout.kt:130` 排斥力公式改 `1.0 / dist` + damping
- [ ] T24 `core/note/graph/CircularLayout.kt` 角度改 `(hash.toLong() and 0xFFFFFFFFL) / 0xFFFFFFFFL * 2 * PI`
- [ ] T25 `feature/morningfreewrite/MorningFreewriteScreen.kt` NoProvider toast 改 `navController.navigate("settings/model_management")`
- [ ] T26 `ForceLayout` / `CircularLayout` 类加 `@Inject constructor`,`GraphModule` 用 `@Binds`

## 验证

- [ ] V1 `./gradlew :app:assembleDebug` 绿
- [ ] V2 `./gradlew :app:ktlintCheck` 绿
- [ ] V3 `./gradlew :app:testDebugUnitTest` 绿
- [ ] V4 `./gradlew :app:connectedDebugAndroidTest` 绿(AiHistoryDaoTest + freshInstall v14)

## 归档

- [ ] Z1 `openspec archive fix-review-r1-2026-07-11 -y`(V1-V4 全绿后)