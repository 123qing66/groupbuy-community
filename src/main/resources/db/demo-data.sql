-- =============================================================
-- 演示数据（组长 A 维护，D 的登录依赖其中 user / role / user_role）
--
-- 用法（Git Bash 里）：
--   "/f/JAVA开发综合实训/mysql8/bin/mysql.exe" -h127.0.0.1 -P3308 -uroot -p123456 < src/main/resources/db/demo-data.sql
--
-- 说明：
--   1. 只重置基础表（角色/用户/自提点/团长），**不会动 activity / goods / stock / 订单**，
--      所以已经开过的团和跑过的压测数据都还在。
--   2. 三个账号密码都是 123456，存的是 BCrypt 哈希（$2a$10$...），
--      哈希是随机盐的，每次生成都不一样，但 matches() 都能通过；
--      想换密码就用 docs 里那句 java GenHash 重新生成再 UPDATE。
--   3. leader 表里的 user_id=1 必须存在，否则开团接口会报 10016
--      （团长没绑定自提点）。
-- =============================================================

USE groupbuy;

SET FOREIGN_KEY_CHECKS = 0;
DELETE FROM user_role;
DELETE FROM leader;
DELETE FROM pickup_point;
DELETE FROM `user`;
DELETE FROM role;
SET FOREIGN_KEY_CHECKS = 1;

-- 角色
INSERT INTO role (id, role_code, role_name) VALUES
    (1, 'ROLE_ADMIN', '平台管理员'),
    (2, 'ROLE_LEADER', '团长'),
    (3, 'ROLE_RESIDENT', '居民');

-- 用户：密码 123456 的 BCrypt 哈希（三个互不相同，因为每次加密自带随机盐）
INSERT INTO `user` (id, username, password, real_name, phone, status) VALUES
    (1, 'leader1', '$2a$10$WCWYyh5M7CITBtfajwRqleewVVfBB8jv2fIFw/jBzuk5SZ7VuNZzm', '张团长', '13800000001', 1),
    (2, 'user1',   '$2a$10$hLNZ2jhKKy/TuVargV6PbebpIHwNyVgBKcEfZAM.EoyZznU1Jtaai', '李小明', '13800000002', 1),
    (3, 'admin1',  '$2a$10$fEG.6sbHcQZ8yZoRz1pgfeF7GJe/V8MhzdB5M20gmPYHks2EHIAXu', '管理员', '13800000003', 1);

-- 授角色：leader1=团长，user1=居民，admin1=管理员
INSERT INTO user_role (user_id, role_id) VALUES (1, 2), (2, 3), (3, 1);

-- 自提点
INSERT INTO pickup_point (id, name, address, contact, phone, status) VALUES
    (1, '阳光小区自提点', '阳光小区3号楼1单元101', '张团长', '13800000001', 1),
    (2, '幸福里驿站',     '幸福里5号楼门厅',       '王阿姨', '13800000004', 1);

-- 团长：与自提点一对一绑定（uk_point_id 保证一个自提点只能有一个团长）
INSERT INTO leader (id, user_id, point_id, community, status) VALUES
    (1, 1, 1, '阳光小区', 1);

SELECT '演示数据已就绪' AS result;
SELECT u.id, u.username, u.real_name, r.role_code
FROM `user` u LEFT JOIN user_role ur ON ur.user_id = u.id
              LEFT JOIN role r ON r.id = ur.role_id
ORDER BY u.id;
