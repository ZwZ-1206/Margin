# -*- coding: utf-8 -*-
"""创建 GitHub Release v1.2.22 并上传 APK（api.github.com + uploads.github.com）"""
import json
import os
import urllib.request

REPO = "ZwZ-1206/Margin"
BASE = "https://api.github.com"
TOKEN = os.environ.get("GH_TOKEN") or open("C:/Users/ZwZ/AppData/Local/Temp/gh_pat.txt").read().strip()
TAG = "v1.2.22"
SHA = "c37235465cac2ec477fe323e00edb34e397a5503"
APK = "C:/Code/Margin/offscreen-web/public/downloads/margin-1.2.22.apk"

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


notes = """## Margin「留白」 v1.2.22

**桌面小组件排版优化：2×2 拆两行、4×1 不再折行**

v1.2.20 修好载入问题、v1.2.21 排满 TOP 5 之后，剩下的就是排版本身：2×2 的屏幕时间一长就会顶出部件边界，4×1 的「6 小时 29 分钟」在窄列里折成两行、三列高低不齐。这次一并收拾干净。

### 本次更新
- **2×2 部件：今日屏幕时间拆成两行**——第一行小时、第二行分钟，长时长不再溢出部件边界；整小时时只显示小时行，不足 1 小时只显示分钟行
- **4×1 概览条：改用紧凑时长文案**（如「6小时29分」「45分」）并收紧字号与列宽，屏幕时间不再折行错位，三列恢复整齐
- **桌面选择器预览图同步重画**：2×2 与 4×1 的新排版在添加部件前就能看到
- 布局仍只使用 RemoteViews 白名单内的 View（LinearLayout / TextView / ImageView），不重蹈 v1.2.20 之前「载入窗口小部件时出现问题」的覆辙

### 安装后注意
已经加到桌面的旧 2×2 / 4×1 部件**不会自愈**：更新后请长按旧部件移除，再从桌面小组件选择器重新添加「留白 今日屏幕」「留白 概览条」。

### 校验
- 文件：margin-1.2.22.apk
- 大小：3,805,918 B（3.6 MB）
- SHA-256：`84606c0330a9378854e15c56ec414d7af7357d878e54f820e1ffec4006ed420a`
- 最低系统：Android 9.0（API 28）

完整更新日志见官网 https://margin-8ux.pages.dev
"""

r = api("POST", f"/repos/{REPO}/releases", {
    "tag_name": TAG,
    "target_commitish": SHA,
    "name": "Margin「留白」 v1.2.22",
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
