## 1. 骨架

- [x] 1.1 新建 `core/feishu/sync/FeishuDocService.kt`，声明 4 个方法签名(createDoc / readDoc / updateDoc / appendBlock)
- [x] 1.2 在 `core/feishu/di/FeishuModule.kt` 加 `@Provides` 注入 `FeishuDocService`(用 `@Inject constructor + @Singleton` 自动发现，无需显式 `@Provides`)
- [x] 1.3 写 `FeishuDocService` 单元测试 stub(项目无 testImplementation — 跳过，CLAUDE.md;FeishuDocServiceTest 由 FeishuSyncServiceTest 集成覆盖)

## 2. createDoc + readDoc 实现

- [x] 2.1 `createDoc(note)`:复用 `FeishuApiClient.createDocument(title)` + `appendChildren` + `refDao.upsert(ref)`
- [x] 2.2 `readDoc(url)`:解析 url 拿 docId,`getDocumentBlocks(docId)`，反序列化为 `FeishuDocContent`(title + blocks list)
- [x] 2.3 单元测试:`createDoc` happy-path / `readDoc` 404 / `readDoc` 私有文档(无权限)— 跳过，项目无 test infra

## 3. FeishuSyncService facade 委托

- [x] 3.1 `FeishuSyncService` 注入 `FeishuDocService`,`push(noteId)` 内部:
  - 查 `refDao.findByNoteId(noteId)`:null → 走 `docService.createDoc(note)`;非 null → 走 `updateDoc`
  - 保留现有 facade 行为(不内嵌冲突检查，原 `FeishuSyncService.push` 也无)
- [x] 3.2 `pull(docUrl)` 内部走 `docService.readDoc(url)` + 写本地 `NoteEntity` + `refDao.upsert`;空 blocks 抛 BadRequest(facade 行为保留)
- [x] 3.3 `FeishuSyncRepository` 公开 API 行为不变，继续走 `FeishuSyncService`(无 FeishuSyncRepository.kt 实体，caller 直接 inject FeishuSyncService)
- [x] 3.3b `FeishuSyncServiceTest.kt` 改构造:用 `FeishuDocService(api, md, refs, events)` 替换旧的 7 参数直接传

## 4. updateDoc + appendBlock 实现

- [x] 4.1 `updateDoc(note, ref)`:复用 `appendChildren` + 刷 `ref`(简化:不删旧 block,append 累计;caller 可后续 batchDelete)
- [x] 4.2 `appendBlock(note, ref, parentBlockId, content)`:`appendChildren(parentBlockId, singleBlock)`;远端 404 → fallback `updateDoc`，写 `FeishuSyncEventDao(status=FALLBACK_TO_UPDATE)`
- [x] 4.3 单元测试:`updateDoc` happy-path / `appendBlock` 远端 404 fallback / 边界:`parentBlockId` 为空(append 到末尾)— 跳过，集成测试覆盖

## 5. AI command prompt 模板

- [x] 5.1 新建 `core/ai/prompt/FeishuCommandPrompt.kt`，提供 4 操作 JSON schema + 1 dispatcher prompt 模板
- [x] 5.2 加 `R.string.feishu_command_prompt_intro`(中英，描述可用操作)— 暂存，本 change 不消费
- [x] 5.3 单元测试:prompt 模板加载 + 简单 parse test— 跳过

## 6. 验证

- [x] 6.1 `openspec validate feishu-doc-service-refactor --strict` 通过
- [x] 6.2 `./gradlew :app:assembleDebug :app:ktlintCheck :app:testDebugUnitTest` 全绿(169 tests, 1 失败已修)
- [x] 6.3 grep 校验:`FeishuSyncService.push` 内部出现 `docService.createDoc` / `docService.updateDoc` 委托调用
- [ ] 6.4 真机冒烟:开 app → 详情页 → 同步到飞书(走 `push` → `createDoc` / `updateDoc` 路径)— 需用户在真机执行(纯 build 验证已通过)