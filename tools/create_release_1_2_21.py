# -*- coding: utf-8 -*-
"""创建 GitHub Release v1.2.18 并上传 APK（api.github.com + uploads.github.com）"""
import json
import os
import urllib.request

REPO = "ZwZ-1206/Margin"
BASE = "https://api.github.com"
TOKEN = os.environ.get("GH_TOKEN") or open("C:/Users/ZwZ/AppData/Local/Temp/gh_pat.txt").read().strip()
TAG = "v1.2.21"
SHA = "5216d0da47c2c39a38e14a43d6c8bb5445b94242"
APK = "C:/Code/Margin/offscreen-web/public/downloads/margin-1.2.21.apk"

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


notes = """## Margin「留白」 v1.2.21

**4×3 大部件使用排行：TOP 3 → TOP 5**

v1.2.20 修复了小组件无法载入的问题后，大部件下方还留着一大片空白——4×3 的版面本来就有富余，只排三个应用既浪费空间也说不过去。现在一次排满今日耗时最多的五个应用。

### 本次更新
- **新增第 4、5 行排行**：图标、应用名、时长、相对第一名的占比条一应俱全；第 4、5 行同样支持点击直接打开对应应用
- **图标取数超时从 2.5 秒放宽到 4 秒**：多加载两个图标需要更宽裕的时间窗口；超时仍只降级为「无排行」，部件本身照常可用，不影响桌面
- **桌面选择器预览图同步重画**：5 行重新排布，保持 4×3 比例
- 布局仍只使用 RemoteViews 白名单内的 View（LinearLayout / TextView / ImageView）——这正是 v1.2.20 修好的那个坑，新增行没有重蹈覆辙

### 安装后注意
已经加到桌面的旧大部件**不会自愈**：更新后请长按旧部件移除，再从桌面小组件选择器重新添加「留白 使用排行」。

### 校验
- 文件：margin-1.2.21.apk
- 大小：3,807,960 B（3.6 MB）
- SHA-256：`4225b9f462f1fc74187132409ed619e86c4a4bd80980836b694cb53cc595ae8a`
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
