package com.example.opspilot.service;

import com.example.opspilot.exception.LlmCallException;
import com.example.opspilot.exception.LlmExceptionClassifier;
import com.example.opspilot.exception.LlmFailureCategory;
import com.openai.errors.OpenAIServiceException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LlmGatewayTest {

    @Mock
    ChatClient chatClient;

    @Mock(answer = Answers.RETURNS_SELF)
    ChatClient.ChatClientRequestSpec requestSpec;

    @Mock
    ChatClient.CallResponseSpec callResponseSpec;

    private LlmGateway gateway;

    @Mock
    ChatClient.StreamResponseSpec streamResponseSpec;

    @BeforeEach
    void setUp() {
        gateway = new LlmGateway(chatClient, new LlmExceptionClassifier());

        when(chatClient.prompt()).thenReturn(requestSpec);
    }

    @Test
    void shouldConvertSyncExceptionToLlmCallException() {

        // Arrange

        //1. Mock OpenAIServiceException
        OpenAIServiceException exception = mock(OpenAIServiceException.class);
        //2. 设置 statusCode() 返回 429
        when(exception.statusCode()).thenReturn(429);
        //3. 让 callResponseSpec.chatClientResponse() 抛出这个异常
        when(callResponseSpec.chatClientResponse())
                .thenThrow(exception);
        //4. 设置requestSpec.call() 返回 callResponseSpec
        when(requestSpec.call())
                .thenReturn(callResponseSpec);

        // Act：执行并捕获异常

        // 5. 调用 gateway.call()
        LlmCallException llmCallException = assertThrows(
                LlmCallException.class,
                () -> gateway.call(List.of(), ToolCallingChatOptions.builder().build())
        );


        // Assert：验证异常内容

        // 7. 验证 category 是 RATE_LIMITED
        assertEquals(LlmFailureCategory.RATE_LIMITED, llmCallException.getCategory());
        // 8. 验证 HTTP 状态码是 429
        assertEquals(429, llmCallException.getHttpStatusCode());
        // 9. 验证 cause 是原始异常
        assertSame(exception, llmCallException.getCause());
    }

    @Test
    void shouldConvertStreamExceptionToLlmCallException() {

        // Arrange

        // 1. Mock OpenAIServiceException，设置状态码 429
        OpenAIServiceException exception = mock(OpenAIServiceException.class);
        when(exception.statusCode()).thenReturn(429);
        // 2. 模拟 requestSpec.stream() 返回 streamResponseSpec
        when(requestSpec.stream()).thenReturn(streamResponseSpec);
        // 3. 模拟 streamResponseSpec.chatResponse()返回 Flux.error(exception)
        when(streamResponseSpec.chatResponse()).thenReturn(Flux.<ChatResponse>error(exception));


        // Act

        // 4. 调用 gateway.stream()
        //    使用 blockLast() 触发执行
        //    使用 assertThrows() 捕获异常
        LlmCallException llmCallException = assertThrows(
                LlmCallException.class,
                () -> gateway.stream(List.of(),ToolCallingChatOptions.builder().build()).blockLast()
        );


        // Assert

        // 7. 验证 category 是 RATE_LIMITED
        assertEquals(LlmFailureCategory.RATE_LIMITED, llmCallException.getCategory());
        // 8. 验证 HTTP 状态码是 429
        assertEquals(429, llmCallException.getHttpStatusCode());
        // 9. 验证 cause 是原始异常
        assertSame(exception, llmCallException.getCause());
    }

    @Test
    void shouldNotWrapExistingLlmCallException() {

        // Arrange
        // 1. 创建一个真实的 LlmCallException
        LlmCallException exception = new LlmCallException(
                LlmFailureCategory.RATE_LIMITED,
                429,
                "已经分类的异常",
                new RuntimeException("原始异常"));
        // 2. 让 callResponseSpec.chatClientResponse() 抛出它
        when(callResponseSpec.chatClientResponse()).thenThrow(exception);
        // 3. 设置 requestSpec.call() 返回 callResponseSpec
        when(requestSpec.call()).thenReturn(callResponseSpec);

        // Act
        // 4. 调用 gateway.call()，使用 assertThrows 捕获异常
        LlmCallException llmCallException = assertThrows(LlmCallException.class,
                () -> gateway.call(List.of(), ToolCallingChatOptions.builder().build()));

        // Assert
        // 5. 使用 assertSame 验证返回的是原来的异常对象
        assertSame(exception, llmCallException);
    }

    @Test
    void shouldReturnOriginalResponseWhenStreamSucceeds() {

        // Arrange
        // 1. 创建一个真实的 ChatResponse
        ChatResponse response = new ChatResponse(List.of());
        // 2. 设置 requestSpec.stream() 返回 streamResponseSpec
        when(requestSpec.stream()).thenReturn(streamResponseSpec);
        // 3. 设置 chatResponse() 返回 Flux.just(response)
        when(streamResponseSpec.chatResponse()).thenReturn(Flux.just(response));

        // Act
        // 4. gateway.stream(...).blockLast()
        ChatResponse result = gateway.stream(List.of(), ToolCallingChatOptions.builder().build()).blockLast();

        // Assert
        // 5. assertSame(response, result)
        assertSame(response, result);
    }

    @Test
    void shouldReturnOriginalResponseWhenSyncSucceeds() {

        // Arrange

        // 1. 创建一个真实的 ChatResponse
        ChatResponse response = new ChatResponse(List.of());
        // 2. 创建一个真实的 ChatClientResponse
        //    提示：new ChatClientResponse(chatResponse, Map.of())
        ChatClientResponse clientResponse = new ChatClientResponse(response, Map.of());
        // 3. 设置 requestSpec.call() 返回 callResponseSpec
        when(requestSpec.call()).thenReturn(callResponseSpec);
        // 4. 设置 callResponseSpec.chatClientResponse()
        //    返回刚刚创建的 ChatClientResponse
        when(callResponseSpec.chatClientResponse()).thenReturn(clientResponse);


        // Act

        // 5. 调用 gateway.call()
        ChatClientResponse result = gateway.call(List.of(), ToolCallingChatOptions.builder().build());


        // Assert

        // 6. 使用 assertSame 验证返回的是原始 ChatClientResponse
        assertSame(clientResponse, result);
    }
}
