package com.example.smartkb.common;

/**
 * 用户上下文 —— 通过 ThreadLocal 持有当前请求的用户 ID。
 * <p>
 * 由 {@link com.example.smartkb.user.interceptor.JwtAuthenticationInterceptor}
 * 在请求进入时写入，请求结束时清理，避免手动传递 userId 参数。
 */
public final class UserContext {

    private static final ThreadLocal<Long> USER_ID_HOLDER = new ThreadLocal<>();

    private UserContext() {
    }

    /** 设置当前用户 ID（由拦截器调用） */
    public static void setUserId(Long userId) {
        if (userId == null) {
            throw new IllegalArgumentException("userId 不能为空");
        }
        USER_ID_HOLDER.set(userId);
    }

    /** 获取当前用户 ID，可能为 null（未登录状态） */
    public static Long getUserId() {
        return USER_ID_HOLDER.get();
    }

    /** 获取当前用户 ID，若未登录则抛出业务异常 */
    public static Long requireUserId() {
        Long userId = getUserId();
        if (userId == null) {
            throw new BusinessException(ErrorCode.USER_NOT_LOGGED_IN, "用户未登录");
        }
        return userId;
    }

    /** 清理当前用户 ID（由拦截器在请求完成后调用） */
    public static void clear() {
        USER_ID_HOLDER.remove();
    }
}

