# 压测报告：下单链路 200 并发（Issue #27）

> 执行时间：2026-11-19 00:11
> 执行人：D
> 代码版本：`feature/perf-loadtest`（基于 `develop` @ `96747e7`）

## 一、结论

| 验收项 | 要求 | 实测 | 判定 |
|---|---|---|---|
| 剩余库存 | = 0 | `stock.available = 0`，Redis 库存键 = 0 | ✅ |
| 订单数 | = 1000 | `order_info` 共 1000 条，1000 个不同用户，1000 个不同订单号 | ✅ |
| 无超卖 | 不允许负库存 | `available` 从未小于 0，`locked = 1000` 与 `total` 相等 | ✅ |
| 无重复扣减 | 同一用户不得多次扣减 | 重复用户数 = 0，`stock.version = 1000` | ✅ |
| 无异常报错 | 服务端零异常 | 应用日志 14.6 万行，`ERROR/Exception` 计数 = 0 | ✅ |

**下单链路在 200 并发下没有超卖，库存完全守恒。**

## 二、环境与参数

| 项 | 值 |
|---|---|
| 客户端 / 服务端 | 同一台机器（单机压测） |
| JDK | Temurin 21.0.12（pom 声明 25，本地 `-Djava.version=21`） |
| MySQL | 8.4.11 便携版，3308 端口 |
| Redis | 8.10.3 便携版，6379 端口，**持久化关闭**（RDB fork 会抖动，影响并发数据准确性） |
| 应用 | Spring Boot 3.x + Tomcat 10.1.55，Hikari 连接池 20 |
| 压测工具 | `perf/LoadTest.java`，JDK 21 虚拟线程 `Thread.ofVirtual()`，零第三方依赖 |
| 目标接口 | `POST /api/orders`（下单，接口 #7） |
| 活动 | id `999001`，库存 1000，每人限购 1 |
| 请求总量 | 1000（每个账号 1 单） |

## 三、结果

```
并发            200
总请求          1000
总耗时          2.08 s
吞吐            481.0 req/s
成功(code=0)    1000  (100.0%)
库存不足 10003  0
重复下单 10015  0
被限流 429      0
活动未开始10002 0
其他失败        0

延迟 P50        383 ms
延迟 P95        495 ms
延迟 P99        568 ms
延迟 Max        658 ms
```

## 四、数据核对

核对脚本：`perf/sql/02-verify.sql`（`bash perf/verify.sh`）。

**① 库存与销量守恒**

| stock_total | stock_available | stock_locked | sold_num | order_num_sum | 判定 |
|---|---|---|---|---|---|
| 1000 | **0** | 1000 | 1000 | 1000 | ✅ 库存刚好清零，无超卖 |

`total = available + locked`，且 `locked = sold_num = order_num_sum = 1000`，三者对得上。

**② 订单数与人数**

| order_count | distinct_users | distinct_order_no | total_num |
|---|---|---|---|
| 1000 | 1000 | 1000 | 1000 |

1000 条订单、1000 个不同用户、1000 个不同订单号、总购买数 1000 —— 一人一单，无重复写入。

**③ 重复下单 / 重复扣减**

| dup_user_count |
|---|
| **0** |

**④ 订单号重复**：0 行（`order_no` 唯一约束生效）。
**⑤ 订单状态分布**：1000 条全部为 `0`（待支付），符合"只下单不支付"的压测范围。

**⑥ 乐观锁版本号（额外证据）**

```
stock.version = 1000
```

`version` 是 CAS 更新时的乐观锁版本，每成功扣减一次 +1。它精确等于 1000，说明**恰好发生 1000 次成功的库存扣减，没有丢失更新、没有重复扣减**。这比单看 `available = 0` 更有说服力——`available = 0` 也可能是扣多了再被覆盖，`version = 1000` 只能来自 1000 次成功 CAS。

## 五、链路分析

下单经过四层保护，本次压测验证的是它们串起来的实际行为：

1. **令牌桶限流**（按 userId，容量 10、5/s 补充）—— 每人只发 1 个请求，所以 429 = 0 符合预期。若本轮出现 429，说明脚本有问题，不是限流误伤。
2. **Redis Lua 预扣** —— 原子 `DECR`，1000 个请求无一失败（库存不足 10003 = 0），说明预扣这层没成为瓶颈。
3. **唯一索引 + 幂等键** —— `order_info.uk_user_activity` 与 `groupbuy:order:idem:<userId>:<activityId>` 双重保险，重复下单 10015 = 0。
4. **DB CAS 兜底** —— `UPDATE stock SET available = available - 1, version = version + 1 WHERE goods_id = ? AND available >= 1`，最终 `version = 1000` 证明这层承担了全部 1000 次扣减且没有一次失败。

## 六、已知限制（如实说明）

- **单机压测**：客户端与服务端同机，200 并发与 Tomcat、MySQL 抢 CPU，QPS 只反映本机能力，**不代表生产容量**。本轮验收的核心是并发正确性（不超卖），不是吞吐。
- **连接池只有 20**：`application-dev.yml` 中 `hikari.maximum-pool-size=20`，200 并发下约 180 个请求在等连接，P95 的 495 ms 很大一部分来自这里。要压吞吐需先调池子。
- **未预热**：冷启动后第一批流量，JIT 与连接池都在爬坡，延迟偏保守。
- **未覆盖支付/成团**：本轮只打下单（接口 #7）。成团判定（#20）、到期退款（#22）尚未实现，`current_num` 与 `refund` 表不参与验证。
- **限购为 1**：因此用 1000 个账号而非 200 账号各下 5 单（唯一索引 + 幂等键决定了做不到一人多单）。

## 七、复现步骤

```bash
# 0. 起依赖（MySQL 3308、Redis 6379、应用 8080）
E:\PortableDev\mysql-start.cmd
E:\PortableDev\redis-start.cmd
mvn -Djava.version=21 spring-boot:run

# 1. 造数据 + 造 1000 个账号（会重置活动、清 Redis 幂等键）
DB_PASSWORD=123456 bash perf/seed.sh

# 2. 压测
bash perf/run-loadtest.sh

# 3. 核对
DB_PASSWORD=123456 bash perf/verify.sh
```

**重跑前必须执行 `seed.sh`**：幂等键 TTL 30 分钟，不清会让第二轮 1000 个请求全部命中幂等、返回上一轮订单，库存一点不扣，看起来像没跑。
