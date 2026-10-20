#!/usr/bin/env bash
# Margin「留白」· 一键发版：构建 → 部署 Cloudflare Pages → 线上校验
#
# 依赖：本机已 `npx wrangler login`（登录态存在 %APPDATA%/xdg.config/.wrangler）
# 用法：./deploy.sh            ；./deploy.sh --no-verify 跳过线上校验
set -euo pipefail

ROOT="$(cd "$(dirname "$0")" && pwd)"
cd "$ROOT/offscreen-web"

echo "▶ 1/4 构建"
npm run build

echo "▶ 2/4 部署到 Cloudflare Pages (project: margin)"
npx --yes wrangler pages deploy dist \
  --project-name=margin \
  --branch=main \
  --commit-dirty=true

if [[ "${1:-}" == "--no-verify" ]]; then
  echo "▶ 跳过线上校验"
  exit 0
fi

BASE="https://margin-8ux.pages.dev"
echo "▶ 3/4 校验版本清单"
curl -s --noproxy '*' "$BASE/downloads/version.json?t=$(date +%s)" | head -c 200
echo

echo "▶ 4/4 校验 APK 完整性"
VER=$(curl -s --noproxy '*' "$BASE/downloads/version.json?t=$(date +%s)" \
  | grep -o '"version": *"[^"]*"' | head -1 | cut -d'"' -f4)
LOCAL_SHA=$(sha256sum "public/downloads/margin-$VER.apk" | cut -d' ' -f1)
REMOTE_SHA=$(curl -sL --noproxy '*' "$BASE/downloads/margin-$VER.apk" | sha256sum | cut -d' ' -f1)
echo "本地 $LOCAL_SHA"
echo "线上 $REMOTE_SHA"
[[ "$LOCAL_SHA" == "$REMOTE_SHA" ]] && echo "✅ $VER 已上线" || { echo "❌ 校验失败"; exit 1; }
