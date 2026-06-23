package com.example.smartkb.common;

/**
 * 统一错误码常量类。
 * <p>
 * 编码规则（共 5 位）：
 * <ul>
 *   <li>第一位 4 = 客户端错误（参数校验、权限、资源不存在）</li>
 *   <li>第一位 5 = 服务端错误（数据库、外部依赖、内部状态）</li>
 *   <li>第二位 0 = 模块标识：0=通用, 1=user, 2=kb, 3=document, 4=chat, 5=search/llm</li>
 *   <li>后三位为顺序号</li>
 * </ul>
 * 例如：50013 = 服务端错误(5)、document(0→3)、文档记录删除失败(013)
 */
public final class ErrorCode {

    private ErrorCode() {
    }

    // ========== 通用 (00) ==========
    public static final int VALIDATION_FAILED = 40000;
    public static final int USER_NOT_LOGGED_IN = 40101;
    public static final int TOKEN_INVALID = 40102;

    // ========== User 模块 (01) ==========
    public static final int USERNAME_EXISTS = 40901;
    public static final int USERNAME_OR_PASSWORD_ERROR = 40103;
    public static final int USER_NOT_FOUND = 40104;

    // ========== KB 模块 (02) ==========
    public static final int KB_NOT_FOUND = 40401;
    public static final int KB_CREATE_FAILED = 50001;
    public static final int KB_DELETE_FAILED = 50002;
    public static final int KB_HAS_PROCESSING_DOCUMENTS = 40902;

    // ========== Document 模块 (03) ==========
    public static final int FILE_EMPTY = 40010;
    public static final int FILE_TOO_LARGE = 40011;
    public static final int FILE_TYPE_NOT_ALLOWED = 40012;
    public static final int FILE_CONTENT_TYPE_MISMATCH = 40013;
    public static final int DOCUMENT_NOT_FOUND = 40410;
    public static final int DOCUMENT_PROCESSING = 40910;
    public static final int DOCUMENT_UPLOAD_FAILED = 50010;
    public static final int DOCUMENT_RECORD_FAILED = 50011;
    public static final int DOCUMENT_READ_FAILED = 50012;
    public static final int DOCUMENT_DELETE_RECORD_FAILED = 50013;
    public static final int DOCUMENT_FILE_DELETE_FAILED = 50014;

    // ========== Chat 模块 (04) ==========
    public static final int CONVERSATION_NOT_FOUND = 40420;
    public static final int CONVERSATION_CREATE_FAILED = 50020;

    // ========== Search/LLM 模块 (05) ==========
    public static final int QUERY_EMPTY = 40030;
    public static final int INVALID_TOP_K = 40031;
    public static final int REQUEST_ID_TOO_LONG = 40020;
    public static final int FIELD_EMPTY = 40021;
}
