#!/usr/bin/env bash
# =============================================================
# 压测结果核对（Issue #27 验收三项：无超卖 / 订单数 / 无重复扣减）
# 用法：bash perf/verify.sh
# =============================================================
set -euo pipefail
cd "$(dirname "$0")/.."

MYSQL_BIN=${MYSQL_BIN:-}
if [ -z "$MYSQL_BIN" ]; then
  if [ -x "/e/PortableDev/apps/mysql-8.4.11-winx64/bin/mysql.exe" ]; then
    MYSQL_BIN="/e/PortableDev/apps/mysql-8.4.11-winx64/bin/mysql.exe"
  else
    MYSQL_BIN="mysql"
  fi
fi

DB_HOST=${DB_HOST:-127.0.0.1}
DB_PORT=${DB_PORT:-3308}
DB_USER=${DB_USER:-root}
DB_PASSWORD=${DB_PASSWORD:-123456}
DB_NAME=${DB_NAME:-groupbuy}

"$MYSQL_BIN" -h"$DB_HOST" -P"$DB_PORT" -u"$DB_USER" -p"$DB_PASSWORD" "$DB_NAME" \
  --table < perf/sql/02-verify.sql 2>/dev/null
