# Feedback Channel

> **状态**: v1 internal testing 阶段，2026-06-27 起启用。
> **范围**: 5 名内测人员 + 自用反馈。
> **替代**: 不接 GitHub Issues / Jira / Sentry(单人项目维护负担 vs 价值不匹配，5 人范围邮件/群就够)。

## 反馈入口

> ⚠️ **TODO(替换为实际联系方式)**: 由用户在本段填写真实联系方式 ——
>
> - **首选**: TODO(替换为邮件 / 飞书机器人 webhook / 微信群二维码)
> - **应急**: TODO(替换为电话 / 短信，仅 CRITICAL)
>
> 替换后把本段上方 ⚠️ 标记去掉。

## Bug Report 模板

bug 反馈请按以下 7 字段模板填写，缺字段的反馈会先被打回重提。

### 1. 设备型号

例:小米 14 / 华为 Mate 60 Pro / OPPO Find X7 / vivo X100 / Pixel 8 / 一加 12 ...

### 2. 系统版本

例:MIUI 14.0.6 / HarmonyOS 4.2 / ColorOS 14 / OriginOS 4 / Android 14 ...

### 3. App versionCode

`设置 → 关于 → 版本号` 处查看，例:`101` (debug) / `100` (release)。

### 4. 复现步骤

按 1./2./3. 顺序列出，每步一行;尽量精准到点击位置。

例:

1. 打开 app
2. 进入「随手记」列表
3. 点右下角 + 新建
4. 写 5 行字
5. 长按选中第 3 行
6. 点 AI 按钮 → 扩写

### 5. 期望 vs 实际

- **期望**: AI 在 3 秒内返回扩写后的内容，流式逐字展示
- **实际**: 一直转圈，10 秒后弹"网络失败"

### 6. 截图 / 录屏

bug 必带截图;崩溃 / 卡死 / 闪退必带录屏(5~15 秒即可)。

### 7. logcat(可选，但强烈建议附)

```bash
adb logcat -d -t 5000 | grep -i 'xiaozha\|FATAL\|AndroidRuntime'
```

输出贴进反馈;崩溃 stacktrace 在 `AndroidRuntime` 段。

## 提单流程

```
   ┌─────────────────┐
   │ 内测人员发现 bug │
   └────────┬────────┘
            │
            ▼
   ┌──────────────────────────┐
   │ 1. 查 known-issues.md    │ ◀── 已记录?直接走 workaround
   └────────┬─────────────────┘
            │ 未记录
            ▼
   ┌──────────────────────────┐
   │ 2. 填 7 字段模板         │
   │ 3. 发到反馈入口(待替换)  │
   └────────┬─────────────────┘
            │
            ▼
   ┌──────────────────────────┐
   │ 4. AI 每周扫一次反馈     │ ◀── 单人项目 + AI 整理
   └────────┬─────────────────┘
            │
            ▼
   ┌──────────────────────────┐
   │ 5. 落到 known-issues.md  │
   │    + 标 status (open)    │
   └────────┬─────────────────┘
            │
            ▼
   ┌──────────────────────────┐
   │ 6. 用户过目 + 标 severity│ ◀── HIGH / CRITICAL 当天处理
   └────────┬─────────────────┘
            │
            ▼
   ┌──────────────────────────┐
   │ 7. 状态机迁移            │
   │    open → resolved       │
   │         / won't fix      │
   │         / deferred-      │
   │           accepted       │
   └──────────────────────────┘
```

## 反馈优先级

| severity | 定义 | 处理 SLA |
|---|---|---|
| **CRITICAL** | app 闪退 / 主流程不可用 / 数据丢失 | 24h 内响应，48h 内出 fix 或 workaround |
| **HIGH** | 主流程能跑但明显异常(AI 调用全失败 / 数据同步全挂) | 72h 内响应 |
| **MEDIUM** | 边角功能异常 / 国产 ROM 单平台问题 | 1 周内整理到 known-issues |
| **LOW** | 体验瑕疵 / 文案 / 视觉小 bug | 内测期满前统一评估 |

## 反馈数据去重与归档

- 同一 bug 多人提 → 在 `known-issues.md` 中合并 1 条，所有 reporter 列入"reported by"列表。
- 修复后的 issue:`status: resolved` 保留 2 个 release 版本 → 移到 `docs/reviews/<date>-<change-name>-bug-fix-archive.md`。
- v1 release 前 `known-issues.md` 中所有 `[open]` 必须转为 `[resolved]` / `[won't fix]` / `[deferred-accepted]`。

## 不在反馈渠道处理的内容

- **功能请求**: 走 roadmap §15 候选池，内测阶段暂不接新功能请求。
- **AI 模型选择建议**: 不接，5 人内测范围不构成模型选型依据。
- **第三方服务故障**: DeepSeek / 飞书等服务侧问题由用户联系对应厂商;app 侧只在 bug 影响用户时记录。
- **商务合作 / 媒体询问**: 不在内测阶段开放。

## 升级到 issue tracker(可选)

如果未来内测扩到 50+ 人或开放公测，**可**把反馈迁移到 GitHub Issues / Linear / Sentry，本文件作为入口迁移的过渡。

但 MUST NOT 把 issue tracker 作为当前必需路径 —— 内测 5 人范围，单点维护反而低效。

## 关联文档

- [internal-testing.md](./internal-testing.md) — 内测范围 / MVP 验收标准
- [known-issues.md](./known-issues.md) — 已知问题汇总
- [release-checklist.md](./release-checklist.md) — release 前置检查
- [development-setup.md](./development-setup.md) — 内测人员拉 logcat 的环境准备