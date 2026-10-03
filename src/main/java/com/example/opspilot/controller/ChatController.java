package com.example.opspilot.controller;

import com.example.opspilot.service.AgentLoopService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/chat")
public class ChatController {
    private final AgentLoopService agentLoopService;

    public ChatController(AgentLoopService agentLoopService){
        this.agentLoopService = agentLoopService;
    }

    @PostMapping
    public ChatResponse chat(@RequestBody ChatRequest request){
        String content = agentLoopService.chat(request.message());
        return new ChatResponse(content);
    }

    public record ChatRequest(String message){

    }
    public record ChatResponse(String content){

    }
}
