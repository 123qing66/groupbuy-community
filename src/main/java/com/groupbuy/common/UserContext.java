package com.groupbuy.common;

/**
 * 当前登录用户的上下文（临时版，等 D 的 JWT 拦截器接上后自动生效）。
 *
 * <p>做法：ThreadLocal 存 userId。D 在 #10 的拦截器里调
 * {@code UserContext.set(userId, roles)}，controller 里 {@code UserContext.getUserId()} 即可拿到；
 * 拦截器没接上时，这里返回演示用的默认值，保证 A/C 的接口现在就能联调。</p>
 *
 * <p>注意：用完必须 {@link #clear()}，否则线程池复用会串号。</p>
 */
public final class UserContext {

    private static final ThreadLocal<Long> USER_ID = new ThreadLocal<>();
    private static final ThreadLocal<String> ROLES = new ThreadLocal<>();

    /** 演示用默认用户 id（拦截器未接入时的兜底） */
    private static final long DEFAULT_USER_ID = 1L;

    private UserContext() {
    }

    public static void set(Long userId, String roles) {
        USER_ID.set(userId);
        ROLES.set(roles);
    }

    public static Long getUserId() {
        Long id = USER_ID.get();
        return id == null ? DEFAULT_USER_ID : id;
    }

    public static String getRoles() {
        String r = ROLES.get();
        return r == null ? "" : r;
    }

    public static boolean hasRole(String role) {
        return getRoles().contains(role);
    }

    public static void clear() {
        USER_ID.remove();
        ROLES.remove();
    }
}
