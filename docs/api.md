# 社区团购拼团系统 —— 接口契约 v1.0（冻结版）

> **状态：已冻结（Day3 下午）**
> 冻结后任何人不得擅自改字段。确需变更 → **开 Issue 说明（标题带 `接口变更`）→ 群里说一声 → A 更新本文件 → 升版本号 v1.1**。
> 各人写 DTO / Mapper / Controller 一律以本文件为准，字段名、类型不许自己发挥。

---

## 一、通用约定（所有人必须遵守）

| 项 | 约定 |
|---|---|
| 路径前缀 | `/api` |
| 请求/响应格式 | `application/json;charset=UTF-8` |
| 统一响应体 | `{ "code": 0, "msg": "ok", "data": {...} }`，见 `common/R.java` |
| 鉴权方式 | `Authorization: Bearer <token>`（除登录、注册、活动列表/详情外都要带） |
| 分页参数 | `pageNum`（从 1 开始，默认 1）、`pageSize`（默认 10，最大 100） |
| 分页返回 | `{ "total": 总条数, "pages": 总页数, "list": [...] }`（`dto/PageResult.java`） |
| 时间格式 | `yyyy-MM-dd HH:mm:ss`，字符串传输，后端用 `LocalDateTime` 接 |
| 金额 | `DECIMAL(10,2)`，JSON 里用数字（如 `19.90`），**禁止用 double 做运算**，统一 `BigDecimal` |
| 幂等要求 | 下单（#7）、支付回调（#8）、核销（#10）三处必须做幂等，重复请求返回首次结果，不得重复扣款/扣库存/重复核销 |

### 角色说明

| 角色码 | 含义 | 能干什么 |
|---|---|---|
| `ROLE_RESIDENT` | 居民 | 浏览活动、下单、支付、查看我的订单 |
| `ROLE_LEADER` | 团长 | 上面全部 + 开团、管理自己的活动、核销提货码 |
| `ROLE_ADMIN` | 平台管理员 | 上面全部 + 全部后台接口（统计、导出、下架） |

---

## 二、错误码表

| code | 含义 | HTTP | 谁会用到 |
|---|---|---|---|
| 0 | 成功 | 200 | 全部 |
| 400 | 参数错误（缺字段/格式错） | 400 | 全部 |
| 401 | 未登录或 token 过期 | 401 | D 的拦截器 |
| 403 | 无权限（角色不匹配） | 403 | D 的拦截器 |
| 404 | 资源不存在 | 404 | 全部 |
| 429 | 触发限流（令牌桶满了） | 429 | **B 的下单接口** |
| 500 | 系统异常 | 500 | 全局兜底 |
| 10001 | 团购活动不存在 | 200 | A |
| 10002 | 活动未在进行中 | 200 | A / B |
| 10003 | 库存不足 | 200 | **B** |
| 10004 | 超出限购数量 | 200 | **B** |
| 10005 | 订单不存在 | 200 | C |
| 10006 | 订单状态不合法 | 200 | C |
| 10007 | 支付回调验签失败 | 200 | C |
| 10008 | 拼团人数未达标 | 200 | C |
| 10009 | 核销码无效 | 200 | C |
| 10010 | 核销码已使用 | 200 | C |
| 10011 | 退款失败 | 200 | C |
| 10012 | 用户名已存在 | 200 | D |
| 10013 | 用户名或密码错误 | 200 | D |
| 10014 | 活动状态流转非法 | 200 | A |
| 10015 | 订单已存在（重复下单） | 200 | **B** |
| 10016 | 自提点已被其他团长绑定 | 200 | A |

> 约定：**业务错误也返回 HTTP 200**，靠 `code` 区分（前端好处理）；只有 401/403/404/429 这类沿用 HTTP 语义。

---

## 三、接口清单（18 条）

### 1. 登录 —— D

```
POST /api/auth/login
```

权限：无（不需要 token）

请求：

```json
{ "username": "zhangsan", "password": "123456" }
```

响应 `data`：

```json
{
  "token": "eyJhbGciOi...",
  "userId": 1,
  "username": "zhangsan",
  "realName": "张三",
  "roles": ["ROLE_RESIDENT"]
}
```

错误码：10013（用户名或密码错误）、400

> 密码必须 BCrypt 加密存储，登录用 `BCryptPasswordEncoder.matches()` 比对，**不解密**。

---

### 2. 注册 —— D

```
POST /api/users/register
```

权限：无

请求：

```json
{ "username": "lisi", "password": "123456", "realName": "李四", "phone": "13800138000", "roleCode": "ROLE_RESIDENT" }
```

响应 `data`：`{ "userId": 2 }`

错误码：10012（用户名已存在，靠 `uk_username` 唯一索引兜底）、400

---

### 3. 活动列表（分页 + 条件搜索）—— A

```
GET /api/activities
```

权限：无（游客可看）

| 参数 | 类型 | 必填 | 说明 |
|---|---|---|---|
| keyword | String | 否 | 标题模糊匹配 |
| status | Integer | 否 | 0待开始 1进行中 2已成团 3已失败 4已下架 |
| leaderId | Long | 否 | 团长后台"我开的活动" |
| pointId | Long | 否 | 按自提点筛选 |
| pageNum / pageSize | Integer | 否 | 分页 |

响应 `data`：`PageResult<ActivityVO>`

```json
{
  "total": 20, "pages": 2,
  "list": [
    { "id": 1, "title": "周末水果团", "leaderId": 3, "pointId": 1,
      "targetNum": 50, "currentNum": 12, "buyLimit": 2,
      "status": 1, "startTime": "2026-10-21 10:00:00", "endTime": "2026-10-23 18:00:00" }
  ]
}
```

> **考核点**：这里必须用**动态 SQL**（`<if>` 判断 keyword/status/pointId），至少 3 处条件分支，对应 Issue #6。

---

### 4. 活动详情（带缓存保护）—— A

```
GET /api/activities/{id}
```

权限：无

响应 `data`：`ActivityVO` + `goods` 列表 + 库存

```json
{
  "id": 1, "title": "周末水果团", "status": 1,
  "targetNum": 50, "currentNum": 12, "buyLimit": 2,
  "startTime": "2026-10-21 10:00:00", "endTime": "2026-10-23 18:00:00",
  "point": { "id": 1, "name": "阳光小区自提点", "address": "..." },
  "goods": [
    { "id": 11, "name": "泰国金枕榴莲", "originPrice": 129.00, "groupPrice": 89.00, "available": 1000 }
  ]
}
```

> **考核点（Issue #14 #18）**：必须走 `CacheClient` 的 Cache-Aside：
> ① 空值缓存防**穿透**（查不到也缓存 5 分钟）；
> ② 逻辑过期 + 互斥锁防**击穿**（热点 key 过期只放一个线程回源）；
> ③ TTL 加 ±10% 随机抖动防**雪崩**。
> 压测验收（#28）：热点 key 过期瞬间 100 并发，DB 回源次数 ≤ 1。

---

### 5. 团长开团 —— A

```
POST /api/activities
```

权限：`ROLE_LEADER`

请求：

```json
{
  "title": "周末水果团", "pointId": 1,
  "targetNum": 50, "buyLimit": 2,
  "startTime": "2026-10-21 10:00:00", "endTime": "2026-10-23 18:00:00",
  "goods": [
    { "name": "泰国金枕榴莲", "originPrice": 129.00, "groupPrice": 89.00, "total": 1000 }
  ]
}
```

响应 `data`：`{ "activityId": 1 }`

规则：
- 开团时**同步初始化库存**（`stock` 表插入 `total = available`，`locked = 0`）
- 活动初始状态 `status = 0`（待开始）；到 `start_time` 由状态机转 1
- `activity.leader_id` 存 **leader 表主键**（不是 user_id），与 #12 绑定接口、#15 排行榜口径一致
- 开团人必须是团长，且只能用**自己已绑定**的自提点
- 错误码：10016（自提点归属校验失败：未绑定该自提点 / 已被其他团长绑定）、403（非团长）、400

---

### 6. 活动状态变更 —— A

```
PUT /api/activities/{id}/status
```

权限：`ROLE_LEADER`（自己的团）/ `ROLE_ADMIN`（全部）

请求：

```json
{ "status": 4, "version": 3 }
```

响应 `data`：`{ "id": 1, "status": 4 }`

**状态机（显式转移表，越权转移直接报 10014）**：

| 从 | 到 | 触发条件 |
|---|---|---|
| 0 待开始 | 1 进行中 | 到达 `start_time`（定时任务或首次访问时惰性触发） |
| 1 进行中 | 2 已成团 | `current_num >= target_num`（成团判定，C 调用，只判一次） |
| 1 进行中 | 3 已失败 | 到达 `end_time` 且人数不足（#22 定时扫描） |
| 1/2 | 4 已下架 | 管理员手动下架（#17） |
| 2 已成团 | — | 终态，不可再变 |

> **并发保护**：更新带 `version` 乐观锁：`UPDATE activity SET status=?, version=version+1 WHERE id=? AND version=?`，影响行数 0 即重试。

---

### 7. 参团下单 ⭐（全系统最难，B 的得分点）

```
POST /api/orders
```

权限：`ROLE_RESIDENT` / `ROLE_LEADER`

请求：

```json
{ "activityId": 1, "goodsId": 11, "num": 1 }
```

响应 `data`：

```json
{ "orderNo": "GB2026102100012345", "payAmount": 89.00, "expireTime": "2026-10-21 10:15:00" }
```

**执行顺序（三步，缺一不可）**：

```
① 限流   ：RateLimiter.tryAcquire("order:" + userId) → 失败抛 429
② 预扣   ：StockRedisHelper.deduct(activityId, num)  （Lua 原子，返回 0 = 库存不足）
③ 落库   ：INSERT order_info（撞 uk_user_activity 就返回 10015）
           + UPDATE stock SET available=available-?, locked=locked+?
             WHERE goods_id=? AND available>=?   ← DB CAS 兜底
```

**失败必须回滚**：③ 抛异常 → 立刻 `StockRedisHelper.rollback(...)` 把 Redis 预扣还回去。

| 校验 | 错误码 |
|---|---|
| 活动不存在 | 10001 |
| 活动不在进行中（status ≠ 1、不在时间窗） | 10002 |
| Redis 预扣失败 | 10003 |
| 超过 `buy_limit` | 10004 |
| 重复下单（唯一索引） | 10015 |
| 令牌桶限流 | 429 |

> **压测验收（#27）**：200 并发打 1000 库存，**成功订单数必须 ≤ 1000，且 `available + locked` 不为负**，零超卖。

---

### 8. 模拟支付回调（幂等）—— C

```
POST /api/orders/{id}/pay
```

权限：登录用户（自己的单）；真实的第三方回调走 `/api/pay/callback`（见下）

请求：

```json
{ "orderNo": "GB2026102100012345", "tradeNo": "MOCK20261021000001", "amount": 89.00, "sign": "a1b2c3..." }
```

响应 `data`：`{ "orderNo": "...", "orderStatus": 1, "payTime": "2026-10-21 10:02:31" }`

**幂等做法（二选一，都要写进报告）**：

- 表里已有 `payment.uk_trade_no`：同一 `trade_no` 第二次插入直接失败 → 判定重复回调，返回首次结果
- 或用 `idempotent_record`：`(biz_type='PAY_CALLBACK', biz_key=trade_no)` 插成功才继续

验签：模拟算法 `sign = MD5(orderNo + tradeNo + amount + secret)`，不一致报 10007。

支付成功后：
1. `order_info.status` 0 → 1（已支付待成团），写 `pay_time`
2. `payment.status` 0 → 1，写 `callback_time`
3. 调 **A 的活动服务**更新 `activity.current_num` → 触发成团判定 #20

---

### 9. 我的订单 —— C

```
GET /api/orders
```

权限：登录用户（只能看自己的；`ROLE_ADMIN` 可看全部）

| 参数 | 类型 | 说明 |
|---|---|---|
| status | Integer | 0待支付 1已支付 2已成团 3已提货 4已取消 5已退款 |
| pageNum / pageSize | Integer | 分页 |

响应 `data`：`PageResult<OrderVO>`

```json
{
  "total": 5, "pages": 1,
  "list": [
    { "orderNo": "GB...", "activityTitle": "周末水果团", "goodsName": "泰国金枕榴莲",
      "num": 1, "payAmount": 89.00, "status": 2,
      "pickupCode": "PC20261021ABCD1234",
      "pointName": "阳光小区自提点",
      "createTime": "2026-10-21 10:01:00", "payTime": "2026-10-21 10:02:31" }
  ]
}
```

> `pickupCode` 仅在 `status = 2`（已成团待提货）时返回，未成团为 `null`。

---

### 10. 团长核销（一次性，幂等）—— C

```
POST /api/pickup-codes/{code}/verify
```

权限：`ROLE_LEADER`（且必须是该自提点的团长）

请求：无 body；`code` 走路径参数

响应 `data`：

```json
{ "code": "PC20261021ABCD1234", "orderNo": "GB...", "goodsName": "泰国金枕榴莲", "num": 1, "verifyTime": "2026-10-23 15:20:00" }
```

**幂等实现（必写）**：

```sql
UPDATE pickup_code SET status=1, verify_time=NOW(), verifier_id=?
WHERE code=? AND status=0
```

- 影响行数 = 1 → 核销成功
- 影响行数 = 0 → 说明已核销或码无效 → 返回 10010，不报错、不改数据

| 情况 | 错误码 |
|---|---|
| 码不存在 | 10009 |
| 码已核销 | 10010 |
| 不是本自提点的团长 | 403 |

> **压测验收（#29）**：同一个码连续核销 5 次，**只有第 1 次成功**，后 4 次返回 10010，库存/订单状态不被二次修改。

---

### 11. 自提点管理 —— A

```
GET    /api/pickup-points          # 列表（status=1 的启用项，不分页或简单分页）
POST   /api/pickup-points          # 新增（ROLE_ADMIN）
PUT    /api/pickup-points/{id}     # 修改（ROLE_ADMIN）
DELETE /api/pickup-points/{id}     # 停用（逻辑删除，status 置 0）
```

请求（POST/PUT）：

```json
{ "name": "阳光小区自提点", "address": "xx路xx号", "contact": "王团长", "phone": "13900139000" }
```

响应 `data`：`{ "id": 1, "name": "...", "status": 1 }` / `PageResult<PickupPointVO>`

---

### 12. 团长绑定自提点 + 核销码生成 —— A

```
PUT /api/leaders/{id}/bind-point
```

权限：`ROLE_ADMIN`

请求：`{ "pointId": 1 }`

规则：
- `leader.point_id` 有唯一索引 `uk_point_id`，已被别的团长绑定 → 返回 10016
- 绑定成功后**不立即生成核销码**；核销码在**成团时**由 A 提供的 `PickupCodeService.generate(orderId)` 生成

**核销码生成规则（A 定，C 只调用）**：

| 项 | 约定 |
|---|---|
| 格式 | `PC` + yyyyMMdd + 8 位随机大写字母数字，如 `PC20261021ABCD1234` |
| 唯一性 | `pickup_code.uk_code` + `uk_order_id`（一单一码） |
| 生成时机 | 成团判定成功（活动 status → 2）时，批量给该活动所有已支付订单生成 |
| 谁调用 | C 在成团逻辑里调 `pickupCodeService.generateForActivity(activityId)` |

---

### 13. 成团率统计 —— D

```
GET /api/admin/statistics/group-rate
```

权限：`ROLE_ADMIN`

| 参数 | 说明 |
|---|---|
| startDate / endDate | 统计区间（按活动创建时间，默认近 30 天） |

响应 `data`：

```json
{ "totalActivity": 120, "successActivity": 86, "failedActivity": 24, "ongoingActivity": 10, "groupRate": "71.67%" }
```

---

### 14. 退款对账 —— D

```
GET /api/admin/statistics/refund-reconciliation
```

权限：`ROLE_ADMIN`

响应 `data`：

```json
{
  "refundTotal": 24, "refundAmount": 2136.00,
  "mismatchCount": 0,
  "details": [ { "refundNo": "RF...", "orderNo": "GB...", "amount": 89.00, "status": 1, "refundTime": "..." } ]
}
```

> `mismatchCount`：退款单金额与订单实付金额不一致的条数（正常应为 0，是"对账"的意义所在）。

---

### 15. 团长业绩排行 —— D

```
GET /api/admin/statistics/leader-ranking
```

权限：`ROLE_ADMIN`

| 参数 | 说明 |
|---|---|
| limit | 返回前 N 名，默认 10 |

响应 `data`：

```json
{ "list": [ { "leaderId": 3, "leaderName": "王团长", "community": "阳光小区", "activityCount": 15, "orderCount": 620, "gmv": 55180.00, "groupRate": "80.00%" } ] }
```

---

### 16. Excel 导出 —— D

```
GET /api/admin/statistics/export?type=activity|order|refund
```

权限：`ROLE_ADMIN`

响应：**文件流**（`Content-Type: application/vnd.openxmlformats-officedocument.spreadsheetml.sheet`），不套 `R` 包装

> 用 EasyExcel；导出数据量 ≥ 300 行（演示数据保证够），大文件用 `SXSSF` 流式写。

---

### 17. 活动下架 —— D（调 A 的服务）

```
POST /api/admin/activities/{id}/offline
```

权限：`ROLE_ADMIN`

请求：`{ "reason": "违规" }`（可选）

规则：
- 只能从 1（进行中）/ 2（已成团）下架到 4（已下架）；非法流转报 10014
- 下架时若该活动有待支付订单 → 触发 C 的取消/退款逻辑
- D **不要直接写 `activity` 表的 SQL**，统一调 A 的 `ActivityService.offline(id, reason)`（避免两个人改同一张表冲突）

---

### 18. 拼团进度推送（加分项）—— C

```
GET /api/activities/{id}/progress
```

权限：无

响应（`text/event-stream`，SSE）：

```
data: {"activityId":1,"currentNum":37,"targetNum":50,"percent":"74.00%","status":1}

data: {"activityId":1,"currentNum":38,"targetNum":50,"percent":"76.00%","status":1}
```

> 备选实现：前端 3 秒轮询 `GET /api/activities/{id}` 的 `currentNum` 字段即可，SSE 做出来算 bonus（对应 Issue #35）。

---

## 四、订单状态与活动状态对照

| order_info.status | 含义 | 由谁流转 |
|---|---|---|
| 0 | 待支付 | B 下单创建 |
| 1 | 已支付待成团 | C 支付回调 |
| 2 | 已成团待提货 | C 成团判定 |
| 3 | 已提货 | C 核销成功 |
| 4 | 已取消 | 超时未支付（B 的下单超时 / C 的定时任务） |
| 5 | 已退款 | C 批量退款 |

| activity.status | 含义 |
|---|---|
| 0 | 待开始 |
| 1 | 进行中 |
| 2 | 已成团 |
| 3 | 已失败 |
| 4 | 已下架 |

---

## 五、冻结后的变更流程

1. 发现要改字段 → **开 Issue**，标题格式 `接口变更：#7 下单接口增加 couponId`
2. 群里 @ 相关人（改 #7 要 @B，改 #10 要 @C）
3. A 更新本文件 → 版本号 +1（v1.0 → v1.1）→ 在 Issue 里回复"已更新"
4. 各自同步改自己的 DTO / Mapper，**完成后在 Issue 里打勾**

---

## 六、接口到人对照（写代码时认准自己的）

| 人 | 接口编号 | 数量 |
|---|---|---|
| **A** | #3 #4 #5 #6 #11 #12 | 6 |
| **B** | #7 | 1（最难的一个） |
| **C** | #8 #9 #10 #18 | 4 |
| **D** | #1 #2 #13 #14 #15 #16 #17 | 7 |

> B、C 接口少是因为他们分别要写**报告书全文**和**演示视频**；A 和 D 接口数持平，代码量正好平分。
