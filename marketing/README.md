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

## 六张的版式

| # | 版式 | 结构 | 适用 |
|---|---|---|---|
| 01 | A 经典 | 标题左上 + 手机居中 + 左右标注 | 主力款，信息最全 |
| 02 | B 大字报 | 标题居中最大 + 手机沉底 + 胶囊 CTA | 封面/首图，冲视觉 |
| 03 | C 分栏 | 左侧文案与标注、右侧大手机 | 需要放大界面细节 |
| 04 | D 横排 | 标题在上、三张标注横排在下 | 功能点并列 |
| 05 | E 大数字 | 巨号数字主导 + 手机缩小在右下 | 数据类卖点 |
| 06 | F 宣言 | 全居中、极小手机、一句话 | 收尾/情绪页 |

> 每张在 9:16 与 3:4 下各有独立的坐标覆盖（`.p9x16.layout-x` / `.p3x4.layout-x`），改尺寸不用重排版。

## 图标

`assets/icon-256.png` / `icon-512.png` —— 由 App 的真实图标源图生成（圆角方、透明角），海报品牌行与桌面场景都用它，不是另画的图形。

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
