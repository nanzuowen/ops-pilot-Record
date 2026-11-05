package com.example.opspilot.exception;

import com.openai.errors.OpenAIServiceException;
import org.springframework.stereotype.Component;
import java.net.SocketTimeoutException;
import java.net.ConnectException;
import java.net.UnknownHostException;

@Component
public class LlmExceptionClassifier {

    public LlmCallException classify(Throwable exception){

        Throwable current = exception;

        while (current != null) {

            //检查当前异常类型
            if (current instanceof OpenAIServiceException serviceException) {
                int statusCode = serviceException.statusCode();

                LlmFailureCategory llmFailureCategory = switch (statusCode) {
                    case 400,422 -> LlmFailureCategory.INVALID_REQUEST;
                    case 401 -> LlmFailureCategory.AUTHENTICATION;
                    case 403 -> LlmFailureCategory.PERMISSION_DENIED;
                    case 408 -> LlmFailureCategory.TIMEOUT;
                    case 429 -> LlmFailureCategory.RATE_LIMITED;
                    default -> statusCode >= 500 && statusCode <= 599
                            ? LlmFailureCategory.UPSTREAM_UNAVAILABLE
                            : LlmFailureCategory.UNKNOWN;
                };

                return new LlmCallException(
                        llmFailureCategory,
                        statusCode,
                        current.getMessage(),
                        exception
                );
            }

            if (current instanceof SocketTimeoutException) {
                return new LlmCallException(
                        LlmFailureCategory.TIMEOUT,
                        null,
                        current.getMessage(),
                        exception
                );
            }

            if (current instanceof ConnectException || current instanceof UnknownHostException) {
                return new LlmCallException(
                        LlmFailureCategory.UPSTREAM_UNAVAILABLE,
                        null,
                        current.getMessage(),
                        exception
                );
            }

            if (current.getCause() == current) {
                break;
            }

            current = current.getCause();
        }

        return new LlmCallException(
                LlmFailureCategory.UNKNOWN,
                null,
                "未知 LLM 调用异常",
                exception
        );
    }
}
