package com.groupbuy.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 活动列表集成测试（A 的接口 #3，D 在 #26 里补集成覆盖）。
 *
 * <p>盯两件事：</p>
 * <ol>
 *   <li><b>免鉴权放行</b>：居民没登录也要能浏览团购，这是业务要求，
 *       测试用例就是这条约定的守门人 —— 哪天有人把 /api/activities 从白名单里删了，
 *       这里立刻红；</li>
 *   <li><b>动态 SQL + 分页</b>：写成真 SQL 打到真库，才能验证
 *       {@code <if>} 分支和 limit/offset 拼出来是对的（Mockito 单测验不了这个）。</li>
 * </ol>
 */
class ActivityQueryIntegrationTest extends BaseIntegrationTest {

    private static final String KEYWORD = "IT集成测试团";

    @BeforeEach
    void seedActivities() {
        insertActivity(KEYWORD + "-进行中A", 1);
        insertActivity(KEYWORD + "-进行中B", 1);
        insertActivity(KEYWORD + "-待开始", 0);
    }

    @Test
    @DisplayName("活动列表免鉴权：不带 token 也能拿到 200 + code 0")
    void listWithoutToken_shouldPass() throws Exception {
        mockMvc.perform(get("/api/activities"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    @Test
    @DisplayName("按状态 + 关键字筛选：只返回进行中的那 2 条")
    void list_filterByStatusAndKeyword() throws Exception {
        mockMvc.perform(get("/api/activities")
                        .param("status", "1")
                        .param("keyword", KEYWORD))
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.total").value(2))
                .andExpect(jsonPath("$.data.list.length()").value(2));
    }

    @Test
    @DisplayName("分页：pageSize=1 时只返回 1 条，但 total 仍是 2（前端靠 total 算页码）")
    void list_pagingShouldKeepTotal() throws Exception {
        mockMvc.perform(get("/api/activities")
                        .param("status", "1")
                        .param("keyword", KEYWORD)
                        .param("pageNum", "1")
                        .param("pageSize", "1"))
                .andExpect(jsonPath("$.data.total").value(2))
                .andExpect(jsonPath("$.data.pages").value(2))
                .andExpect(jsonPath("$.data.list.length()").value(1));
    }

    @Test
    @DisplayName("第二页：pageNum=2 拿到剩下那条，不与第一页重复")
    void list_secondPage_shouldReturnRest() throws Exception {
        String firstPageTitle = mockMvc.perform(get("/api/activities")
                        .param("status", "1").param("keyword", KEYWORD)
                        .param("pageNum", "1").param("pageSize", "1"))
                .andReturn().getResponse().getContentAsString();

        String secondPage = mockMvc.perform(get("/api/activities")
                        .param("status", "1").param("keyword", KEYWORD)
                        .param("pageNum", "2").param("pageSize", "1"))
                .andExpect(jsonPath("$.data.list.length()").value(1))
                .andReturn().getResponse().getContentAsString();

        String firstTitle = objectMapper.readTree(firstPageTitle)
                .path("data").path("list").get(0).path("title").asText();
        String secondTitle = objectMapper.readTree(secondPage)
                .path("data").path("list").get(0).path("title").asText();

        org.junit.jupiter.api.Assertions.assertNotEquals(firstTitle, secondTitle,
                "第一页和第二页返回了同一条活动");
    }

    private void insertActivity(String title, int status) {
        LocalDateTime now = LocalDateTime.now();
        jdbcTemplate.update(
                "INSERT INTO activity (title, leader_id, point_id, target_num, current_num, buy_limit, status, start_time, end_time) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                title, 1L, 1L, 10, 0, 2, status, now.minusHours(1), now.plusHours(2));
    }
}
