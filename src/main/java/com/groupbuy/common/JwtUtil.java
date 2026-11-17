package com.groupbuy.common;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * JWT 工具（D 负责，接口 #1 的 token 签发与校验）。
 *
 * <p>为什么自己写而不是引 jjwt：本项目离线构建，且 JWT 的结构本来就是
 * "base64url(header).base64url(payload).base64url(HMAC-SHA256 签名)" 三段，
 * 用 JDK 自带的 {@code javax.crypto.Mac} 就能实现，零第三方依赖，
 * 答辩时也能把三段结构逐个讲清楚。</p>
 *
 * <p>安全点：
 * <ul>
 *   <li>签名比对用 {@link MessageDigest#isEqual} —— 常量时间比较，防计时攻击；</li>
 *   <li>校验顺序固定：先验签再解 payload，签名不对根本不解析内容；</li>
 *   <li>token 无状态，服务端不存；要"踢下线"再加 Redis 黑名单（进阶选做）。</li>
 * </ul>
 */
@Component
public class JwtUtil {

    private static final String ALG = "HmacSHA256";
    private static final String HEADER = "{\"alg\":\"HS256\",\"typ\":\"JWT\"}";

    private final String secret;
    private final long expireMillis;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public JwtUtil(@Value("${groupbuy.jwt.secret}") String secret,
                   @Value("${groupbuy.jwt.expire-hours:24}") long expireHours) {
        this.secret = secret;
        this.expireMillis = expireHours * 3600 * 1000L;
    }

    /** 签发的 token 载荷 */
    @Getter
    public static class Claims {
        private final Long userId;
        private final String username;
        /** 逗号分隔的角色编码，如 "ROLE_LEADER,ROLE_RESIDENT" */
        private final String roles;

        private Claims(Long userId, String username, String roles) {
            this.userId = userId;
            this.username = username;
            this.roles = roles == null ? "" : roles;
        }

        public boolean hasRole(String role) {
            return roles.contains(role);
        }
    }

    /** 签发 token */
    public String create(Long userId, String username, String roles) {
        long now = System.currentTimeMillis();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("sub", userId);
        payload.put("username", username);
        payload.put("roles", roles == null ? "" : roles);
        payload.put("iat", now / 1000);
        payload.put("exp", (now + expireMillis) / 1000);
        String body = encode(HEADER) + "." + encode(toJson(payload));
        return body + "." + sign(body);
    }

    /** 校验并解析；签名错 / 过期 / 格式错一律抛 401 */
    public Claims parse(String token) {
        if (token == null || token.isEmpty()) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        String[] parts = token.split("\\.");
        if (parts.length != 3) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "token 格式错误");
        }
        String body = parts[0] + "." + parts[1];
        String expected = sign(body);
        // 常量时间比较，别用 equals
        if (!MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
                parts[2].getBytes(StandardCharsets.UTF_8))) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "签名校验失败");
        }
        try {
            JsonNode node = objectMapper.readTree(decode(parts[1]));
            long exp = node.path("exp").asLong(0L);
            if (exp == 0L || exp * 1000 < System.currentTimeMillis()) {
                throw new BizException(ErrorCode.UNAUTHORIZED, "token 已过期，请重新登录");
            }
            return new Claims(node.path("sub").asLong(), node.path("username").asText(),
                    node.path("roles").asText(""));
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "token 解析失败");
        }
    }

    private String sign(String body) {
        try {
            Mac mac = Mac.getInstance(ALG);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), ALG));
            byte[] bytes = mac.doFinal(body.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        } catch (Exception e) {
            throw new IllegalStateException("JWT 签名失败", e);
        }
    }

    private String encode(String json) {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(json.getBytes(StandardCharsets.UTF_8));
    }

    private String decode(String segment) {
        return new String(Base64.getUrlDecoder().decode(segment), StandardCharsets.UTF_8);
    }

    private String toJson(Map<String, Object> map) {
        try {
            return objectMapper.writeValueAsString(map);
        } catch (Exception e) {
            throw new IllegalStateException("JWT 载荷序列化失败", e);
        }
    }
}
