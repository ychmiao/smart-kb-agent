package com.example.smartkb.user.vo;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class TokenResponse {

    private final String tokenType;
    private final String accessToken;
    private final String refreshToken;
    private final long accessTokenExpiresIn;
}

