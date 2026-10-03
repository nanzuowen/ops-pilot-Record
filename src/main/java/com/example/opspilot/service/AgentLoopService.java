package com.example.opspilot.service;

import com.example.opspilot.tool.SearchLogsTool;
import com.example.opspilot.tool.ServiceMetricsTool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.AdvisorParams;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionResult;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
public class AgentLoopService {

    private static final int MAX_STEPS = 5;

    private final ChatClient chatClient;
    private final ServiceMetricsTool serviceMetricsTool;
    private final SearchLogsTool searchLogsTool;

    private final ToolCallingManager toolCallingManager =
            ToolCallingManager.builder().build();

    public AgentLoopService(
            ChatClient.Builder chatClientBuilder,
            ServiceMetricsTool serviceMetricsTool,
            SearchLogsTool searchLogsTool) {

        this.chatClient = chatClientBuilder
                .defaultSystem("""
                        You are OpsPilot, an AI assistant for diagnosing microservice failures.
                        
                        Rules:
                        - Focus on Java microservice troubleshooting.
                        - Give concise answers.
                        - Explain the evidence behind your conclusion.
                        - Use tools when runtime information is needed.
                        - Never claim that you accessed metrics, logs, traces, or configs unless a tool actually returned that data.
                        - If information is insufficient, say what information is missing.
                        - Clearly distinguish tool evidence from your own inference.
                        - Do not invent thresholds or configuration values that were not returned by tools.
                        """)
                .build();
        this.serviceMetricsTool = serviceMetricsTool;
        this.searchLogsTool = searchLogsTool;
    }

    public String chat(String message) {

        // 1. 把 @Tool 方法转换成 ToolCallback
        ToolCallback[] tools = ToolCallbacks.from(serviceMetricsTool,searchLogsTool);

        // 2. 告诉模型本轮可以使用哪些 Tool
        ChatOptions chatOptions = ToolCallingChatOptions.builder()
                .toolCallbacks(tools)
                .build();

        // ToolCallingManager 执行 Tool 时需要当前 Prompt
        Prompt prompt = new Prompt(
                List.of(new UserMessage(message)),
                chatOptions
        );

        // 3. 第一次调用 LLM
        ChatClientResponse response = chatClient.prompt()
                .user(message)

                // Spring AI 2.0.1 的 options() 接收 Builder
                .options(chatOptions.mutate())

                // 关闭 Spring AI 自动 Tool Loop
                // V2 由我们自己驱动 Agent Loop
                .advisors(
                        AdvisorParams.toolCallingAdvisorAutoRegister(false)
                )
                .call()
                .chatClientResponse();

        int step = 0;

        // 4. Agent Loop
        while (response.chatResponse() != null
                && response.chatResponse().hasToolCalls()) {

            step++;

            if (step > MAX_STEPS) {
                throw new IllegalStateException(
                        "Agent 超过最大执行轮数：" + MAX_STEPS
                );
            }

            log.info(
                    "[AgentLoop] step={}, toolCalls={}",
                    step,
                    response.chatResponse()
                            .getResult()
                            .getOutput()
                            .getToolCalls()
            );

            // 5. 执行模型请求的 Tool
            ToolExecutionResult toolResult =
                    toolCallingManager.executeToolCalls(
                            prompt,
                            response.chatResponse()
                    );

            // Tool result + 历史消息组成下一轮 Prompt
            prompt = new Prompt(
                    toolResult.conversationHistory(),
                    chatOptions
            );

            // 6. 把 Tool Result 再交给 LLM
            response = chatClient.prompt()
                    .messages(toolResult.conversationHistory())

                    // 同样需要 Builder
                    .options(chatOptions.mutate())

                    .advisors(
                            AdvisorParams.toolCallingAdvisorAutoRegister(false)
                    )
                    .call()
                    .chatClientResponse();
        }

        // 7. LLM 不再请求 Tool，Agent Loop 结束
        if (response.chatResponse() == null
                || response.chatResponse().getResult() == null) {

            throw new IllegalStateException("LLM 未返回有效结果");
        }

        log.info("[AgentLoop] finished, steps={}", step);

        return response.chatResponse()
                .getResult()
                .getOutput()
                .getText();
    }
}