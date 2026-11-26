# ThirupalTech Community AI

An independently deployable Spring Boot REST microservice that provides general conversational answers using Groq through Spring AI, with Google Gemini as an automatic fallback. This initial release does not connect to the ThirupalTech backend or a database and does not retrieve live community information.

## Architecture

```text
React application
    -> POST /api/v1/community-ai/chat
    -> Spring Boot controller and validation
    -> CommunityAIService
    -> AIProvider interface
        -> GroqAIProvider -> Groq API (primary)
        -> GeminiAIProvider -> Google Gemini API (fallback)
```

The configured system prompt sets community-focused behavior and makes it clear that current events or program details are not available. The prompt, model, and generation settings are configurable without exposing provider credentials to the React client.

## Technology stack

- Java 17
- Spring Boot 3.5.16
- Spring AI 1.1.8
- Maven
- Spring Web, Validation, Actuator
- Groq API (OpenAI-compatible)
- Google Gemini Developer API (Google GenAI, fallback)

## Prerequisites

- JDK 17
- Maven 3.9+
- A Groq API key from [Groq Console](https://console.groq.com/keys)
- A Gemini API key from [Google AI Studio](https://aistudio.google.com/app/apikey) for fallback

## Local setup

From this directory, set both API keys in your shell. Groq is tried first; Gemini is called only if Groq fails. Never put keys in React or commit them to source control.

The root `env.default` file is a reference template; Spring Boot does not load it automatically.

PowerShell:

```powershell
$env:GROQ_API_KEY = "your-groq-api-key"
$env:GEMINI_API_KEY = "your-google-ai-studio-key"
```

macOS/Linux:

```bash
export GROQ_API_KEY="your-groq-api-key"
export GEMINI_API_KEY="your-google-ai-studio-key"
```

Optional settings can also be supplied as environment variables. The defaults below match `env.default`.

| Variable | Default | Description |
| --- | --- | --- |
| `GROQ_MODEL` | `openai/gpt-oss-120b` | Primary Groq model identifier |
| `GROQ_TEMPERATURE` | `0.7` | Groq generation temperature |
| `GROQ_MAX_OUTPUT_TOKENS` | `1024` | Maximum Groq output tokens |
| `GROQ_REQUEST_TIMEOUT` | `30s` | Maximum time the service waits for Groq |
| `GEMINI_MODEL` | `gemini-3.8-flash` | Gemini fallback model identifier |
| `GEMINI_TEMPERATURE` | `0.7` | Generation temperature (0.0–1.0) |
| `GEMINI_MAX_OUTPUT_TOKENS` | `1024` | Maximum generated output tokens |
| `GEMINI_REQUEST_TIMEOUT` | `130s` | Maximum time the service waits for Gemini |
| `COMMUNITY_AI_ALLOWED_ORIGINS` | `*` | Comma-separated browser origins allowed by CORS |

Provider timeouts apply sequentially. If Groq times out or fails, the service may then wait for the full Gemini timeout, so the worst-case wait is approximately both timeout values combined. Model availability and quotas depend on each provider account.

## Build and run

```bash
mvn clean package
mvn spring-boot:run
```

The service listens on port `8082`. Health information is available at `http://localhost:8082/actuator/health`.

## API

### `POST /api/v1/community-ai/chat`

Request:

```json
{
  "message": "What is ThirupalTech?"
}
```

Example with curl:

```bash
curl -X POST http://localhost:8082/api/v1/community-ai/chat \
  -H "Content-Type: application/json" \
  -d '{"message":"What is ThirupalTech?"}'
```

Successful response (`200 OK`):

```json
{
  "answer": "ThirupalTech is a community platform...",
  "timestamp": "2026-11-25T09:30:00Z"
}
```

Messages must contain non-whitespace text and be no longer than 2000 characters. Errors use a consistent response shape and do not expose provider exception details:

```json
{
  "timestamp": "2026-11-25T09:30:00Z",
  "status": 400,
  "code": "VALIDATION_ERROR",
  "message": "The request is invalid. Check that message is present and does not exceed 2000 characters.",
  "path": "/api/v1/community-ai/chat"
}
```

The API uses `400` for invalid requests, `429` for provider rate limits, `502` for provider failures or empty output, `503` for provider connectivity issues, and `500` for unexpected internal failures.

## Tests

```bash
mvn test
```

Tests mock the Spring AI `ChatClient` instances and do not call either provider or require real API keys.

## Troubleshooting

- **Missing or invalid API key:** confirm `GROQ_API_KEY` and `GEMINI_API_KEY` are set in the same shell that starts Maven. Both keys stay on the server and are never returned by the API.
- **Groq failure:** the service automatically attempts Gemini. Check the provider-specific logs and ensure the Gemini key has available quota.
- **Model not found or unavailable:** select a model enabled for the relevant account using `GROQ_MODEL` or `GEMINI_MODEL`.
- **Rate limited:** provider rate limits may trigger fallback; if both providers are rate-limited, the service returns `429 AI_RATE_LIMITED`.
- **Provider unavailable or request timeout:** check provider status and network connectivity. The service returns a sanitized `503` error for recognized connectivity failures.
- **Browser CORS errors:** add the frontend origin to `COMMUNITY_AI_ALLOWED_ORIGINS`, separated from other origins with a comma.
- **Port 8082 in use:** stop the conflicting process or override the port with Spring Boot's `--server.port` option.
