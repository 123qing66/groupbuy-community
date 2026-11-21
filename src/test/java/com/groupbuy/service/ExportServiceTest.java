package com.groupbuy.service;

import com.groupbuy.common.BizException;
import com.groupbuy.common.ErrorCode;
import com.groupbuy.dto.OrderExportRow;
import com.groupbuy.mapper.StatisticsMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletResponse;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link ExportService} 单元测试（D 负责，Issue #24）。
 *
 * <p>用 {@link MockHttpServletResponse} 接住输出字节，只验"写出来的是不是一个 xlsx"、
 * 响应头对不对、非法参数有没有提前拦截；Excel 内部行列内容由真实导出后解压 xlsx 核对
 * （Mockito 看不到 POI 的写字细节，硬测反而脆弱）。</p>
 */
@ExtendWith(MockitoExtension.class)
class ExportServiceTest {

    @Mock
    private StatisticsMapper statisticsMapper;

    @InjectMocks
    private ExportService exportService;

    @Test
    @DisplayName("type 大小写与首尾空格都能识别")
    void ofShouldNormalizeInput() {
        assertEquals(ExportService.ExportType.ORDER, ExportService.ExportType.of("ORDER"));
        assertEquals(ExportService.ExportType.ACTIVITY, ExportService.ExportType.of(" activity "));
        assertEquals(ExportService.ExportType.REFUND, ExportService.ExportType.of("Refund"));
    }

    @Test
    @DisplayName("type 为空或非法时报参数错误，且提示可选值")
    void ofShouldRejectInvalidInput() {
        BizException nullEx = assertThrows(BizException.class, () -> ExportService.ExportType.of(null));
        assertEquals(ErrorCode.PARAM_ERROR.getCode(), nullEx.getCode());
        assertTrue(nullEx.getMessage().contains("activity / order / refund"));

        assertThrows(BizException.class, () -> ExportService.ExportType.of("   "));
        assertThrows(BizException.class, () -> ExportService.ExportType.of("orders"));
    }

    @Test
    @DisplayName("导出订单：输出 xlsx 字节流，带 Content-Disposition 与中文文件名")
    void exportShouldWriteXlsxStream() throws Exception {
        OrderExportRow row = new OrderExportRow();
        row.setId(1L);
        row.setOrderNo("GB20261020000001");
        row.setUserName("李小明");
        row.setActivityTitle("赣南脐橙");
        row.setGoodsName("赣南脐橙 10 斤装");
        row.setNum(1);
        row.setPayAmount(new BigDecimal("39.90"));
        row.setStatus(2);
        row.setPayTime("2026-10-20 09:03:00");
        row.setCreateTime("2026-10-20 08:00:00");

        // 返回不足一批（< 500）即结束，验证分页循环能正常收敛
        when(statisticsMapper.selectExportOrders(0L, 500)).thenReturn(List.of(row));

        MockHttpServletResponse response = new MockHttpServletResponse();
        exportService.export("order", response);

        byte[] bytes = response.getContentAsByteArray();
        assertTrue(bytes.length > 0, "应当写出文件内容");
        // xlsx 本质是 zip，前两个字节固定为 PK
        assertEquals('P', (char) bytes[0]);
        assertEquals('K', (char) bytes[1]);
        assertTrue(response.getContentType().startsWith("application/vnd.openxmlformats"));
        assertTrue(response.getHeader("Content-Disposition").contains("filename*=UTF-8''"));
        verify(statisticsMapper).selectExportOrders(0L, 500);
    }

    @Test
    @DisplayName("非法 type 直接抛错，响应体不写任何字节（还能正常返回 JSON 错误）")
    void exportShouldFailBeforeWritingBytes() {
        MockHttpServletResponse response = new MockHttpServletResponse();

        BizException ex = assertThrows(BizException.class, () -> exportService.export("nope", response));

        assertEquals(ErrorCode.PARAM_ERROR.getCode(), ex.getCode());
        assertEquals(0, response.getContentAsByteArray().length, "必须先校验再写流，否则无法回滚成 JSON 错误");
        assertNull(response.getContentType());
    }
}
