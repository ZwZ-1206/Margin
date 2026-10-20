# Margin 留白

> 放下手机，为更精彩的生活留白。
> A little less scrolling. A little more living.

一款**完全免费**的 Android 屏幕使用洞察 + 专注计时应用。无账号、无会员、无订阅、无广告、无付费墙；设置页内置赞助收款码窗口，赞助不解锁任何功能。

本仓库包含三部分：Android 应用、配套官网（唯一分发渠道）、以及立项以来的设计与评估文档。

---

## 目录结构

```
.
├── liubai-android/           Android 应用（9 个 Gradle 模块，Kotlin + Compose）
│   ├── app/                  组合根：Application / MainActivity / 导航 / 启动图标
│   ├── core/common/          纯 JVM 工具（日期、格式化、错误类型）
│   ├── core/designsystem/    设计 Token、主题、「留白之环」组件
│   ├── domain/               纯 JVM 领域层（引擎/算法/仓储接口），8 个单测
│   ├── data/                 Room / DataStore / UsageStats 采集 / 前台服务 / Worker / 备份
│   └── feature/{home,focus,stats,settings}/
│
├── offscreen-web/            官网（Vite + React + TS，零运行时依赖）
│   ├── src/pages/            首页 / 下载 / 更新日志 / 支持
│   └── public/downloads/     发布用 APK + version.json 版本清单
│
├── docs/                     设计与评估文档
│   ├── 01-登录与会员体系改造方案.md
│   ├── 02-设计与工程提升方向.md
│   ├── 03-Android-APK-实现方案.md
│   ├── 04-品牌命名与定位.md
│   └── 05-Margin 品牌视觉规范.md
│
└── apk-analysis/             原版 APK 逆向分析产物（资源表、布局树、类清单、字符串库）
```

## 快速开始

### 构建 APK

```bash
cd liubai-android
# 1) 指定本机 SDK
echo "sdk.dir=/path/to/Android/Sdk" > local.properties
# 2) 调试包
./gradlew :app:assembleDebug
# 3) 发布包（需先配置 keystore.properties，见子项目 README）
./gradlew :app:assembleRelease
```

产物：`liubai-android/app/build/outputs/apk/release/app-release.apk`（约 2.7MB）

### 运行官网

```bash
cd offscreen-web
npm install
npm run dev        # 开发
npm run build      # 构建（产物在 dist/）
```

## 当前状态

| 项 | 状态 |
|---|---|
| Android 应用 | ✅ 可构建可安装（v1.1.0，2.7MB，V2 签名） |
| 专注系统 | ✅ 番茄钟/倒计时/正计时，断点恢复，六种程序化白噪音 |
| 屏幕洞察 | ✅ UsageStats 事件驱动采集 + 30 分钟对账 + 缺口标注 |
| 统计 | ✅ 日/周/月，双柱图，应用排行，专注记录 |
| 桌面小部件 | ✅ 小（2×2）/ 中（4×1）两种尺寸 |
| 单应用限额 | ✅ 每日上限 + 临近 5 分钟预警，同一天只提醒一次 |
| 挑战体系 | ✅ 连续使用 / 拿起次数 / 就寝提醒（默认关闭） |
| 应用内帮助 | ✅ 设置页「帮助与常见问题」，离线可读 |
| 分享卡片 | ✅ 专注完成后生成卡片保存到相册 |
| 数据 | ✅ JSON 备份导出/导入，全部本地 |
| 赞助 | ✅ 设置页上传收款码（无任何功能门控） |
| 官网 | ✅ 四页，双端适配实测，含真实 APK 与校验值 |

## 上线前需替换的占位

1. `offscreen-web/src/site/download.ts` → `SITE_ORIGIN` 改为真实域名（二维码必须是绝对地址）
2. `offscreen-web/src/pages/SupportPage.tsx` → 真实反馈邮箱
3. App 设置页 → 上传你自己的收款码；官网 `SponsorCard` 占位 QR 同步替换

## 许可

MIT License，见 `liubai-android/LICENSE`。

## 推送到 GitHub（双击即可）

仓库：https://github.com/ZwZ-1206/Margin

1. 复制令牌到剪贴板（GitHub → Settings → Developer settings → Personal access tokens）
   - 细粒度：Repository access 选 `Margin`，Permissions → **Contents: Read and write**
   - 或经典：勾选 `repo` + `workflow`
2. 双击根目录的 **`推送代码到GitHub.bat`**
3. 脚本会自动：推送 `main` → 按 `version.json` 创建/复用 Release → 上传对应 APK

细节：
- 令牌只在该次命令中作为 URL 参数使用，**不会写入 `.git/config`**（脚本结束会把 origin 复位成干净地址）
- 推送失败会自动重试 4 次，后两次会临时清空代理环境变量走直连
- 输出里只显示令牌前 11 位

## 一键发版

```bash
# 1) 打出 release APK 并双签名
cd liubai-android && ./gradlew :app:assembleRelease && ./sign-release.sh

# 2) 把 APK 放进官网，更新 version.json 的 version/sizeMB/sha256/notes
#    文件：offscreen-web/public/downloads/margin-<版本>.apk

# 3) 构建 + 部署 + 线上校验
cd .. && ./deploy.sh
```

`deploy.sh` 复用本机 wrangler 登录态，无需每次给 token；末尾会自动比对线上与本地 APK 的 SHA-256，不一致就非零退出。
