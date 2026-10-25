"""github.com 不通时，用 commit-tree 在本地复现远端 commit，对齐本地与远端 sha。

背景：tools/push_via_api.py 通过 Git Data API 推送时，message 会被去掉尾换行，
导致远端 commit 与本地 commit 的 sha 不同（内容逐 blob 相同，树一致）。

复现配方（2026-10-24 实测命中）：
  - tree / parent：用远端 commit 的（parent 链式取上一个复现结果）
  - author / committer：名字邮箱用本地 commit 的，日期用本地 %aI / %cI 原串
    （GitHub API 会把日期显示成 UTC，但对象里保留原 +0800 偏移）
  - message：用 API 返回值，**不加尾换行**
"""
import json
import os
import subprocess
import urllib.request

REPO = "ZwZ-1206/Margin"
BASE = "https://api.github.com"
TOKEN = os.environ.get("GH_TOKEN")
CWD = "C:/Code/Margin"
START = os.environ.get("START_SHA", "b2355ca")  # 推送前的远端 sha，本地必然存在


def api(path):
    req = urllib.request.Request(BASE + path)
    req.add_header("Authorization", f"Bearer {TOKEN}")
    req.add_header("Accept", "application/vnd.github+json")
    req.add_header("User-Agent", "margin-release-bot")
    with urllib.request.urlopen(req, timeout=60) as r:
        return json.loads(r.read())


def run(args, env=None, stdin=None):
    return subprocess.run(["git", *args], cwd=CWD, capture_output=True,
                          check=True, env=env, input=stdin)


def sh(*args):
    return run(list(args)).stdout.decode().strip()


# 1. 远端：从 ref 沿 parent 收集本次推送产生的 commit（正序）
chain = []
sha = api(f"/repos/{REPO}/git/ref/heads/main")["object"]["sha"]
while True:
    c = api(f"/repos/{REPO}/git/commits/{sha}")
    chain.append(c)
    if not c["parents"] or c["parents"][0]["sha"].startswith(START):
        break
    sha = c["parents"][0]["sha"]
chain.reverse()

# 2. 本地：同一区间内的提交（正序），与远端一一对应
local = [s for s in sh("rev-list", "--reverse", f"{START}..HEAD").splitlines() if s]
print(f"远端 {len(chain)} 个，本地 {len(local)} 个")
if len(chain) != len(local):
    print("数量不一致，放弃自动对齐（请人工核对）")
    raise SystemExit(1)

# 3. 逐个复现
parent = START
matched = []
for remote_c, local_sha in zip(chain, local):
    env = dict(os.environ)
    env.update({
        "GIT_AUTHOR_NAME": sh("log", "-1", "--format=%an", local_sha),
        "GIT_AUTHOR_EMAIL": sh("log", "-1", "--format=%ae", local_sha),
        "GIT_AUTHOR_DATE": sh("log", "-1", "--format=%aI", local_sha),
        "GIT_COMMITTER_NAME": sh("log", "-1", "--format=%cn", local_sha),
        "GIT_COMMITTER_EMAIL": sh("log", "-1", "--format=%ce", local_sha),
        "GIT_COMMITTER_DATE": sh("log", "-1", "--format=%cI", local_sha),
    })
    out = run(["commit-tree", remote_c["tree"]["sha"], "-p", parent],
              env=env, stdin=remote_c["message"].encode())
    got = out.stdout.decode().strip()
    if got != remote_c["sha"]:
        print(f"✗ 未命中 {remote_c['sha'][:10]} {remote_c['message'].splitlines()[0]}")
        raise SystemExit(1)
    print(f"✓ {got[:10]} {remote_c['message'].splitlines()[0]}")
    matched.append(got)
    parent = got

# 4. 对齐本地引用
head = matched[-1]
run(["update-ref", "refs/heads/main", head])
run(["update-ref", "refs/remotes/origin/main", head])
print(f"\n本地 main 已对齐到 {head}")
