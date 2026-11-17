package com.groupbuy.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web MVC 配置（D 负责）。
 *
 * <p>只拦 {@code /api/**}，静态页（index.html）和接口文档（doc.html、swagger）
 * 不在该前缀下，天然放行，不用额外配。</p>
 *
 * <p>CORS 这里放开是为了 B/C/D 各自起前端（或演示页用 file:// 打开）时能直连
 * 本机 8080；**上线前要把 allowedOrigins 收敛成真实域名**，别留 "*"。</p>
 */
@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final AuthInterceptor authInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 具体哪些路径免鉴权，写在 AuthInterceptor 里（支持按 GET/POST 区分）
        registry.addInterceptor(authInterceptor).addPathPatterns("/api/**");
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOriginPatterns("*")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true)
                .maxAge(3600);
    }
}
