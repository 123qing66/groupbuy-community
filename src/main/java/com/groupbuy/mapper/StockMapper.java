package com.groupbuy.mapper;

import com.groupbuy.entity.Stock;
import org.apache.ibatis.annotations.Param;

/**
 * 库存 Mapper（A 建表并初始化，B 在 #16 用 casDeduct 做 DB 兜底）。
 *
 * <p>casDeduct 是一条带条件的 UPDATE —— {@code available >= num} 由数据库原子判断，
 * 这是"零超卖"的最后一道防线：Redis 预扣挡并发，DB CAS 挡数据一致性。</p>
 */
public interface StockMapper {

    /** 开团初始化库存：total = available，locked = 0 */
    int init(@Param("goodsId") Long goodsId, @Param("total") int total);

    Stock selectByGoodsId(@Param("goodsId") Long goodsId);

    /**
     * DB CAS 扣减：预扣成功后再落库，影响行数 0 表示库存不足。
     * 不变式：total = available + locked
     */
    int casDeduct(@Param("goodsId") Long goodsId, @Param("num") int num);

    /** 回滚：预扣后下单失败 / 超时取消时把库存还回去 */
    int casRollback(@Param("goodsId") Long goodsId, @Param("num") int num);
}
