package com.example.opspilot.exception;

public class LlmCallException extends RuntimeException{

    // 异常分类
    private final LlmFailureCategory category;

    // HTTP 状态码，可能为空
    private final Integer httpStatusCode;

    public LlmCallException(LlmFailureCategory category, Integer httpStatusCode,
                            String message, Throwable cause){

        super(message, cause);

        this.category = category;
        this.httpStatusCode = httpStatusCode;
    }

    public Integer getHttpStatusCode() {
        return httpStatusCode;
    }

    public LlmFailureCategory getCategory(){
        return category;
    }

}
