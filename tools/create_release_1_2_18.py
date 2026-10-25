# -*- coding: utf-8 -*-
"""创建 GitHub Release v1.2.18 并上传 APK（api.github.com + uploads.github.com）"""
import json
import os
import urllib.request

REPO = "ZwZ-1206/Margin"
BASE = "https://api.github.com"
TOKEN = os.environ.get("GH_TOKEN") or open("C:/Users/ZwZ/AppData/Local/Temp/gh_pat.txt").read().strip()
TAG = "v1.2.18"
SHA = "4c1951e348ab5c2ab113a6f4e1356387c31d230e"
APK = "C:/Code/Margin/offscreen-web/public/downloads/margin-1.2.18.apk"

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


notes = """## Margin「留白」 v1.2.18

**修复 OPPO / ColorOS 等 OEM 桌面添加小组件时提示「载入窗口小部件时出现问题」（根因修复）**

v1.2.16 / v1.2.17 改的是布局语法，但在该类机型上三个尺寸仍无法添加。真正的根因在**绑定窗口期**：厂商桌面在 `bindAppWidgetId` 之后会等待应用回传可渲染的 RemoteViews，等不及就弹这个错误——而 v1.2.15 起新增的 TOP 3 图标加载（PackageManager 取图标）冷启动时可能耗时数秒，且旧实现里 2×2 与 4×1 用不到排行却也白付这份开销。

本次修复：

- **绑定瞬间先上占位内容**：`onUpdate` 同步阶段推一份零 IO 的占位 RemoteViews，桌面在绑定瞬间就拿到可渲染内容，真实数据再由协程异步补上
- **取数按尺寸拆分**：2×2 与 4×1 只读轻量数据（Room + 使用情况），不再白白承担图标加载开销；只有 4×3 单独加载 TOP 3
- **超时保护**：TOP 3 取数加 2.5 秒超时，失败也保持部件可用（排行暂为空），不会拖住桌面
- **图标查询去重**：TOP 3 共享同一个应用标签解析器，不再给每个应用各建一份；标签取不到时回退为包名

### 安装后注意
已经加到桌面的旧部件**不会自愈**：更新后请长按旧部件移除，再从桌面小组件选择器重新添加。三个尺寸名称分别为「留白 今日屏幕」「留白 概览条」「留白 使用排行」。

### 校验
- 文件：margin-1.2.18.apk
- 大小：3,805,148 B（3.6 MB）
- SHA-256：`bc479c629b70c688a05accd1bd3dcbc85767017b362e658aabe341a4c3b4a706`
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
