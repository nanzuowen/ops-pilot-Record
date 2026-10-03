package com.example.opspilot.tool;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class RestartServiceTool {
    @Tool(
            name = "restartService",
            description = """
                    Restart a microservice.
                    This is a destructive operation and must only be used
                    when the user explicitly requests a restart.
                    """
    )
    public String restartService(
            @ToolParam(description = "Name of the service to restart")
            String serviceName
    ){
        log.warn("[restartService] EXECUTED service={}", serviceName);

        //Mock, 不真的操作 Kubernetes
        return "Service" + serviceName + " restarted successfully";
    }
}
