package com.example.smartkb.config;

import io.milvus.client.MilvusServiceClient;
import io.milvus.param.ConnectParam;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

@Configuration
public class MilvusConfig {

    @Bean(destroyMethod = "close")
    public MilvusServiceClient milvusServiceClient(MilvusProperties properties) {
        ConnectParam connectParam = ConnectParam.newBuilder()
                .withHost(properties.getHost())
                .withPort(properties.getPort())
                .withConnectTimeout(properties.getConnectTimeout().toMillis(), TimeUnit.MILLISECONDS)
                .withRpcDeadline(properties.getRpcTimeout().toMillis(), TimeUnit.MILLISECONDS)
                .build();
        return new MilvusServiceClient(connectParam);
    }
}

