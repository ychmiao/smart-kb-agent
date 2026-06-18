package com.example.smartkb.user.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.smartkb.common.BusinessException;
import com.example.smartkb.user.dto.LoginRequest;
import com.example.smartkb.user.dto.RegisterRequest;
import com.example.smartkb.user.entity.User;
import com.example.smartkb.user.mapper.UserMapper;
import com.example.smartkb.user.service.UserService;
import com.example.smartkb.user.util.JwtUtils;
import com.example.smartkb.user.vo.TokenResponse;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {

    private final PasswordEncoder passwordEncoder;
    private final JwtUtils jwtUtils;

    public UserServiceImpl(PasswordEncoder passwordEncoder, JwtUtils jwtUtils) {
        this.passwordEncoder = passwordEncoder;
        this.jwtUtils = jwtUtils;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long register(RegisterRequest request) {
        long count = lambdaQuery().eq(User::getUsername, request.getUsername()).count();
        if (count > 0) {
            throw new BusinessException(40901, "用户名已存在");
        }

        User user = new User();
        user.setUsername(request.getUsername());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setEmail(normalizeEmail(request.getEmail()));
        try {
            save(user);
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(40901, "用户名已存在", exception);
        }
        return user.getId();
    }

    @Override
    public TokenResponse login(LoginRequest request) {
        User user = lambdaQuery()
                .eq(User::getUsername, request.getUsername())
                .one();
        if (user == null || !passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new BusinessException(40103, "用户名或密码错误");
        }
        return createTokenResponse(user.getId());
    }

    @Override
    public TokenResponse refreshToken(String refreshToken) {
        Long userId = jwtUtils.parseRefreshToken(refreshToken);
        if (getById(userId) == null) {
            throw new BusinessException(40104, "用户不存在或已停用");
        }
        return createTokenResponse(userId);
    }

    private TokenResponse createTokenResponse(Long userId) {
        return new TokenResponse(
                "Bearer",
                jwtUtils.generateAccessToken(userId),
                jwtUtils.generateRefreshToken(userId),
                jwtUtils.getAccessTokenExpiresInSeconds()
        );
    }

    private String normalizeEmail(String email) {
        if (email == null || email.isBlank()) {
            return null;
        }
        return email.trim().toLowerCase();
    }
}

