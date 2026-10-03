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

V0：会聊天
↓
V1：会使用工具
↓
V2：会循环使用工具完成任务
↓
V3：让 Agent 获得外部知识
↓
V4：让 Agent 记住上下文，并在关键操作前请求人工确认
↓
V5：让 Agent 通过 MCP 接入外部工具
↓
最后：让 Agent 流式输出，并提供简单 UI

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


### V3 - RAG

- 接入 Ollama Embedding
- 使用 nomic-embed-text 生成文本向量
- 使用 SimpleVectorStore 存储 Runbook
- 添加 Redis 连接池耗尽 Runbook
- 添加 MySQL 慢查询 Runbook
- 添加下游服务超时 Runbook
- 实现 searchRunbook Tool
- 基于向量相似度进行语义检索
- 将 RAG 接入 Agent Loop
- 实现 Metrics + Logs + Runbook 联合故障诊断

### V4 - Memory + Human-in-the-loop ✅

在 Agent Loop 基础上加入多轮会话记忆和高风险 Tool 人工审批。

新增：

- 接入 Spring AI ChatMemory
- 使用 conversationId 隔离不同会话
- 保存 User / Assistant / Tool Call / Tool Result 上下文
- 支持跨轮次引用历史诊断结果和 Tool Evidence
- 新增 restartService Tool
- 对有副作用 Tool 增加 Human-in-the-loop 审批
- 使用 PendingApproval 保存暂停时的 Agent 执行状态
- 支持 approve / reject
- 审批通过后恢复 Agent Loop 并继续执行
- 拒绝审批时不执行危险 Tool

核心流程：

User
  ↓
LLM
  ↓
Tool Call
  ↓
Safe Tool ─────────────→ Execute
  ↓
restartService
  ↓
Approval Required
  ↓
Human Approve / Reject
  ↓
Approve → Execute Tool → Resume Agent Loop
Reject  → Do Not Execute

### V5 - MCP ✅

在现有 Agent Loop 基础上接入 MCP，使 Agent 可以同时调用本地 Tool 和外部 MCP Tool。

新增：

- 新建独立 `mcp-server` Spring Boot 服务
- 使用 Streamable HTTP 暴露 MCP Server
- 新增 `getTrace` MCP Tool
- OpsPilot 接入 Spring AI MCP Client
- 使用 `SyncMcpToolCallbackProvider` 获取 MCP Tool
- 将 Local Tool 和 MCP Tool 统一转换为 `ToolCallback`
- 将 MCP Tool 接入现有手写 Agent Loop
- DeepSeek 可自主决定是否调用 `getTrace`
- 支持 Metrics + Logs + Trace + Runbook 联合诊断
- 保留 restartService Human-in-the-loop 审批机制

核心流程：

User
↓
DeepSeek
↓
Agent Loop
↓
Local Tool / MCP Tool
↓
Tool Result
↓
DeepSeek 再次推理
↓
最终诊断

MCP 调用链：

OpsPilot :8082
↓
MCP Client
↓
Streamable HTTP
↓
MCP Server :8083
↓
getTrace