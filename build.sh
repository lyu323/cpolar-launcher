#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
OUT_DIR="${OUT_DIR:-$PWD/out}"
mkdir -p "$OUT_DIR"

# 本地工具链路径，全部可由环境变量覆盖；仓库不附带任何 SDK / 密钥 / 内核二进制
export JH="${JH:-/path/to/jdk17}"
export BT="${BT:-/path/to/android-build-tools}"
export AJ="${AJ:-/path/to/android-35/android.jar}"
export LD_LIBRARY_PATH="$BT/lib64:${LD_LIBRARY_PATH:-}"
export JAVA_HOME="$JH"
export PATH="$JH/bin:$PATH"

echo "== 清理 =="
rm -rf classes dexout gen res.zip base.apk work.apk aligned.apk
mkdir -p classes dexout gen

echo "== 生成图标 =="
python3 make_icon.py

echo "== aapt2 compile res =="
$BT/aapt2 compile --dir res -o res.zip

echo "== javac =="
$JH/bin/javac -encoding UTF-8 -source 8 -target 8 \
    -bootclasspath "$AJ" -cp "$AJ" -d classes $(find src -name '*.java')
echo "classes: $(find classes -name '*.class' | wc -l)"

echo "== d8 =="
$JH/bin/java -cp "$BT/lib/d8.jar" com.android.tools.r8.D8 \
    --lib "$AJ" --min-api 23 --output dexout $(find classes -name '*.class')
ls -lh dexout/classes.dex

echo "== aapt2 link =="
$BT/aapt2 link -o base.apk -I "$AJ" \
    --manifest AndroidManifest.xml \
    --min-sdk-version 23 --target-sdk-version 35 \
    --version-code 3 --version-name 1.2.0 \
    --java gen res.zip

WORK_ABS="$PWD/work.apk"
cp base.apk "$WORK_ABS"
zip -q -j "$WORK_ABS" dexout/classes.dex

echo "== 加入 cpolar 官方客户端 (stored, 不压缩) =="
# cpolar 客户端是官方发布的第三方闭源二进制，不随本仓库分发：
# 请自备对应 ABI 的 libcpolar.so，放到 $CPOLAR_LIBS_DIR/lib/<abi>/ 下。
STAGE="${CPOLAR_LIBS_DIR:-../cpolar-libs}"
if [ ! -d "$STAGE/lib" ]; then
    echo "缺少内核二进制：请设置 CPOLAR_LIBS_DIR 指向含 lib/<abi>/libcpolar.so 的目录"; exit 1
fi
(cd "$STAGE" && zip -q -0 -r "$WORK_ABS" lib)

echo "== zipalign =="
$BT/zipalign -f 4 "$WORK_ABS" aligned.apk

echo "== apksigner =="
# 签名密钥绝不随源码分发：自备 .jks，密码只从环境变量读取
KS_FILE="${KS_FILE:-$PWD/cpolar.jks}"
if [ -n "${KS_PASS:-}" ] && [ -f "$KS_FILE" ]; then
    $BT/apksigner sign \
        --ks "$KS_FILE" --ks-pass "pass:$KS_PASS" \
        --key-pass "pass:${KEY_PASS:-$KS_PASS}" \
        --v1-signing-enabled true --v2-signing-enabled true \
        --out "$OUT_DIR/CpolarLauncher-v1.2.0.apk" aligned.apk
    echo "== verify =="
    $BT/apksigner verify --verbose "$OUT_DIR/CpolarLauncher-v1.2.0.apk"
else
    echo "== 未配置 KS_PASS / keystore，输出未签名 APK（仅供本地调试） =="
    cp aligned.apk "$OUT_DIR/CpolarLauncher-v1.2.0-unsigned.apk"
fi
ls -lh "$OUT_DIR"
echo "== 完成 =="
