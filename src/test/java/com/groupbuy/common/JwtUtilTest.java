package com.groupbuy.common;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * JWT 与密码加密的纯逻辑单测（D 负责）。
 *
 * <p>不连数据库、不连 Redis，CI 上必过，用来守住鉴权这块最容易出事的底线。</p>
 */
class JwtUtilTest {

    private final JwtUtil jwtUtil = new JwtUtil("unit-test-secret", 24);

    @Test
    void 签发后能解析出用户信息() {
        String token = jwtUtil.create(7L, "leader1", "ROLE_LEADER,ROLE_RESIDENT");

        JwtUtil.Claims claims = jwtUtil.parse(token);
        assertEquals(7L, claims.getUserId());
        assertEquals("leader1", claims.getUsername());
        assertTrue(claims.hasRole("ROLE_LEADER"));
        assertFalse(claims.hasRole("ROLE_ADMIN"));
        // JWT 三段结构
        assertEquals(3, token.split("\\.").length);
    }

    @Test
    void 篡改签名必须被拒() {
        String token = jwtUtil.create(7L, "leader1", "ROLE_LEADER");
        String tampered = token.substring(0, token.length() - 2) + "xy";

        BizException ex = assertThrows(BizException.class, () -> jwtUtil.parse(tampered));
        assertEquals(ErrorCode.UNAUTHORIZED.getCode(), ex.getCode());
    }

    @Test
    void 换个密钥签发的token不能通过校验() {
        String token = new JwtUtil("another-secret", 24).create(7L, "leader1", "ROLE_LEADER");

        assertThrows(BizException.class, () -> jwtUtil.parse(token));
    }

    @Test
    void 过期token必须被拒() {
        // 有效期传负数 → 签发出来就是过期的
        JwtUtil expiredJwt = new JwtUtil("unit-test-secret", -1);
        String token = expiredJwt.create(7L, "leader1", "ROLE_LEADER");

        BizException ex = assertThrows(BizException.class, () -> jwtUtil.parse(token));
        assertEquals(ErrorCode.UNAUTHORIZED.getCode(), ex.getCode());
    }

    @Test
    void 格式不对的token必须被拒() {
        assertThrows(BizException.class, () -> jwtUtil.parse("abc"));
        assertThrows(BizException.class, () -> jwtUtil.parse(""));
        assertThrows(BizException.class, () -> jwtUtil.parse(null));
    }

    @Test
    void BCrypt_同密码两次哈希不同但都能匹配() {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(10);
        String raw = "123456";

        String hash1 = encoder.encode(raw);
        String hash2 = encoder.encode(raw);

        // 自带随机盐，所以两次结果必须不一样
        assertNotEquals(hash1, hash2);
        // 但都能验证通过，且错误密码不通过
        assertTrue(encoder.matches(raw, hash1));
        assertTrue(encoder.matches(raw, hash2));
        assertFalse(encoder.matches("1234567", hash1));
        // 哈希不等于明文
        assertNotEquals(raw, hash1);
    }
}
