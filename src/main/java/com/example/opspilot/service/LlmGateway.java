package com.example.opspilot.service;

import org.springframework.ai.chat.client.AdvisorParams;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.List;

@Service
public class LlmGateway {

    private final ChatClient chatClient;

    public LlmGateway(ChatClient chatClient){
        this.chatClient = chatClient;
    }

    //1.同步调用
    public ChatClientResponse call(
            List<Message> messages,
            ChatOptions options
    ){
        return chatClient.prompt()
                .messages(messages)
                .options(options.mutate())
                .advisors(
                        AdvisorParams.toolCallingAdvisorAutoRegister(false)
                )
                .call()
                .chatClientResponse();
    }

    // 2.流式调用
    public Flux<ChatResponse> stream(
            List<Message> messages,
            ChatOptions options
    ){
        return chatClient.prompt()
                .messages(messages)
                .options(options.mutate())
                .advisors(
                        AdvisorParams.toolCallingAdvisorAutoRegister(false)
                )
                .stream()
                .chatResponse();
    }
}
