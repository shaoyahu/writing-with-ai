## Why
编辑器从 OutlinedTextField 升级为 Markdown 渲染编辑器，v1 先定义接口+简单实现，v2 引入渲染库。
## What Changes
- MarkdownEditor 接口 + SimpleMarkdownEditor v1
- EditorModule DI
## Capabilities
### New Capabilities
- `rich-text-editor`: Markdown 编辑器接口
## Impact
- core/editor/ 新包 + DI
