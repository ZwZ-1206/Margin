"""通过 GitHub Git Data API 把本地未推送的提交逐个推到远端。

用途：当前代理对 github.com 返回 502 而 api.github.com 正常时，用它绕过 push。

**注意副作用**：API 生成的 commit 与本地 commit 内容完全相同（文件树逐 blob 一致），
但 commit 对象本身不同，远端会得到新的 sha。因此推送后本地分支与远端分叉，
网络恢复后需要对齐一次：

    git fetch origin main && git reset --hard origin/main

用法：
    GH_TOKEN=... START_SHA=<推送前的远端 sha> SKIP=<已推进的提交数> python tools/push_via_api.py
"""
import base64
import json
import os
import subprocess
import sys
import urllib.request

REPO = "ZwZ-1206/Margin"
BASE = "https://api.github.com"
TOKEN = os.environ["GH_TOKEN"]
BRANCH = "main"
CWD = "C:/Code/Margin"

# 默认直连；需要走代理时设 GIT_PROXY=http://127.0.0.1:PORT
PROXY = os.environ.get("GIT_PROXY", "")
opener = urllib.request.build_opener(
    urllib.request.ProxyHandler({"http": PROXY, "https": PROXY}) if PROXY
    else urllib.request.ProxyHandler({})
)


def api(method, path, payload=None):
    url = path if path.startswith("http") else BASE + path
    data = json.dumps(payload).encode() if payload is not None else None
    req = urllib.request.Request(url, data=data, method=method)
    req.add_header("Authorization", f"Bearer {TOKEN}")
    req.add_header("Accept", "application/vnd.github+json")
    req.add_header("Content-Type", "application/json")
    req.add_header("User-Agent", "margin-release-bot")
    with opener.open(req, timeout=180) as resp:
        body = resp.read()
        return json.loads(body) if body else {}


def git(*args, text=True):
    out = subprocess.run(
        ["git", "-c", "core.quotepath=false", *args],
        cwd=CWD, capture_output=True, check=True,
    ).stdout
    return out.decode().strip() if text else out


remote = api("GET", f"/repos/{REPO}/git/ref/heads/{BRANCH}")
remote_sha = remote["object"]["sha"]
print(f"远端 {BRANCH} = {remote_sha}")

# 起点固定为本次推送开始前的远端 sha（本地一定存在该对象）；
# SKIP 用于中断后续跑时跳过已经推上去的几个提交。
START = os.environ.get("START_SHA", remote_sha)
SKIP = int(os.environ.get("SKIP", "0"))
all_commits = [s for s in git("rev-list", "--reverse", f"{START}..HEAD").splitlines() if s]
pending = all_commits[SKIP:]
print(f"本地待推 {len(all_commits)} 个，跳过前 {SKIP} 个，实际处理 {len(pending)} 个")
if not pending:
    print("没有待推送的提交")
    sys.exit(0)

for sha in pending:
    subject = git("log", "-1", "--format=%s", sha)
    message = git("log", "-1", "--format=%B", sha)
    author = git("log", "-1", "--format=%an", sha)
    author_mail = git("log", "-1", "--format=%ae", sha)
    author_date = git("log", "-1", "--format=%aI", sha)
    committer = git("log", "-1", "--format=%cn", sha)
    committer_mail = git("log", "-1", "--format=%ce", sha)
    committer_date = git("log", "-1", "--format=%cI", sha)

    parent_commit = api("GET", f"/repos/{REPO}/git/commits/{remote_sha}")
    base_tree = parent_commit["tree"]["sha"]

    entries = git("diff-tree", "-r", "--no-commit-id", "--name-status", sha).splitlines()
    tree_items = []
    for line in entries:
        parts = line.split("\t")
        status, path = parts[0], parts[-1]
        if status.startswith("D"):
            tree_items.append({"path": path, "mode": "100644", "type": "blob", "sha": None})
            print(f"  - {path}")
            continue
        raw = git("show", f"{sha}:{path}", text=False)
        mode = git("ls-tree", sha, path).split()[0]
        blob = api("POST", f"/repos/{REPO}/git/blobs", {
            "content": base64.b64encode(raw).decode(),
            "encoding": "base64",
        })
        tree_items.append({"path": path, "mode": mode, "type": "blob", "sha": blob["sha"]})
        print(f"  + {path} ({len(raw)} B)")

    new_tree = api("POST", f"/repos/{REPO}/git/trees", {"base_tree": base_tree, "tree": tree_items})
    new_commit = api("POST", f"/repos/{REPO}/git/commits", {
        "message": message,
        "tree": new_tree["sha"],
        "parents": [remote_sha],
        "author": {"name": author, "email": author_mail, "date": author_date},
        "committer": {"name": committer, "email": committer_mail, "date": committer_date},
    })
    same = "✓ sha 一致" if new_commit["sha"] == sha else f"≠ 本地 {sha[:10]}"
    print(f"提交 {subject}\n  远端 {new_commit['sha'][:10]} {same}")

    api("PATCH", f"/repos/{REPO}/git/refs/heads/{BRANCH}", {
        "sha": new_commit["sha"],
        "force": False,
    })
    remote_sha = new_commit["sha"]

print(f"\n完成，{BRANCH} = {remote_sha}")
