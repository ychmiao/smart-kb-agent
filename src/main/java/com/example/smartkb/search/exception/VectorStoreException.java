package com.example.smartkb.search.exception;

import com.example.smartkb.common.BusinessException;

public class VectorStoreException extends BusinessException {

    public VectorStoreException(String message) {
        super(50310, message);
    }

    public VectorStoreException(String message, Throwable cause) {
        super(50310, message, cause);
    }
}

