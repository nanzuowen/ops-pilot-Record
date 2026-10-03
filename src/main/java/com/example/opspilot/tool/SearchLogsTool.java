package com.example.opspilot.tool;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class SearchLogsTool {

    private static final Logger log = LoggerFactory.getLogger(SearchLogsTool.class);

    @Tool(
            name = "searchLogs",
            description = """
                    Search recent application logs of a microservice.
                    Use this tool when log evidence is needed to investigate
                    errors, timeouts, exceptions, connection failures,
                    or abnormal behavior.
                    """
    )
    public SearchLogsResult searchLogs(
            @ToolParam(description = "Name of the microservice, for example order-service")
            String serviceName){

            log.info("Tool searchLogs called, serviceName={}",serviceName);

            if ("order-service".equalsIgnoreCase(serviceName)){
                return new SearchLogsResult(
                        serviceName,
                        List.of(
                                "ERROR RedisConnectionFailureException: Could not get a resource from the pool",
                                "WARN Redis connection wait time exceeded 1000ms",
                                "WARN Failed to acquire Redis connection after waiting",
                                "INFO redis.pool.active=50, redis.pool.maxActive=50, redis.pool.idle=0"
                        )
                );
            }
            return new SearchLogsResult(
                    serviceName,
                    List.of(
                            "INFO Service is running normally",
                            "INFO No recent errors found"
                    )
            );
    }
    public record SearchLogsResult(
            String serviceName,
            List<String> logs
    ){

    }
}
