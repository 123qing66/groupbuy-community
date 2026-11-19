package com.groupbuy.controller;

import com.groupbuy.common.BizException;
import com.groupbuy.common.ErrorCode;
import com.groupbuy.common.R;
import com.groupbuy.common.UserContext;
import com.groupbuy.dto.GroupRateVO;
import com.groupbuy.dto.RefundReconciliationVO;
import com.groupbuy.service.StatisticsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * 后台管理接口（D 负责，Issue #23 / #24）。
 *
 * <p>整个 {@code /api/admin/**} 都要求 {@code ROLE_ADMIN}。拦截器({@code AuthInterceptor})
 * 只负责把 token 解析成 roles 塞进 {@link UserContext}，不判断角色 ——
 * 所以角色校验在每个方法入口显式做一次，见 {@link #requireAdmin()}。</p>
 */
@Tag(name = "后台统计")
@RestController
@RequestMapping("/api/admin/statistics")
@RequiredArgsConstructor
public class AdminController {

    private final StatisticsService statisticsService;

    @Operation(summary = "#13 成团率统计",
            description = "按活动创建时间统计，默认近 30 天；分母不含已下架活动")
    @GetMapping("/group-rate")
    public R<GroupRateVO> groupRate(
            @RequestParam(required = false)
            @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate startDate,
            @RequestParam(required = false)
            @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate endDate) {
        requireAdmin();
        return R.ok(statisticsService.groupRate(startDate, endDate));
    }

    @Operation(summary = "#14 退款对账",
            description = "退款汇总 + 明细；mismatchCount 为退款金额与订单实付不一致的条数，正常应为 0")
    @GetMapping("/refund-reconciliation")
    public R<RefundReconciliationVO> refundReconciliation() {
        requireAdmin();
        return R.ok(statisticsService.refundReconciliation());
    }

    /**
     * 角色校验：非管理员直接 403。
     *
     * <p>抛 {@link BizException} 而不是返回 ResponseEntity 403，是因为全项目统一走
     * {@code {code,msg,data}} 的响应体，前端只认 {@code code}，混用 HTTP 状态码会让
     * 前端要写两套错误处理。</p>
     */
    private void requireAdmin() {
        if (!UserContext.hasRole("ROLE_ADMIN")) {
            throw new BizException(ErrorCode.FORBIDDEN, "需要管理员权限");
        }
    }
}
