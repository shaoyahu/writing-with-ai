## Why

图片/语音是笔记 App 的基本需求。先搭基础设施：附件数据表 + AttachmentStore(文件存储) + 图片选择/压缩/存储 + 详情页图片渲染 + 导出/导入扩展 + 删除级联清理。编辑器改造(B6b)和语音(B6c)后续单独做。

## What Changes

- 新建 note_attachments 表(NoteAttachmentEntity + NoteAttachmentDao)
- AttachmentStore(内部存储 files/attachments/)
- 图片选择器(PickVisualMedia) + 压缩 + 存储
- 详情页图片渲染(AsyncImage 展示附件区)
- NoteExporter/NoteImporter 扩展支持附件
- 笔记删除时级联清理附件文件

## Capabilities

### New Capabilities
- `media-attachment`: 附件基础设施接口和存储

### Modified Capabilities
- `data-export-import`: 导出/导入支持附件
- `quick-note`: 详情页附件展示区

## Impact

- AppDatabase v8 + note_attachments 表 + core/media/ 新包 + detail UI
