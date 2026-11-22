# 压测（Issue #27：200 并发 / 1000 库存 / 无超卖）

> **实测结果见 [REPORT.md](REPORT.md)**：1000 请求全部成功、库存守恒、零超卖，P95 495 ms。

本目录只放压测相关脚本，不参与 Maven 构建（不在 `src/` 下，`pom.xml` 没动）。
脚本用 **JDK 21 单文件直接运行**，不需要编译、不加任何依赖。

```
perf/
├── sql/
│   ├── 01-seed-activity.sql   造活动/商品/库存（固定 id 999001，可重复执行）
│   └── 02-verify.sql          验收三项核对
├── SeedData.java              注册 + 登录 1000 个账号，产出 token
├── LoadTest.java              200 并发下单，统计延迟与错误分布
├── seed.sh                    一键：建数据 + 清 Redis + 预热库存 + 造账号
├── run-loadtest.sh            一键：跑压测
├── verify.sh                  一键：跑 SQL 核对
├── REPORT.md                  本次实测报告（结论、数据、已知限制）
└── .run/                      运行产物（tokens.txt / result.txt，已 gitignore）
```

## 一、前置条件

三样都得起着，缺一个压测结果就没有意义：

| 依赖 | 启动方式 |
|---|---|
| MySQL（3308） | `E:\PortableDev\mysql-start.cmd` |
| Redis（6379） | `E:\PortableDev\redis-start.cmd` |
| 应用（8080） | IDEA 里跑 `GroupbuyApplication`，或用 Maven：`mvn -Djava.version=21 spring-boot:run` |

> 应用启动一定要带 `-Djava.version=21`：`pom.xml` 里写的是 25，但本机便携环境最高 JDK 21，不加会直接编译失败。

## 二、跑法（三步）

```bash
# 1. 重置数据 + 造 1000 个账号（约 20~30 秒，大部分时间花在 BCrypt 上）
bash perf/seed.sh

# 2. 跑压测：200 并发，总共 1000 个请求
bash perf/run-loadtest.sh

# 3. SQL 核对：库存是否清零、订单数是否 1000、有没有重复扣减
bash perf/verify.sh
```

参数都可以用环境变量覆盖：

```bash
USERS=1000 STOCK=1000 bash perf/seed.sh          # 账号数 / 库存
CONCURRENCY=200 TOTAL=1000 bash perf/run-loadtest.sh
BASE=http://127.0.0.1:8080 bash perf/seed.sh     # 换服务地址
```

## 三、验收标准

跑完看两个地方：`perf/.run/result.txt`（接口侧）和 `verify.sh` 的输出（数据侧）。

| 项 | 期望 | 说明 |
|---|---|---|
| `stock_available` | **0** | 1000 库存被 1000 个请求刚好抢完 |
| `stock_available` | **不得为负** | 负数就是超卖，直接判失败 |
| `order_count` | **1000** | 与库存消耗一一对应 |
| `distinct_users` | **1000** | `uk_user_activity` 保证一人一单 |
| `dup_user_count` | **0** | 同一用户多单 = 幂等失效 |
| `sold_num` | **= 订单 num 之和** | 库存减少量必须等于卖出量，不能"扣了库存没订单" |
| `成功(code=0)` | **1000** | 全部成功 |
| `被限流 429` | **0** | 见下方"为什么不会触发限流" |

## 四、几个设计取舍（写报告可以直接用）

**1. 为什么用 SQL 造活动，而不是调 `POST /api/activities`**

开团接口会校验"当前用户是团长且已绑定自提点"（`ActivityService.create` 第 151-158 行），
而 `leader` 行的创建与自提点绑定属于 A 的模块（#11/#12）。压测的目标是下单链路的并发正确性，
不应该被造数据这条旁路卡住。固定 id `999001` 还有个好处：`verify.sql` 能直接按 id 核对，
每次压测的数据完全可复现。

**2. 为什么是 1000 个用户，而不是 200 个用户各下 5 单**

两道限制决定了必须一人一单：

- `order_info.uk_user_activity`：一个用户在一个活动只能有一单；
- Redis 幂等键 `groupbuy:order:idem:<userId>:<activityId>`：30 分钟内重复提交只返回首次订单。

所以"200 并发 + 1000 库存"的正确解释是：**1000 个请求在 200 的并发窗口里涌入，每人买 1 份**。
如果想测"库存小于请求数"的场景，把 `STOCK` 调小即可（比如 `STOCK=100`，那 900 个请求应该报 10003）。

**3. 为什么不会触发限流**

`OrderCreateService` 的令牌桶是**按用户**开的：`rateLimiter.tryAcquire("order:" + userId, 10, 5)`，
容量 10、每秒补 5 个。本压测每个用户只发 1 个请求，桶里永远是满的，所以 429 应为 0。
如果跑出来 429 很多，说明脚本有问题（比如同一个 token 被用了多次），不是限流本身的锅。

**4. 重跑必须重置 Redis，否则数据全错**

幂等键 TTL 30 分钟。第二轮如果不清，1000 个请求会全部命中幂等、直接返回上一轮的订单
（HTTP 200、code=0），库存一点没扣，`verify.sql` 会显示"库存没变、订单没增加"。
`seed.sh` 里已经做了：删 `groupbuy:order:idem:*`、`groupbuy:rl:*`、`activity:detail:999001`，
并重新 `SET groupbuy:stock:activity:999001 1000`。

## 五、已知限制（报告里要如实写）

- **单机压测**：客户端和服务端在同一台机器上，200 并发会和 Tomcat、MySQL 抢 CPU，
  QPS 数字只反映本机能力，不代表生产容量。这次验收的核心是**并发正确性**（不超卖），不是吞吐。
- **数据库连接池只有 20**：`application-dev.yml` 里 `hikari.maximum-pool-size=20`，
  200 并发会有 180 个请求在等连接，P95 延迟被这一层拉高是正常的。要压吞吐得先调池子。
- **没有预热到稳定态**：每轮都是冷启动后的第一批流量，缓存、JIT 都在爬坡，延迟偏保守。
- **未覆盖支付/成团**：本轮只打下单（接口 #7）。成团判定（#20）、到期退款（#22）还没实现，
  所以 `current_num` 和 `refund` 表本轮不参与验证。
