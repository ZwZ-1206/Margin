# Margin 留白 · Android

放下手机，为更精彩的生活留白。
A little less scrolling. A little more living.

一款**完全免费**的屏幕使用洞察 + 专注计时应用：无账号、无会员、无订阅、无广告、无付费墙、无第三方追踪 SDK。
所有数据只存在你的手机里。设置页内置**赞助窗口**，可上传你自己的收款码供用户自愿赞助——赞助不解锁任何功能，因为所有功能本就免费。

- 中文名：留白
- 英文名：Margin
- 包名：`com.liubai.app`
- 版本：v1.1.0（versionCode 2）

---

## 一、核心功能

| 模块 | 能力 |
|---|---|
| **首页** | 今日屏幕时间环形进度（「留白之环」60° 缺口）、与昨日对比、今日专注/次数/应用数统计、占用最多 Top3、一键开始专注 |
| **专注** | 番茄钟 / 倒计时 / 正计时三模式；暂停-继续-结束；标签 + Emoji；6 种程序化合成白噪音（雨声/溪流/森林/海浪/咖啡馆/棕噪音）；进程被杀后从断点恢复 |
| **统计** | 今天/本周/本月切换；屏幕+专注双柱图；应用排行 Top10；专注记录列表（完成/中断） |
| **干预** | 单应用每日限额（临近 5 分钟 + 超出各提醒一次）；挑战：连续使用 / 拿起次数 / 就寝提醒——全部默认关闭 |
| **小部件** | 小（2×2）/ 中（4×1）两种尺寸，显示今日屏幕、专注、拿起次数，点击进入 App |
| **分享** | 专注完成后生成结果卡片（Canvas 绘制）保存到系统相册 |
| **设置** | 浅色/深色/跟随系统；每日屏幕目标；番茄钟参数；倒计时默认时长；屏幕监控开关与权限检查；**应用限额**、**挑战开关**、**帮助与常见问题**；**JSON 备份导出/导入**；**赞助收款码上传**；关于 |

### 关键实现取舍

- **白噪音是算出来的**：`WhiteNoiseSynth` 用 AudioTrack 实时生成 PCM，安装包内**零音频文件**（原版有 4MB m4a）
- **专注计时不怕被杀**：引擎状态每秒落 Checkpoint 到 Room，`AlarmManager` 精确闹钟双保险；进程死后由 `FocusAlarmReceiver` 唤醒归档
- **屏幕统计诚实标注误差**：UsageStats 事件流驱动为主、30 分钟周期对账兜底；检测到的缺口在 UI 上标注为估算值，而不是假装精确
- **零网络**：无统计、无崩溃上报、无遥测。备份文件经系统文件选择器由用户自行保管

---

## 二、项目结构（9 个 Gradle 模块）

```
liubai-android/
├── app/                    组合根：Application / MainActivity / 导航 / 品牌图标
├── core/
│   ├── common/             纯 JVM：日期键、时长格式化、统一错误类型
│   └── designsystem/       Compose 主题、设计 Token、「留白之环」与通用组件
├── domain/                 纯 JVM（零 Android 依赖）：模型 / 专注状态机 / 统计算法 / 仓储接口
├── data/                   Room / DataStore / UsageStats 采集 / 前台服务 / WorkManager / 备份
└── feature/
    ├── home/  focus/  stats/  settings/     四个功能页（Compose + ViewModel）
```

**依赖规则**：`domain` 与 `core:common` 是纯 JVM 模块，不得引用任何 Android API（编写 `android.*` import 会立刻编译失败）；`feature` 只依赖 `domain`/`core`/`data`，彼此之间禁止互相依赖。

### 分层与数据流

```
UI (Compose)  →  ViewModel (StateFlow<UiState>)  →  UseCase/Engine (domain)  →  Repository (data)
```

- `domain` 中的 `FocusSessionEngine` 是纯函数状态机，用假时钟即可单测（8 个单元测试在 `domain/src/test`）
- 后台（Service/Worker/Receiver）通过 `ServiceLocator`（手动 DI）获取仓储，避免引入注解处理器

---

## 三、构建说明

### 前置条件

- JDK 17
- Android SDK：platform **android-37.0**、build-tools 37.0.0
- Gradle 8.13（使用仓库自带的 wrapper，无需预装）

在 `local.properties` 中指定 SDK 路径（该文件不入库）：

```properties
sdk.dir=C:/Apps/Android-SDK
```

### 常用命令

```bash
# 调试包（applicationId 后缀 .debug）
./gradlew :app:assembleDebug

# 发布包（需 keystore.properties，见下）
./gradlew :app:assembleRelease

# 运行 domain 单元测试（专注状态机）
./gradlew :domain:test

# 全量编译校验
./gradlew assembleDebug test
```

产物路径：`app/build/outputs/apk/{debug,release}/app-*.apk`

### 签名配置

Release 构建从项目根目录的 `keystore.properties` 读取签名信息（**该文件不入库**）。创建你自己的密钥：

```bash
keytool -genkeypair -v -keystore keystore/margin-release.jks \
  -keyalg RSA -keysize 2048 -validity 10000 -alias margin
```

然后新建 `keystore.properties`：

```properties
storeFile=keystore/margin-release.jks
storePassword=<你的密码>
keyAlias=margin
keyPassword=<你的密码>
```

未提供该文件时 Release 构建会自动回退到 debug 签名（可安装，但升级会冲突，仅供本地验证）。

### 版本矩阵（已验证可构建）

| 项 | 版本 |
|---|---|
| AGP / Gradle | 8.13.2 / 8.13 |
| Kotlin / KSP | 2.2.21 / 2.2.21-2.0.5 |
| Compose BOM | 2025.06.00 |
| Room | 2.8.5 |
| Navigation Compose | 2.9.8 |
| Lifecycle | 2.9.4 |
| Core KTX / Activity Compose | 1.17.0 / 1.12.4 |
| DataStore / WorkManager | 1.2.1 / 2.12.0 |

> 注意：navigation 2.10.x、core 1.19.x、lifecycle 2.11.x 要求 AGP 9.1+ 与 compileSdk 37，
> 与 AGP 8.13 组合会触发 `checkAarMetadata` 失败，故本仓库锁定为上表组合。

---

## 四、权限说明

| 权限 | 用途 |
|---|---|
| `PACKAGE_USAGE_STATS` | 屏幕使用统计数据源（需用户在系统设置手动授权） |
| `FOREGROUND_SERVICE` / `_SPECIAL_USE` | 专注计时常驻通知 |
| `SCHEDULE_EXACT_ALARM` / `USE_EXACT_ALARM` | 专注结束提醒；不可用时自动降级为近似闹钟 |
| `POST_NOTIFICATIONS` | 专注状态、每日报告 |
| `RECEIVE_BOOT_COMPLETED` | 重启后恢复监控与专注 |
| `VIBRATE` | 阶段切换触觉反馈 |
| `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` | 引导加入电池优化白名单（不强制） |

除「使用情况访问」外，其余权限均可拒绝后继续使用；拒绝不影响专注与记录功能。

---

## 五、品牌

- 中文名：留白 ｜ 英文名：**Margin**
- 视觉基调：奶油纸底（#F1EEE7 / #F8F6F1）、墨色文字（#1B1915）、叠翠主色（#0A7A61）、暖橙辅色（#E8930C）
- 品牌图形：**留白之环**——缺口 60° 朝上的圆环，缺口即「留给生活的白」；环上光点随进度移动
- 详见仓库根目录 `../docs/04-品牌命名与定位.md` 与 `../docs/05-Margin 品牌视觉规范.md`

---

## 六、开源许可

MIT License，见 [LICENSE](./LICENSE)。
