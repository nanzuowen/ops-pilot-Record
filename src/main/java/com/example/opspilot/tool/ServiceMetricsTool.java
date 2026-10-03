package com.example.opspilot.tool;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class ServiceMetricsTool {
    private static final Logger log =
            LoggerFactory.getLogger(ServiceMetricsTool.class);

    @Tool(
            name = "getServiceMetrics",
            description = """
                    Get runtime metrics of a microservice.
                    Use this tool when you need to inspect the current health,
                    performance, resource usage, connection pools,
                    latency or error rate of a service.
                    """
    )
    public ServiceMetrics getServiceMetrics(
            @ToolParam(description = "Name of the microservice,for example order-service")
            String serviceName){
        log.info("Tool getServiceMetrics called, serviceName={}", serviceName);

        if ("order-service".equalsIgnoreCase(serviceName)){
            return new ServiceMetrics(
                    serviceName,
                    42.3,
                    68.5,
                    380,
                    1250,
                    8.7,
                    Map.of(
                            "redis.pool.maxActive",50,
                            "redis.pool.active",50,
                            "redis.pool.idle",0,
                            "redis.pool.pendingThreads",32,
                            "redis.command.avgLatencyMs",8
                    )
            );
        }

        return new ServiceMetrics(
                serviceName,
                25.0,
                45.0,
                120,
                80,
                0.2,
                Map.of()
        );
    }

    public record ServiceMetrics(
            String serviceName,
            double cpuUsagePercent,
            double memoryUsagePercent,
            int requestQps,
            long p99LatencyMs,
            double errorRatePercent,
            Map<String,Object> details
    ){

    }
}
