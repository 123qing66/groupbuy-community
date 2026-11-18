-- =============================================================
-- 压测数据准备（Issue #27：200 并发 1000 库存无超卖）
--
-- 为什么用 SQL 造数据，而不是走 POST /api/activities 开团：
--   开团要求"当前用户是团长且已绑定自提点"，而 leader 行的创建和
--   自提点绑定属于 A 的模块（#11/#12），压测不该被那条链路阻塞。
--   本文件固定 id（999001），让每次压测的活动/商品/库存完全可复现，
--   也方便 02-verify.sql 直接按 id 核对。
--
-- 可重复执行：每次都先清掉上一次的压测痕迹。
-- =============================================================

-- 角色种子：全新的库（data.sql 只有 SELECT 1）里 role 表是空的，
-- 不先插角色，注册接口会报「角色不存在」。
INSERT IGNORE INTO `role` (`role_code`, `role_name`) VALUES
  ('ROLE_RESIDENT', '居民'),
  ('ROLE_LEADER',   '团长'),
  ('ROLE_ADMIN',    '平台管理员');

-- 清理上一次压测（顺序：先订单，再库存/商品，最后活动）
DELETE FROM `order_info` WHERE `activity_id` = 999001;
DELETE FROM `stock`      WHERE `goods_id`   = 999001;
DELETE FROM `goods`      WHERE `id`         = 999001;
DELETE FROM `activity`   WHERE `id`         = 999001;
DELETE FROM `pickup_point` WHERE `id`       = 999001;

-- 自提点（活动 point_id 非空，给一个真实的点，避免详情接口取不到）
INSERT INTO `pickup_point` (`id`, `name`, `address`, `contact`, `phone`, `status`) VALUES
  (999001, '压测自提点', '压测地址（不入库真实位置）', '压测联系人', '13800000000', 1);

-- 活动：直接置为「进行中」1，时间窗覆盖当前时刻
--   buy_limit = 1  → 每人限购 1 份，保证 1 个用户最多贡献 1 单
--   target_num = 1000 → 成团目标与库存对齐，方便后续 #23 统计口径
--   start_time < now < end_time，否则下单会报 10002（活动未在进行中）
INSERT INTO `activity`
  (`id`, `title`, `leader_id`, `point_id`, `target_num`, `current_num`,
   `buy_limit`, `status`, `start_time`, `end_time`, `version`)
VALUES
  (999001, 'PERF-压测活动-200并发1000库存', 999001, 999001, 1000, 0,
   1, 1, NOW() - INTERVAL 1 HOUR, NOW() + INTERVAL 2 HOUR, 0);

-- 商品（下单要求商品属于该活动且 status=1 上架）
INSERT INTO `goods`
  (`id`, `activity_id`, `name`, `cover`, `origin_price`, `group_price`, `status`)
VALUES
  (999001, 999001, '压测商品-1份装', NULL, 10.00, 9.90, 1);

-- 库存：total = available = 1000，locked = 0
INSERT INTO `stock` (`goods_id`, `total`, `available`, `locked`, `version`) VALUES
  (999001, 1000, 1000, 0, 0);

-- 自检
SELECT
  a.id            AS activity_id,
  a.status        AS activity_status,
  a.buy_limit     AS buy_limit,
  g.id            AS goods_id,
  s.total         AS stock_total,
  s.available     AS stock_available
FROM `activity` a
JOIN `goods` g ON g.activity_id = a.id
JOIN `stock` s ON s.goods_id = g.id
WHERE a.id = 999001;
