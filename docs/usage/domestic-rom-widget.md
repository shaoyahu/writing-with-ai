# 国产 ROM 桌面 Widget 适配说明

> widget-rome-compat change 产物。适用版本:v1 之后。

## 1. 适配状态表

| ROM | GlanceStateDefinition 持久化 | 颜色 token(M3) | DateUtils locale | 空状态 hint |
| --- | --- | --- | --- | --- |
| **MIUI**(小米 / Redmi) | ✓ | ✓ | ✓ | ✓ |
| **EMUI / HarmonyOS**(华为 / Honor) | ✓ | ✓ | ✓ | ✓ |
| **ColorOS**(OPPO / realme) | ✓ | ✓ | ✓ | ✓ |
| **OriginOS**(vivo / iQOO) | ✓ | ✓ | ✓ | ✓ |
| AOSP / Pixel / 索尼 / 摩托 | ✓ | ✓ | ✓ | —(无 hint) |

## 2. 用户自助教程

### 2.1 小米 MIUI / Redmi

1. 打开系统 **设置**
2. 进入 **应用 → 应用管理**
3. 找到 **writing-with-ai**,点击进入
4. 选择 **自启动**
5. 关闭 MIUI 自带的"智能限制",允许本应用自启动

![MIUI 自启动管理](images/miui-self-start.png)

### 2.2 华为 EMUI / HarmonyOS

1. 打开系统 **设置**
2. 进入 **应用 → 应用启动管理**
3. 找到 **writing-with-ai**
4. 关闭 **自动管理**,手动开启 **允许自启动 / 关联启动 / 后台活动** 三个开关

![EMUI 启动管理](images/emui-launch.png)

### 2.3 OPPO ColorOS / realme

1. 打开系统 **设置**
2. 进入 **电池**
3. 关闭 **睡眠待机优化** 与 **应用速冻**
4. 找到 **writing-with-ai**,关闭 **后台冻结**

![ColorOS 电池优化](images/coloros-battery.png)

### 2.4 vivo OriginOS / iQOO

1. 打开系统 **设置**
2. 进入 **电池 → 后台高耗电**
3. 找到 **writing-with-ai**,允许 **高耗电运行**

![OriginOS 后台高耗电](images/originos-battery.png)

## 3. 已知限制

- 国产 ROM 后台杀 widget 进程是**厂商系统级行为**,App 层无法绕过。缓解措施:
  - **WorkManager 15 分钟兜底**:即便 widget 进程被杀,系统级 WorkManager 仍能拉起 widget 重新渲染
  - **用户开自启动**:上述 2.x 各 ROM 教程,一次性配置后 widget 进程不易被回收
  - **下拉桌面刷新**:Android 12+ launcher 支持"强制刷新 widget",用户主动触发
- **GlanceStateDefinition 持久化**仅缓存 `cachedNoteIds` / `lastRefreshAt` / `romVendor` 三个字段;Room 数据由 `provideGlance` 实时拉取,不在缓存中
- **颜色 token** 跟随系统暗色 / 亮色 / Material You;若用户开启"动态取色",widget 主色会随壁纸变化

## 4. ROM 检测原理

`core/widget/RomDetector.kt` 用 `Build.MANUFACTURER` + `Build.BRAND` 白名单命中:

| 关键词(MANUFACTURER / BRAND) | 命中 |
| --- | --- |
| `Xiaomi` / `Redmi` | MIUI |
| `HUAWEI` / `Honor` | EMUI |
| `OPPO` / `realme` | COLOROS |
| `vivo` / `iQOO` | ORIGINOS |
| 其他(包含空字符串 / Google) | AOSP |

**Why MANUFACTURER + BRAND 双判**:主品牌(MANUFACTURER)主判 + 子品牌(BRAND)兜底(红米 / 荣耀 / 真我 / iQOO 在父品牌下作为独立 SKU)。