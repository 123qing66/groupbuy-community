# 时序图：参团下单（B 负责，Issue #16）

> 三步缺一不可：限流 → Redis 预扣 → DB CAS 落库；任一步失败都要回滚预扣。
> 对应验收要点①：200 并发 1000 库存零超卖。

```mermaid
sequenceDiagram
    autonumber
    actor U as 居民
    participant C as OrderController
    participant RL as RateLimiter(rate_limit.lua)
    participant SH as StockRedisHelper(stock_deduct.lua)
    participant OM as OrderMapper
    participant SM as StockMapper
    participant DB as MySQL

    U->>C: POST /api/orders {activityId, goodsId, num}
    C->>RL: tryAcquire("order:"+userId)
    alt 令牌桶没令牌
        RL-->>C: false
        C-->>U: 429 请求过于频繁
    else 拿到令牌
        RL-->>C: true
        C->>C: 校验活动时间窗 / 限购 / 状态
        C->>SH: deduct(activityId, num)
        alt 返回 0 库存不足 / -1 未预热
            SH-->>C: 0
            C-->>U: 10003 库存不足
        else 返回 1 预扣成功
            SH-->>C: 1
            C->>DB: BEGIN
            C->>OM: insertOrder(...) 撞 uk_user_activity 则回滚
            C->>SM: casDeduct(goodsId, num) 影响行数必须=1
            alt CAS 失败
                SM-->>C: 0
                C->>SH: rollback(activityId, num)
                C->>DB: ROLLBACK
                C-->>U: 10003 库存不足
            else CAS 成功
                SM-->>C: 1
                C->>DB: COMMIT
                C-->>U: {orderNo, payAmount, expireTime}
            end
        end
    end
```

## 为什么是"Redis 预扣 + DB CAS"两层

| 层 | 作用 | 挡什么 |
|---|---|---|
| Redis Lua 预扣 | 挡住 99% 的并发请求，扛住流量 | 超卖（原子脚本内判断） |
| DB CAS `available >= num` | 最终一致性的最后防线 | Redis 故障/未预热时的数据错乱 |

> 只用 Redis：Redis 挂了就超卖；只用 DB：200 并发打 DB 会把连接池打满。两层配合才稳。
