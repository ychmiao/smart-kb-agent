package com.example.smartkb.common;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Modifier;

import static org.assertj.core.api.Assertions.assertThat;

class ErrorCodeTest {

    @Test
    void allErrorCodesShouldBePositive() throws Exception {
        for (var field : ErrorCode.class.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers()) && field.getType() == int.class) {
                int code = field.getInt(null);
                assertThat(code)
                        .as("Error code " + field.getName() + " should be positive")
                        .isPositive();
            }
        }
    }

    @Test
    void errorCodeRangesShouldBeValid() throws Exception {
        for (var field : ErrorCode.class.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers()) && field.getType() == int.class) {
                int code = field.getInt(null);
                assertThat(code)
                        .as("Error code " + field.getName() + "=" + code + " should be in 4xxxx or 5xxxx range")
                        .satisfiesAnyOf(
                                c -> assertThat(c).isBetween(40000, 49999),
                                c -> assertThat(c).isBetween(50000, 59999)
                        );
            }
        }
    }

    @Test
    void shouldNotInstantiate() throws Exception {
        var constructor = ErrorCode.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        // 只是测试可以反射构造，但有私有构造器
        assertThat(Modifier.isPrivate(constructor.getModifiers())).isTrue();
    }
}
