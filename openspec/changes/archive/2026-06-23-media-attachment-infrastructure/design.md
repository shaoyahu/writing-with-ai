## Context

笔记内容是纯文本 Markdown，图片需要存储为本地文件 + Markdown 引用。当前编辑器是 OutlinedTextField，v1 只在详情页渲染图片，编辑页保持纯文本。

## Goals / Non-Goals

**Goals:**
- 附件数据表 + 文件存储基础设施
- 图片选择/压缩/保存
- 详情页图片渲染
- 导出/导入含附件

**Non-Goals:**
- 不改编辑器(B6b rich-text-editor 做)
- 不做语音(B6c voice-insert 做)
- 不做图片编辑(裁剪/滤镜)

## Decisions

### D1: 附件存在内部存储 files/attachments/{noteId}/

用 `context.filesDir/attachments/{noteId}/{attachmentId}.{ext}` 路径，不需要运行时权限。

### D2: 图片压缩到 1920px 长边

用 `BitmapFactory` + `compress`(JPEG 85%)，长边超过 1920px 等比缩放。

### D3: 详情页附件区用 LazyRow

在笔记正文下方加水平滚动的图片缩略图行，点击可全屏查看。
