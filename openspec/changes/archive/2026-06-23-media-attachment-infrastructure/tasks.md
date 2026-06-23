## Tasks

### 数据层
- [ ] NoteAttachmentEntity + NoteAttachmentDao
- [ ] AppDatabase v8 Migration

### 文件存储
- [ ] AttachmentStore(保存/读取/删除/列出附件)

### 图片选择+压缩
- [ ] ImageCompressor(长边 1920px + JPEG 85%)
- [ ] 详情屏加图片选择按钮(PickVisualMedia)

### 详情页附件展示
- [ ] 详情页正文下方加附件图片 LazyRow
- [ ] 点击图片全屏查看

### 导出/导入扩展
- [ ] NoteExporter 导出含附件
- [ ] NoteImporter 导入含附件
- [ ] 笔记删除时级联清理附件文件

### 验证
- [ ] ./gradlew :app:check 全绿
