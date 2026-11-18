-- =============================================================
-- 压测结果核对（Issue #27 验收要点）
--
-- 跑法：mysql -h127.0.0.1 -P3308 -uroot -p groupbuy < perf/sql/02-verify.sql
--
-- 判定标准（压测参数：库存 1000、并发 200、总请求 1000、每人限购 1）：
--   ① 超卖：sold_num 必须 = 1000，stock_available 必须 = 0，且不允许出现负数
--   ② 订单数：order_count 必须 = 1000，distinct_users 必须 = 1000（一人一单）
--   ③ 重复扣减：dup_user_count 必须 = 0（uk_user_activity 兜底）
--               order_no 唯一数必须 = 订单数（uk_order_no 兜底）
-- =============================================================

SELECT '=== ① 库存与销量守恒 ===' AS `--`;

SELECT
  s.total                                                   AS stock_total,
  s.available                                               AS stock_available,
  s.locked                                                  AS stock_locked,
  (s.total - s.available)                                   AS sold_num,
  IFNULL((SELECT SUM(num) FROM order_info WHERE activity_id = 999001), 0) AS order_num_sum,
  CASE
    WHEN s.available < 0                                              THEN '❌ 超卖（可用库存为负）'
    WHEN s.total - s.available <> IFNULL((SELECT SUM(num) FROM order_info WHERE activity_id = 999001), 0)
                                                                      THEN '❌ 库存与订单不守恒'
    WHEN s.available = 0                                              THEN '✅ 库存刚好清零，无超卖'
    ELSE '⚠️ 还有剩余库存（本次请求数小于库存，属正常）'
  END                                                       AS verdict
FROM stock s
WHERE s.goods_id = 999001;

SELECT '=== ② 订单数与人数 ===' AS `--`;

SELECT
  COUNT(*)                        AS order_count,
  COUNT(DISTINCT user_id)         AS distinct_users,
  COUNT(DISTINCT order_no)        AS distinct_order_no,
  SUM(num)                        AS total_num,
  MIN(create_time)                AS first_order_time,
  MAX(create_time)                AS last_order_time
FROM order_info
WHERE activity_id = 999001;

SELECT '=== ③ 重复下单 / 重复扣减 ===' AS `--`;

SELECT COUNT(*) AS dup_user_count
FROM (
  SELECT user_id
  FROM order_info
  WHERE activity_id = 999001
  GROUP BY user_id
  HAVING COUNT(*) > 1
) t;

-- 期望 0 行：有输出说明同一用户生成了多单（幂等失效）
SELECT user_id, COUNT(*) AS cnt
FROM order_info
WHERE activity_id = 999001
GROUP BY user_id
HAVING COUNT(*) > 1
LIMIT 10;

SELECT '=== ④ 订单号重复（期望 0 行）===' AS `--`;

SELECT order_no, COUNT(*) AS cnt
FROM order_info
WHERE activity_id = 999001
GROUP BY order_no
HAVING COUNT(*) > 1;

SELECT '=== ⑤ 订单状态分布（本次压测应全部为 0 待支付）===' AS `--`;

SELECT status, COUNT(*) AS cnt
FROM order_info
WHERE activity_id = 999001
GROUP BY status
ORDER BY status;
