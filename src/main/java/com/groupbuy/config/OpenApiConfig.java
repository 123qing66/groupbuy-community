package com.groupbuy.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Knife4j / OpenAPI3 分组配置（D 负责，Issue #25）。
 *
 * <p>为什么要分组：全系统 18 个接口一次性铺在 {@code /doc.html} 里，A/B/C/D 四个人
 * 找自己的接口要翻半天，答辩演示时也不好讲。按「领域」切成 6 组之后，
 * 左上角下拉框一选就只剩自己那摊事。</p>
 *
 * <p>分组靠 <b>路径前缀</b> 匹配，不依赖 {@code @Tag}，所以各人新加 Controller
 * 只要遵守 {@code /api/xxx} 前缀就自动进组，不需要回来改这个文件。</p>
 *
 * <p>注意：{@code /api/activities/**} 会同时收走 {@code /api/activities/{id}/progress}
 * （#18 SSE，C 的加分项），这是预期的——它本来就属于活动域。</p>
 */
@Configuration
public class OpenApiConfig {

    /**
     * 文档总标题与说明。
     *
     * <p>{@code version} 与 {@code docs/api.md} 的契约版本号保持一致；契约升级到 v1.1 时
     * 这个字符串也要跟着改，否则文档页上显示的版本会误导人。</p>
     */
    @Bean
    public OpenAPI groupbuyOpenAPI() {
        return new OpenAPI().info(new Info()
                .title("社区团购拼团系统 API")
                .version("v1.0")
                .description("""
                        接口契约（冻结版）见仓库 `docs/api.md`，字段名与类型以该文件为准。

                        **统一响应体**：`{ "code": 0, "msg": "ok", "data": {...} }`

                        **错误约定**：业务错误也返回 HTTP 200，靠 `code` 区分（10001~10016）；
                        只有 401 未登录、403 无权限、404 不存在、429 限流沿用 HTTP 语义。

                        **鉴权**：除登录、注册、活动列表/详情外，均需
                        `Authorization: Bearer <token>`（登录接口返回的 `data.token`）。

                        **可直接执行的请求示例**：仓库 `docs/http/` 目录（VS Code REST Client 插件打开即可发请求）。
                        """));
    }

    /** 第 0 组：健康检查 + 登录鉴权（#1 #2，D 的接口）。 */
    @Bean
    public GroupedOpenApi authGroup() {
        return GroupedOpenApi.builder()
                .group("00-通用与鉴权")
                .pathsToMatch("/api/health", "/api/auth/**", "/api/users/**")
                .build();
    }

    /** 第 1 组：团购活动域（#3 #4 #5 #6 #18，A + C）。 */
    @Bean
    public GroupedOpenApi activityGroup() {
        return GroupedOpenApi.builder()
                .group("01-团购活动")
                .pathsToMatch("/api/activities/**")
                .build();
    }

    /** 第 2 组：自提点与团长绑定（#11 #12，A）。 */
    @Bean
    public GroupedOpenApi pointGroup() {
        return GroupedOpenApi.builder()
                .group("02-自提点与团长")
                .pathsToMatch("/api/pickup-points/**", "/api/leaders/**")
                .build();
    }

    /** 第 3 组：参团下单与支付（#7 #8 #9，B + C）。 */
    @Bean
    public GroupedOpenApi orderGroup() {
        return GroupedOpenApi.builder()
                .group("03-下单与支付")
                .pathsToMatch("/api/orders/**", "/api/pay/**")
                .build();
    }

    /** 第 4 组：核销提货（#10，C）。 */
    @Bean
    public GroupedOpenApi pickupGroup() {
        return GroupedOpenApi.builder()
                .group("04-核销提货")
                .pathsToMatch("/api/pickup-codes/**")
                .build();
    }

    /** 第 5 组：后台管理（#13 #14 #15 #16 #17，D 的接口）。 */
    @Bean
    public GroupedOpenApi adminGroup() {
        return GroupedOpenApi.builder()
                .group("05-后台管理")
                .pathsToMatch("/api/admin/**")
                .build();
    }
}
