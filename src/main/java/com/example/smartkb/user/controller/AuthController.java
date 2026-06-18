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

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserService userService;

    public AuthController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping("/register")
    public Result<Long> register(@Valid @RequestBody RegisterRequest request) {
        return Result.success(userService.register(request));
    }

    @PostMapping("/login")
    public Result<TokenResponse> login(@Valid @RequestBody LoginRequest request) {
        return Result.success(userService.login(request));
    }

    @PostMapping("/refresh")
    public Result<TokenResponse> refreshToken(@Valid @RequestBody RefreshTokenRequest request) {
        return Result.success(userService.refreshToken(request.getRefreshToken()));
    }
}

