---
name: remoteviews-widget-loading-fix
displayName: "Android 桌面小组件「载入窗口小部件时出现问题」修复手册"
description: >
  OEM 桌面（OPPO/ColorOS、小米 MIUI、Samsung OneUI 等）添加自定义小组件时
  报「载入窗口小部件时出现问题 / Error loading widget」的根因定位与修复方法。
  真根因是 RemoteViews 的 View 类白名单——普通 <View>/<Space> 在桌面进程里
  inflate 会被拒绝。本文档覆盖：症状判读、logcat 只读排查、白名单合规写法、
  防御性 Provider 实现、验证清单与常见弯路复盘。
tags: [android, appwidget, remoteviews, oem-launcher, coloros, widget]
---

# Android 桌面小组件「载入窗口小部件时出现问题」修复手册

> 适用场景：自定义 `AppWidgetProvider` + `RemoteViews` 的桌面小组件，在部分
> 国产 OEM 桌面（OPPO/ColorOS、小米、三星等，尤其 Android 12+）上添加时报
> 「载入窗口小部件时出现问题」，但应用自身无任何崩溃日志。
>
> 本文来自 Margin「留白」v1.2.15→v1.2.22 的真实排障过程，根因与修复方案均
> 已在 OPPO PMA110 / Android 17 上验证。

---

## 1. 一句话结论

小组件布局**不是在你的 App 进程里 inflate 的，而是在桌面（Launcher）进程里
inflate 的**。桌面进程有一份严格的 **RemoteViews View 类白名单**，普通
`<View>`、`<Space>` 这类"无语义占位控件"不在白名单内，会被
`LayoutInflater.failNotAllowed` 直接拒绝，抛
`InflateException: Class not allowed to be inflated android.view.View`。
**这个异常发生在桌面进程，App 侧的 `try/catch` 抓不到**，于是桌面只能显示自己的
错误占位图「载入窗口小部件时出现问题」。

只要布局里出现哪怕一个白名单外的 View 类，整个部件就加载失败——与你的
Java/Kotlin 逻辑、超时、样式、包体积全都无关。

---

## 2. 症状判读（先别瞎改）

| 现象 | 说明 | 该查什么 |
|---|---|---|
| 部件已出现在桌面，但内容是系统错误占位图 | **绑定成功，inflate 失败**（在 Launcher 进程） | 布局里的 View 类 / 属性是否合规 |
| 添加动作直接失败、根本放不上桌面 | `onUpdate` 回传前超时，或 `configure` Activity 异常 | Provider 回传时机、`bindAppWidgetId` 超时 |
| App 自己 `crash` 或 `logcat` 有 App 进程堆栈 | 问题在 App 侧逻辑 | 你的 `RemoteViews` 构建/`updateAppWidget` 调用 |
| 同一布局在 Android 模拟器/原生桌面能显示，OEM 机上不行 | **白名单差异**（老系统宽松、新系统严格） | 见第 3 节 |

**关键判读**：错误图是桌面画的，不是你画的。所以"在 App 里加 try/catch""改成
异步加载""减小包体积""换 ProgressBar 为 ImageView"这类改法都**碰不到真根因**——
它们在 v1.2.16/17/18 里全部试过，全部无效。

---

## 3. 真根因：RemoteViews View 白名单

`AppWidgetHostView` 在 Launcher 进程里用 `LayoutInflater` 加载你的
`remoteViews` 布局，并启用 `filter=true`（只允许白名单 View）。白名单大致等价于
官方文档 "Creating App Widget Layouts" 列出的控件：

✅ **安全可放心用**
- `LinearLayout` / `RelativeLayout` / `FrameLayout`
- `TextView` / `ImageView` / `ImageButton`
- `Button` / `Chronometer` / `AnalogClock` / `ProgressBar`（部分 OEM 受限，谨慎）
- `ListView` / `GridView` / `StackView` / `AdapterViewFlipper`（需 `RemoteViewsService`）

❌ **高危（OEM 桌面常直接拒绝）**
- `<View>`（裸 `android.view.View`）——分隔线、占位、spacer 别用它
- `<Space>`——同样不在白名单
- 任何自定义 `View` / `ViewGroup`（除非在 `RemoteViews` 支持的有限自定义集合内）
- `WebView`、`MapView`、`SurfaceView`、 `TextureView` 等

> 注意：**旧版本 Android 的 Launcher 实现较宽松**，裸 `<View>` 可能"侥幸"过关；
> 但 Android 12+ 尤其 ColorOS / 部分 MIUI 已严格 enforce。所以"以前能用、某个
> 版本后突然不行"通常是 OEM 升级了白名单策略，而不是你代码变了——但本例是
> v1.2.15 重构时**新引入了**裸 `<View>` 分隔线，等于自己埋雷。

---

## 4. 排查方法：只读 logcat（不碰手机）

用户约束：「只修改我们开发的 APP 本身，不许调试我的手机」。因此排障全程**只读**：

1. 手机开启 **USB 调试**（开发者选项），用数据线连电脑，在弹窗点「允许」。
2. 电脑执行（任选其一，按机型过滤）：
   ```bash
   # 实时看全部，grep 关键字
   adb logcat | grep -iE "InflateException|failNotAllowed|not allowed to be inflated|AppWidget"
   # 或只看系统/桌面进程（OPPO 桌面包名常为 com.oppo.launcher / com.coloros.xxx）
   adb logcat -b system | grep -iE "widget|inflate"
   ```
3. **复现**：在桌面上添加/重新添加出问题的部件。
4. 在 logcat 里你会看到类似：
   ```
   LayoutInflater: inflating: android.view.View
   LayoutInflater: Attempting to inflate ...
   LayoutInflater: failNotAllowed: Class not allowed to be inflated android.view.View
   AndroidRuntime: java.lang.InflateException:
       Error inflating class android.view.View
   ```
   直接告诉你**哪个类**被拒（这里是 `android.view.View`）。

> 没有 USB 调试时，可临时在 `AppWidgetProvider` 里把关键步骤文本写到部件卡片上
> （诊断版，见第 6 节），用"卡片上显示什么"反推执行到哪一步——但这是退路，能连
> `adb` 优先用 `adb logcat`，信息最全。

---

## 5. 修复方案

### 5.1 把分隔线/占位从 `<View>` 改成 `<ImageView>`

**修复前（v1.2.15 引入，根因）：**
```xml
<View
    android:layout_width="match_parent"
    android:layout_height="1dp"
    android:background="#E6E1D6" />
```

**修复后（v1.2.20 起，合规）：**
```xml
<ImageView
    android:layout_width="match_parent"
    android:layout_height="1dp"
    android:background="#E6E1D6" />
```

`<ImageView>` 在白名单内，用 `background` 画一条 1dp 的线或色块即可替代分隔线。
竖直分隔线同理（`layout_width="1dp"`）。**全仓库所有部件布局都要搜一遍**，确认
没有残留裸 `<View>` / `<Space>`：
```bash
grep -rn "<View\b\|<Space\b" app/src/main/res/layout/widget_*.xml
# 期望：无匹配（注释里的文字说明不算）
```

### 5.2 布局级约束清单（写进布局文件头部注释，防止复发）

```xml
<!-- 兼容性约束（Android 17 / ColorOS 实测）：
     1. 布局在桌面进程 inflate，有 View 类白名单：
        普通 <View>/<Space> 会抛 "Class not allowed to be inflated"
        → 分隔线一律用 ImageView + background；
     2. 仅用 RemoteViews 官方支持的 View：
        LinearLayout / TextView / ImageView / …；
     3. 不用 previewLayout（用静态 previewImage 占位）、不用主题属性样式、
        只用 API 1 就有的属性。 -->
```

### 5.3 防御性 Provider 实现（v1.2.18 沉淀，仍保留）

即使布局合规，也建议让 Provider 在**任何取数异常时**都能回传一个合法
`RemoteViews`，避免"白屏/超时占位"被误判为"加载失败"：

```kotlin
override fun onUpdate(context: Context, mgr: AppWidgetManager, ids: IntArray) {
    // 1) 同步先推一个零 IO 的占位，满足桌面 bindAppWidgetId 的回传时限
    ids.forEach { id ->
        mgr.updateAppWidget(id, RemoteViews(pkg, layout))
    }
    // 2) 再异步填充真实数据，全程 runCatching + 超时兜底
    scope.launch {
        ids.forEach { id ->
            runCatching {
                val data = withTimeout(2_500L) { WidgetData.snapshot(app) }
                val views = build(app, layout, data)   // 纯 RemoteViews 构建
                mgr.updateAppWidget(id, views)
            }
        }
    }
}
```

要点：
- **`onUpdate` 先同步回传占位**再异步取数，避免桌面 `bind` 超时（OPPO 超时窗口短）。
- 所有 `updateAppWidget` 包 `runCatching`，单个 id 失败不影响其余。
- 取数（如 App 使用排行要读 `PackageManager` 图标）用 `withTimeout` 兜底，
  超时只降级为「无排行」，部件照常可用。
- 大部件（含图标行列）单独走重方法，小/中部件走轻方法，互不拖慢。

### 5.4 时长文案也别"撑爆"部件（v1.2.22 附带修复）

这不是加载失败，但会导致**文字溢出/折行错位**，容易被误认为排版坏了：
- 2×2：把 `6 小时 29 分钟` 单行拆成「第一行小时 / 第二行分钟」两行，整点只显小时、
  不足 1 小时只显分钟。
- 4×1：新增 `DurationFormat.compact()` 输出 `6小时29分` / `45分`，并把字号列宽
  收紧凑，防窄列折行。

```kotlin
// core/common .../Common.kt
object DurationFormat {
    fun chinese(ms: Long): String { /* "1 小时 52 分钟" */ }
    fun compact(ms: Long): String { /* "1小时52分" / "52分" */ }
}
```

---

## 6. 诊断版部件（定位"卡在哪一步"的退路）

连不上 `adb` 时，可临时把执行链路打到卡片上，用"卡片显示什么"反推：
- 默认显示 `DIAG-PENDING` → `onUpdate` 的回调根本没被调用（绑定/权限问题）。
- 显示某段真实文本 → 链路通，问题在展示（inflate/样式）。
- 仍是系统错误占位图 → 100% 是 inflate 失败（白名单/属性），见第 3 节。

```kotlin
// 诊断期：把状态文本 setText 到卡片主 TextView，并写日志到 widget_diag.log
// 定位完成后整段删除，回归真实逻辑
```

> 这个手段只用于"无 logcat 可用"的窘境；有 `adb` 时直接用第 4 节的 `logcat`，
> 不要长期保留诊断代码。

---

## 7. 验证清单（发版前必过）

- [ ] `grep` 确认所有 `widget_*.xml` **无**裸 `<View>` / `<Space>`。
- [ ] 布局只含白名单 View（LinearLayout/TextView/ImageView 等）。
- [ ] 未使用 `previewLayout`、未使用需要主题/style 的属性。
- [ ] `appWidgetProviderInfo` 的 `previewImage` 指向静态图（不用 `previewLayout`）。
- [ ] Provider `onUpdate` 先同步占位、再异步填充、全程 `runCatching`。
- [ ] 用 `tools/widget_previews.py`（PIL + 系统中文字体）重画预览图，确认排版。
- [ ] 真机安装后**长按删掉旧部件再重新添加**（旧部件不自愈），确认不再报错误图。
- [ ] 三尺寸（2×2 / 4×1 / 4×3）逐一验证。

---

## 8. 版本复盘：哪些尝试走了弯路（别重蹈）

| 版本 | 假设 / 改动 | 结果 | 教训 |
|---|---|---|---|
| v1.2.16 | 以为是布局属性/样式问题 | ❌ 仍失败 | 没查 View 类本身 |
| v1.2.17 | 极简布局：去 margin、去 ellipsize、ProgressBar→ImageView、去 previewLayout、全程 try/catch | ❌ 仍失败 | 方向对了一半（去了 previewLayout），但漏了 `<View>` 分隔线 |
| v1.2.18 | 以为是 ColorOS bind 超时：拆分 snapshot、加 2.5s 超时 | ❌ 仍失败 | 超时是次要问题，根因在 inflate |
| v1.2.19 | 诊断版，把执行链打到卡片 | ⚠️ 定位辅助 | 确认"不是卡在取数"，逼出 logcat 排查 |
| v1.2.20 | **真根因修复：裸 `<View>`→`<ImageView>`** | ✅ 成功 | **查 View 标签是否在 RemoteViews 白名单** |
| v1.2.21 | 大部件 TOP 3→TOP 5（顺手优化） | ✅ | — |
| v1.2.22 | 2×2 拆两行、4×1 紧凑文案防折行 | ✅ | 美观 ≠ 加载失败，但都归在部件修复里 |

**核心教训**：碰到「载入窗口小部件时出现问题」，**第一反应不是改 Java 逻辑，而是
打开布局 XML 逐个核对 View 标签与属性是否在 RemoteViews 白名单**。OEM 桌面把
inflate 放进自己进程并 enforce 白名单，这是 App 侧 try/catch 永远抓不到的盲区。

---

## 9. 一句话速记

> 桌面小组件加载失败 = 布局在 Launcher 进程 inflate + 白名单外的 View。
> 先 `adb logcat | grep failNotAllowed` 看被拒的类，
> 把 `<View>`/`<Space>` 全换成 `<ImageView>`，别在 App 里白加 try/catch。
