# 系统架构图（A 负责，Issue #15）

> 用 Mermaid 写，GitHub 直接渲染；B 写报告第 4 章时可直接截图或转 PNG。

## 分层架构

```mermaid
graph TD
    subgraph 前端/调用方
        A1[居民端] --> G
        A2[团长端] --> G
        A3[管理后台] --> G
    end

    G([Nginx / 直接调用 :8080])

    subgraph Controller 层
        C1[AuthController - D]
        C2[ActivityController - A]
        C3[OrderController - B]
        C4[PayController / PickupController - C]
        C5[AdminController - D]
    end

    subgraph Service 层
        S1[ActivityService - A]
        S2[PickupPointService / LeaderService - A]
        S3[OrderService 下单抢购 - B]
        S4[PayService / GroupService / VerifyService - C]
        S5[AuthService / StatisticsService - D]
    end

    subgraph 基础设施
        R1[(Redis 7<br/>库存预扣/限流/缓存/锁)]
        R2[(MySQL 8<br/>13 张表)]
    end

    G --> C1 & C2 & C3 & C4 & C5
    C2 --> S1 & S2
    C3 --> S3
    C4 --> S4
    C1 & C5 --> S5

    S1 --> R1
    S1 --> R2
    S3 --> R1
    S3 --> R2
    S4 --> R1
    S4 --> R2
    S5 --> R2
```

## 包结构

```
com.groupbuy
├─ common       统一响应体 R / 错误码 / 业务异常 / 状态机 / 用户上下文   （A）
├─ config       全局异常、Redis、MyBatis 配置                          （A）
├─ redis        Lua 预扣、限流、CacheClient、分布式锁                  （A）
├─ entity       13 张表对应的实体                                      （A/B/C 各自的表）
├─ dto          18 个入参出参类（docs/api.md 冻结）                    （A）
├─ mapper       MyBatis 接口 + XML（动态 SQL）                         （各人）
├─ service      业务逻辑                                               （各人）
└─ controller   18 条接口                                              （各人）
```

## 高可用三板斧（压测取证点）

| 问题 | 解法 | 落点 | 验收 |
|---|---|---|---|
| 缓存穿透 | 空值缓存（查不到也缓存 2 分钟） | `CacheClient` | — |
| 缓存击穿 | 逻辑过期 + 互斥锁，只放一个线程回源 | `CacheClient.getWithLogicalExpire` | #28 回源 ≤1 次 |
| 缓存雪崩 | TTL ±10% 随机抖动 | `CacheClient` | — |
| 超卖 | Redis Lua 原子预扣 + DB CAS 兜底 | `stock_deduct.lua` + `StockMapper.casDeduct` | #27 零超卖 |
| 刷爆接口 | 令牌桶限流，超限 429 | `rate_limit.lua` | — |
