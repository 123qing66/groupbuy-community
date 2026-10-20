-- =============================================================
-- 社区团购拼团系统 —— 数据库建表脚本
-- 对应 Issue #3（Day3 上午完成，Day3 下午冻结接口时使用）
--
-- 环境：MySQL 8.0 / InnoDB / utf8mb4
-- 执行方式（CI 里就是这两行）：
--   mysql -uroot -proot groupbuy < src/main/resources/db/schema.sql
--   mysql -uroot -proot groupbuy < src/main/resources/db/data.sql
--
-- 三条硬规矩：
--   1. 每张表前写注释说明用途；每个索引必须写清楚"为什么建"（考核点）。
--   2. 开头统一 DROP TABLE IF EXISTS，保证脚本可重复执行、CI 可反复跑。
--   3. 表名避开 MySQL 保留字 —— 订单表叫 order_info 而不是 order，
--      否则每个 SQL 都得写反引号，MyBatis 里也容易踩坑。
--
-- 为什么不加物理外键（FOREIGN KEY）：
--   高并发下单链路里，外键会在每次 INSERT/UPDATE 时触发父表行锁校验，
--   等于给热点表（activity / stock）额外加锁，直接拖垮抢购性能。
--   这里用"逻辑外键 + 索引"替代，依赖关系靠代码事务保证，
--   同时避免删表顺序、数据迁移、后续分库的麻烦。
-- =============================================================

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- =============================================================
-- 一、用户与权限（D 负责 #10）
-- =============================================================

-- 用户表：居民、团长、平台管理员共用一张表，具体身份看 user_role
DROP TABLE IF EXISTS `user`;
CREATE TABLE `user` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `username`    VARCHAR(64)  NOT NULL                COMMENT '登录名，唯一',
    `password`    VARCHAR(128) NOT NULL                COMMENT 'BCrypt 加密后的密码，绝不明文',
    `real_name`   VARCHAR(64)  DEFAULT NULL            COMMENT '真实姓名',
    `phone`       VARCHAR(20)  DEFAULT NULL            COMMENT '手机号',
    `status`      TINYINT      NOT NULL DEFAULT 1      COMMENT '状态：1正常 0禁用',
    `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    -- 为什么建：登录时按 username 查用户，是全系统最高频的单点查询；
    --          加唯一索引既提速，又从数据库层面杜绝重名注册。
    UNIQUE KEY `uk_username` (`username`),
    -- 为什么建：后台按手机号检索用户（团长联系居民），避免全表扫描。
    KEY `idx_phone` (`phone`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户表';

-- 角色表：ROLE_RESIDENT 居民 / ROLE_LEADER 团长 / ROLE_ADMIN 管理员
DROP TABLE IF EXISTS `role`;
CREATE TABLE `role` (
    `id`          BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    `role_code`   VARCHAR(64) NOT NULL                COMMENT '角色编码，如 ROLE_LEADER',
    `role_name`   VARCHAR(64) NOT NULL                COMMENT '角色名称，如 团长',
    `create_time` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    -- 为什么建：鉴权时按 role_code 反查角色，且保证编码不重复。
    UNIQUE KEY `uk_role_code` (`role_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='角色表';

-- 用户-角色关联表：一个人可以有多个角色（比如管理员兼团长）
DROP TABLE IF EXISTS `user_role`;
CREATE TABLE `user_role` (
    `id`      BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    `user_id` BIGINT NOT NULL                COMMENT '用户 id',
    `role_id` BIGINT NOT NULL                COMMENT '角色 id',
    PRIMARY KEY (`id`),
    -- 为什么建：防止同一用户被重复授予同一角色。
    UNIQUE KEY `uk_user_role` (`user_id`, `role_id`),
    -- 为什么建：按角色查人（列出全部团长）。
    KEY `idx_role_id` (`role_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户角色关联表';

-- =============================================================
-- 二、自提点与团长（A 负责 #9）
-- =============================================================

-- 自提点：居民下单后去这里提货
DROP TABLE IF EXISTS `pickup_point`;
CREATE TABLE `pickup_point` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `name`        VARCHAR(128) NOT NULL                COMMENT '自提点名称',
    `address`     VARCHAR(255) DEFAULT NULL            COMMENT '详细地址',
    `contact`     VARCHAR(64)  DEFAULT NULL            COMMENT '联系人',
    `phone`       VARCHAR(20)  DEFAULT NULL            COMMENT '联系电话',
    `status`      TINYINT      NOT NULL DEFAULT 1      COMMENT '状态：1启用 0停用',
    `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    -- 为什么建：列表页只展示启用的自提点，且按名称排序，复合索引覆盖查询+排序。
    KEY `idx_status_name` (`status`, `name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='自提点表';

-- 团长表：团长是 user 的扩展资料，且一个自提点只能有一个团长
DROP TABLE IF EXISTS `leader`;
CREATE TABLE `leader` (
    `id`          BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    `user_id`     BIGINT      NOT NULL                COMMENT '关联 user.id',
    `point_id`    BIGINT      DEFAULT NULL            COMMENT '绑定的自提点 id（一对一）',
    `community`   VARCHAR(128) DEFAULT NULL           COMMENT '所在小区',
    `status`      TINYINT     NOT NULL DEFAULT 1      COMMENT '状态：1在职 0停用',
    `create_time` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    -- 为什么建：一个人只能当一次团长，重复插入直接报错拦截。
    UNIQUE KEY `uk_user_id` (`user_id`),
    -- 为什么建：自提点和团长是一对一，唯一索引保证不会被两个团长抢同一个点。
    UNIQUE KEY `uk_point_id` (`point_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='团长表';

-- =============================================================
-- 三、团购活动与商品（A 负责 #5 #6 #7）
-- =============================================================

-- 团购活动：一个活动 = 一个团（团长开团），状态由时间和人数共同驱动
-- 状态：0待开始 1进行中 2已成团 3已失败 4已下架
DROP TABLE IF EXISTS `activity`;
CREATE TABLE `activity` (
    `id`          BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    `title`       VARCHAR(128)  NOT NULL                COMMENT '活动标题',
    `leader_id`   BIGINT        NOT NULL                COMMENT '开团团长 id',
    `point_id`    BIGINT        NOT NULL                COMMENT '自提点 id',
    `target_num`  INT           NOT NULL                COMMENT '成团目标人数',
    `current_num` INT           NOT NULL DEFAULT 0      COMMENT '当前已参团（已支付）人数',
    `buy_limit`   INT           NOT NULL DEFAULT 1      COMMENT '每人限购份数',
    `status`      TINYINT       NOT NULL DEFAULT 0      COMMENT '0待开始 1进行中 2已成团 3已失败 4已下架',
    `start_time`  DATETIME      NOT NULL                COMMENT '活动开始时间',
    `end_time`    DATETIME      NOT NULL                COMMENT '活动结束时间',
    `version`     INT           NOT NULL DEFAULT 0      COMMENT '乐观锁版本号（状态机并发转移用）',
    `create_time` DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    -- 为什么建：活动列表页的核心查询就是"按状态筛选 + 按开始时间倒序分页"（接口 #3），
    --          复合索引的字段顺序与 WHERE + ORDER BY 完全对齐，避免 filesort。
    KEY `idx_status_start` (`status`, `start_time`),
    -- 为什么建：团长后台"我开的活动"（接口 #5）+ 状态转移时按团长按人查。
    KEY `idx_leader_status` (`leader_id`, `status`),
    -- 为什么建：到期扫描任务（#22）要捞"已结束但还没成团/失败"的活动，
    --          用 end_time 做范围扫描，不加索引就是全表扫。
    KEY `idx_end_status` (`end_time`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='团购活动表';

-- 商品表：挂在活动下的商品（一个活动可以有多个商品）
DROP TABLE IF EXISTS `goods`;
CREATE TABLE `goods` (
    `id`           BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    `activity_id`  BIGINT        NOT NULL                COMMENT '所属活动 id',
    `name`         VARCHAR(128)  NOT NULL                COMMENT '商品名称',
    `cover`        VARCHAR(255)  DEFAULT NULL            COMMENT '封面图 URL',
    `origin_price` DECIMAL(10,2) NOT NULL                COMMENT '原价',
    `group_price`  DECIMAL(10,2) NOT NULL                COMMENT '团购价',
    `status`       TINYINT       NOT NULL DEFAULT 1     COMMENT '状态：1上架 0下架',
    `create_time`  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    -- 为什么建：活动详情（接口 #4）要一次性拉出该活动的全部上架商品，
    --          用 (activity_id, status) 覆盖"按活动查 + 过滤下架"。
    KEY `idx_activity_status` (`activity_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='商品表';

-- 库存表：单独拆出来，是为了让"扣库存"这一行变成热点行，
--         CAS 和 Redis 预扣都以它为唯一落点，方便压测取证（验收要点①）
DROP TABLE IF EXISTS `stock`;
CREATE TABLE `stock` (
    `id`         BIGINT  NOT NULL AUTO_INCREMENT COMMENT '主键',
    `goods_id`   BIGINT  NOT NULL                COMMENT '商品 id，一对一',
    `total`      INT     NOT NULL DEFAULT 0      COMMENT '总库存',
    `available`  INT     NOT NULL DEFAULT 0      COMMENT '可用库存（可扣）',
    `locked`     INT     NOT NULL DEFAULT 0      COMMENT '已锁定（预扣未支付）',
    `version`    INT     NOT NULL DEFAULT 0      COMMENT '乐观锁版本号',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    -- 为什么建：扣库存按 goods_id 定位，唯一索引同时防止重复初始化库存行。
    UNIQUE KEY `uk_goods_id` (`goods_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='库存表';

-- =============================================================
-- 四、订单、支付、退款、核销（B 负责 #16 #17；C 负责 #19—#22）
-- =============================================================

-- 订单表（表名用 order_info，因为 order 是 MySQL 保留字）
-- 状态：0待支付 1已支付待成团 2已成团待提货 3已提货 4已取消 5已退款
DROP TABLE IF EXISTS `order_info`;
CREATE TABLE `order_info` (
    `id`          BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    `order_no`    VARCHAR(32)   NOT NULL                COMMENT '订单号，对外暴露',
    `user_id`     BIGINT        NOT NULL                COMMENT '下单用户 id',
    `activity_id` BIGINT        NOT NULL                COMMENT '活动 id',
    `goods_id`    BIGINT        NOT NULL                COMMENT '商品 id',
    `num`         INT           NOT NULL DEFAULT 1      COMMENT '购买份数',
    `pay_amount`  DECIMAL(10,2) NOT NULL                COMMENT '实付金额',
    `status`      TINYINT       NOT NULL DEFAULT 0      COMMENT '0待支付 1已支付 2已成团 3已提货 4已取消 5已退款',
    `expire_time` DATETIME      DEFAULT NULL            COMMENT '支付超时时间（超时自动取消）',
    `pay_time`    DATETIME      DEFAULT NULL            COMMENT '支付时间',
    `create_time` DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    -- 为什么建：订单号是对外唯一凭证，所有查询/回调都按它定位。
    UNIQUE KEY `uk_order_no` (`order_no`),
    -- 为什么建（关键）：这一条同时兜住两件事 ——
      -- ① 限购：一个用户在一个活动只能有一单，重复下单直接撞唯一键；
      -- ② 下单幂等：用户狂点、网络重试，第二次插入必然失败，不会生成重复订单。
      -- 比"先查再插"可靠，因为那是两步操作，并发下挡不住。
    UNIQUE KEY `uk_user_activity` (`user_id`, `activity_id`),
    -- 为什么建：我的订单列表（接口 #9）按用户 + 时间倒序分页。
    KEY `idx_user_time` (`user_id`, `create_time`),
    -- 为什么建：超时取消任务扫描"待支付且已过期"的订单，靠 (status, expire_time) 走索引。
    KEY `idx_status_expire` (`status`, `expire_time`),
    -- 为什么建：按活动统计参团数（成团判定 #20、统计 #23 都要用到）。
    KEY `idx_activity_status` (`activity_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='订单表';

-- 支付流水表：模拟第三方支付，回调带 trade_no，靠它做幂等
DROP TABLE IF EXISTS `payment`;
CREATE TABLE `payment` (
    `id`          BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    `pay_no`      VARCHAR(32)   NOT NULL                COMMENT '本站支付流水号',
    `order_no`    VARCHAR(32)   NOT NULL                COMMENT '订单号',
    `trade_no`    VARCHAR(64)   DEFAULT NULL            COMMENT '第三方交易号（模拟）',
    `amount`      DECIMAL(10,2) NOT NULL                COMMENT '支付金额',
    `channel`     VARCHAR(32)   NOT NULL DEFAULT 'MOCK' COMMENT '支付渠道',
    `status`      TINYINT       NOT NULL DEFAULT 0      COMMENT '0待支付 1已支付 2已关闭',
    `sign`        VARCHAR(128)  DEFAULT NULL            COMMENT '回调签名（模拟验签）',
    `callback_time` DATETIME    DEFAULT NULL            COMMENT '回调时间',
    `create_time` DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    -- 为什么建：本站流水号唯一。
    UNIQUE KEY `uk_pay_no` (`pay_no`),
    -- 为什么建（幂等核心）：第三方交易号唯一 —— 同一个 trade_no 回调两次，
      -- 第二次插入直接失败，或配合状态 CAS 保证只生效一次，避免重复入账。
    UNIQUE KEY `uk_trade_no` (`trade_no`),
    -- 为什么建：按订单反查支付记录。
    KEY `idx_order_no` (`order_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='支付流水表';

-- 核销码表：A 生成（成团后），C 核销（一次性）
DROP TABLE IF EXISTS `pickup_code`;
CREATE TABLE `pickup_code` (
    `id`           BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    `code`         VARCHAR(32) NOT NULL                COMMENT '核销码，全局唯一',
    `order_id`     BIGINT      NOT NULL                COMMENT '订单 id，一单一码',
    `order_no`     VARCHAR(32) NOT NULL                COMMENT '订单号（冗余，便于查询对账）',
    `user_id`      BIGINT      NOT NULL                COMMENT '提货人 id',
    `point_id`     BIGINT      NOT NULL                COMMENT '自提点 id',
    `status`       TINYINT     NOT NULL DEFAULT 0      COMMENT '0未核销 1已核销',
    `verify_time`  DATETIME    DEFAULT NULL            COMMENT '核销时间',
    `verifier_id`  BIGINT      DEFAULT NULL            COMMENT '核销人（团长）id',
    `create_time`  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    -- 为什么建：核销时按码查询，是最频繁的单点查询，必须唯一。
    UNIQUE KEY `uk_code` (`code`),
    -- 为什么建：一单一码，防止重复生成（成团回调重入时兜底）。
    UNIQUE KEY `uk_order_id` (`order_id`),
    -- 为什么建：团长后台按自提点 + 状态筛选待核销清单。
    KEY `idx_point_status` (`point_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='提货核销码表';

-- 退款单表：活动到期未成团 → 批量退款
DROP TABLE IF EXISTS `refund`;
CREATE TABLE `refund` (
    `id`          BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    `refund_no`   VARCHAR(32)   NOT NULL                COMMENT '退款单号',
    `order_no`    VARCHAR(32)   NOT NULL                COMMENT '订单号',
    `pay_no`      VARCHAR(32)   DEFAULT NULL            COMMENT '原支付流水号',
    `amount`      DECIMAL(10,2) NOT NULL                COMMENT '退款金额',
    `reason`      VARCHAR(255)  DEFAULT NULL            COMMENT '退款原因',
    `status`      TINYINT       NOT NULL DEFAULT 0      COMMENT '0退款中 1已退款 2退款失败',
    `refund_time` DATETIME      DEFAULT NULL            COMMENT '退款完成时间',
    `create_time` DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    -- 为什么建：退款单号唯一。
    UNIQUE KEY `uk_refund_no` (`refund_no`),
    -- 为什么建（防重复退款）：一单只退一次，定时任务重复扫到也不会二次退款。
    UNIQUE KEY `uk_order_no` (`order_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='退款单表';

-- 幂等记录表：C 用它兜住支付回调、成团判定、核销三处的重复请求
DROP TABLE IF EXISTS `idempotent_record`;
CREATE TABLE `idempotent_record` (
    `id`         BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    `biz_type`   VARCHAR(32) NOT NULL                COMMENT '业务类型：PAY_CALLBACK / GROUP_SUCCESS / VERIFY',
    `biz_key`    VARCHAR(64) NOT NULL                COMMENT '业务唯一键：如 trade_no、活动 id、核销码',
    `result`     VARCHAR(255) DEFAULT NULL           COMMENT '首次执行结果快照',
    `create_time` DATETIME   NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    -- 为什么建：幂等的落点 —— 同一 (biz_type, biz_key) 只能插一次，
      -- 第二次插入失败即判定为重复请求，直接返回首次结果。
    UNIQUE KEY `uk_biz` (`biz_type`, `biz_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='幂等记录表';

SET FOREIGN_KEY_CHECKS = 1;

-- =============================================================
-- 建表完成，共 13 张表：
--   用户权限（3）：user / role / user_role                    —— D 负责
--   自提团长（2）：pickup_point / leader                      —— A 负责
--   活动商品（3）：activity / goods / stock                   —— A 负责
--   交易履约（5）：order_info / payment / pickup_code / refund / idempotent_record
--                                                            —— B 写 order_info 的创建、C 写其余
--
-- 下一步（Day3 下午）：A 冻结 docs/api.md，B/C/D 按此表结构写 DTO 和 Mapper。
-- =============================================================
