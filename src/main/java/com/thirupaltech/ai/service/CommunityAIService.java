package com.thirupaltech.ai.service;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import com.google.genai.errors.ApiException;
import com.thirupaltech.ai.constants.AIConstants;
import com.thirupaltech.ai.exception.AIServiceException;
import com.thirupaltech.ai.provider.AIProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;

@Service
public class CommunityAIService {

    private static final Logger log = LoggerFactory.getLogger(CommunityAIService.class);

    private final AIProvider primaryProvider;
    private final AIProvider fallbackProvider;
    private final String systemPrompt;
    private final AsyncTaskExecutor providerExecutor;

    public CommunityAIService(
            @Qualifier("groqAIProvider") AIProvider primaryProvider,
            @Qualifier("geminiAIProvider") AIProvider fallbackProvider,
            @Value("${community-ai.system-prompt}") String systemPrompt,
            @Qualifier("aiProviderExecutor") AsyncTaskExecutor providerExecutor) {
        this.primaryProvider = primaryProvider;
        this.fallbackProvider = fallbackProvider;
        this.systemPrompt = systemPrompt;
        this.providerExecutor = providerExecutor;
    }

    public String chat(String message) {
        log.debug("Executing community AI provider request");
        try {
            return callProvider(primaryProvider, message);
        } catch (AIServiceException primaryFailure) {
            if (Thread.currentThread().isInterrupted()) {
                throw primaryFailure;
            }

            log.warn(
                    "{} provider failed with {}; attempting {} fallback",
                    primaryProvider.name(),
                    primaryFailure.getErrorCode(),
                    fallbackProvider.name());
            try {
                return callProvider(fallbackProvider, message);
            } catch (AIServiceException fallbackFailure) {
                fallbackFailure.addSuppressed(primaryFailure);
                log.error(
                        "Both AI providers failed: {}={}, {}={}",
                        primaryProvider.name(),
                        primaryFailure.getErrorCode(),
                        fallbackProvider.name(),
                        fallbackFailure.getErrorCode());
                throw fallbackFailure;
            }
        }
    }

    private String callProvider(AIProvider provider, String message) {
        String providerName = provider.name();
        Duration requestTimeout = provider.requestTimeout();
        Future<String> response;
        try {
            long submittedAtNanos = System.nanoTime();
            log.info("Submitting {} provider request", providerName);
            response = providerExecutor.submit(() -> {
                long providerStartedAtNanos = System.nanoTime();
                log.info(
                        "{} provider task started after {} ms in the executor queue",
                        providerName,
                        elapsedMillis(submittedAtNanos, providerStartedAtNanos));
                try {
                    String content = provider.generate(systemPrompt, message);
                    log.info("{} provider call succeeded in {} ms",
                            providerName,
                            elapsedMillis(providerStartedAtNanos, System.nanoTime()));
                    return content;
                } catch (RuntimeException exception) {
                    log.warn(
                            "{} provider call failed in {} ms ({})",
                            providerName,
                            elapsedMillis(providerStartedAtNanos, System.nanoTime()),
                            exception.getClass().getSimpleName());
                    throw exception;
                }
            });
        } catch (RuntimeException exception) {
            log.warn("{} provider executor rejected a request", providerName);
            throw new AIServiceException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    AIConstants.PROVIDER_UNAVAILABLE_ERROR_CODE,
                    "The AI provider is temporarily unavailable.",
                    exception);
        }

        try {
            String answer = response.get(requestTimeout.toMillis(), TimeUnit.MILLISECONDS);
            if (answer == null || answer.isBlank()) {
                throw new AIServiceException(
                        HttpStatus.BAD_GATEWAY,
                        AIConstants.EMPTY_RESPONSE_ERROR_CODE,
                        "The AI provider returned an empty response.");
            }
            return answer.trim();
        } catch (TimeoutException exception) {
            log.warn("{} provider request timed out after {} ms", providerName, requestTimeout.toMillis());
            response.cancel(true);
            throw new AIServiceException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    AIConstants.PROVIDER_UNAVAILABLE_ERROR_CODE,
                    "The AI provider request timed out.",
                    exception);
        } catch (InterruptedException exception) {
            response.cancel(true);
            Thread.currentThread().interrupt();
            throw new AIServiceException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    AIConstants.PROVIDER_UNAVAILABLE_ERROR_CODE,
                    "The AI provider request was interrupted.",
                    exception);
        } catch (ExecutionException exception) {
            Throwable providerFailure = exception.getCause();
            if (providerFailure instanceof RuntimeException runtimeException) {
                AIServiceException serviceException = mapProviderFailure(runtimeException);
                log.warn("{} provider request failed with {}", providerName, serviceException.getErrorCode());
                throw serviceException;
            }
            throw new AIServiceException(
                    HttpStatus.BAD_GATEWAY,
                    AIConstants.PROVIDER_ERROR_CODE,
                    "The AI provider request failed.",
                    providerFailure);
        } catch (AIServiceException exception) {
            throw exception;
        }
    }

    private static long elapsedMillis(long startTimeNanos, long endTimeNanos) {
        return TimeUnit.NANOSECONDS.toMillis(endTimeNanos - startTimeNanos);
    }

    private AIServiceException mapProviderFailure(RuntimeException exception) {
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof RestClientResponseException responseException) {
                HttpStatus status = responseException.getStatusCode().value() == 429
                        ? HttpStatus.TOO_MANY_REQUESTS
                        : responseException.getStatusCode().is5xxServerError()
                                ? HttpStatus.SERVICE_UNAVAILABLE
                                : HttpStatus.BAD_GATEWAY;
                String errorCode = status == HttpStatus.TOO_MANY_REQUESTS
                        ? AIConstants.RATE_LIMIT_ERROR_CODE
                        : AIConstants.PROVIDER_ERROR_CODE;
                return new AIServiceException(status, errorCode, "The AI provider request failed.", exception);
            }
            if (cause instanceof ApiException apiException) {
                int statusCode = apiException.code();
                HttpStatus status = statusCode == 429
                        ? HttpStatus.TOO_MANY_REQUESTS
                        : statusCode >= 500 && statusCode < 600
                                ? HttpStatus.SERVICE_UNAVAILABLE
                                : HttpStatus.BAD_GATEWAY;
                String errorCode = status == HttpStatus.TOO_MANY_REQUESTS
                        ? AIConstants.RATE_LIMIT_ERROR_CODE
                        : status == HttpStatus.SERVICE_UNAVAILABLE
                                ? AIConstants.PROVIDER_UNAVAILABLE_ERROR_CODE
                                : AIConstants.PROVIDER_ERROR_CODE;
                return new AIServiceException(status, errorCode, "The AI provider request failed.", exception);
            }
            if (cause instanceof ResourceAccessException
                    || cause instanceof IOException
                    || cause instanceof TimeoutException) {
                return new AIServiceException(
                        HttpStatus.SERVICE_UNAVAILABLE,
                        AIConstants.PROVIDER_UNAVAILABLE_ERROR_CODE,
                        "The AI provider is temporarily unavailable.",
                        exception);
            }
            String exceptionType = cause.getClass().getSimpleName().toLowerCase();
            if (exceptionType.contains("ratelimit") || exceptionType.contains("toomanyrequests")) {
                return new AIServiceException(
                        HttpStatus.TOO_MANY_REQUESTS,
                        AIConstants.RATE_LIMIT_ERROR_CODE,
                        "The AI provider is temporarily rate-limiting requests.",
                        exception);
            }
            if (exceptionType.contains("unavailable") || exceptionType.contains("serviceunavailable")) {
                return new AIServiceException(
                        HttpStatus.SERVICE_UNAVAILABLE,
                        AIConstants.PROVIDER_UNAVAILABLE_ERROR_CODE,
                        "The AI provider is temporarily unavailable.",
                        exception);
            }
        }
        return new AIServiceException(
                HttpStatus.BAD_GATEWAY,
                AIConstants.PROVIDER_ERROR_CODE,
                "The AI provider request failed.",
                exception);
    }
}
