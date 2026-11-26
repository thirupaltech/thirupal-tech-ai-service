package com.thirupaltech.ai.provider;

import java.time.Duration;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class GeminiAIProvider implements AIProvider {

    private final ChatClient chatClient;
    private final Duration requestTimeout;

    public GeminiAIProvider(
            @Qualifier("geminiChatClient") ChatClient chatClient,
            @Value("${community-ai.provider.gemini-request-timeout:130s}") Duration requestTimeout) {
        this.chatClient = chatClient;
        this.requestTimeout = requestTimeout;
    }

    @Override
    public String name() {
        return "Gemini";
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
