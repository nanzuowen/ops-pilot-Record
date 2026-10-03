# OpsPilot

基于 Java + Spring AI 构建的微服务智能故障诊断 Agent，用于学习和实践 AI Agent 核心技术。

## 技术栈

- Java 21
- Spring Boot
- Spring AI
- DeepSeek API
- Ollama + nomic-embed-text

## 开发进度

### V0：DeepSeek 基础对话 ✅

已完成：

- 接入 Spring AI 和 DeepSeek API
- 使用 ChatClient 调用 LLM
- 配置 OpsPilot System Prompt
- 实现 `POST /api/chat` 接口
- 完成 Prompt → Spring AI → DeepSeek → Response 基础链路

### 后续计划

- V1：Tool Calling
- V2：Agent Loop
- V3：RAG
- V4：Memory + Human-in-the-loop
- V5：MCP
- 最后：SSE Streaming + 简单 UI