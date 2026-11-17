package com.groupbuy.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * 密码加密配置（D 负责）。
 *
 * <p>只引了 {@code spring-security-crypto} 这一个包，不引入整套 Spring Security
 * （会带一堆自动配置和过滤器链，和我们的自研 JWT 拦截器打架）。</p>
 *
 * <p>BCrypt 特点：每次加密结果都不一样（自带随机盐），所以只能
 * {@code matches(明文, 哈希)} 比对，不能反解，也不能用 SQL 比对。</p>
 */
@Configuration
public class SecurityConfig {

    /** strength=10，是性能与安全的常用折中（值越大越慢） */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(10);
    }
}
