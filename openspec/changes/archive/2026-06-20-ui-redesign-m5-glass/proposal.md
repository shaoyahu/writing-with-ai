## Why

当前 App 视觉系统仅走 Material 3 默认 ColorScheme,无统一 design token / spacing / motion / Glass blur,真机显示"UI 丑"(用户反馈)。M4 全闭环(M1~M5 已归档)后,**视觉与交互需要一轮集中打磨**,对齐"快速记录 + AI 辅助写作"的产品定位。

**真实 provider 接入**拆到独立 change `provider-real-integration`(model-management UI + apikey 加密 + 切换 providerId);本 change 只做视觉与交互,不动 provider 切换逻辑。

## What Changes

- **设计系统**:`app/ui/theme/` 重设计 —— Material 3 You ColorScheme + 统一 `LocalSpacing` CompositionLocal + 自定义 typography(中文优化字号)+ Motion tokens(spring animation / fade-in scale)
- **随手记列表**:卡片化(原 ListItem → Card),长按拖拽 + 滑动删除 + 顶部 sticky date header + 暗色模式视觉一致
- **随手记详情**:Full-screen read mode,顶部 toolbar 简洁化,底部栏 ✨ 按钮用 FAB 风格 surface,ActionSheet 走 BottomSheet(Slide-up)替代当前 Popup 弹窗
- **随手记编辑**:BottomSheet-style autosave 提示 + 顶部 toolbar 简化
- **AI 流式面板**:Card with rounded corners + glass blur 背景 + typing animation + Markdown preview 实时渲染
- **设置**:Material 3 List 风格 + Group 分类 + Switch / Slider 标准控件
- **Onboarding**:渐变背景 + Glass card + 三步指示器
- **3 个 widget**(2x2 / 4x2 / 4x1):Material You 圆角 + surface tint + Glance surface elevation + 高对比文字

**非 BREAKING**:UI 视觉调整,所有 Nav 路由 / 数据 schema / AI 抽象层 / 测试套件 不动;现有 widget 实例重新加载视觉,功能等价。providerId 仍走 `fake`(`provider-real-integration` change 替换)。

## Capabilities

### New Capabilities

无(纯视觉 + Composable 重组)。

### Modified Capabilities

- `quick-note`:列表 / 详情 / 编辑 UI 重设计(M3 起的样式全部刷新)
- `ai-actions`:ActionSheet 走 BottomSheet;StreamingPanel 重设计
- `app-shell`:Onboarding 屏重设计 + theme token 重建
- `home-screen-widget`:2x2 / 4x2 Glance 视觉重设计
- `home-screen-widget-1x4`:4x1 Glance 视觉重设计
- `settings`:Settings 主屏 + 数据迁移 + 提示词模板 UI 风格统一

## Impact

- **新代码**:
  - `app/src/main/java/com/yy/writingwithai/app/ui/theme/` —— Color/Type/Shape/Motion tokens 全重写
  - 5 个 feature 各 1~3 个 Composable 改动
- **新依赖**:Material 3 已用;Glass blur 走 Compose 1.6+ 原生 `haze` 风格自己实现(RenderEffect.blur API 28+)或 fallback 70% 透明 surface
- **复用**:Navigation / Hilt / Room / DataStore / AiGateway / provider adapter 0 改动;`SecureApiKeyStore` 已落地,**本 change 不接**
- **provider 切换**:`AiActionViewModel` providerId 仍硬编码 `fake`(留给 `provider-real-integration`)
- **测试**:UI test 不动;widget Glance 渲染测试不破
- **真机**:PGU110(Android 16)验证视觉;小米 MIUI / 华为 EMUI 兜底走 fallback 70% surface(无 blur)