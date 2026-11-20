package com.groupbuy.dto;

import com.alibaba.excel.annotation.ExcelIgnore;
import com.alibaba.excel.annotation.ExcelProperty;
import com.alibaba.excel.annotation.format.NumberFormat;
import com.alibaba.excel.annotation.write.style.ColumnWidth;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 订单明细导出行（D 负责，Issue #24，接口 #16）。
 *
 * <p>{@code id} 只用于游标分页，不导出。</p>
 */
@Data
@ColumnWidth(20)
public class OrderExportRow {

    @ExcelIgnore
    private Long id;

    @ExcelProperty("订单号")
    private String orderNo;

    @ExcelProperty("用户")
    private String userName;

    @ExcelProperty("活动")
    private String activityTitle;

    @ExcelProperty("商品")
    private String goodsName;

    @ExcelProperty("份数")
    private Integer num;

    @ExcelProperty("实付金额(元)")
    @NumberFormat("#,##0.00")
    private BigDecimal payAmount;

    @ExcelIgnore
    private Integer status;

    @ExcelProperty("订单状态")
    private String statusText;

    @ExcelProperty("支付时间")
    private String payTime;

    @ExcelProperty("下单时间")
    private String createTime;
}
