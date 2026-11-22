package com.groupbuy.service;

import com.groupbuy.entity.PickupCode;
import com.groupbuy.mapper.PickupCodeMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 核销码服务单测（A 负责，#12 生成侧）。
 *
 * <p>重点验证格式约定 {@code PC + yyyyMMdd + 8位}，以及批量生成"一单一码、不重复"。</p>
 */
@ExtendWith(MockitoExtension.class)
class PickupCodeServiceTest {

    @Mock private PickupCodeMapper pickupCodeMapper;

    @InjectMocks private PickupCodeService pickupCodeService;

    /** PC + 8位日期 + 8位（去掉 0/O/1/I 的大写字母数字） */
    private static final Pattern CODE_PATTERN =
            Pattern.compile("^PC\\d{8}[ABCDEFGHJKLMNPQRSTUVWXYZ23456789]{8}$");

    private PickupCodeService.CodeSeed seed(Long orderId, String orderNo) {
        PickupCodeService.CodeSeed s = new PickupCodeService.CodeSeed();
        s.setOrderId(orderId);
        s.setOrderNo(orderNo);
        s.setUserId(7L);
        return s;
    }

    @Test
    @DisplayName("#12 单个生成：格式正确，字段回填完整")
    void generate() {
        when(pickupCodeMapper.insert(org.mockito.ArgumentMatchers.any(PickupCode.class)))
                .thenReturn(1);

        String code = pickupCodeService.generate(100L, "GB2026102100012345", 7L, 1L);

        assertTrue(CODE_PATTERN.matcher(code).matches(), "核销码格式不符: " + code);
        assertTrue(code.startsWith("PC" + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"))));

        ArgumentCaptor<PickupCode> cap = ArgumentCaptor.forClass(PickupCode.class);
        verify(pickupCodeMapper).insert(cap.capture());
        assertEquals(code, cap.getValue().getCode());
        assertEquals(100L, cap.getValue().getOrderId());
        assertEquals("GB2026102100012345", cap.getValue().getOrderNo());
        assertEquals(7L, cap.getValue().getUserId());
        assertEquals(1L, cap.getValue().getPointId());
    }

    @Test
    @DisplayName("#12 批量生成：一单一码、码不重复、pointId 一致")
    void generateBatch() {
        List<PickupCodeService.CodeSeed> seeds = List.of(
                seed(100L, "GB001"), seed(101L, "GB002"), seed(102L, "GB003"));
        when(pickupCodeMapper.insertBatch(anyList())).thenReturn(3);

        int rows = pickupCodeService.generateBatch(9L, seeds);

        assertEquals(3, rows);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<PickupCode>> cap = ArgumentCaptor.forClass(List.class);
        verify(pickupCodeMapper).insertBatch(cap.capture());
        List<PickupCode> codes = cap.getValue();
        assertEquals(3, codes.size());

        Set<String> unique = new HashSet<>();
        Set<Long> orderIds = new HashSet<>();
        for (PickupCode c : codes) {
            assertTrue(CODE_PATTERN.matcher(c.getCode()).matches(), "格式不符: " + c.getCode());
            assertTrue(unique.add(c.getCode()), "出现重复核销码: " + c.getCode());
            assertTrue(orderIds.add(c.getOrderId()), "一单多码: " + c.getOrderId());
            assertEquals(9L, c.getPointId());
        }
    }

    @Test
    @DisplayName("#12 空列表：不生成、不落库")
    void generateBatchEmpty() {
        assertEquals(0, pickupCodeService.generateBatch(9L, List.of()));
        assertEquals(0, pickupCodeService.generateBatch(9L, null));
        verify(pickupCodeMapper, never()).insertBatch(anyList());
    }

    @Test
    @DisplayName("#12 大量生成：随机码碰撞概率可忽略（1000 个全不同）")
    void generateManyNoCollision() {
        List<PickupCodeService.CodeSeed> seeds = new ArrayList<>();
        for (long i = 0; i < 1000; i++) {
            seeds.add(seed(i, "GB" + i));
        }
        when(pickupCodeMapper.insertBatch(anyList())).thenReturn(seeds.size());

        pickupCodeService.generateBatch(9L, seeds);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<PickupCode>> cap = ArgumentCaptor.forClass(List.class);
        verify(pickupCodeMapper).insertBatch(cap.capture());
        Set<String> unique = new HashSet<>();
        for (PickupCode c : cap.getValue()) {
            unique.add(c.getCode());
        }
        assertEquals(1000, unique.size(), "1000 个核销码应全部唯一");
        assertNotEquals(0, unique.size());
    }
}
