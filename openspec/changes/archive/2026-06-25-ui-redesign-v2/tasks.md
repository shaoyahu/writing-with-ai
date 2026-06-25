## 1. 设计系统 Token 重建

- [x] 1.1 重写 `Color.kt`:种子色改为墨绿 #1B6B4A + 琥珀 #D4940A + 薄荷 #2BAD8E；Light/Dark ColorScheme 全部重算；新增 warning/warningDark 语义色 token
- [x] 1.2 重写 `Theme.kt`:CustomColors 扩展为 success/successDark/warning/warningDark 四字段；更新 DefaultLightCustomColors/DefaultDarkCustomColors
- [x] 1.3 扩展 `Spacing.kt`:新增 xs2(2dp)/sm2(12dp)/md2(20dp)/xl2(40dp) 四档，共 9 档
- [x] 1.4 扩展 `CornerRadius.kt`:新增 xs(4dp)/xl(24dp)，共 5 档(xs/sm/md/lg/xl)
- [x] 1.5 更新 `Shape.kt`:Shapes 统一使用 LocalCornerRadius.md(12dp) 作为 medium default
- [x] 1.6 编译验证:跑 `./gradlew :app:assembleDebug` + `ktlintCheck` 确认 token 文件编译通过

## 2. 笔记列表页重设计

- [x] 2.1 重写搜索栏:从 OutlinedTextField 改为填充背景圆角搜索框(surfaceVariant 背景 + xl(24dp) 圆角 + leadingIcon)
- [x] 2.2 重写 NoteRow:Card 改 border-card(elevation=0 + 1dp outlineVariant border + md(12dp) 圆角)；新增 3dp 左侧彩色竖条(tag 色或 primary)
- [x] 2.3 重写 EmptyState:新增 64dp 大图标 + 品牌文案 + primary CTA 按钮
- [x] 2.4 更新 Shimmer/Skeleton:ShimmerBox 背景色适配新 palette；NoteListSkeleton 使用新 Spacing token
- [x] 2.5 编译+ktlint 验证

## 3. 笔记详情页重设计

- [x] 3.1 标题区域:改用 headlineLarge textStyle；标签行 FlowRow + SuggestionChip 保持不变
- [x] 3.2 底部操作栏:从 FAB+DropdownMenu 改为固定 BottomBar(Share+AutoAwesome 两图标)，Pin/Delete/Export 保留 TopAppBar MoreVert
- [x] 3.3 RelatedNotesSection:外层包 Surface(surfaceVariant, md(12dp) 圆角) + section header
- [x] 3.4 编译+ktlint 验证

## 4. 笔记编辑器重设计

- [x] 4.1 标题:OutlinedTextField 改为 BasicTextField(headlineMedium textStyle + 无边框 + decorationBox 手写 placeholder)
- [x] 4.2 正文:OutlinedTextField 改为 BasicTextField(bodyLarge textStyle + Modifier.weight(1f).fillMaxHeight() + decorationBox)
- [x] 4.3 Tag 区:TagInputRow 外层包 Surface(surfaceVariant, md(12dp) 圆角) 视觉分离
- [x] 4.4 标题/正文之间:加 HorizontalDivider(outlineVariant, alpha=0.3) 分隔线
- [x] 4.5 编译+ktlint 验证

## 5. 「我的」页面重设计

- [x] 5.1 SectionCard 圆角:从 0dp 改为 md(12dp)
- [x] 5.2 每个 ListItem 加 leading icon(AI 配置→SmartToy / 实体别名→LocalOffer / 数据→Storage / 飞书→Cloud / 设置→Settings / 关于→Info)
- [x] 5.3 每个 SectionCard 上方加 section header label(Text, titleSmall, primary 色)
- [x] 5.4 编译+ktlint 验证

## 6. Onboarding 重设计

- [x] 6.1 顶部品牌区域:Box(primaryContainer 背景) + headlineLarge 标题 + bodyMedium 副标题
- [x] 6.2 条款区域:LazyColumn 外层包 Surface(surfaceContainerLow, md(12dp) 圆角)
- [x] 6.3 底部按钮区:Button 改用 primary 色(跟随新 palette)；OutlinedButton 保持不变
- [x] 6.4 编译+ktlint 验证

## 7. 模型管理 + 设置页优化

- [x] 7.1 ProviderInfoCard 简化:删 PingCard 独立卡片，ping 结果改为 inline badge(在 provider 列表下方)
- [x] 7.2 Provider 卡片边框:选中态改 2dp primary border(取代 3dp success border)
- [x] 7.3 SettingsScreen 保持当前(Single Switch)，样式跟随新 token 自动适配
- [x] 7.4 编译+ktlint 验证

## 8. App Shell + AI Streaming 视觉适配

- [x] 8.1 FAB 颜色:containerColor 从 primary 改为 secondary(琥珀色)
- [x] 8.2 StreamingPanel:跟随新 palette(primary 变为墨绿);cornerRadius 跟 LocalCornerRadius
- [x] 8.3 ActionSheet:cornerRadius 改 md(12dp);icon 色改 primary
- [x] 8.4 编译+ktlint 验证

## 9. strings.xml + 暗色模式验证

- [x] 9.1 strings.xml 新增品牌文案:section header labels(AI 配置/数据管理/关于等);空状态 tagline
- [x] 9.2 暗色模式全页面验证:逐页确认暗色下新 palette 对比度达标(WCAG AA)
- [x] 9.3 最终全量编译+ktlint+单测:`./gradlew :app:testDebugUnitTest :app:ktlintCheck :app:assembleDebug`
