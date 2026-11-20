package com.groupbuy.service;

import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.ExcelWriter;
import com.alibaba.excel.write.metadata.WriteSheet;
import com.groupbuy.common.BizException;
import com.groupbuy.common.ErrorCode;
import com.groupbuy.dto.ActivityExportRow;
import com.groupbuy.dto.OrderExportRow;
import com.groupbuy.dto.RefundExportRow;
import com.groupbuy.mapper.StatisticsMapper;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * Excel 导出服务（D 负责，Issue #24，接口 #16）。
 *
 * <p>三条导出（活动 / 订单 / 退款）共用一套骨架：</p>
 * <ol>
 *   <li>校验 type，非法值在写任何字节之前就抛 400 —— 一旦开始往 response 写流，
 *       就没法再返回 {@code {code,msg,data}} 的 JSON 了；</li>
 *   <li>批量拉数据（游标分页，每批 {@value #BATCH_SIZE} 行）→ 写一批 → 再拉下一批。
 *       不把全表读进 List，行数涨到几十万也是常数内存；</li>
 *   <li>状态码在这里翻中文，SQL 只出原始 code，数据层不做展示逻辑。</li>
 * </ol>
 *
 * <p>为什么用 EasyExcel 而不用裸 POI：它底层就是 SXSSF 流式写（契约要求），
 * 注解映射省掉手写 CellStyle 和行游标管理。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExportService {

    private static final String XLSX_CONTENT_TYPE =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    /** 每批拉取行数：太小网络往返多，太大内存波动大，500 是经验值 */
    private static final int BATCH_SIZE = 500;

    private static final DateTimeFormatter FILE_STAMP = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    private static final Map<Integer, String> ACTIVITY_STATUS = Map.of(
            0, "待开始", 1, "进行中", 2, "已成团", 3, "已失败", 4, "已下架");

    private static final Map<Integer, String> ORDER_STATUS = Map.of(
            0, "待支付", 1, "已支付", 2, "已成团", 3, "已提货", 4, "已取消", 5, "已退款");

    private static final Map<Integer, String> REFUND_STATUS = Map.of(
            0, "退款中", 1, "已退款", 2, "退款失败");

    private final StatisticsMapper statisticsMapper;

    /**
     * 按类型导出并直接写入 HTTP 响应流（不套 {@code R} 包装，契约要求返回文件流）。
     *
     * @param type activity / order / refund
     * @throws BizException type 非法时抛 400，此时尚未写入任何响应字节
     */
    public void export(String type, HttpServletResponse response) throws IOException {
        ExportType exportType = ExportType.of(type);
        String fileName = exportType.label + "_" + LocalDateTime.now().format(FILE_STAMP) + ".xlsx";

        response.setContentType(XLSX_CONTENT_TYPE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setHeader("Content-Disposition", contentDisposition(fileName, exportType));

        long rows = switch (exportType) {
            case ACTIVITY -> writeActivities(response.getOutputStream());
            case ORDER -> writeOrders(response.getOutputStream());
            case REFUND -> writeRefunds(response.getOutputStream());
        };
        log.info("[导出] type={} file={} rows={}", exportType.code, fileName, rows);
    }

    private long writeActivities(OutputStream out) {
        try (ExcelWriter writer = EasyExcel.write(out, ActivityExportRow.class).build()) {
            WriteSheet sheet = EasyExcel.writerSheet("活动明细").build();
            long afterId = 0L;
            long total = 0L;
            while (true) {
                List<ActivityExportRow> batch = statisticsMapper.selectExportActivities(afterId, BATCH_SIZE);
                if (batch.isEmpty()) {
                    break;
                }
                batch.forEach(row -> row.setStatusText(text(ACTIVITY_STATUS, row.getStatus())));
                writer.write(batch, sheet);
                total += batch.size();
                if (batch.size() < BATCH_SIZE) {
                    break;
                }
                afterId = batch.get(batch.size() - 1).getId();
            }
            return total;
        }
    }

    private long writeOrders(OutputStream out) {
        try (ExcelWriter writer = EasyExcel.write(out, OrderExportRow.class).build()) {
            WriteSheet sheet = EasyExcel.writerSheet("订单明细").build();
            long afterId = 0L;
            long total = 0L;
            while (true) {
                List<OrderExportRow> batch = statisticsMapper.selectExportOrders(afterId, BATCH_SIZE);
                if (batch.isEmpty()) {
                    break;
                }
                batch.forEach(row -> row.setStatusText(text(ORDER_STATUS, row.getStatus())));
                writer.write(batch, sheet);
                total += batch.size();
                if (batch.size() < BATCH_SIZE) {
                    break;
                }
                afterId = batch.get(batch.size() - 1).getId();
            }
            return total;
        }
    }

    private long writeRefunds(OutputStream out) {
        try (ExcelWriter writer = EasyExcel.write(out, RefundExportRow.class).build()) {
            WriteSheet sheet = EasyExcel.writerSheet("退款明细").build();
            long afterId = 0L;
            long total = 0L;
            while (true) {
                List<RefundExportRow> batch = statisticsMapper.selectExportRefunds(afterId, BATCH_SIZE);
                if (batch.isEmpty()) {
                    break;
                }
                batch.forEach(row -> row.setStatusText(text(REFUND_STATUS, row.getStatus())));
                writer.write(batch, sheet);
                total += batch.size();
                if (batch.size() < BATCH_SIZE) {
                    break;
                }
                afterId = batch.get(batch.size() - 1).getId();
            }
            return total;
        }
    }

    /** 状态码翻中文；遇到表里没定义的新状态不抛异常，显示原始码，避免导出一张废表 */
    private static String text(Map<Integer, String> dict, Integer code) {
        if (code == null) {
            return "";
        }
        return dict.getOrDefault(code, "未知(" + code + ")");
    }

    /**
     * 拼 {@code Content-Disposition}：中文文件名必须走 RFC 5987 的 {@code filename*}，
     * 同时给一个纯 ASCII 的 {@code filename} 兜底（老浏览器只认前者）。
     */
    private static String contentDisposition(String fileName, ExportType type) {
        String encoded = URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20");
        return "attachment; filename=\"export-" + type.code + ".xlsx\"; filename*=UTF-8''" + encoded;
    }

    /** 支持导出的数据类型，URL 参数即 {@code code} */
    public enum ExportType {

        ACTIVITY("activity", "活动明细"),
        ORDER("order", "订单明细"),
        REFUND("refund", "退款明细");

        private final String code;
        private final String label;

        ExportType(String code, String label) {
            this.code = code;
            this.label = label;
        }

        public String getCode() {
            return code;
        }

        public String getLabel() {
            return label;
        }

        /** 解析 type 参数；空值或非法值一律 400，并提示合法取值，避免前端猜 */
        public static ExportType of(String raw) {
            if (raw == null || raw.isBlank()) {
                throw new BizException(ErrorCode.PARAM_ERROR, "type 不能为空，可选值：activity / order / refund");
            }
            String normalized = raw.trim().toLowerCase();
            for (ExportType type : values()) {
                if (type.code.equals(normalized)) {
                    return type;
                }
            }
            throw new BizException(ErrorCode.PARAM_ERROR,
                    "不支持的 type：" + raw + "，可选值：activity / order / refund");
        }
    }
}
