package com.example.opspilot.exception;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

public class LlmExceptionHandlerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new TestController())
                .setControllerAdvice(new LlmExceptionHandler())
                .build();
    }

    // 模拟一个会抛出 LlmCallException 的 Controller
    @RestController
    static class TestController {

        @GetMapping("/test/rate-limit")
        public void rateLimit() {
            throw new LlmCallException(
                    LlmFailureCategory.RATE_LIMITED,
                    429,
                    "DeepSeek 请求频率超限",
                    new RuntimeException("原始异常")
            );
        }

        @GetMapping("/test/timeout")
        public void timeout() {
            throw new LlmCallException(
                    LlmFailureCategory.TIMEOUT,
                    408,
                    "DeepSeek 请求超时",
                    new RuntimeException("原始异常")
            );
        }

        @GetMapping("/test/authentication")
        public void authentication() {
            throw new LlmCallException(
                    LlmFailureCategory.AUTHENTICATION,
                    401,
                    "DeepSeek API Key 无效",
                    new RuntimeException("原始异常")
            );
        }

        @GetMapping("/test/unavailable")
        public void unavailable() {
            throw new LlmCallException(
                    LlmFailureCategory.UPSTREAM_UNAVAILABLE,
                    503,
                    "上游服务不可用",
                    new RuntimeException("原始异常")
            );
        }

        @GetMapping("/test/unknown")
        public void unknown() {
            throw new LlmCallException(
                    LlmFailureCategory.UNKNOWN,
                    null,
                    "未知异常",
                    new RuntimeException("原始异常")
            );
        }

        @GetMapping("/test/invalid-request")
        public void invalidRequest() {
            throw new LlmCallException(
                    LlmFailureCategory.INVALID_REQUEST,
                    400,
                    "请求参数无效",
                    new RuntimeException("原始异常")
            );
        }

        @GetMapping("/test/permission-denied")
        public void permissionDenied() {
            throw new LlmCallException(
                    LlmFailureCategory.PERMISSION_DENIED,
                    403,
                    "拒绝访问",
                    new RuntimeException("原始异常")
            );
        }

        @GetMapping("/test/invalid-response")
        public void invalidResponse() {
            throw new LlmCallException(
                    LlmFailureCategory.INVALID_RESPONSE,
                    null,
                    "无响应",
                    new RuntimeException("原始异常")
            );
        }
    }

    @Test
    void shouldReturn503WhenRateLimited() throws Exception {

        // Arrange
        // 已经在 setUp() 中准备好了 MockMvc

        // Act
        // TODO 1：向 /test/rate-limit 发送 GET 请求
        mockMvc.perform(get("/test/rate-limit"))
                // Assert
                // TODO 2：验证 HTTP 状态码为 503
                .andExpect(status().isServiceUnavailable())
                // TODO 3：验证 JSON 中 category 为 RATE_LIMITED
                .andExpect(jsonPath("$.category").value("RATE_LIMITED"))
                // TODO 4：验证 JSON 中 message 为「上游模型服务限流」
                .andExpect(jsonPath("$.message").value("上游模型服务限流"));
    }

    @Test
    void shouldReturn504WhenTimeout() throws Exception {

        // Arrange, Act, Assert
        mockMvc.perform(get("/test/timeout"))
                .andExpect(status().isGatewayTimeout())
                .andExpect(jsonPath("$.category").value("TIMEOUT"))
                .andExpect(jsonPath("$.message").value("上游响应超时"));
    }

    @Test
    void shouldReturn502WhenAuthenticationFailed() throws Exception {

        // Arrange, Act, Assert
        mockMvc.perform(get("/test/authentication"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.category").value("AUTHENTICATION"))
                .andExpect(jsonPath("$.message").value("上游 API 密钥异常"));
    }

    @Test
    void shouldReturn503WhenUnavailable() throws Exception {

        // Arrange, Act, Assert
        mockMvc.perform(get("/test/unavailable"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.category").value("UPSTREAM_UNAVAILABLE"))
                .andExpect(jsonPath("$.message").value("上游服务不可用"));
    }

    @Test
    void shouldReturn500WhenUnknown() throws Exception {

        // Arrange, Act, Assert
        mockMvc.perform(get("/test/unknown"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.category").value("UNKNOWN"))
                .andExpect(jsonPath("$.message").value("未知内部异常"));
    }

    @ParameterizedTest
    @CsvSource({
            // TODO：填写三个接口路径、异常分类和预期 message
            "/test/invalid-request, INVALID_REQUEST, 上游请求被拒绝",
            "/test/permission-denied, PERMISSION_DENIED, 上游权限异常",
            "/test/invalid-response, INVALID_RESPONSE, 上游响应异常"
    })
    void shouldReturn502ForOtherFailures(
            String path,
            String category,
            String message
    ) throws Exception {

        // TODO 1：发送 GET 请求
        // TODO 2：验证 HTTP 状态码为 502
        // TODO 3：验证 category
        // TODO 4：验证 message
        mockMvc.perform(get(path))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.category").value(category))
                .andExpect(jsonPath("$.message").value(message));
    }
}
