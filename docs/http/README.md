# 接口示例（docs/http）

本目录是 **可直接执行的接口请求示例**，纯文本、可版本管理、改一行就能跑，配合 `docs/api.md`（契约）和 `docs/postman/`（Postman 集合）三件套使用：

| 文件/目录 | 作用 | 给谁看 |
|---|---|---|
| `docs/api.md` | 接口**契约**（字段、错误码、规则），冻结版 | 写代码时对照，改字段要走变更流程 |
| `docs/http/`（本目录） | 一条命令就能发的**可执行示例** | 联调、自测、答辩演示 |
| `docs/postman/` | Postman 集合（可视化、带环境变量） | 习惯用 Postman 的同学 |

## 一、怎么用（VS Code，30 秒）

1. 装插件：扩展面板搜 **`REST Client`**（作者 Huachao Mao，图标是一个紫色小信封）
2. 打开任意 `.http` 文件，每个请求上方会出现灰色的 **`Send Request`** 小字，点它即可发送
3. 响应结果出现在右侧新开的标签页里

> 用 IntelliJ IDEA 的同学不用装插件，它自带 HTTP Client，语法基本一致。

**先起服务再发请求**：

```bash
mvn spring-boot:run          # 默认 8080，需要本机 MySQL + Redis 就绪
```

服务没起时点发送会报 `ECONNREFUSED`，不是示例写错了。

## 二、变量与登录顺序（重要）

每个文件顶部有两行变量：

```
@host  = http://localhost:8080
@token = {{loginAdmin.response.body.$.data.token}}
```

`@token` 是**从上一个登录请求的响应里取出来的**（REST Client 的请求变量），所以：

> **先发该文件里的「① 登录」那条请求，再发下面需要鉴权的请求。**

顺序反了会报 `401 未登录`——因为此时 `token` 还是空的。每个需要鉴权的文件都单独写了一条登录请求，就是为了让你不用在文件之间来回跳。

演示账号（密码统一 `897393`，见根目录 `README.md`）：

| 账号 | 角色 | 能干什么 |
|---|---|---|
| `user1` | `ROLE_RESIDENT` 居民 | 浏览、下单、支付、看自己的订单 |
| `leader1` | `ROLE_LEADER` 团长 | 开团、状态流转、核销提货码 |
| `admin1` | `ROLE_ADMIN` 管理员 | 自提点管理、绑定团长、后台统计与导出 |

## 三、文件与接口对照

| 文件 | 覆盖接口 | 契约章节 |
|---|---|---|
| `01-auth.http` | #1 登录、#2 注册（含失败样例） | 三.1、三.2 |
| `02-activity.http` | #3 列表、#4 详情、#5 开团、#6 状态变更、#18 进度推送 | 三.3—三.6、三.18 |
| `03-pickup-point.http` | #11 自提点 CRUD、#12 团长绑定自提点 | 三.11、三.12 |
| `04-order.http` | #7 下单、#8 支付回调、#9 我的订单 | 三.7—三.9 |
| `05-pickup.http` | #10 团长核销（幂等） | 三.10 |
| `06-admin.http` | #13 成团率、#14 退款对账、#15 业绩排行、#16 Excel 导出、#17 活动下架 | 三.13—三.17 |

## 四、当前实现状态（2026-11-17）

示例文件是**按契约写全的**，但仓库里代码还没跟上，部分请求会返回 404。这是正常的，示例本身就是给后面写代码的人对照用的：

| 接口 | 状态 | 所在 Controller |
|---|---|---|
| #1 登录 / #2 注册 | ✅ 已实现 | `AuthController` / `UserController` |
| #3 #4 #5 #6 活动 | ✅ 已实现 | `ActivityController` |
| #7 下单 | ✅ 已实现 | `OrderCreateController` |
| #8 支付回调 | ✅ 已实现 | `PaymentController` |
| #11 自提点 / #12 绑定 | ✅ 已实现 | `PickupPointController` / `LeaderController` |
| #9 我的订单 | ⏳ 待实现（C） | — |
| #10 核销 | ⏳ 待实现（C） | — |
| #13 #14 #15 统计 | ⏳ 待实现（D，Issue #23） | — |
| #16 导出 | ⏳ 待实现（D，Issue #24） | — |
| #17 下架 | ⏳ 待实现（D） | — |
| #18 进度推送 | ⏳ 待实现（C，加分项） | — |

## 五、几个必须知道的坑

**① 数据要依赖演示数据脚本**

`#3 列表`、`#7 下单` 这些能跑出结果，前提是库里已经执行过 `db/schema.sql` + `db/data.sql`。否则查询返回空数组、下单报 `10001 活动不存在`——这不是代码问题。

**② 支付签名要自己算**

`#8 支付回调` 的 `sign` 不是随便填的：

```
sign = MD5(orderNo + tradeNo + amount + "groupbuy-mock-secret")
```

`amount` 用**两位小数字符串**（如 `89.00`），与服务端 `BigDecimal.toString()` 保持一致。
示例文件里的 `sign` 值是按示例单号算好的，换了单号要重算：

```bash
python -c "import hashlib;print(hashlib.md5(('GB20261118000001'+'MOCK20261118000001'+'89.00'+'groupbuy-mock-secret').encode()).hexdigest())"
```

**③ 业务错误也是 HTTP 200**

看到响应 `200` 不代表成功，要看 body 里的 `code`：`0` 才是成功，`10001`~`10016` 是业务失败（对照 `docs/api.md` 第二节的错误码表）。

**④ token 有效期 24 小时**

过期后所有带鉴权的请求都返回 `401`，重新发一次登录请求即可。

**⑤ `#16 导出` 返回的是文件流**

不套 `{code,msg,data}` 包装，REST Client 会提示保存文件，属正常现象。

---

## 六、Knife4j 分组（Issue #25 的另一半）

启动服务后打开 <http://localhost:8080/doc.html>，左上角下拉框里会看到 6 个分组，不用再在几十个接口里翻自己的：

| 分组 | 覆盖路径 | 负责人 | 对应接口 |
|---|---|---|---|
| `00-通用与鉴权` | `/api/health`、`/api/auth/**`、`/api/users/**` | D | #1 #2 |
| `01-团购活动` | `/api/activities/**` | A（+C 的 #18） | #3 #4 #5 #6 |
| `02-自提点与团长` | `/api/pickup-points/**`、`/api/leaders/**` | A | #11 #12 |
| `03-下单与支付` | `/api/orders/**`、`/api/pay/**` | B（+C 的 #8） | #7 #8 #9 |
| `04-核销提货` | `/api/pickup-codes/**` | C | #10 |
| `05-后台管理` | `/api/admin/**` | D | #13 #14 #15 #16 #17 |

配置位置：`src/main/java/com/groupbuy/config/OpenApiConfig.java`。

**分组靠路径前缀匹配，不靠 `@Tag`**——所以你新加 Controller 时只要遵守 `/api/xxx` 的路径约定，接口就自动进对应的组，不用回来改这个配置。

分组名前面的数字是为了让下拉框按顺序排（Knife4j 按字母序排，加数字才能固定顺序）。

