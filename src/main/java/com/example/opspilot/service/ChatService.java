package com.example.opspilot.service;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

@Service
public class ChatService {
    private final ChatClient chatClient;

    public ChatService(ChatClient.Builder builder){
        this.chatClient = builder
                .defaultSystem("""
                        You are OpsPilot, an AI assistant for diagnosing microservice failures.
                        
                        Rules:
                        - Focus on Java microservice troubleshooting.
                        - Give concise answers.
                        - Explain the evidence behind your conclusion.
                        - If information is insufficient, say what information is missing.
                        - Do not pretend that you have accessed metrics, logs, traces, or configs.
                        """)
                .build();
    }

    public String chat(String message){
        return chatClient
                .prompt()
                .user(message)
                .call()
                .content();
    }
}
