package com.example.smartkb.user.util;

import com.example.smartkb.common.BusinessException;
import com.example.smartkb.user.config.JwtProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtUtilsTest {

    private static final String SECRET = "test-jwt-secret-that-is-at-least-32-chars-long-for-testing";

    private JwtUtils jwtUtils;

    @BeforeEach
    void setUp() {
        JwtProperties properties = new JwtProperties();
        properties.setSecret(SECRET);
        properties.setAccessTokenTtl(Duration.ofMinutes(15));
        properties.setRefreshTokenTtl(Duration.ofDays(7));
        jwtUtils = new JwtUtils(properties);
    }

    @Test
    void shouldGenerateAndParseAccessToken() {
        String token = jwtUtils.generateAccessToken(1L);
        assertThat(token).isNotBlank();

        Long userId = jwtUtils.parseAccessToken(token);
        assertThat(userId).isEqualTo(1L);
    }

    @Test
    void shouldGenerateAndParseRefreshToken() {
        String token = jwtUtils.generateRefreshToken(2L);
        assertThat(token).isNotBlank();

        Long userId = jwtUtils.parseRefreshToken(token);
        assertThat(userId).isEqualTo(2L);
    }

    @Test
    void shouldRejectRefreshTokenUsedAsAccessToken() {
        String refreshToken = jwtUtils.generateRefreshToken(3L);

        assertThatThrownBy(() -> jwtUtils.parseAccessToken(refreshToken))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Token 类型无效");
    }

    @Test
    void shouldRejectAccessTokenUsedAsRefreshToken() {
        String accessToken = jwtUtils.generateAccessToken(4L);

        assertThatThrownBy(() -> jwtUtils.parseRefreshToken(accessToken))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Token 类型无效");
    }

    @Test
    void shouldRejectExpiredToken() {
        JwtProperties shortLived = new JwtProperties();
        shortLived.setSecret(SECRET);
        shortLived.setAccessTokenTtl(Duration.ofMillis(1));
        shortLived.setRefreshTokenTtl(Duration.ofDays(7));
        JwtUtils shortLivedUtils = new JwtUtils(shortLived);

        String token = shortLivedUtils.generateAccessToken(5L);

        // 等待令牌过期
        try {
            Thread.sleep(10);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        assertThatThrownBy(() -> jwtUtils.parseAccessToken(token))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Token 无效或已过期");
    }

    @Test
    void shouldRejectInvalidTokenString() {
        assertThatThrownBy(() -> jwtUtils.parseAccessToken("invalid-token"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Token 无效或已过期");
    }

    @Test
    void shouldRejectEmptyToken() {
        assertThatThrownBy(() -> jwtUtils.parseAccessToken(""))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Token 无效或已过期");
    }

    @Test
    void shouldReturnAccessTokenExpiresInSeconds() {
        long expiresIn = jwtUtils.getAccessTokenExpiresInSeconds();
        assertThat(expiresIn).isEqualTo(15 * 60);
    }

    @Test
    void shouldParseMultipleUsers() {
        String token1 = jwtUtils.generateAccessToken(100L);
        String token2 = jwtUtils.generateAccessToken(200L);

        assertThat(jwtUtils.parseAccessToken(token1)).isEqualTo(100L);
        assertThat(jwtUtils.parseAccessToken(token2)).isEqualTo(200L);
    }

    @Test
    void shouldRejectTokenFromDifferentSecret() {
        JwtProperties otherProps = new JwtProperties();
        otherProps.setSecret("a-different-secret-that-is-also-at-least-32-characters-long");
        otherProps.setAccessTokenTtl(Duration.ofMinutes(15));
        otherProps.setRefreshTokenTtl(Duration.ofDays(7));
        JwtUtils otherUtils = new JwtUtils(otherProps);

        String token = otherUtils.generateAccessToken(6L);

        assertThatThrownBy(() -> jwtUtils.parseAccessToken(token))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Token 无效或已过期");
    }
}
