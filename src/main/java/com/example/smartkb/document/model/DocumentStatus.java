package com.example.smartkb.document.model;

import lombok.Getter;

@Getter
public enum DocumentStatus {

    PROCESSING(0),
    COMPLETED(1),
    FAILED(2);

    private final int code;

    DocumentStatus(int code) {
        this.code = code;
    }
}

