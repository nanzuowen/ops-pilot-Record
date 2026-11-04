package com.example.opspilot.service;

import com.example.opspilot.tool.RestartServiceTool;
import com.example.opspilot.tool.SearchLogsTool;
import com.example.opspilot.tool.SearchRunbookTool;
import com.example.opspilot.tool.ServiceMetricsTool;
import lombok.extern.slf4j.Slf4j;
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
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.MessageAggregator;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;
import reactor.core.scheduler.Schedulers;

import java.util.concurrent.atomic.AtomicReference;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class AgentLoopService {

    private static final int MAX_STEPS = 5;

    private final LlmGateway llmGateway;

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
            LlmGateway llmGateway,
            ServiceMetricsTool serviceMetricsTool,
            SearchLogsTool searchLogsTool,
            SearchRunbookTool searchRunbookTool,
            RestartServiceTool restartServiceTool,
            SyncMcpToolCallbackProvider mcpToolCallbackProvider,
            ChatMemory chatMemory) {

        this.llmGateway = llmGateway;
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
        ChatClientResponse response = llmGateway.call(conversationMessages,chatOptions);

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

    public Flux<String> streamChat(String conversationId, String message){
        if (conversationId == null || conversationId.isBlank()){
            return Flux.error(new IllegalArgumentException("conversationId 不能为空"));
        }

        if (pendingApprovals.containsKey(conversationId)){
            return Flux.just("""
                    当前会话存在待确认操作。
                    请先完成 restartService 的人工审批。
                    """);
        }

        //local Tools
        ToolCallback[] localTools = ToolCallbacks.from(serviceMetricsTool, searchLogsTool, searchRunbookTool, restartServiceTool);

        //MCP Tools
        ToolCallback[] mcpTools = mcpToolCallbackProvider.getToolCallbacks();

        List<ToolCallback> allTools = new ArrayList<>(localTools.length+mcpTools.length);

        allTools.addAll(List.of(localTools));
        allTools.addAll(List.of(mcpTools));

        log.info(
                "[AgentStream] availableTools={}",
                allTools.stream()
                        .map(tool -> tool.getToolDefinition().name())
                        .toList()
        );

        ChatOptions chatOptions = ToolCallingChatOptions.builder()
                .toolCallbacks(allTools)
                .build();

        //Chat Memory
        List<Message> conversationMessages = new ArrayList<>(chatMemory.get(conversationId));

        int memorySizeBeforeCurrentTurn = conversationMessages.size();

        conversationMessages.add(new UserMessage(message));

        Prompt prompt = new Prompt(conversationMessages,chatOptions);

        /*
         * 我们现有 Agent Loop 是同步、命令式结构。
         *
         * Flux.create 负责把结果逐段推给客户端，
         * boundedElastic 负责避免 blockLast()
         * 占用 Web 请求线程。
         */
        return Flux.create(
                sink -> Schedulers.boundedElastic()
                        .schedule(() -> {
                            try{
                                runStreamingLoop(
                                        conversationId,
                                        message,
                                        memorySizeBeforeCurrentTurn,
                                        chatOptions,
                                        prompt,
                                        sink
                                );
                            }
                            catch (Exception e){
                                log.error("[AgentStream] failed",e);

                                sink.error(e);
                            }
                        }),

                FluxSink.OverflowStrategy.BUFFER
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

            response = llmGateway.call(toolResult.conversationHistory(),chatOptions);
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

    private void runStreamingLoop(
            String conversationId,
            String userMessage,
            int memorySizeBeforeCurrentTurn,
            ChatOptions chatOptions,
            Prompt initialPrompt,
            FluxSink<String> sink){
        Prompt prompt = initialPrompt;

        int step = 0;

        while(true){
            AtomicReference<ChatResponse> aggregatedResponse =
                    new AtomicReference<>();

            new MessageAggregator()
                    .aggregate(
                            llmGateway.stream(prompt.getInstructions(),chatOptions)
                                    .doOnNext(
                                            chunk ->
                                                    emitTextChunk(
                                                            chunk,
                                                            sink
                                                    )
                                    ),

                            aggregatedResponse::set
                    )
                    .blockLast();

            ChatResponse response =
                    aggregatedResponse.get();

            if (response == null
            || response.getResult() == null){
                throw new IllegalStateException("LLM未返回有效结果");
            }

            /*
             * 没有 Tool Call：
             * Agent Loop 结束。
             */
            if (!response.hasToolCalls()){
                String finalAnswer = response
                        .getResult()
                        .getOutput()
                        .getText();

                List<Message> currentTurnMessages = new ArrayList<>(
                        prompt.getInstructions()
                                .subList(
                                        memorySizeBeforeCurrentTurn,
                                        prompt.getInstructions().size()
                                )
                );

                chatMemory.add(conversationId,currentTurnMessages);

                log.info("[AgentStream] finished, step={}",step);

                sink.complete();
                return;
            }

            // Tool Calling
            step++;

            if (step > MAX_STEPS){
                throw new IllegalStateException("Agent 超过最大执行轮数：" + MAX_STEPS);
            }

            var toolCalls = response
                    .getResult()
                    .getOutput()
                    .getToolCalls();

            log.info("[AgentStream] step={},toolCalls={}",
                    step,
                    toolCalls);

            // Human-in-the-loop
            boolean requiresApproval = toolCalls.stream()
                    .anyMatch(toolCall -> "restartService".equals(toolCall.name()));

            if (requiresApproval){
                pendingApprovals.put(conversationId,
                        new PendingApproval(
                                userMessage,
                                prompt,
                                response,
                                chatOptions,
                                memorySizeBeforeCurrentTurn,
                                step
                        ));

                sink.next("""
                    需要人工确认：Agent 请求执行 restartService。
                    请确认是否允许执行该操作。
                    """);

                sink.complete();

                return;
            }

            //执行 Local / MCP Tool
            ToolExecutionResult toolResult = toolCallingManager.executeToolCalls(prompt,response);

            /*
             * Tool Result 加回上下文，
             * 进入下一轮 LLM。
             */
            prompt = new Prompt(
                    toolResult.conversationHistory(),
                    chatOptions
            );
        }
    }

    private void emitTextChunk(
            ChatResponse chunk,
            FluxSink<String> sink) {

        if (chunk == null
                || chunk.getResult() == null) {
            return;
        }

        String text =
                chunk.getResult()
                        .getOutput()
                        .getText();

        if (text != null && !text.isEmpty()) {
            sink.next(text);
        }
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
        ChatClientResponse response = llmGateway.call(toolResult.conversationHistory(),pending.chatOptions());

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