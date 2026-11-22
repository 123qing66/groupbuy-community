#!/usr/bin/env bash
# =============================================================
# 跑压测（Issue #27）：默认 200 并发 / 1000 总请求 / 每单 1 份
#
# 前置：bash perf/seed.sh 已经跑过（活动、库存、Redis、token 都就绪）
# 用法：bash perf/run-loadtest.sh
#       CONCURRENCY=200 TOTAL=1000 bash perf/run-loadtest.sh
# =============================================================
set -euo pipefail
cd "$(dirname "$0")/.."

JAVA_BIN=${JAVA_BIN:-}
if [ -z "$JAVA_BIN" ]; then
  if [ -x "/e/PortableDev/apps/jdk-21.0.12.1+1/bin/java.exe" ]; then
    JAVA_BIN="/e/PortableDev/apps/jdk-21.0.12.1+1/bin/java.exe"
  else
    JAVA_BIN="java"
  fi
fi

BASE=${BASE:-http://127.0.0.1:8080}
CONCURRENCY=${CONCURRENCY:-200}
TOTAL=${TOTAL:-1000}
AID=${AID:-999001}

"$JAVA_BIN" -Dbase="$BASE" -Dconcurrency="$CONCURRENCY" -Dtotal="$TOTAL" \
  -Dactivity="$AID" -Dgoods="$AID" perf/LoadTest.java
