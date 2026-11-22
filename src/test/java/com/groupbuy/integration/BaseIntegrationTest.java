package com.groupbuy.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * 集成测试基类（Issue #26）。
 *
 * <p>三件事定死，后面写用例的人不用再操心：</p>
 * <ol>
 *   <li><b>真 Spring 上下文 + 真 MySQL</b>：走完整的 Controller → Interceptor →
 *       Service → MyBatis → 数据库链路，不是把 Service mock 一遍；</li>
 *   <li><b>每个用例自动回滚</b>：{@code @Transactional} 让测试数据不落到库里，
 *       反复跑也不会互相污染；</li>
 *   <li><b>Redis 按需 mock</b>：只有下单这类真依赖 Redis 的用例才在自己的类上
 *       用 {@code @MockitoBean} 顶掉，其余用例不受影响。</li>
 * </ol>
 *
 * <p>跑之前要有一个可连的 MySQL，库名 groupbuy，表结构用
 * {@code src/main/resources/db/schema.sql} 初始化；配置见
 * {@code src/test/resources/application-test.yml}（默认指向便携 MySQL 3308）。</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
public abstract class BaseIntegrationTest {

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    /** 把对象转成请求体 JSON */
    protected String json(Object body) throws Exception {
        return objectMapper.writeValueAsString(body);
    }

    /**
     * 单测/集成测试跑完必须清 ThreadLocal，否则同一线程跑下一个用例时
     * {@code UserContext} 还留着上一个用例的用户身份，会造成"莫名 200"的假绿。
     */
    @AfterEach
    void clearUserContext() {
        com.groupbuy.common.UserContext.clear();
    }
}
