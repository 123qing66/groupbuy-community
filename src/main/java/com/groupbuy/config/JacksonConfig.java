package com.groupbuy.config;

import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.format.DateTimeFormatter;

/**
 * 全局时间格式（A 负责，骨架层公共件，B/C/D 直接受益）。
 *
 * <p>契约 {@code docs/api.md} 里所有时间字段都写成
 * {@code yyyy-MM-dd HH:mm:ss}（<b>空格</b>分隔），但 Jackson 默认只认 ISO 的
 * {@code 2026-10-21T10:00:00}，不配全局序列化的话，前端一提交就
 * {@code HttpMessageNotReadableException} → 500。</p>
 *
 * <p>配了之后：入参认空格格式，出参也统一返回空格格式，和契约示例完全一致。</p>
 *
 * <p>注意：{@code spring.jackson.date-format} 只对 {@code java.util.Date} 生效，
 * 对 {@code LocalDateTime} 无效，所以必须写这个配置类。</p>
 */
@Configuration
public class JacksonConfig {

    /** 与 docs/api.md 示例保持一致 */
    private static final String PATTERN = "yyyy-MM-dd HH:mm:ss";

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer jacksonCustomizer() {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(PATTERN);
        return builder -> builder
                .serializers(new LocalDateTimeSerializer(formatter))
                .deserializers(new LocalDateTimeDeserializer(formatter));
    }
}
