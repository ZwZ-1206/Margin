# -*- coding: utf-8 -*-
"""创建 GitHub Release v1.2.18 并上传 APK（api.github.com + uploads.github.com）"""
import json
import os
import urllib.request

REPO = "ZwZ-1206/Margin"
BASE = "https://api.github.com"
TOKEN = os.environ.get("GH_TOKEN") or open("C:/Users/ZwZ/AppData/Local/Temp/gh_pat.txt").read().strip()
TAG = "v1.2.19"
SHA = "0f9a5683fd154df7921b863187cbfca3f8220974"
APK = "C:/Code/Margin/offscreen-web/public/downloads/margin-1.2.19.apk"

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


notes = """## Margin「留白」 v1.2.19（诊断版 / prerelease）

**这是一个临时诊断版本，不含真实的屏幕时长数据。** 用于定位 OPPO / ColorOS 上添加小组件提示「载入窗口小部件时出现问题」的真实原因。

v1.2.16 / v1.2.17 / v1.2.18 三个正式版均未修好该问题。用户截图显示三个尺寸的部件**已成功绑定到桌面**，只是内容加载失败——说明故障发生在桌面进程侧，应用侧的异常捕获、取数优化、布局保守化都无法覆盖。

本版本不再推真实数据，而是把小组件的执行链路与真实异常**直接显示在部件卡片上**：

- 若卡片显示 `DIAG-PENDING`：系统根本没有调用我们的小组件更新回调（桌面侧行为或系统自启动 / 冻结限制）
- 若卡片显示 `DIAG` 开头的执行信息（含 ids、包名、可达性）：更新链路正常，问题出在数据或内容构建
- 若卡片仍显示「载入窗口小部件时出现问题」：initialLayout 在桌面进程里 inflate 失败

同时会把同样的诊断信息落盘到应用的 `Android/data/com.liubai.app/files/widget_diag.log`。

### 使用方式
安装后删掉桌面上旧的「留白」部件，重新添加，然后把卡片上显示的文字截图反馈即可。定位到根因后会发布正式修复版并恢复全部功能。

### 校验
- 文件：margin-1.2.19.apk
- 大小：3,805,156 B（3.6 MB）
- SHA-256：`131b8c411f2c38933ef268d45456e07af60edc65f2921814ae703027574b4746`
- 最低系统：Android 9.0（API 28）
"""

r = api("POST", f"/repos/{REPO}/releases", {
    "tag_name": TAG,
    "target_commitish": SHA,
    "name": "Margin「留白」 v1.2.18",
    "body": notes,
    "draft": False,
    "prerelease": True,
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
