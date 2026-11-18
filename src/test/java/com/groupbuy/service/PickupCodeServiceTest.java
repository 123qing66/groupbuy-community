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
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 核销码生成单元测试（A 生成 / C 核销，D 在 #26 里补测试）。
 *
 * <p>码是线下取货的唯一凭据，格式错了团长就核不了销，所以这里的断言盯三件事：</p>
 * <ol>
 *   <li>格式必须是 {@code PC + yyyyMMdd + 8 位}，团长肉眼可见今天的日期；</li>
 *   <li>字符集里<b>不能出现 0/O/1/I</b> —— 手抄码时最容易混的四个字符；</li>
 *   <li>批量生成要按 pointId 统一归属，空列表不能打到 Mapper。</li>
 * </ol>
 */
@ExtendWith(MockitoExtension.class)
class PickupCodeServiceTest {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");

    /** 与 CHARS 常量对齐：去掉了 0 O 1 I */
    private static final String ALLOWED = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

    @Mock
    private PickupCodeMapper pickupCodeMapper;

    @InjectMocks
    private PickupCodeService pickupCodeService;

    @Test
    @DisplayName("单个生成：格式 PC+当天日期+8位，且字符集不含 0/O/1/I")
    void generate_shouldFollowFormat() {
        when(pickupCodeMapper.insert(any(PickupCode.class))).thenReturn(1);

        String code = pickupCodeService.generate(100L, "GB20261118001", 2L, 1L);

        String today = LocalDate.now().format(DATE_FMT);
        assertTrue(code.startsWith("PC" + today), "前缀应为 PC+当天日期：" + code);
        assertEquals(2 + 8 + 8, code.length(), "PC(2) + 日期(8) + 随机(8)");

        String random = code.substring(10);
        for (char c : random.toCharArray()) {
            assertTrue(ALLOWED.indexOf(c) >= 0, "出现了易混淆字符：" + c + " in " + code);
            assertFalse("0O1I".indexOf(c) >= 0, "不允许 0/O/1/I：" + code);
        }
    }

    @Test
    @DisplayName("单个生成：入库的字段与入参一致（orderId/orderNo/userId/pointId 全部透传）")
    void generate_shouldPassAllFieldsToMapper() {
        when(pickupCodeMapper.insert(any(PickupCode.class))).thenReturn(1);

        String code = pickupCodeService.generate(100L, "GB20261118001", 2L, 1L);

        ArgumentCaptor<PickupCode> captor = ArgumentCaptor.forClass(PickupCode.class);
        verify(pickupCodeMapper).insert(captor.capture());
        PickupCode saved = captor.getValue();
        assertEquals(code, saved.getCode());
        assertEquals(100L, saved.getOrderId());
        assertEquals("GB20261118001", saved.getOrderNo());
        assertEquals(2L, saved.getUserId());
        assertEquals(1L, saved.getPointId());
    }

    @Test
    @DisplayName("单个生成：连续 500 个码不重复（撞码会让 uk_code 直接插入失败）")
    void generate_shouldNotCollide() {
        when(pickupCodeMapper.insert(any(PickupCode.class))).thenReturn(1);

        Set<String> codes = new HashSet<>();
        for (int i = 0; i < 500; i++) {
            codes.add(pickupCodeService.generate((long) i, "GB" + i, 2L, 1L));
        }
        assertEquals(500, codes.size(), "500 次生成出现重复码");
    }

    @Test
    @DisplayName("批量生成：空列表/ null 返回 0，不打 Mapper")
    void generateBatch_empty_shouldReturnZero() {
        assertEquals(0, pickupCodeService.generateBatch(1L, null));
        assertEquals(0, pickupCodeService.generateBatch(1L, List.of()));
        verify(pickupCodeMapper, never()).insertBatch(anyList());
    }

    @Test
    @DisplayName("批量生成：一单一码，全部挂到同一个自提点，返回值取 Mapper 影响行数")
    void generateBatch_shouldBuildOneCodePerSeed() {
        List<PickupCodeService.CodeSeed> seeds = List.of(
                seed(11L, "GB001", 2L),
                seed(12L, "GB002", 3L),
                seed(13L, "GB003", 4L));
        when(pickupCodeMapper.insertBatch(anyList())).thenReturn(3);

        int rows = pickupCodeService.generateBatch(1L, seeds);

        assertEquals(3, rows);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<PickupCode>> captor = ArgumentCaptor.forClass(List.class);
        verify(pickupCodeMapper).insertBatch(captor.capture());
        List<PickupCode> saved = captor.getValue();
        assertEquals(3, saved.size());
        assertEquals("GB001", saved.get(0).getOrderNo());
        assertEquals(11L, saved.get(0).getOrderId());
        assertEquals(1L, saved.get(0).getPointId(), "pointId 应统一取入参");
        assertNotEquals(saved.get(0).getCode(), saved.get(1).getCode());
    }

    private static PickupCodeService.CodeSeed seed(Long orderId, String orderNo, Long userId) {
        PickupCodeService.CodeSeed seed = new PickupCodeService.CodeSeed();
        seed.setOrderId(orderId);
        seed.setOrderNo(orderNo);
        seed.setUserId(userId);
        return seed;
    }
}
