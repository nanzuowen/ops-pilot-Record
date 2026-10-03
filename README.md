# OpsPilot

基于 Java + Spring AI 构建的微服务智能故障诊断 Agent，用于学习和实践 AI Agent 核心技术。

## 技术栈

- Java 21
- Spring Boot
- Spring AI
- DeepSeek API
- Ollama + nomic-embed-text

### 总计划

- V0：DeepSeek基础对话
- V1：Tool Calling
- V2：Agent Loop
- V3：RAG
- V4：Memory + Human-in-the-loop
- V5：MCP
- 最后：SSE Streaming + 简单 UI

```text
V0：会聊天
↓
V1：会使用工具
↓
V2：会循环使用工具完成任务
↓
V3：让 Agent 获得外部知识
```

## 开发进度

### V0：DeepSeek 基础对话 ✅

已完成：

- 接入 Spring AI 和 DeepSeek API
- 使用 ChatClient 调用 LLM
- 配置 OpsPilot System Prompt
- 实现 `POST /api/chat` 接口
- 完成 Prompt → Spring AI → DeepSeek → Response 基础链路

## V1 - Tool Calling

当前实现 `getServiceMetrics` Tool。

Agent 可以根据用户问题自主判断是否需要调用工具：

- `帮我检查一下 order-service 为什么最近响应很慢`
    - DeepSeek 自动调用 `getServiceMetrics`
    - 获取 Mock Metrics
    - 根据指标诊断 Redis 连接池耗尽

- `什么是 Redis 连接池？`
    - 不调用 Tool
    - LLM 直接回答

核心链路：

User → LLM → Tool Call → Java Tool → Tool Result → LLM → Answer

## V2 - Agent Loop ✅

在 V1 Tool Calling 基础上，手动实现 Agent Loop。

新增：

- `AgentLoopService`
- `searchLogs` Tool
- `ToolCallingManager`
- Tool Result 回填 Conversation History
- `MAX_STEPS` 最大循环轮数保护

核心流程：

```text
User
  ↓
LLM
  ↓
Tool Call
  ↓
Java Tool
  ↓
Tool Result
  ↓
LLM 再次推理
  ↓
继续调用 Tool / 输出最终答案
```