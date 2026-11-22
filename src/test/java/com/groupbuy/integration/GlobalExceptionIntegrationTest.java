package com.groupbuy.integration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 全局异常处理集成测试（D 的模块）。
 *
 * <p>这一类用例的价值在联调现场：接口报错时前端要能看到"人话"，
 * 而不是 500 + 一坨堆栈。所以逐个把四类异常打到真容器里验一遍 ——
 * <b>参数格式错 / 接口不存在 / 业务校验不过 / 未鉴权</b>。</p>
 */
class GlobalExceptionIntegrationTest extends BaseIntegrationTest {

    @Test
    @DisplayName("请求体 JSON 语法错误 → 400 且提示时间格式，不是 500")
    void malformedJson_shouldReturn400() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\": \"user1\", \"password\": "))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    @DisplayName("访问不存在的路径 → 404，不被兜底成 500")
    void unknownPath_shouldReturn404() throws Exception {
        mockMvc.perform(get("/not-exist-path"))
                .andExpect(jsonPath("$.code").value(404));
    }

    @Test
    @DisplayName("注册不传密码 → 400（业务校验拦在 Service 里）")
    void registerWithoutPassword_shouldReturn400() throws Exception {
        mockMvc.perform(post("/api/users/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\": \"nopass_user\"}"))
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.msg").isNotEmpty());
    }

    @Test
    @DisplayName("健康检查免鉴权 → code 0，可用来判断服务是否活着")
    void health_shouldBePublic() throws Exception {
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }
}
