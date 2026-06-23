## Tasks

### 数据模型扩展
- [x] NoteEntity 新增 syncRevision/syncStatus/lastSyncedAt 字段
- [x] 新建 SyncMetaEntity + SyncMetaDao
- [x] AppDatabase v7 Migration

### SyncEngine 接口
- [x] 新建 core/sync/ 包
- [x] SyncEngine 接口(push/pull/getStatus)
- [x] SyncStatus sealed class + SyncResult
- [x] FakeSyncEngine 实现

### WorkManager 骨架
- [x] SyncWorker(一次性 enqueue，不做实际同步)
- [x] SyncModule Hilt DI

### 验证
- [x] ./gradlew :app:check 全绿
