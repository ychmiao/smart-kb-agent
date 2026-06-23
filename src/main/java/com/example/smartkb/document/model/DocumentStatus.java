package com.example.smartkb.document.model;

import lombok.Getter;

/**
 * 文档处理状态枚举。
 * <p>
 * <ul>
 *   <li>PROCESSING（0）：刚上传，正在异步解析和向量化</li>
 *   <li>COMPLETED（1）：解析、分块、向量化入库全部完成</li>
 *   <li>FAILED（2）：处理过程中出现异常，error_msg 记录可诊断的原因</li>
 * </ul>
 */
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

