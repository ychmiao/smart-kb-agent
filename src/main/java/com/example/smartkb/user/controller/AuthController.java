package com.example.smartkb.user.controller;

import com.example.smartkb.common.Result;
import com.example.smartkb.user.dto.LoginRequest;
import com.example.smartkb.user.dto.RefreshTokenRequest;
import com.example.smartkb.user.dto.RegisterRequest;
import com.example.smartkb.user.service.UserService;
import com.example.smartkb.user.vo.TokenResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 认证 Controller —— 注册、登录、令牌刷新。
 * <p>
 * 这三个接口不在 JWT 拦截器的拦截范围内（{@link com.example.smartkb.config.WebMvcConfig} 已排除）。
 * 注册和登录均使用 BCrypt 加密/校验密码，登录成功返回 Access Token + Refresh Token。
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserService userService;

    public AuthController(UserService userService) {
        this.userService = userService;
    }

    /** 用户注册：BCrypt 加密密码，返回用户 ID */
    @PostMapping("/register")
    public Result<Long> register(@Valid @RequestBody RegisterRequest request) {
        return Result.success(userService.register(request));
    }

    /** 用户登录：校验用户名密码，返回令牌对 */
    @PostMapping("/login")
    public Result<TokenResponse> login(@Valid @RequestBody LoginRequest request) {
        return Result.success(userService.login(request));
    }

    /** 刷新令牌：使用 Refresh Token 换取全新令牌对 */
    @PostMapping("/refresh")
    public Result<TokenResponse> refreshToken(@Valid @RequestBody RefreshTokenRequest request) {
        return Result.success(userService.refreshToken(request.getRefreshToken()));
    }
}

