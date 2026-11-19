package com.groupbuy.dto;

import com.alibaba.excel.annotation.ExcelIgnore;
import com.alibaba.excel.annotation.ExcelProperty;
import com.alibaba.excel.annotation.format.NumberFormat;
import com.alibaba.excel.annotation.write.style.ColumnWidth;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 活动明细导出行（D 负责，Issue #24，接口 #16）。
 *
 * <p>为什么单独建行模型而不复用 {@code ActivityVO}：导出的列名、列序、以及"状态要中文"
 * 都是文件契约的一部分，和接口 JSON 契约会各自演进；复用会导致改接口把报表改坏。</p>
 *
 * <p>{@code id} 只用来做游标分页（{@code WHERE id > afterId}），不出现在 Excel 里。</p>
 */
@Data
@ColumnWidth(20)
public class ActivityExportRow {

    @ExcelIgnore
    private Long id;

    @ExcelProperty("活动ID")
    private Long activityId;

    @ExcelProperty("活动名称")
    private String title;

    @ExcelProperty("团长")
    private String leaderName;

    @ExcelProperty("小区")
    private String community;

    @ExcelProperty("自提点")
    private String pointName;

    @ExcelProperty("目标人数")
    private Integer targetNum;

    @ExcelProperty("已参团人数")
    private Integer currentNum;

    @ExcelProperty("团购价(元)")
    @NumberFormat("#,##0.00")
    private BigDecimal groupPrice;

    /** 原始状态码，导出前翻成中文，不直接出现在文件里 */
    @ExcelIgnore
    private Integer status;

    @ExcelProperty("状态")
    private String statusText;

    @ExcelProperty("开始时间")
    private String startTime;

    @ExcelProperty("结束时间")
    private String endTime;
}
