package com.example.smartkb.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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
@ConfigurationProperties(prefix = "kb.milvus")
public class MilvusProperties {

    @NotBlank
    private String host;

    @Min(1)
    @Max(65535)
    private int port;

    @Min(1)
    private int dimension;

    @NotNull
    private Duration connectTimeout;

    @NotNull
    private Duration rpcTimeout;
}

