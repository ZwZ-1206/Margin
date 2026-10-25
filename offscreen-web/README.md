# Margin「留白」 · 官方网站

配套 Android 应用「Margin 留白」的官方网站，同时作为 iOS 侧的 PWA 入口（专注计时，添加到主屏幕使用）。

- **应用**：专注番茄钟 + 屏幕使用洞察，完全免费，无会员、无订阅、无广告
- **本站**：应用介绍 / 下载页 / 更新日志 / 用户支持（FAQ + 反馈 + 隐私摘要）+ 赞助入口

## 技术栈

Vite 5 + React 18 + TypeScript，零运行时依赖（无 UI 框架、无路由库、无图表库）。
设计 Token 与 APK 的 `:core:designsystem` 同值同源（叠翠 Teal / 暖橙 Amber / 暖纸浅色 / 墨青深色）。

## 本地开发

```bash
npm install
npm run dev        # http://localhost:5173
npm run build      # 产出 dist/（base 为相对路径，可部署到任意静态托管）
npm run preview    # 本地预览构建产物
```

## 部署

`npm run build` 后的 `dist/` 为纯静态站点，可直接部署到 GitHub Pages / Vercel / Netlify / 对象存储，
或将 `dist/` 内容放入任意 Web 服务器目录。`manifest.webmanifest` 与 `icon.svg` 已内置，部署后支持
「添加到主屏幕」（iOS 即以 PWA 形态使用）。

## 上线前待替换项

| 位置 | 内容 |
|---|---|
| `src/pages/SupportPage.tsx` | `FEEDBACK_EMAIL` 改为真实反馈邮箱 |
| `src/pages/DownloadPage.tsx` | 发布后填入真实 APK 下载链接与 SHA-256 校验和 |
| `src/site/data.ts` | `CHANGELOG` 按版本持续维护 |
| 赞助收款码 | 真实二维码图片替换占位图（App 内由设置页上传，本站放静态图） |

## 目录结构

```
public/            manifest、图标（「留白之环」）
src/
  styles/          tokens.css（设计变量）/ global.css
  components/      SiteHeader / SiteFooter / PhoneMockup / SponsorCard / RingLogo / Icons
  pages/           Home / Download / Changelog / Support
  site/data.ts     功能、更新日志、FAQ 内容
  hooks/           useTheme（浅色/深色/跟随系统）
```

## 响应式适配（桌面 / 平板 / 手机）

已按三档视口实测（Playwright 截图，见 `shots/`）：

| 视口 | 布局 |
|---|---|
| 桌面 ≥1280px | 容器 1140px，Hero 左右双栏（文案 + 手机样机），功能 3 列，隐私 4 列 |
| 笔记本 901–1279px | 容器 1080px，同上双栏布局 |
| 平板 681–900px | Hero 单栏（样机上移），功能 2 列，隐私 2 列，下载/支持单栏 |
| 手机 ≤680px | 汉堡菜单（44px 触控、点击外部关闭），全部单栏，按钮全宽，QR 132px |
| 小屏 ≤380px | 手机样机缩至 232px，间距收紧 |

其他适配细节：iPhone 安全区（`env(safe-area-inset-*)`）、`100dvh`、`prefers-reduced-motion` 全局降级、
`focus-visible` 键盘焦点环、`-webkit-tap-highlight` 禁用、深/浅/跟随系统三主题。

## 视觉 QA（Playwright）

```bash
npm run build && npx vite preview --port 4173   # 另开终端
node shots/take.mjs                             # 生成 shots/*.png（4 页面 × 3 视口 + 深色 + 菜单）
```

## 分发（官网即唯一渠道）

- `public/downloads/margin-1.2.15.apk` —— 当前发布包（3.6MB，签名证书 SHA-256 `d62427bc…cf4`）
- `public/downloads/version.json` —— 机器可读版本清单：版本号、体积、SHA-256、下载路径、镜像、证书指纹
- 下载页在运行时读取该文件渲染；二维码编码 `SITE_ORIGIN + apk.url`
- **上线前请修改** `src/site/download.ts` 中的 `SITE_ORIGIN` 为真实域名（二维码必须是绝对地址）
- 发新版流程：替换 APK → 更新 version.json（版本号/体积/SHA-256/日期/说明）→ `npm run build` → 上传

```bash
# 生成校验值
sha256sum margin-1.0.0.apk
```
