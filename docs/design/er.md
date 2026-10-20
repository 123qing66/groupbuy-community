# ER 图（A 负责，Issue #15）

> 逻辑外键，不加物理 FOREIGN KEY（高并发下单会触发父表行锁，拖垮抢购性能）。

```mermaid
erDiagram
    USER ||--o{ USER_ROLE : "拥有角色"
    ROLE ||--o{ USER_ROLE : "被授予"
    USER ||--o| LEADER : "可成为团长"
    PICKUP_POINT ||--o| LEADER : "一对一绑定"
    LEADER ||--o{ ACTIVITY : "开团"
    PICKUP_POINT ||--o{ ACTIVITY : "指定自提点"
    ACTIVITY ||--o{ GOODS : "包含商品"
    GOODS ||--|| STOCK : "一对一库存"
    ACTIVITY ||--o{ ORDER_INFO : "产生订单"
    USER ||--o{ ORDER_INFO : "下单"
    GOODS ||--o{ ORDER_INFO : "被购买"
    ORDER_INFO ||--|| PAYMENT : "支付流水"
    ORDER_INFO ||--o| PICKUP_CODE : "一单一码"
    ORDER_INFO ||--o| REFUND : "退款单"

    USER {
        BIGINT id PK
        VARCHAR username UK
        VARCHAR password "BCrypt"
        VARCHAR phone
        TINYINT status
    }
    ROLE {
        BIGINT id PK
        VARCHAR role_code UK "ROLE_RESIDENT/LEADER/ADMIN"
    }
    LEADER {
        BIGINT id PK
        BIGINT user_id UK
        BIGINT point_id UK "一对一自提点"
    }
    PICKUP_POINT {
        BIGINT id PK
        VARCHAR name
        VARCHAR address
        TINYINT status
    }
    ACTIVITY {
        BIGINT id PK
        BIGINT leader_id
        BIGINT point_id
        INT target_num "成团目标"
        INT current_num "当前人数"
        INT buy_limit "限购"
        TINYINT status "0-4"
        INT version "乐观锁"
    }
    GOODS {
        BIGINT id PK
        BIGINT activity_id
        DECIMAL group_price
    }
    STOCK {
        BIGINT id PK
        BIGINT goods_id UK "唯一热点行"
        INT total
        INT available "可扣"
        INT locked "预扣中"
        INT version
    }
    ORDER_INFO {
        BIGINT id PK
        VARCHAR order_no UK
        BIGINT user_id
        BIGINT activity_id
        TINYINT status "0-5"
    }
    PAYMENT {
        BIGINT id PK
        VARCHAR pay_no UK
        VARCHAR trade_no UK "回调幂等"
    }
    PICKUP_CODE {
        BIGINT id PK
        VARCHAR code UK
        BIGINT order_id UK "一单一码"
        TINYINT status "0未核销 1已核销"
    }
    REFUND {
        BIGINT id PK
        VARCHAR refund_no UK
        VARCHAR order_no UK "防重复退款"
    }
```

## 三个唯一索引兜住并发（报告第 4 章重点）

| 索引 | 挡住的并发问题 | 谁用 |
|---|---|---|
| `order_info.uk_user_activity` | 重复下单 + 超限购 | B（#16 #17） |
| `payment.uk_trade_no` | 支付回调重放 | C（#19） |
| `refund.uk_order_no` | 同一单退两次 | C（#22） |
| `pickup_code.uk_order_id` | 成团回调重入重复发码 | A（#9） |

> 比"先查再插"可靠：那是两步操作，并发下挡不住；唯一索引是数据库层面的原子拦截。
