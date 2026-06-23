## Tasks

### 1x1 Widget
- [x] 新增 QuickNote1x1Widget.kt (Glance AppWidget)
- [x] 新增 QuickNote1x1WidgetReceiver.kt
- [x] 新增 res/xml/widget_info_1x1.xml
- [x] AndroidManifest 注册 1x1 widget receiver
- [x] strings.xml 新增 1x1 widget label

### 2x2 切换
- [x] WidgetState 新增 currentNoteIndex 字段
- [x] WidgetStateStore 新增 incrementNoteIndex() 方法
- [x] QuickNoteWidget body 点击触发 ActionCallback 切换笔记

### 验证
- [x] ./gradlew :app:check 全绿
