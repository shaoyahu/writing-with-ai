## Context
当前编辑器用 OutlinedTextField，无法渲染 Markdown/图片。先定义接口，v1 简单封装 BasicTextField。
## Goals / Non-Goals
**Goals:** MarkdownEditor 接口 + v1 简单实现 + DI
**Non-Goals:** 不引入第三方渲染库(v2)
