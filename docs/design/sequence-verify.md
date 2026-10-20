# 时序图：支付回调 → 成团 → 核销（C 负责，Issue #19—#21）

```mermaid
sequenceDiagram
    autonumber
    participant P as 第三方(模拟)
    participant PC as PayController
    participant IR as idempotent_record
    participant AS as ActivityService(A提供)
    participant PCS as PickupCodeService(A提供)
    participant VM as PickupCodeMapper
    participant L as 团长

    P->>PC: POST /api/orders/{id}/pay {orderNo, tradeNo, sign}
    PC->>PC: 验签 MD5(orderNo+tradeNo+amount+secret)
    alt 验签失败
        PC-->>P: 10007 验签失败
    else 验签通过
        PC->>IR: insert(PAY_CALLBACK, tradeNo)
        alt 唯一键冲突 = 重复回调
            IR-->>PC: DuplicateKey
            PC-->>P: 返回首次结果（幂等）
        else 首次回调
            PC->>PC: order.status 0→1，payment.status→1
            PC->>AS: incrCurrentNum(activityId, 1)
            AS->>AS: 判成团：currentNum >= targetNum ?
            alt 成团（只判一次，CAS）
                AS-->>PC: status → 2
                PC->>PCS: generateBatch(pointId, 已支付订单列表)
                PCS->>VM: insertBatch（uk_order_id 兜底）
            end
            PC-->>P: {orderStatus:1, payTime}
        end
    end

    L->>VM: POST /api/pickup-codes/{code}/verify
    VM->>VM: UPDATE ... SET status=1 WHERE code=? AND status=0
    alt 影响行数 = 1
        VM-->>L: 核销成功（返回商品/数量/时间）
    else 影响行数 = 0
        VM-->>L: 10010 核销码已使用（幂等，无副作用）
    end
```

## 三处幂等（报告第 5 章亮点）

| 场景 | 幂等落点 | 验收 |
|---|---|---|
| 支付回调重放 | `payment.uk_trade_no` + `idempotent_record(biz_type,biz_key)` | 同 trade_no 两次只入账一次 |
| 成团重复判定 | `activity.version` 乐观锁 + 状态机（2 是终态） | 只加一次人数、只发一次码 |
| 核销重复提交 | `UPDATE ... WHERE status=0` 影响行数判断 | #29 同码 5 次仅 1 次成功 |
