package com.groupbuy.dto;

import com.alibaba.excel.annotation.ExcelIgnore;
import com.alibaba.excel.annotation.ExcelProperty;
import com.alibaba.excel.annotation.format.NumberFormat;
import com.alibaba.excel.annotation.write.style.ColumnWidth;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 退款明细导出行（D 负责，Issue #24，接口 #16）。
 *
 * <p>{@code id} 只用于游标分页，不导出。</p>
 */
@Data
@ColumnWidth(20)
public class RefundExportRow {

    @ExcelIgnore
    private Long id;

    @ExcelProperty("退款单号")
    private String refundNo;

    @ExcelProperty("订单号")
    private String orderNo;

    @ExcelProperty("支付流水号")
    private String payNo;

    @ExcelProperty("退款金额(元)")
    @NumberFormat("#,##0.00")
    private BigDecimal amount;

    @ExcelProperty("退款原因")
    private String reason;

    @ExcelIgnore
    private Integer status;

    @ExcelProperty("退款状态")
    private String statusText;

    @ExcelProperty("退款完成时间")
    private String refundTime;

    @ExcelProperty("申请时间")
    private String createTime;
}
