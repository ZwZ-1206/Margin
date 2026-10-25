# -*- coding: utf-8 -*-
"""创建 GitHub Release v1.2.17 并上传 APK（api.github.com + uploads.github.com）"""
import json
import os
import urllib.request

REPO = "ZwZ-1206/Margin"
BASE = "https://api.github.com"
TOKEN = os.environ.get("GH_TOKEN") or open("C:/Users/ZwZ/AppData/Local/Temp/gh_pat.txt").read().strip()
TAG = "v1.2.17"
SHA = "d9c88823e76f1a9b6e63f606ddeb62697e6b36a3"
APK = "C:/Code/Margin/offscreen-web/public/downloads/margin-1.2.17.apk"

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


notes = """## Margin「留白」 v1.2.17

**修复 OPPO / ColorOS 等 OEM 桌面添加小组件时提示「载入窗口小部件时出现问题」**

v1.2.16 只修了大尺寸部件的主题属性样式，但在该类机型上三个尺寸仍无法添加。该提示由**桌面启动器进程**抛出——RemoteViews 布局在启动器进程里 inflate 失败，或 App 进程在绑定窗口期崩溃，应用侧的异常捕获拦不住。本次把三个部件的布局全部退回到最保守的构造集：

- 布局只用 RemoteViews 官方支持的 View（LinearLayout / TextView / View / ImageView）与 API 1 属性；`marginVertical/Horizontal` 改回显式 `marginTop/Bottom/Start/End`
- `widget_info` 去掉 `previewLayout`、改为静态位图 `previewImage`——桌面选择器只解码图片，不会在启动器进程里 inflate 我们的布局
- 大部件 TOP 3 占比条从 `ProgressBar` 改为 `ImageView + ClipDrawable`（RemoteViews 标准反射动作驱动）
- 更新链路全面加固：取数 / 构建 / 推送任何一步失败都只降级为占位布局，App 进程不再可能崩在绑定窗口期

### 安装后注意
已经加到桌面的旧部件**不会自愈**：更新后请长按旧部件移除，再从桌面小组件选择器重新添加。三个尺寸名称分别为「留白 今日屏幕」「留白 概览条」「留白 使用排行」。

### 校验
- 文件：margin-1.2.17.apk
- 大小：3,805,152 B（3.6 MB）
- SHA-256：`b7a0a31a86e272b9659779ef889c8c040df237d684872acb796c06ddf5ddceb9`
- 最低系统：Android 9.0（API 28）

完整更新日志见官网 https://margin-8ux.pages.dev
"""

r = api("POST", f"/repos/{REPO}/releases", {
    "tag_name": TAG,
    "target_commitish": SHA,
    "name": "Margin「留白」 v1.2.17",
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
