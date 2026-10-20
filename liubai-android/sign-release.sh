#!/usr/bin/env bash
# 重新对 Release APK 执行 v1 + v2 双签名（关闭 v3/v4）
#
# 为什么需要：国产 ROM（ColorOS / MIUI 等）从浏览器或文件管理器安装时，
# 仅 v2 或 v3 签名会被判定为「安装包已损坏」；v1 + v2 是最兼容的组合。
# 另外 AGP 的 enableV1Signing 在部分版本不生效，故用 apksigner 手动签。
#
# 用法：在 assembleRelease 之后执行 ./sign-release.sh
set -euo pipefail

cd "$(dirname "$0")"

# apksigner 需要 JAVA_HOME；未设置时自动探测常见安装位置
if [ -z "${JAVA_HOME:-}" ]; then
  for cand in "C:/Apps/JDK-17" "C:/Program Files/Android/Android Studio/jbr" "C:/Program Files/Java/jdk-17"; do
    if [ -x "$cand/bin/java.exe" ]; then export JAVA_HOME="$cand"; break; fi
  done
fi
if [ -z "${JAVA_HOME:-}" ]; then
  echo "ERROR: 未找到 JDK 17，请设置 JAVA_HOME" >&2
  exit 1
fi

APK=app/build/outputs/apk/release/app-release.apk
SDK_DIR=$(grep '^sdk.dir=' local.properties | cut -d= -f2)
APKSIGNER="$SDK_DIR/build-tools/37.0.0/apksigner.bat"
ZIPALIGN="$SDK_DIR/build-tools/37.0.0/zipalign.exe"
KS=keystore/margin-release.jks
ALIAS=margin
PASS=liubai2026

"$ZIPALIGN" -p -f 4 "$APK" "$APK.aligned"
"$APKSIGNER" sign \
  --ks "$KS" --ks-key-alias "$ALIAS" \
  --ks-pass "pass:$PASS" --key-pass "pass:$PASS" \
  --min-sdk-version 28 \
  --v1-signing-enabled=true --v2-signing-enabled=true \
  --v3-signing-enabled=false --v4-signing-enabled=false \
  --out "$APK.dual" \
  "$APK.aligned"
mv "$APK.dual" "$APK"
rm -f "$APK.aligned"

echo "已重新签名（v1 + v2）：$APK"
sha256sum "$APK"
