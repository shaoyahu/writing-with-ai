## Why

多设备同步是用户高频需求。先搭建同步基础设施：数据模型扩展(syncRevision/syncStatus字段) + SyncEngine接口 + FakeSyncEngine + WorkManager骨架 + DI。后续 B5b/B5c 对接实际后端和 UI。

## What Changes

- NoteEntity 新增 syncRevision/syncStatus/lastSyncedAt 字段
- 新建 sync_meta 表(deviceId/lastSyncTimestamp/remoteEndpoint)
- AppDatabase v7 Migration
- SyncEngine 接口 + SyncStatus sealed class + SyncResult
- FakeSyncEngine 实现(纯本地循环验证接口)
- SyncWorker WorkManager 骨架
- SyncModule DI

## Capabilities

### New Capabilities
- `cloud-sync-foundation`: 同步基础设施接口和数据模型

### Modified Capabilities
- `data-export-import`: NoteEntity 扩展 sync 字段

## Impact

- AppDatabase v7 + NoteEntity + sync_meta 表 + core/sync/ 新包 + DI
