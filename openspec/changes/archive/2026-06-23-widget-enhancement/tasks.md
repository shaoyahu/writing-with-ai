## Tasks

### 1x1 Widget
- [ ] 新增 QuickNote1x1Widget.kt (Glance AppWidget)
- [ ] 新增 QuickNote1x1WidgetReceiver.kt
- [ ] 新增 res/xml/widget_info_1x1.xml
- [ ] AndroidManifest 注册 1x1 widget receiver
- [ ] strings.xml 新增 1x1 widget label

### 2x2 切换
- [ ] WidgetState 新增 currentNoteIndex 字段
- [ ] WidgetStateStore 新增 incrementNoteIndex() 方法
- [ ] QuickNoteWidget body 点击触发 ActionCallback 切换笔记

### 验证
- [ ] ./gradlew :app:check 全绿
