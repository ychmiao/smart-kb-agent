package com.example.smartkb.user.interceptor;

import com.example.smartkb.common.BusinessException;
import com.example.smartkb.common.ErrorCode;
import com.example.smartkb.common.UserContext;
import com.example.smartkb.user.util.JwtUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * JWT 认证拦截器 —— 从请求头提取并校验 Access Token，设置用户上下文。
 * <p>
 * 拦截路径：{@code /api/**}（排除 {@code /api/auth/register}、{@code login}、{@code refresh}）。
 * 校验通过后将 userId 写入 {@link UserContext}，后续业务代码通过
 * {@code UserContext.requireUserId()} 获取当前登录用户。
 */
@Component
public class JwtAuthenticationInterceptor implements HandlerInterceptor {

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtUtils jwtUtils;

    public JwtAuthenticationInterceptor(JwtUtils jwtUtils) {
        this.jwtUtils = jwtUtils;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // 从请求头提取 Authorization: Bearer xxx
        String authorization = request.getHeader(AUTHORIZATION_HEADER);
        if (authorization == null || !authorization.startsWith(BEARER_PREFIX)) {
            throw new BusinessException(ErrorCode.USER_NOT_LOGGED_IN, "用户未登录");
        }
        // 提取 Access Token 并解析 userId
        String accessToken = authorization.substring(BEARER_PREFIX.length()).trim();
        if (accessToken.isEmpty()) {
            throw new BusinessException(ErrorCode.TOKEN_INVALID, "Token 无效或已过期");
        }
        // 将 userId 写入 ThreadLocal，供后续请求处理使用
        UserContext.setUserId(jwtUtils.parseAccessToken(accessToken));
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler,
                                Exception exception) {
        // 请求完成后必须清理 ThreadLocal，防止内存泄漏和线程污染
        UserContext.clear();
    }
}

