package com.example.opspilotmcp.tool;

import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

@Component
public class TraceMcpTool {

    @McpTool(
            name = "getTrace",
            description = "查询指定服务的调用链，用于定位下游服务超时、慢调用和错误传播"
    )
    public String getTrace(
            @McpToolParam(
                    description = "服务名称，例如 order-service",
                    required = true
            )
            String serviceName) {

        if ("order-service".equalsIgnoreCase(serviceName)) {
            return """
                    traceId: trace-order-001
                    service: order-service
                    totalDuration: 3250ms

                    spans:
                    - order-service -> payment-service
                      duration: 85ms
                      status: OK

                    - order-service -> inventory-service
                      duration: 3012ms
                      status: TIMEOUT

                    evidence:
                    inventory-service 调用耗时超过 3 秒，并发生 TIMEOUT。
                    """;
        }

        return """
                service: %s
                trace: 未发现异常调用链
                """.formatted(serviceName);
    }
}