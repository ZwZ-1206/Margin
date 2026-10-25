# -*- coding: utf-8 -*-
"""创建 GitHub Release v1.2.18 并上传 APK（api.github.com + uploads.github.com）"""
import json
import os
import urllib.request

REPO = "ZwZ-1206/Margin"
BASE = "https://api.github.com"
TOKEN = os.environ.get("GH_TOKEN") or open("C:/Users/ZwZ/AppData/Local/Temp/gh_pat.txt").read().strip()
TAG = "v1.2.20"
SHA = "b932c80e8f226abe87f9ab5ebee585061440a552"
APK = "C:/Code/Margin/offscreen-web/public/downloads/margin-1.2.20.apk"

opener = urllib.request.build_opener(urllib.request.ProxyHandler({}))


def api(method, path, payload=None, ctype="application/json"):
    data = json.dumps(payload).encode() if payload is not None else None
    req = urllib.request.Request(BASE + path, data=data, method=method)
    req.add_header("Authorization", f"Bearer {TOKEN}")
    req.add_header("Accept", "application/vnd.github+json")
    req.add_header("Content-Type", ctype)
    req.add_header("User-Agent", "margin-release-bot")
    with opener.open(req, timeout=180) as resp:
        body = resp.read()
        return json.loads(body) if body else {}


notes = """## Margin「留白」 v1.2.20

**修复 OPPO / ColorOS 等 OEM 桌面添加小组件时提示「载入窗口小部件时出现问题」——真根因**

经 logcat 实测定位（OPPO PMA110 / Android 17 / ColorOS）：

```
W AppWidgetHostView: android.view.InflateException: Binary XML file line #60
   in com.liubai.app:layout/widget_small: Error inflating class android.view.View
Caused by: android.view.InflateException: Class not allowed to be inflated android.view.View
   at android.view.LayoutInflater.failNotAllowed(LayoutInflater.java:810)
```

**根因**：小组件布局是在**桌面启动器进程**里 inflate 的，该进程的 `LayoutInflater` 有 View 类白名单，普通 `android.view.View` 不在其中，直接抛异常，桌面随即显示「载入窗口小部件时出现问题」。v1.2.15 的布局重写给三个尺寸都加了 `<View>` 分隔线，这正是问题起点。

v1.2.16 / v1.2.17 / v1.2.18 三个版本分别改了主题属性样式、预览方式、绑定窗口期时序，都没有覆盖到**标签本身**，所以全部无效。

### 本次修复
- 三个布局共 **5 处 `<View>` 分隔线全部改为 `ImageView` + background**，视觉完全不变，且落在 RemoteViews 官方支持的 View 白名单内（LinearLayout / TextView / ImageView）
- 发布 APK 已验证：四个部件布局的纯 `View` 元素数量均为 0
- 保留 v1.2.18 的改进：添加部件瞬间先上占位内容、2×2 与 4×1 只读轻量数据、4×3 的 TOP 3 排行单独加载并加 2.5 秒超时保护
- 布局注释里写明了该白名单约束，防止以后再犯

### 安装后注意
已经加到桌面的旧部件**不会自愈**：更新后请长按旧部件移除，再从桌面小组件选择器重新添加。三个尺寸名称分别为「留白 今日屏幕」「留白 概览条」「留白 使用排行」。

### 校验
- 文件：margin-1.2.20.apk
- 大小：3,805,134 B（3.6 MB）
- SHA-256：`11464264620b84c5359b2a640fbd1ff92a1e547ba0b16a414b768f31e5b1886a`
- 最低系统：Android 9.0（API 28）

完整更新日志见官网 https://margin-8ux.pages.dev
"""

r = api("POST", f"/repos/{REPO}/releases", {
    "tag_name": TAG,
    "target_commitish": SHA,
    "name": "Margin「留白」 v1.2.18",
    "body": notes,
    "draft": False,
    "prerelease": False,
})
print("release:", r.get("html_url"), r.get("id"))

upload_url = r["upload_url"].split("{")[0]
name = os.path.basename(APK)
req = urllib.request.Request(
    f"{upload_url}?name={name}",
    data=open(APK, "rb").read(),
    method="POST",
)
req.add_header("Authorization", f"Bearer {TOKEN}")
req.add_header("Accept", "application/vnd.github+json")
req.add_header("Content-Type", "application/vnd.android.package-archive")
req.add_header("User-Agent", "margin-release-bot")
with opener.open(req, timeout=600) as resp:
    asset = json.loads(resp.read())
print("asset:", asset.get("name"), asset.get("size"), asset.get("state"))
