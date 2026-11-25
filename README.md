# 社区团购拼团系统（groupbuy-community）

> 居民参团、团长开团、管理员兜底的社区团购后端：从浏览活动、下单支付、成团生成提货码，到自提点核销的完整链路。
> 接口契约 v1.0 已冻结，详见 [docs/api.md](docs/api.md)。

> **🎬 演示视频**：`docs/社区团购拼团系统-演示视频.mp4`（7 分 03 秒，3.1MB，H.264）。
> 若平台对视频大小仍有限制，视频另行上传网盘，链接：**[待填写——上传后替换本行]**。
> 配套：录制脚本 `docs/demo-video-script.md`、配音文稿 `docs/demo-video-配音文稿.md`。

## 环境要求

| 依赖 | 版本 | 说明 |
|---|---|---|
| JDK | 25 | pom 中 `java.version=25`，编译与运行需一致 |
| Maven | 3.9+ | `mvn clean package` 一键构建 |
| MySQL | 8.0 | 端口见下方两种启动方式 |
| Redis | 7 | 缓存、Lua 预扣、限流依赖 |

> 环境注意：若本机 `JAVA_HOME` 指向 JDK 21 而 pom 要求 25，请先 `mvn clean` 再构建，避免残留字节码版本不一致（class version 69 vs 65）。

## 技术栈

| 层 | 技术 |
|---|---|
| 语言 / 框架 | JDK 25、Spring Boot 3.5.16 |
| 持久层 | MyBatis 3.0.3、MySQL 8.0 |
| 缓存 / 并发 | Redis 7、Lua 原子预扣、令牌桶限流、DB CAS 兜底 |
| 接口文档 | Knife4j 4.4.0（OpenAPI 3） |
| 鉴权 | 自研 JWT 拦截器 + BCrypt 密码加密 + RBAC 三角色 |
| 构建 / 质量 | Maven、JaCoCo 0.8.14、GitHub Actions CI |
| 压测 / 接口测试 | Postman 集合（带断言）、JMeter 场景见 Issue #27—#29 |

## 快速开始

### 方式一：Docker Compose 一键启动（进阶项 #34）

```bash
mvn clean package -DskipTests
docker compose up -d
```

- 应用接口文档：<http://localhost:8080/doc.html>
- MySQL：`127.0.0.1:3307`（root / root，库 `groupbuy`）
- Redis：`127.0.0.1:6380`
- 只起中间件：`docker compose up -d mysql redis`

> ⚠️ 当前 `docker-compose.yml` 只挂载了 `data.sql`（内容仅 `SELECT 1;` 占位），**演示账号不会自动生成**。启动后先导入演示数据再登录：
>
> ```bash
> docker exec -i groupbuy-mysql mysql -uroot -proot groupbuy < src/main/resources/db/demo-data.sql
> ```

### 方式二：本地开发

前置：学校便携版 MySQL 跑在 `127.0.0.1:3308`（root / 123456），本机 Redis 跑在 `6379`。

```bash
"/f/JAVA开发综合实训/mysql8/bin/mysql.exe" -h127.0.0.1 -P3308 -uroot -p123456 < src/main/resources/db/schema.sql
"/f/JAVA开发综合实训/mysql8/bin/mysql.exe" -h127.0.0.1 -P3308 -uroot -p123456 < src/main/resources/db/demo-data.sql
mvn spring-boot:run
```

开发配置见 `src/main/resources/application-dev.yml`，端口 / 密码均可用环境变量覆盖（`DB_HOST`、`DB_PORT`、`DB_PASSWORD`、`REDIS_HOST`、`REDIS_PORT`）。

## 演示账号

密码统一为 `123456`（数据库里存 BCrypt 哈希，每次生成的字符串不同但都能校验通过）。

| 账号 | 角色 | 用途 |
|---|---|---|
| `leader1` | `ROLE_LEADER` 团长 | 开团、状态流转、绑自提点、核销提货码 |
| `user1` | `ROLE_RESIDENT` 居民 | 浏览活动、参团下单、支付 |
| `admin1` | `ROLE_ADMIN` 管理员 | 自提点管理、绑定团长、后台统计与导出 |

## 接口与测试

- **接口文档**：启动后访问 <http://localhost:8080/doc.html>；契约见 [docs/api.md](docs/api.md)。
- **Postman**：导入 `docs/postman/` 下的集合与环境文件，选择环境「社区团购-本地」，按 `00 → 01 → 02 → 03 → 04` 顺序执行（登录 token 与活动 id 会自动写入环境变量）。
- **单元测试**：`mvn test`；覆盖率报告在 `target/site/jacoco/index.html`。

三个容易踩的约定：

1. 业务错误也返回 **HTTP 200**，真实结果看响应体的 `code`（如 `{"code":401}`）。
2. 时间格式是 `yyyy-MM-dd HH:mm:ss`，不是 ISO 8601。
3. `activity.leader_id` 存的是 `leader` 表主键，不是 `user` 表 id。

## 项目结构

```
src/main/java/com/groupbuy/
├── common/        统一响应体 R、错误码、状态枚举、上下文、JWT 工具
├── config/        JWT 拦截器、Web MVC、Jackson、密码加密
├── controller/    #3-#18 共 18 个接口
├── dto/           入参 / 出参（字段以冻结契约为准）
├── entity/        13 张表对应实体
├── mapper/        MyBatis Mapper 接口
├── redis/         Lua 预扣 / 回滚、限流、CacheClient
└── service/       活动域、下单、支付成团核销、鉴权、后台统计
src/main/resources/
├── db/            schema.sql（13 张表）、data.sql、demo-data.sql
├── mapper/        Mapper XML
└── application*.yml
docs/
├── api.md         接口契约 v1.0（冻结版）
└── design/        架构图、ER 图、用例与权限矩阵、时序图
```

## 成员分工

| 成员 | GitHub | 负责范围 |
|---|---|---|
| A（组长） | `123qing66` | 活动域 / 状态机 / 自提点（#3—#6、#11、#12）、接口契约、设计图、仓库基建 |
| B | `Charon0304` | 参团下单 #7（限流 → Redis 预扣 → DB CAS）、报告书 1—5 章 |
| C | `xiaOwO4u` | 支付回调 / 订单查询 / 核销 / 进度推送（#8—#10、#18）、演示视频 |
| D | `zc1012daa` | 登录注册与后台统计（#1、#2、#13—#17）、测试与证据归档 |

## 设计文档

- [接口契约 v1.0](docs/api.md)
- [用例图 / 用例描述表 / 三角色权限矩阵](docs/design/use-case.md)
- [系统架构图](docs/design/architecture.md)
- [ER 图](docs/design/er.md)
- [时序图：参团下单](docs/design/sequence-order.md)
- [时序图：支付 → 成团 → 核销](docs/design/sequence-verify.md)

## 交付物清单

| 序号 | 交付物 | 位置 |
|---|---|---|
| 1 | 项目源码（完整 Maven 工程） | 本 zip 根目录，`mvn clean package` 可构建 |
| 1b | 提交记录导出 | `docs/提交记录.txt`、`docs/贡献统计.md` |
| 2 | 数据库脚本 | `src/main/resources/db/schema.sql` + `data.sql` |
| 3 | 项目说明 | 本文件 `README.md` |
| 4 | 演示视频 | `docs/社区团购拼团系统-演示视频.mp4`（3.1MB，超限则见封面网盘链接） |

## 已知问题

1. **Docker 演示数据需手动导入**：`docker-compose.yml` 只挂载了内容仅为 `SELECT 1;` 的 `data.sql`，一键启动不会自动建演示账号，需执行 `docker exec -i groupbuy-mysql mysql -uroot -proot groupbuy < src/main/resources/db/demo-data.sql`。
2. **JDK 版本需统一**：pom 要求 JDK 25，若本机为 21 需先 `mvn clean` 再构建；否则会报字节码版本不一致。
3. **集成测试依赖真实 MySQL**：5 个 `*IntegrationTest` 需连 MySQL（CI 自动起容器），本地未起库时会报 `Could not open JDBC Connection`；纯 Mockito 单测不受影响。
4. **role 表仅 3 条**：RBAC 只识别三种角色编码，保留 3 条固定字典数据，未强行凑数。
