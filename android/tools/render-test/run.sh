#!/usr/bin/env bash
# 用法: ./run.sh [TestClass|TestClass#method]
# 环境变量: RENDER_OUT=PNG输出目录(默认 /tmp/render-out)  MVN_ARGS="额外 mvn 参数，如 -Dapp.exclude.extra=**/data/Library.java"
set -o pipefail; cd "$(dirname "$0")"
OUT="${RENDER_OUT:-/tmp/render-out}"; mkdir -p "$OUT"
# shellcheck disable=SC2086
mvn -o -q -B test -Drender.out="$OUT" $MVN_ARGS ${1:+-Dtest="$1" -Dsurefire.failIfNoSpecifiedTests=false} 2>&1 | grep -v 'Picked up JAVA_TOOL'
rc=${PIPESTATUS[0]}
echo "PNG -> $OUT"; ls -1t "$OUT"/*.png 2>/dev/null | head -20
exit $rc
