package com.example.smartkb.user.util;

import com.example.smartkb.common.BusinessException;
import com.example.smartkb.common.ErrorCode;
import com.example.smartkb.user.config.JwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

/**
 * JWT 令牌工具类 —— 使用无状态（Stateless）令牌策略。
 * <p>
 * <strong>策略说明：</strong>
 * <ul>
 *   <li>Access Token（15 分钟）：短期身份凭证，携带 tokenType=access 声明</li>
 *   <li>Refresh Token（7 天）：长期刷新凭证，携带 tokenType=refresh 声明</li>
 *   <li>令牌类型分离：Refresh Token 不可用于 API 认证（parseAccessToken 会拒绝）</li>
 *   <li>密钥来源于环境变量 {@code JWT_SECRET}，需至少 32 字符</li>
 * </ul>
 * </p>
 * <p>
 * <strong>安全边界：</strong>
 * <ul>
 *   <li>本实现不维护令牌黑名单，签发后在其生命周期内始终有效</li>
 *   <li>如需支持撤销（用户登出、密码变更），应引入 Redis 存储 tokenId/版本号</li>
 *   <li>TTL 通过 {@link com.example.smartkb.user.config.JwtProperties} 配置</li>
 * </ul>
 * </p>
 */
@Component
public class JwtUtils {

    private static final String TOKEN_TYPE_CLAIM = "tokenType";
    private static final String ACCESS_TOKEN_TYPE = "access";
    private static final String REFRESH_TOKEN_TYPE = "refresh";

    private final JwtProperties properties;
    private final SecretKey secretKey;

    public JwtUtils(JwtProperties properties) {
        this.properties = properties;
        // 使用 HMAC-SHA 算法，密钥字节长度需满足算法要求
        this.secretKey = Keys.hmacShaKeyFor(properties.getSecret().getBytes(StandardCharsets.UTF_8));
    }

    /** 生成 Access Token（默认 15 分钟有效期） */
    public String generateAccessToken(Long userId) {
        return generateToken(userId, ACCESS_TOKEN_TYPE, properties.getAccessTokenTtl().toMillis());
    }

    /** 生成 Refresh Token（默认 7 天有效期） */
    public String generateRefreshToken(Long userId) {
        return generateToken(userId, REFRESH_TOKEN_TYPE, properties.getRefreshTokenTtl().toMillis());
    }

    /** 解析 Access Token，返回 userId；若 tokenType 不匹配则抛出异常 */
    public Long parseAccessToken(String token) {
        return parseToken(token, ACCESS_TOKEN_TYPE);
    }

    /** 解析 Refresh Token，返回 userId；若 tokenType 不匹配则抛出异常 */
    public Long parseRefreshToken(String token) {
        return parseToken(token, REFRESH_TOKEN_TYPE);
    }

    /** 获取 Access Token 的有效期（秒），用于客户端计算刷新时机 */
    public long getAccessTokenExpiresInSeconds() {
        return properties.getAccessTokenTtl().toSeconds();
    }

    /** 通用令牌生成：包含 userId(subject)、tokenType、签发时间和过期时间 */
    private String generateToken(Long userId, String tokenType, long ttlMillis) {
        Instant issuedAt = Instant.now();
        Instant expiration = issuedAt.plusMillis(ttlMillis);
        return Jwts.builder()
                .subject(userId.toString())
                .claim(TOKEN_TYPE_CLAIM, tokenType)
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(expiration))
                .signWith(secretKey)
                .compact();
    }

    /** 通用令牌解析：校验签名、过期时间和 tokenType，返回 userId */
    private Long parseToken(String token, String expectedType) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(secretKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            // 校验令牌类型声明，防止 Access/Refresh Token 混用
            String tokenType = claims.get(TOKEN_TYPE_CLAIM, String.class);
            if (!expectedType.equals(tokenType)) {
                throw new BusinessException(ErrorCode.TOKEN_INVALID, "Token 类型无效");
            }
            return Long.valueOf(claims.getSubject());
        } catch (BusinessException exception) {
            throw exception;
        } catch (JwtException | IllegalArgumentException exception) {
            // JwtException 涵盖：过期、签名不匹配、格式错误等
            throw new BusinessException(ErrorCode.TOKEN_INVALID, "Token 无效或已过期", exception);
        }
    }
}

