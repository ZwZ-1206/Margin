# Margin「留白」· 社交海报物料

小红书 / 抖音用的竖版推广图，全部由 `app-shot-poster` 技能流程生成。

## 产物

```
marketing/
├── screens.html           # 6 个界面（按 App 真实设计 token 渲染）
├── shots/                 # 390×844 @2x 截图（780×1688）
├── posters.html           # 海报骨架（?p=N&size=916|34 单张导出）
├── posters/               # 抖音 9:16 → 1080×1920
│   └── 34/                # 小红书 3:4 → 1080×1440
└── shots/contact-sheet.png / posters/_contact-*.png   # 拼版预览
```

## 复跑

```bash
# 1) 起静态服务（必须 HTTP）
cd marketing && python -m http.server 45130 --bind 127.0.0.1

# 2) 截界面（Playwright 在 offscreen-web 的 node_modules 里）
cd ../offscreen-web && node tools/capture-shots.mjs

# 3) 出海报（两个尺寸各 6 张）
node tools/export-posters.mjs
```

## 换素材 / 改文案

- **换成真机截图**：把 6 张 390×844 的 PNG 覆盖到 `shots/shot-0N.png`，重跑第 3 步即可，海报骨架不用动
- **改文案**：`posters.html` 顶部的 `DATA` 数组，每项含 `eyebrow / l1 / l2 / hl / notes / float / ac / bg`
- **改主色**：`ac`（主色）、`bg`（底色 tint）、`acs`（装饰光晕），六张同源不同色

## 设计约束（照 skill 执行）

- 标题每行 ≤6 汉字，只高亮 1 个词
- 正文字号 ≥28px（1080 宽基准）
- 手机样机只保留机身外框，不添加假状态栏 / 假电量
- 界面按视口比例等比放入，不裁切、不拉伸
- 不写死随机数据（截图里的数字只作示意，文案不引用具体数值）
