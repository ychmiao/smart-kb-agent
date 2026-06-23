package com.example.smartkb.user.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.smartkb.common.BusinessException;
import com.example.smartkb.common.ErrorCode;
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

/**
 * 用户服务实现 —— 处理注册、登录和令牌刷新。
 * <p>
 * 注册时使用 BCrypt 加密密码，通过 {@code lambdaQuery()} 检查用户名唯一性。
 * 登录成功同时签发 Access Token（15 分钟）和 Refresh Token（7 天），
 * 采用无状态 JWT 策略，令牌不存储于 Redis。
 */
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
        // 先通过 MyBatis-Plus 条件查询检查用户名是否已存在
        long count = lambdaQuery().eq(User::getUsername, request.getUsername()).count();
        if (count > 0) {
            throw new BusinessException(ErrorCode.USERNAME_EXISTS, "用户名已存在");
        }

        User user = new User();
        user.setUsername(request.getUsername());
        // BCrypt 加密，不可逆存储
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setEmail(normalizeEmail(request.getEmail()));
        try {
            save(user);
        } catch (DuplicateKeyException exception) {
            // 并发注册场景的兜底检查
            throw new BusinessException(ErrorCode.USERNAME_EXISTS, "用户名已存在", exception);
        }
        return user.getId();
    }

    @Override
    public TokenResponse login(LoginRequest request) {
        // 按用户名查找用户
        User user = lambdaQuery()
                .eq(User::getUsername, request.getUsername())
                .one();
        // 用户不存在或密码不匹配时返回统一错误（防止用户名枚举）
        if (user == null || !passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new BusinessException(ErrorCode.USERNAME_OR_PASSWORD_ERROR, "用户名或密码错误");
        }
        return createTokenResponse(user.getId());
    }

    @Override
    public TokenResponse refreshToken(String refreshToken) {
        // 解析 Refresh Token，提取 userId
        Long userId = jwtUtils.parseRefreshToken(refreshToken);
        // 验证用户仍存在（未被删除）
        if (getById(userId) == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND, "用户不存在或已停用");
        }
        // 签发全新的令牌对（Access + Refresh）
        return createTokenResponse(userId);
    }

    /** 创建包含 Bearer 类型、Access Token、Refresh Token 和过期时间的令牌响应 */
    private TokenResponse createTokenResponse(Long userId) {
        return new TokenResponse(
                "Bearer",
                jwtUtils.generateAccessToken(userId),
                jwtUtils.generateRefreshToken(userId),
                jwtUtils.getAccessTokenExpiresInSeconds()
        );
    }

    /** 规范化邮箱：去空格、转小写；空值存为 null */
    private String normalizeEmail(String email) {
        if (email == null || email.isBlank()) {
            return null;
        }
        return email.trim().toLowerCase();
    }
}

