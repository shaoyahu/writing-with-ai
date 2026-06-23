## Context

当前笔记数据只在本地 Room，无任何同步机制。需要先定义同步协议的接口和数据模型，让后续后端对接和 UI 都依赖这个抽象。

## Goals / Non-Goals

**Goals:**
- NoteEntity 扩展 sync 字段
- SyncEngine 接口定义(push/pull/status)
- FakeSyncEngine 验证接口
- WorkManager 骨架(SyncWorker)

**Non-Goals:**
- 不对接实际后端(B5b 做)
- 不做同步 UI(B5c 做)
- 不做冲突解决(复用 FeishuConflictResolver 模式，B5b 实现)

## Decisions

### D1: syncRevision 用 String 而非 Int

String 可兼容 ETag/版本号/时间戳等多种后端方案，Int 只能兼容递增版本号。

### D2: SyncEngine 接口用 suspend fun 而非 Flow

同步是"请求-响应"模式而非流式，用 suspend fun 更自然。

### D3: FakeSyncEngine 直接返回传入数据

push 返回新 revision，pull 返回空列表(模拟无远程数据)。
