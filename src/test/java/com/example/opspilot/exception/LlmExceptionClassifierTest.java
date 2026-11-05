package com.example.opspilot.exception;

import com.openai.errors.OpenAIServiceException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;

class LlmExceptionClassifierTest {

    private final LlmExceptionClassifier classifier =
            new LlmExceptionClassifier();

    @Test
    void shouldClassifyHttp429AsRateLimited() {

        // Arrange：准备测试数据

        // TODO 1：Mock 一个 OpenAIServiceException
        OpenAIServiceException exception = mock(OpenAIServiceException.class);

        // TODO 2：让 statusCode() 返回 429
        when(exception.statusCode()).thenReturn(429);


        // Act：执行被测试的方法

        // TODO 3：调用 classifier.classify()
        LlmCallException llmCallException = classifier.classify(exception);


        // Assert：验证结果

        // TODO 4：验证异常分类是 RATE_LIMITED
        assertEquals(LlmFailureCategory.RATE_LIMITED, llmCallException.getCategory());

        // TODO 5：验证 HTTP 状态码是 429
        assertEquals(429, llmCallException.getHttpStatusCode());

        // TODO 6：验证原始异常 cause 没有丢失
        assertSame(exception, llmCallException.getCause());
    }

    @Test
    void shouldClassifyWrappedExceptionAsRateLimited() {
        // 1. Mock OpenAIServiceException，设置状态码 429
        OpenAIServiceException exception = mock(OpenAIServiceException.class);
        when(exception.statusCode()).thenReturn(429);

        // 2. 使用 RuntimeException 包装它
        RuntimeException runtimeException = new RuntimeException(exception);

        // 3. 把外层异常传给 classifier.classify()
        LlmCallException llmCallException = classifier.classify(runtimeException);

        // 4. 验证分类仍然是 RATE_LIMITED
        assertEquals(LlmFailureCategory.RATE_LIMITED, llmCallException.getCategory());

        // 5. 验证 HTTP 状态码仍然是 429
        assertEquals(429, llmCallException.getHttpStatusCode());

        // 6. 验证 cause 是最外层异常对象
        assertSame(runtimeException, llmCallException.getCause());
    }

    @ParameterizedTest
    @CsvSource({
            "400, INVALID_REQUEST",
            "401, AUTHENTICATION",
            "403, PERMISSION_DENIED",
            "408, TIMEOUT",
            "422, INVALID_REQUEST",
            "429, RATE_LIMITED",
            "402, UNKNOWN",
            "499, UNKNOWN",
            "500, UPSTREAM_UNAVAILABLE",
            "599, UPSTREAM_UNAVAILABLE"
    })
    void shouldClassifyHttpStatusCorrectly(
            int statusCode,
            String expectedCategory
    ) {
        // Arrange：创建 Mock，设置状态码
        OpenAIServiceException exception = mock(OpenAIServiceException.class);
        when(exception.statusCode()).thenReturn(statusCode);

        // Act：调用 classifier.classify()
        LlmCallException llmCallException = classifier.classify(exception);

        // Assert：验证分类和状态码
        assertEquals(statusCode, llmCallException.getHttpStatusCode());

        assertEquals(
                LlmFailureCategory.valueOf(expectedCategory),
                llmCallException.getCategory()
        );
    }

    @Test
    void shouldClassifySocketTimeoutAsTimeout() {

        // Arrange: 创建一个 SocketTimeoutException
        SocketTimeoutException exception = new SocketTimeoutException("连续读取超时");

        // Act: 调用 classifier.classify()
        LlmCallException llmCallException = classifier.classify(exception);

        // Assert
        assertEquals(LlmFailureCategory.TIMEOUT, llmCallException.getCategory());
        assertNull(llmCallException.getHttpStatusCode());
        assertSame(exception, llmCallException.getCause());
    }

    @Test
    void shouldClassifyConnectExceptionAsUnavailable() {

        // Arrange
        ConnectException exception = new ConnectException("无法建立网络连接，例如连接被拒绝");

        // Act
        LlmCallException llmCallException = classifier.classify(exception);

        //Assert
        assertEquals(LlmFailureCategory.UPSTREAM_UNAVAILABLE, llmCallException.getCategory());
        assertNull(llmCallException.getHttpStatusCode());
        assertSame(exception, llmCallException.getCause());
    }

    @Test
    void shouldClassifyUnknownHostAsUnavailable() {
        // Arrange
        UnknownHostException exception = new UnknownHostException("DNS 无法解析目标主机名");

        //Act
        LlmCallException llmCallException = classifier.classify(exception);

        //Assert
        assertEquals(LlmFailureCategory.UPSTREAM_UNAVAILABLE, llmCallException.getCategory());
        assertNull(llmCallException.getHttpStatusCode());
        assertSame(exception, llmCallException.getCause());
    }

}