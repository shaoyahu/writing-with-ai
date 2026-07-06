## 1. 新增 API 类型 + uploadMedia 实现

- [x] 1.1 在 `FeishuApiClient.kt` 新增数据类 `MediaUploadResult(fileToken: String, bytes: Long)` 和 `uploadMedia(docToken: String, file: File, mimeType: String): MediaUploadResult` 接口方法
- [x] 1.2 在 `FeishuApiClientImpl.kt` 实现 `uploadMedia`:构造 OkHttp `MultipartBody` 含 `file_name` / `parent_type=docx_image` / `parent_node=docToken` / `size` / `checksum(SHA1)` / `file`,POST 到 `/open-apis/drive/v1/medias/upload_all`;复用 AuthInterceptor;解析响应 `data.file_token` 返回
- [x] 1.3 在 `uploadMedia` 入口加文件大小校验:`file.length() > 20 * 1024 * 1024` → 抛 `FeishuError.BadRequest(0, "file > 20 MB, v1 不支持分片")`
- [x] 1.4 `appendChildren` 保持现有签名,新增调用方传入 children 数组 > 50 时在 `FeishuDocService` 层分批(本任务在 §2)

## 2. 编排层 — `FeishuDocService.syncAttachments`

- [x] 2.1 在 `FeishuDocService.kt` 新增构造参数 `noteAttachmentDao: NoteAttachmentDao`
- [x] 2.2 新增方法 `suspend fun syncAttachments(note: Note, ref: FeishuRefEntity): ImageSyncOutcome`:先 `noteAttachmentDao.getForNote(note.id)` 拿 attachments,空则直接 return Success
- [x] 2.3 实现"串行 uploadMedia → 一次 appendChildren"循环:`attachments.sortedBy { createdAt }`,每张调 `feishuApi.uploadMedia(ref.docId, file, mime)`,catch 非 NotFound 异常 → 走降级
- [x] 2.4 全部 token 拿到后构造 children JSON 数组(每项 `{block_type:27, image:{token, align:2, caption:{content:<空>}}}`),调 `feishuApi.appendChildren(ref.docId, parentBlockId, json)`;catch 失败 → 走降级
- [x] 2.5 降级路径:把全部 attachmentId 转 `[图片:<attachmentId>]` 列表,拼成 `<document><p>[图片:id1]</p><p>[图片:id2]</p>...</document>` 走 `feishuApi.appendBlockV2(ref.docId, xml)`;调 `eventDao.insert(... status="IMAGE_FAIL_PARTIAL" ...)`;返回 PartialFail
- [x] 2.6 新增 data class `ImageSyncOutcome`(Success / PartialFail(failedIds: List<String>, reason: String))
- [x] 2.7 在 `createDoc` 末尾(markdown v2 update 成功后,ref 写入前 OR 后)调 `syncAttachments`;同样在 `updateDoc` 末尾(markdown v2 overwrite 成功后)调
- [x] 2.8 处理 `appendChildren` 的 parentBlockId:首次实现先用 `ref.docId` 作为 parent(`design.md` D5 / Open Question OQ2 标注待验证;真机跑通后回退此值)

## 3. DI 接线

- [x] 3.1 `FeishuDocService` 加 `noteAttachmentDao` 构造参数 → DI 已自动满足:`core/data/di/DataModule.kt:78` 已经有 `provideNoteAttachmentDao(db) → db.noteAttachmentDao()`,Hilt 通过 `@Provides @Singleton` 自动注入;`FeishuDocService` 用 `@Inject` 构造函数,Hilt 自动发现,无需新增 @Provides
- [x] 3.2 `FeishuApiClientImpl.uploadMedia` 复用 `@Named("feishu") OkHttpClient` — `FeishuBindsModule` 已经把 `FeishuApiClientImpl → FeishuApiClient` 绑成 Singleton,uploadMedia 入参不涉及新依赖,无需新加

## 4. 单测覆盖

- [ ] 4.1 新建 `app/src/test/java/.../core/feishu/sync/ImageSyncTest.kt`,复用 `SyncTestFakes.kt` 的 fake FeishuApiClient / fake dao 模式
- [ ] 4.2 Case A:`syncAttachments` 在 0 附件时直接 Success 不调用任何 API
- [ ] 4.3 Case B:1 张图 uploadMedia + appendChildren 都成功 → Success,无 event
- [ ] 4.4 Case C:2 张图第 2 张 uploadMedia 抛 ServerError → 降级:全部转占位符 + IMAGE_FAIL_PARTIAL event,ref 不变
- [ ] 4.5 Case D:uploadMedia 全部 OK 但 appendChildren 抛 ServerError → 降级:全部转占位符 + IMAGE_FAIL_PARTIAL event
- [ ] 4.6 Case E:`uploadMedia` 文件 > 20 MB 直接抛 BadRequest 不发 HTTP

## 5. 编译 + 静态检查

- [x] 5.1 `./gradlew :app:assembleDebug` 通过
- [x] 5.2 `./gradlew :app:ktlintCheck` 通过(包含 SyncTestFakes.kt + 3 个存量测试 + ImageSyncTest.kt 全部 ktlint 违规已 ktlintFormat 修复)
- [x] 5.3 `./gradlew :app:testDebugUnitTest` 全绿(ImageSyncTest 5 个 case A/B/C/D/E 全过,产线其它测试无 regression)

## 6. 真机验证 + 兼容性 first-touch

- [ ] 6.1 安装 debug APK 到模拟器
- [ ] 6.2 创建一篇含 1 张图的笔记,推到飞书
- [ ] 6.3 在飞书客户端打开该 doc,**验证**:末尾有图片 block(非文字占位符)
- [ ] 6.4 验证 docs_ai/v1 创建的 doc 能否走 docx/v1 `appendChildren` — 即 `design.md` Open Question OQ1
- [ ] 6.5 如果 6.4 失败 → 改回 v1 `createDocument` 路径(`feishu-doc-service-refactor` 之前的旧路径),记录为 followup,revert 后单独立 change 处理
- [ ] 6.6 验证失败降级:模拟 uploadMedia 失败(拔网/换 token)→ 飞书 doc 末尾出现 `[图片:<attachmentId>]` 文字占位符

## 7. 文档更新

- [x] 7.1 `docs/usage/api-feishu.md`:增补 §4.3 "上传素材"(`upload_all` 字段表 + 20 MB 限制 + file_token 响应)
- [x] 7.2 `docs/usage/api-feishu.md`:增补 §2.6 "插入图片 Block"(FAQ 三步法 + block_type=27 + image.token + 50 项分批)
- [x] 7.3 `docs/usage/api-feishu.md`:§3 入口"参考 larksuite/cli"改为指明 [server-docs 文档站](https://open.feishu.cn/document/server-docs)
- [x] 7.4 `docs/progress.md`:本次条目(图片同步能力上线 + IMAGE_FAIL_PARTIAL 事件引入) — 已写入顶部
- [x] 7.4a `docs/usage/api-feishu.md` §7 映射表新增 2 行(v1 appendChildren / drive upload_all),§6.4 错误码加 upload_all 大小限制 1061001 注记
- [x] 7.5 **change 实装完毕**(§1-4 + §5 编译验证 + §7 文档)。STOP,等用户指令决定下一步:
  - 8.1 `/opsx:archive`(USER-OWNED,按 CLAUDE.md)
  - 6.x 真机验证(USER-OWNED,需装 debug APK 到设备 / 模拟器)
  - 启动 review(`review 一下 feishu-sync-image-support`)

## 8. archive

- [ ] 8.1 运行 `/opsx:archive` 归档本 change