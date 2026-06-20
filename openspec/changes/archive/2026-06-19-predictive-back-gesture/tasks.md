## 1. AndroidManifest 配置

- [ ] 1.1 改 `app/src/main/AndroidManifest.xml` `<application>` 加 `android:enableOnBackInvokedCallback="true"`(targetSdk 35 + Android 14+ 强制)
- [ ] 1.2 改 `<activity android:name=".app.MainActivity">` 加 `android:enableOnBackInvokedCallback="true"` + `android:windowSoftInputMode="adjustResize"`
- [ ] 1.3 不写自定义 `BackHandler`(roadmap §7.2 明确);让 `NavHost` + `OnBackPressedDispatcher` 自管

## 2. WidgetIntent helper

- [ ] 2.1 新建 `core/widget/WidgetIntentHelpers.kt`:
  ```kotlin
  internal fun Context.createTaskStackPendingIntent(route: String, requestCode: Int): PendingIntent {
      val intent = Intent(this, MainActivity::class.java)
          .putExtra(OpenNoteAction.EXTRA_ROUTE, route)
          .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
      return TaskStackBuilder.create(this)
          .addNextIntentWithParentStack(intent)
          .getPendingIntent(
              requestCode,
              PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
          )
          ?: error("TaskStackBuilder.getPendingIntent returned null")
  }

  private const val REQUEST_CODE_CREATE = 1001
  private const val REQUEST_CODE_OPEN = 1002
  ```

## 3. Widget createNoteIntent 改 TaskStackBuilder

- [ ] 3.1 改 `core/widget/QuickNoteWidget.kt` `createNoteIntent(context)`:
  ```kotlin
  internal fun createNoteIntent(context: Context): PendingIntent =
      context.createTaskStackPendingIntent("quicknote/edit?prefillFocus=true", REQUEST_CODE_CREATE)
  ```
- [ ] 3.2 改 `Widget2x2` + `Widget4x2` 内的 `actionStartActivity(createNoteIntent(context))` → `actionStartActivity(createNoteIntent(context))`(签名不变,只是用 helper)
- [ ] 3.3 删 `Intent.FLAG_ACTIVITY_CLEAR_TOP`(M4-1 旧 flag)— 用 `CLEAR_TASK` 替换

## 4. OpenNoteAction 改 TaskStackBuilder

- [ ] 4.1 改 `core/widget/OpenNoteAction.kt onAction`:
  ```kotlin
  override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
      val noteId = parameters[KEY_NOTE_ID] ?: return
      val intent = Intent(context, MainActivity::class.java)
          .putExtra(EXTRA_ROUTE, "quicknote/detail/$noteId")
          .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
      TaskStackBuilder.create(context)
          .addNextIntentWithParentStack(intent)
          .startActivities()
  }
  ```
- [ ] 4.2 `TaskStackBuilder.startActivities()` 替代裸 `context.startActivity(intent)`(让 back 走 TaskStackBuilder 构造的栈)

## 5. AppNav LaunchedEffect 不动

- [ ] 5.1 `AppNav.kt` `LaunchedEffect(initialRoute)` 已 M4-1 r2 修过 `popUpTo(QuicknoteList) { inclusive = true }`,**M4-2 不动**;widget Intent 走 TaskStackBuilder 是新增路径,AppNav popUpTo 是兜底

## 6. 测试

- [ ] 6.1 加 `core/widget/WidgetIntentHelpersTest.kt`(JUnit5 + Robolectric `ApplicationProvider.getApplicationContext()`):
  - 验 `createTaskStackPendingIntent(route, requestCode=1001).flags and FLAG_IMMUTABLE != 0`
  - 验 `... and FLAG_UPDATE_CURRENT != 0`
  - 验 `getPendingIntent.getIntent().getStringExtra("route") == "quicknote/edit?prefillFocus=true"`
- [ ] 6.2 加 `core/widget/OpenNoteActionTest.kt`(JUnit5 + Robolectric):
  - 验 `onAction` 后 `startActivities()` 被调(intent 含正确 `EXTRA_ROUTE`)

## 7. i18n

- [ ] 7.1 无新增文案(手势 back 无 UI 文案;沿用 M1 既有 `quicknote_editor_cancel` = "取消")

## 8. ktlint + Compose PascalCase

- [ ] 8.1 跑 `./gradlew :app:ktlintCheck` → 已知 PascalCase follow-up 之外,0 新增;新 helper 走 camelCase(`createTaskStackPendingIntent`)

## 9. 整体验收

- [ ] 9.1 `./gradlew :app:assembleDebug` → BUILD SUCCESSFUL
- [ ] 9.2 `./gradlew :app:testDebugUnitTest` → M1+M2+M3+M4-1+M4-2 测试全绿
- [ ] 9.3 `./gradlew :app:lintDebug` → BUILD SUCCESSFUL
- [ ] 9.4 `./gradlew :app:ktlintCheck` → 0 新增违规
- [ ] 9.5 手工冒烟(Pixel Launcher / AOSP 模拟器):
  - 长按桌面 → Widgets → 找到"随手记" → 拖到桌面 → 显示"还没有笔记"
  - 点 widget "+" → MainActivity 启动到编辑页 → 输入文字 → 按系统 back → **回到 launcher 桌面**(不回 App 列表)
  - 点 widget 笔记项 → MainActivity 启动到详情 → 按系统 back → **回到 launcher 桌面**
  - Android 14+ 设备侧滑手势:系统显示 predictive back 动画过渡 → back 到 launcher

## 10. OpenSpec 收尾(apply 通过 review 后做)

- [ ] 10.1 review 通过后,跑 `openspec archive predictive-back-gesture -y`
- [ ] 10.2 更新 `docs/progress.md`:M4-2 完成
- [ ] 10.3 在 `docs/plans/writing-with-ai-mobile-roadmap.md` §13 标注 M4-2 完成;§15.2 标 `predictive-back-gesture` done