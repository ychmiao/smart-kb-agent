package com.example.smartkb.user.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.example.smartkb.user.dto.LoginRequest;
import com.example.smartkb.user.dto.RegisterRequest;
import com.example.smartkb.user.entity.User;
import com.example.smartkb.user.vo.TokenResponse;

public interface UserService extends IService<User> {

    Long register(RegisterRequest request);

    TokenResponse login(LoginRequest request);

    TokenResponse refreshToken(String refreshToken);
}

