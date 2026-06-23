## Tasks

### 数据模型扩展
- [ ] NoteEntity 新增 syncRevision/syncStatus/lastSyncedAt 字段
- [ ] 新建 SyncMetaEntity + SyncMetaDao
- [ ] AppDatabase v7 Migration

### SyncEngine 接口
- [ ] 新建 core/sync/ 包
- [ ] SyncEngine 接口(push/pull/getStatus)
- [ ] SyncStatus sealed class + SyncResult
- [ ] FakeSyncEngine 实现

### WorkManager 骨架
- [ ] SyncWorker(一次性 enqueue，不做实际同步)
- [ ] SyncModule Hilt DI

### 验证
- [ ] ./gradlew :app:check 全绿
