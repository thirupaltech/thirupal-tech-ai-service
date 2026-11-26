package com.thirupaltech.ai.provider;

import java.time.Duration;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class GroqAIProvider implements AIProvider {

    private final ChatClient chatClient;
    private final Duration requestTimeout;

    public GroqAIProvider(
            @Qualifier("groqChatClient") ChatClient chatClient,
            @Value("${community-ai.provider.groq-request-timeout:30s}") Duration requestTimeout) {
        this.chatClient = chatClient;
        this.requestTimeout = requestTimeout;
    }

    @Override
    public String name() {
        return "Groq";
    }

    @Override
    public Duration requestTimeout() {
        return requestTimeout;
    }

    @Override
    public String generate(String systemPrompt, String message) {
        return chatClient.prompt()
                .system(systemPrompt)
                .user(message)
                .call()
                .content();
    }
}
