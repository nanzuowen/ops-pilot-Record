package com.example.opspilot.service;

import com.example.opspilot.tool.RestartServiceTool;
import com.example.opspilot.tool.SearchLogsTool;
import com.example.opspilot.tool.SearchRunbookTool;
import com.example.opspilot.tool.ServiceMetricsTool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.AdvisorParams;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.mcp.SyncMcpToolCallbackProvider;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionResult;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class AgentLoopService {

    private static final int MAX_STEPS = 5;

    private final ChatClient chatClient;

    private final ServiceMetricsTool serviceMetricsTool;
    private final SearchLogsTool searchLogsTool;
    private final SearchRunbookTool searchRunbookTool;
    private final RestartServiceTool restartServiceTool;

    private final SyncMcpToolCallbackProvider mcpToolCallbackProvider;

    private final ToolCallingManager toolCallingManager =
            ToolCallingManager.builder().build();

    private final ChatMemory chatMemory;

    private final Map<String,PendingApproval> pendingApprovals = new ConcurrentHashMap<>();

    public AgentLoopService(
            ChatClient.Builder chatClientBuilder,
            ServiceMetricsTool serviceMetricsTool,
            SearchLogsTool searchLogsTool,
            SearchRunbookTool searchRunbookTool,
            RestartServiceTool restartServiceTool,
            SyncMcpToolCallbackProvider mcpToolCallbackProvider,
            ChatMemory chatMemory) {

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
                        你是 OpsPilot，一个微服务故障诊断 Agent。
                        
                        诊断故障时：
                        
                        1. 优先获取客观运行数据，例如 Metrics 和 Logs。
                        2. 根据已有故障现象搜索相关 Runbook，获取排查依据和处理建议。
                        3. 不要仅凭 Runbook 直接判断故障，必须结合 Metrics、Logs 等实际证据。
                        4. 如果已有证据足够，给出最终诊断，不要继续调用无意义的工具。
                        5. 最终回答需要说明：
                           - 故障类型
                           - 判断依据
                           - 建议处理方式
                        6. restartService 属于有副作用操作，只有用户明确要求重启时才允许请求该 Tool。
                        7. 在 restartService 真正返回成功结果之前，不得声称服务已经重启。
                           
                        - restartService is a destructive operation.
                        - You may request restartService only when the user explicitly asks to restart a service.
                        - Never claim that a service was restarted unless the restartService tool actually returned a successful result.
                        
                        1. 优先获取客观运行数据，例如 Metrics 和 Logs。
                        2. 如果怀疑存在下游服务超时或跨服务慢调用，使用 getTrace 获取调用链证据。
                        3. 根据已有故障现象搜索相关 Runbook，获取排查依据和处理建议。
                        4. 不要仅凭 Runbook 直接判断故障，必须结合 Metrics、Logs、Trace 等实际证据。
                        """)
                .build();
        this.serviceMetricsTool = serviceMetricsTool;
        this.searchLogsTool = searchLogsTool;
        this.searchRunbookTool = searchRunbookTool;
        this.chatMemory = chatMemory;
        this.restartServiceTool = restartServiceTool;
        this.mcpToolCallbackProvider = mcpToolCallbackProvider;
    }

    public String chat(String conversationId, String message) {

        if (conversationId == null || conversationId.isBlank()) {
            throw new IllegalArgumentException("conversationId 不能为空");
        }

        if (pendingApprovals.containsKey(conversationId)) {
            return """
            当前会话存在待确认操作。
            请先完成 restartService 的人工审批。
            """;
        }

        // 1. 把 @Tool 方法转换成 ToolCallback  本地Tool
        ToolCallback[] localTools = ToolCallbacks.from(serviceMetricsTool,searchLogsTool,searchRunbookTool,restartServiceTool);

        //1.1 MCP Server 提供的远程Tool
        ToolCallback[] mcpTools = mcpToolCallbackProvider.getToolCallbacks();

        //1.2 合并 Local Tool + MCP Tool
        List<ToolCallback> allTools = new ArrayList<>(localTools.length + mcpTools.length);

        allTools.addAll(List.of(localTools));
        allTools.addAll(List.of(mcpTools));

        log.info(
                "[AgentLoop] availableTools={}",
                allTools.stream()
                        .map(tool -> tool.getToolDefinition().name())
                        .toList()
        );

        // 2. 告诉模型本轮可以使用哪些 Tool
        ChatOptions chatOptions = ToolCallingChatOptions.builder()
                .toolCallbacks(allTools)
                .build();


        //读取之前的Chat Memory
        List<Message> conversationMessages = new ArrayList<>(chatMemory.get(conversationId));

        //记录本轮开始前有多少历史信息
        int memorySizeBeforeCurrentTurn = conversationMessages.size();

        //加入当前用户信息
        conversationMessages.add(new UserMessage(message));

        // ToolCallingManager 执行 Tool 时需要当前 Prompt
        Prompt prompt = new Prompt(
                conversationMessages,
                chatOptions
        );

        // 3. 第一次调用 LLM
        ChatClientResponse response = chatClient.prompt()
                .messages(conversationMessages)

                // Spring AI 2.0.1 的 options() 接收 Builder
                .options(chatOptions.mutate())

                // 关闭 Spring AI 自动 Tool Loop
                // V2 由我们自己驱动 Agent Loop
                .advisors(
                        AdvisorParams.toolCallingAdvisorAutoRegister(false)
                )
                .call()
                .chatClientResponse();
        return continueLoop(
                conversationId,
                message,
                memorySizeBeforeCurrentTurn,
                chatOptions,
                prompt,
                response,
                0
        );
    }

    private String continueLoop(
            String conversationId,
            String userMessage,
            int memorySizeBeforeCurrentTurn,
            ChatOptions chatOptions,
            Prompt prompt,
            ChatClientResponse response,
            int step) {

        while (response.chatResponse() != null
                && response.chatResponse().hasToolCalls()) {

            step++;

            if (step > MAX_STEPS) {
                throw new IllegalStateException(
                        "Agent 超过最大执行轮数：" + MAX_STEPS
                );
            }

            var toolCalls = response.chatResponse()
                    .getResult()
                    .getOutput()
                    .getToolCalls();

            log.info(
                    "[AgentLoop] step={}, toolCalls={}",
                    step,
                    toolCalls
            );

            boolean requiresApproval = toolCalls.stream()
                    .anyMatch(toolCall ->
                            "restartService".equals(toolCall.name())
                    );

            if (requiresApproval) {

                log.warn(
                        "[AgentLoop] approval required, toolCalls={}",
                        toolCalls
                );

                pendingApprovals.put(
                        conversationId,
                        new PendingApproval(
                                userMessage,
                                prompt,
                                response.chatResponse(),
                                chatOptions,
                                memorySizeBeforeCurrentTurn,
                                step
                        )
                );

                return """
                    需要人工确认：Agent 请求执行 restartService。
                    请确认是否允许执行该操作。
                    """;
            }

            ToolExecutionResult toolResult =
                    toolCallingManager.executeToolCalls(
                            prompt,
                            response.chatResponse()
                    );

            prompt = new Prompt(
                    toolResult.conversationHistory(),
                    chatOptions
            );

            response = chatClient.prompt()
                    .messages(toolResult.conversationHistory())
                    .options(chatOptions.mutate())
                    .advisors(
                            AdvisorParams.toolCallingAdvisorAutoRegister(false)
                    )
                    .call()
                    .chatClientResponse();
        }

        if (response.chatResponse() == null
                || response.chatResponse().getResult() == null) {

            throw new IllegalStateException("LLM 未返回有效结果");
        }

        log.info("[AgentLoop] finished, steps={}", step);

        String finalAnswer = response.chatResponse()
                .getResult()
                .getOutput()
                .getText();

        List<Message> currentTurnMessages =
                new ArrayList<>(
                        prompt.getInstructions().subList(
                                memorySizeBeforeCurrentTurn,
                                prompt.getInstructions().size()
                        )
                );

        currentTurnMessages.add(
                new AssistantMessage(finalAnswer)
        );

        chatMemory.add(
                conversationId,
                currentTurnMessages
        );

        return finalAnswer;
    }

    public String approve(
            String conversationId,
            boolean approved) {

        PendingApproval pending =
                pendingApprovals.remove(conversationId);

        if (pending == null) {
            throw new IllegalStateException(
                    "当前会话没有待审批操作"
            );
        }

        // ==============================
        // 人工拒绝
        // ==============================

        if (!approved) {

            String answer =
                    "已拒绝执行 restartService，服务未重启。";

            List<Message> currentTurnMessages =
                    new ArrayList<>(
                            pending.prompt()
                                    .getInstructions()
                                    .subList(
                                            pending.memorySizeBeforeCurrentTurn(),
                                            pending.prompt()
                                                    .getInstructions()
                                                    .size()
                                    )
                    );

            currentTurnMessages.add(
                    new AssistantMessage(answer)
            );

            chatMemory.add(
                    conversationId,
                    currentTurnMessages
            );

            log.info(
                    "[HITL] rejected, conversationId={}",
                    conversationId
            );

            return answer;
        }


        // ==============================
        // 人工批准
        // 到这里才真正执行 Tool
        // ==============================

        log.info(
                "[HITL] approved, conversationId={}",
                conversationId
        );

        ToolExecutionResult toolResult =
                toolCallingManager.executeToolCalls(
                        pending.prompt(),
                        pending.chatResponse()
                );

        Prompt prompt = new Prompt(
                toolResult.conversationHistory(),
                pending.chatOptions()
        );

        // restartService 的执行结果重新交给 LLM
        ChatClientResponse response = chatClient.prompt()
                .messages(toolResult.conversationHistory())
                .options(
                        pending.chatOptions().mutate()
                )
                .advisors(
                        AdvisorParams
                                .toolCallingAdvisorAutoRegister(false)
                )
                .call()
                .chatClientResponse();

        // 从暂停点继续 Agent Loop
        return continueLoop(
                conversationId,
                pending.userMessage(),
                pending.memorySizeBeforeCurrentTurn(),
                pending.chatOptions(),
                prompt,
                response,
                pending.step()
        );
    }

    private record PendingApproval(
            String userMessage,
            Prompt prompt,
            org.springframework.ai.chat.model.ChatResponse chatResponse,
            ChatOptions chatOptions,
            int memorySizeBeforeCurrentTurn,
            int step
    ){

    }


}