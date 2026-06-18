package com.example.smartkb.user.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Getter
@Setter
@Validated
@Component
@ConfigurationProperties(prefix = "kb.jwt")
public class JwtProperties {

    @NotBlank
    @Size(min = 32, message = "JWT secret must contain at least 32 characters")
    private String secret;

    @NotNull
    private Duration accessTokenTtl;

    @NotNull
    private Duration refreshTokenTtl;
}

