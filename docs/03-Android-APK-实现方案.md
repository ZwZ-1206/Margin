# OffScreen 留白 · Android APK 完整实现方案

> 版本：v1.0 ｜ 日期：2026-10-19 ｜ 状态：开发纲领，待启动
> 上游文档：《01-登录与会员体系改造方案.md》（结论：本项目**不采用**会员体系，见 §9）、《02-设计与工程提升方向.md》
> 产品定位：完全免费的「屏幕使用洞察 + 专注番茄钟」Android 应用；iOS 侧以官网/PWA 兜底

---

## 1. 产品定义与技术栈

### 1.1 产品定义（对照原版的功能取舍）

| 原版功能 | 本版决策 | 理由 |
|---|---|---|
| 屏幕使用监控（UsageStats） | **保留，核心** | 产品基石，纯本地计算 |
| 专注系统（番茄钟/倒计时/正计时/白噪音/标签） | **保留，核心** | 使用频率最高的模块 |
| 统计（时间线/日历/周月对比/App 排行） | **保留** | 本地聚合即可 |
| 挑战与限额（屏幕时间/拿起次数/连续使用/熬夜提醒） | **保留，默认关闭、可选开启** | 依赖监控存活率，需引导 |
| 桌面小部件 | **保留**（2 种尺寸） | 低成本高感知 |
| 登录/账号/会员/内购 | **全部移除** | 用户决策：完全免费、无会员、无付费墙 |
| 赞助 | **新增**：设置页上传收款码 + 首页赞助卡 + 赞助弹窗 | 自愿赞助，无任何功能门控 |
| Firebase/AppsFlyer/微信/支付宝 SDK | **全部移除** | 无账号体系后无必要；包体/隐私/合规全面受益 |

### 1.2 技术栈（2026-10 稳定版）

Kotlin 2.3.21 ｜ AGP 9.2.0 / Gradle 9.4.1 / JDK 17 ｜ Jetpack Compose（BOM 2026.08.00，Material 3）｜ Room 2.8.5 ｜ DataStore 1.2.x ｜ Hilt 2.60.1 ｜ Navigation Compose 2.10.2 + kotlinx.serialization 类型安全路由 ｜ WorkManager 2.11.1 ｜ Coil 3.5.0 ｜ KSP 2.3.21-2.0.x

**关键取舍**：不用 Realm（-8MB，SDK 停止活跃维护）改用 Room；不打包 emoji 字体（-7.6MB）用系统字体；白噪音用 **Web Audio 思路的合成音频**（AudioTrack 程序化生成，-4MB 且无限时长）；无原生库 → **universal APK 单包**，目标下载体积 ≤10MB。

---

## 2. 总体架构与模块划分

```
:app                        // Application、MainActivity、NavHost 装配、Hilt 聚合
:baselineprofile            // Baseline Profile 生成（冷启动 <1.5s 的保障）
:build-logic                // Convention Plugins + 依赖规则 CI 检查

:core:designsystem          // 主题（Color/Type/Shape/Dimension 全套 token）、图标、通用组件
:core:ui                    // UDF 脚手架（UiState/UiEffect）、Modifier 扩展
:core:common                // Dispatchers、ElapsedTimeProvider、AppError、格式化
:core:model                 // 跨模块 data class
:core:database              // Room（唯一 DB 入口）：Entity/DAO/Migration
:core:datastore             // DataStore(Proto)：设置、专注 Checkpoint、赞助配置
:core:testing               // Fake 仓库、MainDispatcherRule、黄金 fixtures

:domain:focus               // FocusSessionEngine 状态机（纯 Kotlin，零 Android import）
:domain:usage               // 区间归约/聚合算法（纯 Kotlin）
:domain:stats               // 连续天数、对比、洞察文案生成

:data:usage                 // UsageStats 采集器 + Repository 实现
:data:focus                 // 会话/标签 Repository + Checkpoint 持久化
:data:settings              // 设置 Repository

:feature:onboarding         // 引导与权限流程
:feature:home               // 今日概览（双环、指标、最近专注、赞助卡）
:feature:focus              // 专注主界面（计时环、控制、白噪音、标签）
:feature:stats              // 统计（柱状/占比/时段块图/排行）
:feature:calendar           // 日历视图
:feature:settings           // 设置、数据导入导出、收款码上传
:feature:sponsor            // 赞助弹窗（Dialog）

:workers                    // UsageReconcileWorker / FocusFinalizeWorker / DailyReportWorker
```

依赖规则（CI 强制）：`:domain:*` 为 `java-library` 纯 JVM 模块，出现 `android.*` import 即 fail；Feature 之间不互相依赖；Room/Ktor 仅 `:core:*` 可引入；任何模块不得依赖 `:app`。

分层数据流：Composable（发事件）→ ViewModel（reduce 为 UiState）→ UseCase → Repository（单一数据源，本地为 SSOT）→ Room DAO(Flow) / UsageStats / DataStore；一次性副作用走 `Channel<UiEffect>`。

---

## 3. 核心功能模块设计

### 3.1 专注计时引擎 `:domain:focus`（最高风险，最优先）

状态机（纯函数 + StateFlow）：

```
Idle ─start▶ FocusRunning ─pause▶ FocusPaused ─resume▶ FocusRunning
                 │complete                       │complete
                 ▼                                ▼
            ShortBreak ──complete──▶ FocusRunning(下一轮)   LongBreak(每4轮)
任意运行态 ─stop(长按800ms确认)▶ Interrupted → 归档会话(不计完成数,计入总投入)
任意态 ─进程死亡▶ Restore(Checkpoint 重建)
```

- 唯一时间源：注入 `ElapsedTimeProvider { SystemClock.elapsedRealtime() }`，**禁用 currentTimeMillis**（改表/时区不影响计时）
- Checkpoint：每 5s + 状态跃迁 + onDestroy 原子写入 DataStore；恢复时 `elapsed ≥ remaining` 判定"离开期间已完成"直接结算
- 双保险：结束前 `AlarmManager.setExactAndAllowWhileIdle` 注册闹钟，进程被杀由 `FinalizeReceiver` 按 Checkpoint 结算
- 番茄参数：专注 25 / 短休 5 / 长休 15 / 每 4 轮长休，全部可配；支持跳过休息、自动开始下一阶段
- 单测：Turbine + Fake 时钟，全迁移/累计/恢复/跨日 覆盖（目标行覆盖 ≥90%）

### 3.2 屏幕监控引擎 `:data:usage` + `:domain:usage`

- 采集：`UsageStatsManager.queryEvents(since, now)` 事件驱动，`ACTIVITY_RESUMED/PAUSED` 配对成区间；同包重叠取并集（分屏）；跨午夜切分；锁屏未闭合区间截断到 `SCREEN_NON_INTERACTIVE`
- 游标：`last_event_ts` + 2–5 分钟重叠窗口 + `(package, start_ts)` 幂等去重
- 三级触发：L1 前台服务（屏幕广播 + 自适应 tick，无事件降频）→ L2 WorkManager 30min 对账补偿 → L3 AlarmManager 心跳/日报兜底
- 缺口模型：屏幕点亮总时长 − 已覆盖区间 > 5min 置 `data_gap`，重放 queryEvents → 日桶差额补偿（`estimated=true`）→ UI 显示"部分数据为估算值"
- 服务类型：`foregroundServiceType="dataSync"`（Android 14+ 强制），低重要性常驻通知显示今日已用时长；`START_STICKY` + onDestroy 经 AlarmManager 1 分钟后重启；**不持 WakeLock、不固定轮询**

### 3.3 数据模型（Room schema）

| 表 | 关键字段 | 用途 |
|---|---|---|
| `app_meta` | package_name PK, label, last_updated | 应用名/图标缓存（避免重复 PackageManager 查询） |
| `usage_interval` | id, package_name, start_ts, end_ts, source(event/estimated) | 原始区间 |
| `daily_usage` | date, package_name, total_ms, open_count, first_used_at, last_used_at, estimated_ms | 日聚合（PK: date+package） |
| `focus_session` | id, mode, tag_id, started_at, ended_at, duration_ms, status(completed/interrupted), cycles | 专注会话 |
| `focus_daily` | date PK, focus_ms, completed_count, interrupt_count | 专注日聚合 |
| `tag` | id PK, name, emoji, sort, archived | 专注标签 |

DataStore(Proto)：`settings`（主题/每日目标/番茄参数/白噪音默认/通知开关）、`timer_checkpoint`（专注引擎状态）、`sponsor`（收款码图片路径、是否显示赞助卡）。

### 3.4 UI 模块（Compose，映射设计规范）

| 页面 | 核心内容 | 关键组件 |
|---|---|---|
| Onboarding | 欢迎 → 使用情况访问 → 通知 → 电池优化(可选) → 悬浮窗(可选) → 完成 | 权限步骤卡 + 系统设置跳转 + AppOps 校验 |
| Home | 问候顶栏 + Hero 双环卡（外环=目标完成度/内环=连续天数占比）+ 3 StatPill + 目标进度条 + 常用标签横滑 + 最近专注 + **赞助卡** | DualRingProgress、StatPill、DeltaBadge、AppMiniCard |
| Focus | 模式分段 + 标签 chips + 280dp 计时环 + 长按确认停止 + 白噪音 chips + mini 悬浮条 | FocusRingTimer、LongPressConfirm、ModeSegmented |
| Stats | 周期分段 + 指标卡 + 图表 tab（柱状/占比/时段块图）+ 本周vs上周并列柱 + 排行 + 洞察卡 | InteractiveBarChart、CategoryDonut、HeatmapBlocks、AppRankRow |
| Calendar | 月历 + 每日专注时长 + 5 档强度色 + 日详情列表 | MonthGrid |
| Settings | 主题预览卡 / 目标滑块 / 番茄 Stepper / 提醒 / 数据导入导出 / **收款码上传** / 关于 | ThemePreviewCard、StepperRow、DangerRow |
| Sponsor | 全屏 Dialog：收款码大图 + 文案 + 「完全免费」声明 | SponsorDialog |

设计 token 落地：`:core:designsystem` 中 `Color.kt / Type.kt / Shape.kt / Dimens.kt` 与官网 CSS 变量**同值同源**（叠翠 Teal 600 `#0A7A61` / 暖橙 `#E8930C` / 暖纸浅色 / 墨青深色，间距 4/8 网格，圆角 6/10/14/20/28，动效 120/200/320/480/720ms + 标准曲线）。品牌符号「留白之环」用于 Launcher 图标、进度环、启动页。

### 3.5 白噪音（程序化合成，零音频资产）

AudioTrack 实时生成：雨声=白噪声→带通 800–2400Hz + 缓慢增益起伏；咖啡馆=棕噪声→低通 500Hz + 随机 3–8s 间隔的清亮叮声（800–2000Hz 正弦短包络）；纯白噪音=棕噪声。音量 0–100 可调，专注开始/结束自动淡入淡出 800ms。相比原版打包 4MB m4a，本方案 **0 字节资产、无限时长、无版权风险**。

---

## 4. 权限配置（Manifest）

| 权限 | 用途 | 适配要点 |
|---|---|---|
| `PACKAGE_USAGE_STATS` | 核心：读取使用统计 | 特殊权限，引导跳系统设置 + `AppOpsManager.unsafeCheckOpNoThrow` 校验 |
| `QUERY_ALL_PACKAGES` | 应用列表/名称/图标 | Android 11+；配 `<queries>` 兜底；Play 政策需在声明中说明用途 |
| `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_DATA_SYNC` | 监控常驻服务 | Android 14+ 必须声明类型；启动时校验 |
| `RECEIVE_BOOT_COMPLETED` | 开机恢复监控 | Android 15 对 BOOT 启动 FGS 有限制，走 AlarmManager/expedited Work 兜底 |
| `SCHEDULE_EXACT_ALARM` | 番茄闹钟/日报/心跳 | Android 12+ 用户可撤销；不可用时降级 `setAndAllowWhileIdle` |
| `POST_NOTIFICATIONS` | Android 13+ 通知 | 运行时申请 |
| `VIBRATE` | 专注完成触觉 | — |
| `SYSTEM_ALERT_WINDOW` | **可选**：专注 mini 悬浮条 | 设置内引导开启，不开则隐藏该功能入口 |
| `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` | 引导用户加白名单，提升监控存活率 | 仅在设置页由用户主动触发，带解释文案 |
| `INTERNET` | 仅帮助文档 WebView 与官网链接 | **无统计 SDK、无账号请求**；network_security_config 固定 cleartext 关闭 |

**相对原版删除**：WRITE_EXTERNAL_STORAGE（改 SAF）、DISABLE_KEYGUARD、REORDER_TASKS、RECEIVE_USER_PRESENT、各家厂商私有权限（bbk/samsung/huawei）——改为标准电池优化 intent + 厂商深链 best-effort 降级。

---

## 5. 赞助模块（免费模式的核心商业化替代）

- **零门控**：不接入任何支付 SDK，不做 entitlement 判断，全部功能对所有安装用户开放
- **入口**：① 首页底部赞助卡（可关闭，关闭后不再显示）② 顶栏 ❤ 图标 ③ 设置页「赞助开发」
- **收款码**：设置页上传图片（相册/文件）→ 存 `filesDir/sponsor/qr.png`（内部存储，不随备份导出）→ SponsorDialog 全屏展示 + 「长按保存二维码」提示
- **文案基调**：「OffScreen 留白 完全免费，无会员无广告。如果它帮到了你，可以请开发者喝杯咖啡 ☕」
- **兜底**：未上传收款码时显示占位插画 + 引导去设置上传

---

## 6. 构建、签名与发布

- 签名：开发者自持 keystore，`signingConfigs` 走 `local.properties` / 环境变量，**不进版本库**
- 产物：① AAB（Google Play，如需）② universal APK（官网分发，arm64-v8a + armeabi-v7a 可选 split；本版无原生库，单包即可）
- 版本策略：`versionCode` 单调递增，`versionName` 语义化；更新日志随版本维护（官网同步）
- 包体预算（CI 门禁）：下载体积 ≤10MB；冷启动 P95 <1.5s；掉帧 <1%
- 官网分发即信任源：下载页提供 SHA-256 校验和 + 安装指引（允许未知来源等）

---

## 7. 官网与 APK 的协同设计

| 维度 | APK | 官网 |
|---|---|---|
| 品牌 | 「留白之环」图标 + 叠翠/暖纸墨青 token | 同一 SVG Logo + 同一 CSS 变量（同值同源） |
| 内容 | 设置→关于→「访问官网」 | 应用介绍/功能/下载/更新日志/支持 |
| 更新 | versionName + 更新日志 | 下载页版本卡片 + Changelog 页与 APK 版本号联动 |
| 支持 | 设置→反馈（mailto） | FAQ/反馈邮箱/隐私政策/备份指南 |
| iOS | — | 官网即 PWA 载体：manifest + 离线缓存，"添加到主屏幕"获得专注计时体验（iOS 无 UsageStats 等价能力，主打专注+手动记录） |
| 赞助 | 收款码弹窗 | 下载页赞助区 + ❤ 入口，同一张码 |

---

## 8. 里程碑与任务分解

| 里程碑 | 周期 | 出口标准 |
|---|---|---|
| **M0 工程基线** | 3 天 | 模块化骨架 + designsystem token + 底部导航空壳 + CI（lint/单测/依赖规则）通过 |
| **M1 专注内核** | 1 周 | 计时引擎单测 ≥90%；专注页可完整跑完番茄钟；首页双环+最近列表；Checkpoint 杀进程恢复 |
| **M2 监控与统计** | 1.5 周 | UsageStats 采集+缺口标记；统计页三图表；日历；小部件 |
| **M3 干预与打磨** | 1 周 | 限额/挑战/熬夜提醒（默认关）；日报通知；引导流程；白噪音合成 |
| **M4 发布** | 3 天 | 赞助模块；导入导出；性能预算达标；签名 APK + 官网下载页上线 |
| **M5 官网** | 与 M4 并行 | 本仓库 `offscreen-web/` 即官网，构建部署 |

---

## 9. 与《01-登录与会员体系改造方案》的关系

该方案作为技术储备**存档不启用**：本版无账号体系，故 §1–§4 的认证/会话/权益全部不实现；其中仍沿用的部分：安全基线（TLS/无日志 PII）、合规（隐私政策、数据导出）、模块化与测试策略。如未来做云同步再按该文档 M2–M4 启用。

---

## 10. 立即启动清单（下一步）

1. `local.properties` 配 SDK；Gradle 版本 catalog；`:build-logic` convention 插件
2. `:core:designsystem` 落地 tokens（与官网 CSS 变量同值）
3. `:domain:focus` 状态机 + 黄金单测（第一优先级，原版最复杂处）
4. `:feature:focus` 专注页 + RingTimer 组件
5. 同步启动官网（`offscreen-web/`）：介绍/下载/更新日志/支持 四页
