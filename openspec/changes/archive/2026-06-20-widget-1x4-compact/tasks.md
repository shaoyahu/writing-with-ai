## 1. i18n keys

- [x] 1.1 `app/src/main/res/values/strings.xml` 加 `widget_1x4_layout_label="1x4 紧凑"` + `widget_1x4_title="随手记"`
- [x] 1.2 `app/src/main/res/values-en/strings.xml` 加 2 个 key 的 TODO(en) 占位

## 2. 独立 receiver + 独立 widget_info_1x4.xml

- [x] 2.1 `app/src/main/res/xml/widget_info_1x4.xml` 新建(`targetCellWidth=1 targetCellHeight=4 minWidth=40dp minHeight=180dp` + `description=@string/widget_1x4_layout_label` + 复用 M4-1 `widget_initial` / `widget_preview`)
- [x] 2.2 `app/src/main/AndroidManifest.xml` 加新 `<receiver android:name=".core.widget.QuickNote1x4WidgetReceiver" android:label="@string/widget_1x4_title">` + meta-data `@xml/widget_info_1x4`

## 3. QuickNote1x4Widget + Receiver

- [x] 3.1 新建 `app/src/main/java/com/yy/writingwithai/core/widget/QuickNote1x4Widget.kt` — `SizeMode.Exact`,`observeRecent(1)`,`Row { 36dp Add 蓝底按钮 | defaultWeight Column 单条笔记(标题单行省略 + relativeTime) }`，无笔记显示 `widget_empty`
- [x] 3.2 新建 `app/src/main/java/com/yy/writingwithai/core/widget/QuickNote1x4WidgetReceiver.kt` — `override val glanceAppWidget = QuickNote1x4Widget()`
- [x] 3.3 `QuickNoteWidget.kt` `formatRelativeTime` 由 `private` 改 `internal`(同包 1x4 widget 复用)
- [x] 3.4 1x4 widget 复用 `OpenNoteAction`(笔记项点击)+ `createNoteIntent`(加号点击，`launchWithTaskStack("quicknote/edit?prefillFocus=true")`)

## 4. 编译验证

- [x] 4.1 `./gradlew :app:assembleDebug` 通过(代码编译 / 资源链接 / AndroidManifest 合法)
- [x] 4.2 `./gradlew :app:installDebug` 到 PGU110 装机成功
- [ ] 4.3 `grep -rE "androidx.compose.foundation|androidx.compose.material3" app/src/main/java/com/yy/writingwithai/core/widget/` → 0 匹配(Glance 约束保持)
- [ ] 4.4 `grep -rE "feature.quicknote.detail|feature.aiwriting|core.ai" app/src/main/java/com/yy/writingwithai/core/widget/` → 0 匹配(self-containment)
- [ ] 4.5 `./gradlew :app:lintDebug` 0 errors
- [ ] 4.6 `./gradlew :app:ktlintCheck` 无新增 violation

## 5. 单测

- [ ] 5.1 `WidgetIntentHelpersTest` 已有 case 覆盖 `launchWithTaskStack(route="quicknote/edit?prefillFocus=true")`,1x4 widget 复用，无需新 case

## 6. 真机验证

- [ ] 6.1 AOSP launcher 长按桌面 → widget → 找"随手记" → picker 是否列 1x4 "随手记"(独立 receiver 应可见)
- [ ] 6.2 添加 1x4 widget → 看加号(蓝底)+ 最近笔记显示正常
- [ ] 6.3 点加号 → 直接到编辑器 + 输入框 focus;back 回 launcher(走 TaskStackBuilder)
- [ ] 6.4 点笔记项 → 进详情;back 回 launcher
- [ ] 6.5 App 内新建笔记 → 1x4 widget 主路径刷新(`QuickNoteWidgetUpdater.updateAll` 触发，1x4 + 2x2 / 4x2 同步更新)

## 7. spec 同步 + 归档

- [ ] 7.1 跑 `/opsx:sync widget-1x4-compact` 把 delta spec 合入主 spec(`openspec/specs/home-screen-widget-1x4/spec.md` 独立保留为新 capability 主 spec;`home-screen-widget` 主 spec 加 MODIFIED 段指向独立 receiver)
- [ ] 7.2 跑 `/opsx:archive widget-1x4-compact` 收口，archive 到 `openspec/changes/archive/2026-06-20-widget-1x4-compact/`
- [ ] 7.3 `docs/progress.md` 加 1 条"M5 + 1x4 widget 增量" 进度条目