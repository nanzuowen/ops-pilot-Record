package com.example.opspilot.controller;

import com.example.opspilot.service.AgentLoopService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.MediaType;
import reactor.core.publisher.Flux;

@RestController
@RequestMapping("/api/chat")
public class ChatController {
    private final AgentLoopService agentLoopService;

    public ChatController(AgentLoopService agentLoopService){
        this.agentLoopService = agentLoopService;
    }

    @PostMapping
    public ChatResponse chat(@RequestBody ChatRequest request){
        String content = agentLoopService.chat(request.conversationId(),request.message());
        return new ChatResponse(content);
    }

    @PostMapping(value = "/stream",produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> stream(@RequestBody ChatRequest request){
        return agentLoopService.streamChat(request.conversationId(),request.message());
    }

    @PostMapping("/approve")
    public ChatResponse approve(
            @RequestBody ApprovalRequest request) {

        String content = agentLoopService.approve(
                request.conversationId(),
                request.approved()
        );

        return new ChatResponse(content);
    }

    public record ChatRequest(String conversationId, String message){

    }
    public record ChatResponse(String content){

    }
    public record ApprovalRequest(
            String conversationId,
            boolean approved
    ) {
    }
}
