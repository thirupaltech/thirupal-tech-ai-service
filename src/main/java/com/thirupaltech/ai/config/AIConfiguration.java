package com.thirupaltech.ai.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.google.genai.GoogleGenAiChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class AIConfiguration {

    @Bean
    ChatClient groqChatClient(OpenAiChatModel groqChatModel) {
        return ChatClient.create(groqChatModel);
    }

    @Bean
    ChatClient geminiChatClient(GoogleGenAiChatModel geminiChatModel) {
        return ChatClient.create(geminiChatModel);
    }

    @Bean
    AsyncTaskExecutor aiProviderExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(8);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(32);
        executor.setThreadNamePrefix("ai-provider-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(5);
        return executor;
    }
}
