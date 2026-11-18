#!/usr/bin/env bash
# =============================================================
# 压测环境重置 + 造数据（Issue #27）
#
# 做的事（顺序不能换）：
#   1. 重建压测活动 / 商品 / 库存（固定 id 999001），清掉上一轮订单
#   2. 清 Redis：库存键、活动详情缓存、下单幂等键、限流桶
#      —— 不清幂等键的话，第二轮会全部命中幂等返回旧订单，库存一点没扣，判定失真
#   3. 重新预热库存到 Redis（不预热，下单 Lua 返回 -1，一律报库存不足）
#   4. 注册并登录压测账号，产出 perf/.run/tokens.txt
#
# 用法：bash perf/seed.sh
# 可用环境变量覆盖：USERS / STOCK / DB_PORT / DB_PASSWORD / BASE
# =============================================================
set -euo pipefail
cd "$(dirname "$0")/.."

# ---------- 工具路径：优先用便携环境，找不到就靠 PATH ----------
pick() { # $1=便携路径 $2=命令名
  if [ -n "${3:-}" ]; then echo "$3"
  elif [ -x "$1" ]; then echo "$1"
  else echo "$2"; fi
}

MYSQL_BIN=$(pick "/e/PortableDev/apps/mysql-8.4.11-winx64/bin/mysql.exe" mysql "${MYSQL_BIN:-}")
REDIS_CLI=$(pick "/e/PortableDev/apps/redis-8.10.3/Redis-8.10.3-Windows-x64-msys2/redis-cli.exe" redis-cli "${REDIS_CLI:-}")
JAVA_BIN=$(pick "/e/PortableDev/apps/jdk-21.0.12.1+1/bin/java.exe" java "${JAVA_BIN:-}")

DB_HOST=${DB_HOST:-127.0.0.1}
DB_PORT=${DB_PORT:-3308}
DB_USER=${DB_USER:-root}
DB_PASSWORD=${DB_PASSWORD:-123456}
DB_NAME=${DB_NAME:-groupbuy}
USERS=${USERS:-1000}
STOCK=${STOCK:-1000}
AID=${AID:-999001}
BASE=${BASE:-http://127.0.0.1:8080}

echo "== 工具 =="
echo "  mysql : $MYSQL_BIN"
echo "  redis : $REDIS_CLI"
echo "  java  : $JAVA_BIN"
echo

# ---------- 1. 数据库 ----------
echo "[1/4] 重建压测活动（id=$AID，库存 $STOCK）"
"$MYSQL_BIN" -h"$DB_HOST" -P"$DB_PORT" -u"$DB_USER" -p"$DB_PASSWORD" "$DB_NAME" \
  < perf/sql/01-seed-activity.sql 2>/dev/null

if [ "$STOCK" != "1000" ]; then
  "$MYSQL_BIN" -h"$DB_HOST" -P"$DB_PORT" -u"$DB_USER" -p"$DB_PASSWORD" "$DB_NAME" \
    -e "UPDATE stock SET total=$STOCK, available=$STOCK, locked=0 WHERE goods_id=$AID;" 2>/dev/null
fi

# ---------- 2. 清 Redis ----------
echo "[2/4] 清 Redis 旧状态"
"$REDIS_CLI" DEL "groupbuy:stock:activity:$AID" "activity:detail:$AID" >/dev/null

IDEM_KEYS=$("$REDIS_CLI" --scan --pattern 'groupbuy:order:idem:*' || true)
if [ -n "$IDEM_KEYS" ]; then
  echo "$IDEM_KEYS" | tr -d '\r' | xargs -r "$REDIS_CLI" DEL >/dev/null
  echo "      已清 $(echo "$IDEM_KEYS" | wc -l) 个幂等键"
fi
RL_KEYS=$("$REDIS_CLI" --scan --pattern 'groupbuy:rl:*' || true)
if [ -n "$RL_KEYS" ]; then
  echo "$RL_KEYS" | tr -d '\r' | xargs -r "$REDIS_CLI" DEL >/dev/null
fi

# ---------- 3. 预热库存 ----------
echo "[3/4] 预热库存到 Redis：groupbuy:stock:activity:$AID = $STOCK"
"$REDIS_CLI" SET "groupbuy:stock:activity:$AID" "$STOCK" >/dev/null

# ---------- 4. 造账号 ----------
echo "[4/4] 注册 / 登录 $USERS 个压测账号"
"$JAVA_BIN" -Dbase="$BASE" -Dusers="$USERS" perf/SeedData.java

echo
echo "环境就绪。跑压测：bash perf/run-loadtest.sh"
