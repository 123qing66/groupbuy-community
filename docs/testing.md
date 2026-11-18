# 测试与覆盖率说明（Issue #26）

> 维护人：D。各人往这里加自己的单测前，先花两分钟看完这篇。

## 1. 怎么跑

```bash
# 全部测试 + 覆盖率门禁（本地便携环境只有 JDK 21，用 -Djava.version=21 覆盖 pom 里的 25）
mvn -Djava.version=21 test

# 只跑单元测试（不需要数据库，秒级）
mvn -Djava.version=21 test -Dtest='*ServiceTest,AuthInterceptorTest,RTest,JwtUtilTest,ActivityStatusTest'

# 只跑集成测试（需要 MySQL 在 3308）
mvn -Djava.version=21 test -Dtest='*IntegrationTest'
```

集成测试要一个可连的 MySQL（库名 `groupbuy`），表结构用
`src/main/resources/db/schema.sql` 初始化；连接参数见
`src/test/resources/application-test.yml`，可用 `DB_HOST` / `DB_PORT` / `DB_PASSWORD` 覆盖。

便携 MySQL 启停：`E:\PortableDev\mysql-start.cmd` / `mysql-stop.cmd`。

## 2. 目录与命名约定

| 位置 | 放什么 | 要不要数据库 |
|---|---|---|
| `src/test/java/com/groupbuy/<模块>/XxxTest.java` | 单元测试，Mockito 打桩 | 不要 |
| `src/test/java/com/groupbuy/integration/XxxIntegrationTest.java` | 集成测试，继承 `BaseIntegrationTest` | 要 |

- **一个类只测一个类**，方法名写成"做什么 + 期望结果"，中文 `@DisplayName` 写清业务含义。
- 断言用 `assertEquals` / `assertThrows`，**不要** `if (...) throw new AssertionError(...)`——
  那样失败时报告里看不到实际值和期望值。
- 集成测试继承 `BaseIntegrationTest` 就自带事务回滚，不用自己清数据。

## 3. 覆盖率门禁

`pom.xml` 里 jacoco 的 `check` 绑在 **test 阶段**（不是 verify）——因为 CI 跑的是
`mvn clean package`，不会进 verify，绑 verify 等于门禁永远不生效。

| 指标 | 门槛 | 当前 |
|---|---|---|
| 指令覆盖率 | ≥ 45% | 53.1% |
| 分支覆盖率 | ≥ 35% | 45.4% |

`entity` / `dto` / `GroupbuyApplication` 已排除，不算进分母（Lombok 生成的
getter/setter 会把数字撑虚高）。

报告位置：`target/site/jacoco/index.html`。CI 也会把它当 artifact 上传。

## 4. 当前测试盘点（D 汇总，共 81 个）

| 类 | 数量 | 覆盖内容 |
|---|---|---|
| `service/AuthServiceTest` | 16 | 登录注册全分支：防用户名枚举、密码校验、两层防重 |
| `service/OrderCreateServiceTest` | 16 | 下单五步 + 回滚纪律（预扣了就必须还） |
| `service/PickupCodeServiceTest` | 5 | 核销码格式、易混淆字符、批量生成 |
| `config/AuthInterceptorTest` | 9 | 白名单放行、401 分支、ThreadLocal 清理 |
| `integration/AuthIntegrationTest` | 5 | 注册→登录→/me 全链路 + 三种拒绝 |
| `integration/OrderCreateIntegrationTest` | 5 | 真库下单、唯一索引幂等、CAS 库存不足 |
| `integration/ActivityQueryIntegrationTest` | 4 | 免鉴权放行、动态 SQL 筛选、分页 |
| `integration/GlobalExceptionIntegrationTest` | 4 | 四类异常的返回码 |
| 原有（组长骨架） | 15 | R / JwtUtil / ActivityStatus / Redis 工具箱（其中 2 个 `@Disabled`） |

各人至少补 2 个自己的单测，直接加在自己的模块包下即可。

## 5. 已知问题（不在本 Issue 范围，单独提）

1. **`UserContext.getUserId()` 未登录时兜底返回 1**（骨架期演示逻辑）。任何漏配拦截器的
   `/api` 路径都会静默以 userId=1 执行。建议改成未登录返回 401，由 D 单独开 Issue。
2. **`db/data.sql` 还是占位文件**（`SELECT 1;`），CI 里数据库没有基础数据。
   集成测试已自带角色兜底（`AuthIntegrationTest#ensureBaseRoles`），但
   `#27` 压测要的 1000 库存种子还压在 `feature/demo-data` 分支上没合并。
3. **核销幂等的集成用例写不了**：`PickupVerifyService` 在 `feature/order-status` 分支上，
   还没合进 develop。等 C 合并后补一个"同码并发核销只成功一次"的用例。
4. **pom 里 `java.version=25`，本地便携环境最高只有 JDK 21**。本地跑测试必须加
   `-Djava.version=21`；CI 用的是 temurin 25，不受影响。
