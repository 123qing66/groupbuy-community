# Postman 测试集合（接口契约 v1.0）

导入两个文件就能测完 A 的活动域全部接口，**不用手写任何请求**。

| 文件 | 内容 |
|---|---|
| `community-groupbuy.postman_collection.json` | 6 个文件夹 · 26 个请求 · 带断言脚本 |
| `社区团购-本地.postman_environment.json` | 环境变量：baseUrl、token、activityId、pointId、version… |

## 一、导入

1. Postman 左上 **Import** → 把两个 JSON 一起拖进去
2. 右上角环境下拉选 **「社区团购-本地」**
3. 先跑 `00 连通性 → 健康检查`，`code: 0` 说明后端活着

## 二、执行方式

**方式 A：一条条点（推荐，边测边看）**

按 `00 → 01 → 02 → 03 → 04` 顺序点 **Send**，每条都带断言，Passed 显示绿字。

- `01 认证` 的 ① 登录跑完，token 自动写进环境变量，后面所有请求自动带 `Authorization: Bearer ...`
- `02 活动` 的 ⑤ 开团跑完，新活动 id 自动写进 `{{activityId}}`
- `03 自提点` 的 ④ 新增跑完，新自提点 id 自动写进 `{{newPointId}}`

**方式 B：整个文件夹一键跑**

右键文件夹 → **Run folder** → Run。一次跑完该组所有断言。

> 顺序不能乱：先登录再开团，先开团再流转。乱序时断言会假失败。

## 三、演示账号（密码均为 `123456`）

| 账号 | 角色（role_code） | 用途 |
|---|---|---|
| `leader1` | `ROLE_LEADER` 团长 | 开团、状态流转、绑自提点（已绑自提点 1） |
| `user1` | `ROLE_RESIDENT` 居民 | 参团下单 |
| `admin1` | `ROLE_ADMIN` 管理员 | 后台管理 |

> 角色编码就这三个：`ROLE_ADMIN` / `ROLE_LEADER` / `ROLE_RESIDENT`。注册时 `roleCode` 传别的值会返 **400 角色不存在**。

## 四、必须知道的三个约定（不是 Bug）

1. **业务错误也返回 HTTP 200**，真实结果看响应体的 `code`。
   例：没带 token 时 `HTTP 200` + `{"code":401}`。断言要写 `res.code`，别写 `pm.response.code`。
2. **时间格式 `yyyy-MM-dd HH:mm:ss`**（空格分隔）。写 `2026-10-22T10:00:00` 会返 400。
3. **`activity.leader_id` 存的是 leader 表主键**，不是 user 表 id。

## 五、错误码速查

| code | 含义 | 哪里会触发 |
|---|---|---|
| 0 | 成功 | — |
| 400 | 参数错误 | 时间格式错、缺字段 |
| 401 | 未登录/过期 | 不带 token 调受保护接口 |
| 10001 | 活动不存在 | 详情查不存在的 id |
| 10002 | 活动未进行中 | 未开始/已结束还去下单 |
| 10003 | 库存不足 | 预扣失败 |
| 10004 | 超出限购 | 超 buyLimit |
| 10007 | 支付验签失败 | 回调 sign 不对 |
| 10009/10010 | 核销码无效/已使用 | 重复核销 |
| 10012 | 用户名已存在 | 重复注册 |
| 10013 | 用户名或密码错误 | 密码错（不区分用户是否存在） |
| 10014 | 状态流转非法 | 已成团改回进行中 |
| 10015 | 重复下单 | 同活动同用户 |
| 10016 | 自提点归属校验失败 | 团长开团用了没绑定的自提点 |
| 429 | 限流 | 下单接口超频（B 实现后） |

## 六、跑完的预期结果

| 文件夹 | 断言条数 | 预期 |
|---|---|---|
| 00 连通性 | 2 | 全绿 |
| 01 认证 | 9 | 全绿（③⑤ 是故意失败的分支，断言"应该报错"） |
| 02 活动 | 18 | 全绿 |
| 03 自提点 | 8 | 全绿 |
| 04 团长 | 1 | 全绿 |
| 05 B/C 占位 | 3 | 返 `code=404`，控制台打提示，不算失败 |

> 已用 newman 实跑验证：**26 个请求 / 37 条断言 / 0 失败，2.7 秒跑完**。

## 七、常见问题

**全部请求都 401** → 没跑登录，或环境没选中。跑 `01 ① 登录（团长）`。

**开团返 401** → 你先跑了「② 登录（居民 user1）」，token 被换成了居民的。回去重跑 ①。

**开团返 10016** → leader1 没绑 `{{pointId}}`。跑 `04 ① 绑定自提点`。

**连不上 127.0.0.1:8080** → 后端没起。启动命令：

```bash
cd /d/shopping/groupbuy-community
mvn spring-boot:run "-Dspring-boot.run.arguments=--server.port=8080 --spring.data.redis.port=6380"
```

**code 500 且日志报 Connection refused** → MySQL 3308 或 Redis 6380 没起。
MySQL：双击 `F:\JAVA开发综合实训\mysql8\start_mysql.bat`
Redis：`cd /f/JAVA开发综合实训/redis && ./redis-server.exe redis-portable.conf`

## 八、命令行跑（可选，不用装 Postman）

```bash
npx newman run docs/postman/community-groupbuy.postman_collection.json \
  -e docs/postman/社区团购-本地.postman_environment.json \
  --reporters cli
```

适合贴到报告里当"接口测试通过"的证据截图。
