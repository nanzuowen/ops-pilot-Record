package com.example.opspilot.exception;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class LlmExceptionHandler {

    @ExceptionHandler(LlmCallException.class)
    public ResponseEntity<LlmErrorResponse> handleLlmException(
            LlmCallException exception
    ) {
        // TODO 1：根据 exception.getCategory()
        //         确定 HTTP 状态码
        int apiStatusCode = switch (exception.getCategory()) {
            case INVALID_REQUEST -> 502;
            case AUTHENTICATION -> 502;
            case PERMISSION_DENIED -> 502;
            case RATE_LIMITED -> 503;
            case UPSTREAM_UNAVAILABLE -> 503;
            case TIMEOUT -> 504;
            case INVALID_RESPONSE -> 502;
            case UNKNOWN -> 500;
        };

        // TODO 2：根据 exception.getCategory()
        //         确定给前端展示的安全提示
        String message = switch (exception.getCategory()) {
            case INVALID_REQUEST -> "上游请求被拒绝";
            case AUTHENTICATION -> "上游 API 密钥异常";
            case PERMISSION_DENIED -> "上游权限异常";
            case RATE_LIMITED -> "上游模型服务限流";
            case UPSTREAM_UNAVAILABLE -> "上游服务不可用";
            case TIMEOUT -> "上游响应超时";
            case INVALID_RESPONSE -> "上游响应异常";
            case UNKNOWN -> "未知内部异常";
        };

        // TODO 3：创建 LlmErrorResponse
        LlmErrorResponse llmErrorResponse = new LlmErrorResponse(exception.getCategory(), message);

        // TODO 4：使用 ResponseEntity 返回状态码和响应体
        return ResponseEntity
                .status(apiStatusCode)
                .body(llmErrorResponse);

    }

    // TODO 5：定义一个内部 record
    //         包含 category 和 message 两个字段
    private record LlmErrorResponse(
            LlmFailureCategory category,
            String message
    ){}
}
