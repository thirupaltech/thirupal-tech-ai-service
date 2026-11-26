package com.thirupaltech.ai.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Duration;

import com.google.genai.errors.ClientException;
import com.thirupaltech.ai.exception.AIServiceException;
import com.thirupaltech.ai.provider.GeminiAIProvider;
import com.thirupaltech.ai.provider.GroqAIProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.core.task.support.TaskExecutorAdapter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;

class CommunityAIServiceTest {

    private final ChatClient groqChatClient = mock(ChatClient.class);
    private final ChatClient.ChatClientRequestSpec groqRequestSpec = mock(ChatClient.ChatClientRequestSpec.class);
    private final ChatClient.CallResponseSpec groqCallResponseSpec = mock(ChatClient.CallResponseSpec.class);
    private final ChatClient geminiChatClient = mock(ChatClient.class);
    private final ChatClient.ChatClientRequestSpec geminiRequestSpec = mock(ChatClient.ChatClientRequestSpec.class);
    private final ChatClient.CallResponseSpec geminiCallResponseSpec = mock(ChatClient.CallResponseSpec.class);
    private CommunityAIService service;

    @BeforeEach
    void setUp() {
        service = new CommunityAIService(
                new GroqAIProvider(groqChatClient, Duration.ofSeconds(30)),
                new GeminiAIProvider(geminiChatClient, Duration.ofSeconds(130)),
                "system instructions",
                new TaskExecutorAdapter(Runnable::run));
        configureClient(groqChatClient, groqRequestSpec, groqCallResponseSpec);
        configureClient(geminiChatClient, geminiRequestSpec, geminiCallResponseSpec);
    }

    @Test
    void returnsGroqResponseWithoutCallingGemini() {
        when(groqCallResponseSpec.content()).thenReturn("  Hello, community!  ");

        assertEquals("Hello, community!", service.chat("Hello"));
        verifyNoInteractions(geminiChatClient);
    }

    @Test
    void fallsBackToGeminiWhenGroqFails() {
        when(groqCallResponseSpec.content()).thenThrow(new IllegalStateException("Groq unavailable"));
        when(geminiCallResponseSpec.content()).thenReturn("Gemini fallback response");

        assertEquals("Gemini fallback response", service.chat("Hello"));
    }

    @Test
    void mapsProviderFailureToSanitizedExceptionWhenBothProvidersFail() {
        when(groqCallResponseSpec.content()).thenThrow(new IllegalStateException("private Groq response"));
        when(geminiCallResponseSpec.content()).thenThrow(new IllegalStateException("private Gemini response"));

        AIServiceException exception = assertThrows(AIServiceException.class, () -> service.chat("Hello"));

        assertEquals(HttpStatus.BAD_GATEWAY, exception.getStatus());
        assertEquals("AI_PROVIDER_ERROR", exception.getErrorCode());
        assertEquals("The AI provider request failed.", exception.getMessage());
    }

    @Test
    void mapsRateLimitToTooManyRequests() {
        HttpClientErrorException rateLimitException = HttpClientErrorException.create(
                HttpStatusCode.valueOf(429),
                "Too Many Requests",
                HttpHeaders.EMPTY,
                new byte[0],
                null);
        when(groqCallResponseSpec.content()).thenThrow(rateLimitException);
        when(geminiCallResponseSpec.content()).thenThrow(rateLimitException);

        AIServiceException exception = assertThrows(AIServiceException.class, () -> service.chat("Hello"));

        assertEquals(HttpStatus.TOO_MANY_REQUESTS, exception.getStatus());
        assertEquals("AI_RATE_LIMITED", exception.getErrorCode());
    }

    @Test
    void mapsGeminiSdkRateLimitToTooManyRequestsAfterGroqFails() {
        when(groqCallResponseSpec.content()).thenThrow(new IllegalStateException("Groq unavailable"));
        when(geminiCallResponseSpec.content())
                .thenThrow(new ClientException(429, "Too Many Requests", "Quota exceeded"));

        AIServiceException exception = assertThrows(AIServiceException.class, () -> service.chat("Hello"));

        assertEquals(HttpStatus.TOO_MANY_REQUESTS, exception.getStatus());
        assertEquals("AI_RATE_LIMITED", exception.getErrorCode());
    }

    @Test
    void rejectsEmptyProviderResponsesFromBothProviders() {
        when(groqCallResponseSpec.content()).thenReturn("  ");
        when(geminiCallResponseSpec.content()).thenReturn("  ");

        AIServiceException exception = assertThrows(AIServiceException.class, () -> service.chat("Hello"));

        assertEquals(HttpStatus.BAD_GATEWAY, exception.getStatus());
        assertEquals("AI_EMPTY_RESPONSE", exception.getErrorCode());
    }

    private static void configureClient(
            ChatClient chatClient,
            ChatClient.ChatClientRequestSpec requestSpec,
            ChatClient.CallResponseSpec callResponseSpec) {
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.system(anyString())).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callResponseSpec);
    }
}
