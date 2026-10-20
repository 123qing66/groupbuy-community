package com.groupbuy.service;

import com.groupbuy.entity.PickupCode;
import com.groupbuy.mapper.PickupCodeMapper;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * 核销码服务（A 生成，C 核销）。
 *
 * <p>生成时机：成团判定成功（活动 status → 2）时，由 C 调
 * {@link #generateBatch} 批量生成，一单一码。</p>
 *
 * <p>格式：{@code PC} + yyyyMMdd + 8 位随机大写字母数字，如 {@code PC20261021A7B3C9D1}。</p>
 *
 * <p>唯一性靠 {@code uk_code} + {@code uk_order_id} 双保险；
 * 重复调用（回调重入）会撞唯一键，调用方捕获 {@code DuplicateKeyException} 跳过即可。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PickupCodeService {

    private static final String PREFIX = "PC";
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final String CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int RANDOM_LEN = 8;

    private static final SecureRandom RANDOM = new SecureRandom();

    private final PickupCodeMapper pickupCodeMapper;

    /** 批量生成的入参：C 从 order_info 查出来后传进来 */
    @Data
    public static class CodeSeed {

        private Long orderId;

        private String orderNo;

        private Long userId;
    }

    /** 单个生成 */
    public String generate(Long orderId, String orderNo, Long userId, Long pointId) {
        PickupCode code = new PickupCode();
        code.setCode(newCode());
        code.setOrderId(orderId);
        code.setOrderNo(orderNo);
        code.setUserId(userId);
        code.setPointId(pointId);
        pickupCodeMapper.insert(code);
        return code.getCode();
    }

    /**
     * 成团时批量生成。
     *
     * @return 生成的码数量
     */
    public int generateBatch(Long pointId, List<CodeSeed> seeds) {
        if (seeds == null || seeds.isEmpty()) {
            return 0;
        }
        List<PickupCode> list = new ArrayList<>(seeds.size());
        for (CodeSeed seed : seeds) {
            PickupCode c = new PickupCode();
            c.setCode(newCode());
            c.setOrderId(seed.getOrderId());
            c.setOrderNo(seed.getOrderNo());
            c.setUserId(seed.getUserId());
            c.setPointId(pointId);
            list.add(c);
        }
        int rows = pickupCodeMapper.insertBatch(list);
        log.info("生成核销码 {} 个，自提点 pointId={}", rows, pointId);
        return rows;
    }

    public PickupCode getByCode(String code) {
        return pickupCodeMapper.selectByCode(code);
    }

    /** PC + 日期 + 8 位随机字符（去掉了易混淆的 0/O/1/I） */
    private String newCode() {
        StringBuilder sb = new StringBuilder(PREFIX)
                .append(LocalDate.now().format(DATE_FMT));
        for (int i = 0; i < RANDOM_LEN; i++) {
            sb.append(CHARS.charAt(RANDOM.nextInt(CHARS.length())));
        }
        return sb.toString();
    }
}
