# ThirupalTech Community AI

An independently deployable Spring Boot REST microservice that provides general conversational answers using Google Gemini through Spring AI. This initial release does not connect to the ThirupalTech backend or a database and does not retrieve live community information.

## Architecture

```text
React application
    -> POST /api/v1/community-ai/chat
    -> Spring Boot controller and validation
    -> CommunityAIService
    -> Spring AI ChatClient
    -> Google Gemini API
```

The configured system prompt sets community-focused behavior and makes it clear that current events or program details are not available. The prompt, model, and generation settings are configurable without exposing provider credentials to the React client.

## Technology stack

- Java 17
- Spring Boot 3.5.16
- Spring AI 1.1.8
- Maven
- Spring Web, Validation, Actuator
- Google Gemini Developer API (Google GenAI)

## Prerequisites

- JDK 17
- Maven 3.9+
- A Gemini API key from [Google AI Studio](https://aistudio.google.com/app/apikey)

## Local setup

From this directory, set the API key in your shell. Never put the key in React or commit it to source control.

PowerShell:

```powershell
$env:GEMINI_API_KEY = "your-google-ai-studio-key"
```

macOS/Linux:

```bash
export GEMINI_API_KEY="your-google-ai-studio-key"
```

Optional settings can also be supplied as environment variables:

| Variable | Default | Description |
| --- | --- | --- |
| `GEMINI_MODEL` | `gemini-2.5-flash-lite` | Gemini model identifier |
| `GEMINI_TEMPERATURE` | `0.7` | Generation temperature (0.0–1.0) |
| `GEMINI_MAX_OUTPUT_TOKENS` | `1024` | Maximum generated output tokens |
| `GEMINI_REQUEST_TIMEOUT` | `30s` | Maximum time the service waits for a provider response |
| `COMMUNITY_AI_ALLOWED_ORIGINS` | `http://localhost:3000,http://localhost:5173` | Comma-separated browser origins allowed by CORS |

Model availability can vary by API account and region. Set `GEMINI_MODEL` to a model available to your Gemini API key if the default is not available.

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

Tests mock the Spring AI `ChatClient` and do not call Gemini or require a real API key.

## Troubleshooting

- **Missing or invalid API key:** confirm `GEMINI_API_KEY` is set in the same shell that starts Maven. The key stays on the server and is never returned by the API.
- **Model not found or unavailable:** choose a model enabled for your Gemini API key with `GEMINI_MODEL`.
- **Rate limited:** Gemini can return `429`; the service returns `429 AI_RATE_LIMITED`. Retry according to your API quota and provider guidance.
- **Provider unavailable or request timeout:** check provider status and network connectivity. The service returns a sanitized `503` error for recognized connectivity failures.
- **Browser CORS errors:** add the frontend origin to `COMMUNITY_AI_ALLOWED_ORIGINS`, separated from other origins with a comma.
- **Port 8082 in use:** stop the conflicting process or override the port with Spring Boot's `--server.port` option.
