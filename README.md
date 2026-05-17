# RestfulRemedy

A Spring Boot REST API that turns unstructured doctor-visit transcripts into structured medical data using Anthropic's Claude. The service extracts medications with [RxNorm](https://www.nlm.nih.gov/research/umls/rxnorm/) codes, diagnoses with [ICD-10](https://www.cdc.gov/nchs/icd/icd10cm.htm) codes, and a one-line visit summary — returned as clean JSON ready for downstream EHR or analytics pipelines.

## Why

Clinical notes are messy free text, but billing, analytics, and electronic health records need structured codes. RestfulRemedy bridges that gap with a single REST call, demonstrating how LLMs can be reliably integrated into a typed, validated, test-covered Java service.

## Features

- **POST `/api/transcripts`** — accepts a raw transcript, returns structured JSON
- **Validated input** with Jakarta Bean Validation (rejects blank or missing transcripts)
- **Centralized error handling** via `@RestControllerAdvice` — clean 4xx/5xx responses for upstream API failures, rate limits, and timeouts
- **Health-check endpoint** at `GET /api/health`
- **Externalized configuration** for API key and model selection
- **Unit-tested** controller and service layers with MockMvc and Mockito

## Tech Stack

| Layer        | Choice                                |
|--------------|---------------------------------------|
| Language     | Java 21                               |
| Framework    | Spring Boot 3.5                       |
| HTTP client  | Spring `RestClient`                   |
| LLM          | Anthropic Claude (Sonnet 4)           |
| Build        | Maven (wrapper included)              |
| Testing      | JUnit 5, Mockito, Spring MockMvc      |
| Boilerplate  | Lombok, SLF4J                         |

## Prerequisites

- Java 21 (JDK)
- An [Anthropic API key](https://console.anthropic.com/)

Maven is **not** required locally — the project ships with the Maven Wrapper (`./mvnw`).

## Configuration

The API key is read from the `CLAUDE_KEY` environment variable:

```bash
export CLAUDE_KEY=sk-ant-...
```

Other settings live in `src/main/resources/application.yml`:

```yaml
server:
  port: 8080

anthropic:
  api-key: ${CLAUDE_KEY:}
  model: claude-sonnet-4-20250514
```

## Getting Started

```bash
# 1. Clone
git clone https://github.com/<egomez714>/RestfulRemedy.git
cd RestfulRemedy

# 2. Set your API key
export CLAUDE_KEY=sk-ant-...

# 3. Run
./mvnw spring-boot:run
```

The service starts on `http://localhost:8080`.

## Usage

### Health check

```bash
curl http://localhost:8080/api/health
```

```json
{ "status": "UP", "service": "RestfulRemedy" }
```

### Analyze a transcript

```bash
curl -X POST http://localhost:8080/api/transcripts \
  -H "Content-Type: application/json" \
  -d '{
    "transcript": "Patient is a 58-year-old male presenting with elevated blood pressure readings over the past three months. Prescribed Lisinopril 10mg daily."
  }'
```

**Response:**

```json
{
  "medications": [
    {
      "name": "Lisinopril",
      "dosage": "10mg daily",
      "rxnorm_code": "104377"
    }
  ],
  "diagnoses": [
    {
      "description": "Essential hypertension",
      "icd10_code": "I10"
    }
  ],
  "summary": "Routine follow-up visit for blood pressure management with new Lisinopril prescription."
}
```

### Error responses

| Status | When                                          |
|--------|-----------------------------------------------|
| 400    | Missing or blank `transcript` field           |
| 502    | Upstream Anthropic API returned 4xx/5xx       |
| 504    | Request to Anthropic API timed out            |
| 500    | Unexpected server error                       |

All errors return `{ "error": "..." }`.

## Running Tests

```bash
./mvnw test
```

Test coverage includes:

- Health-check contract
- Happy-path transcript analysis (mocked service)
- Validation failures (blank and missing fields)
- Service errors surfacing as 500 responses

## Project Structure

```
src/
├── main/
│   ├── java/com/restfulremedy/
│   │   ├── RestfulRemedyApplication.java
│   │   ├── config/        # AnthropicConfig — RestClient + @ConfigurationProperties
│   │   ├── controller/    # TranscriptController — REST endpoints
│   │   ├── dto/           # TranscriptRequest — validated payload
│   │   ├── exception/     # GlobalExceptionHandler — @RestControllerAdvice
│   │   └── service/       # TranscriptService — Claude integration + JSON parsing
│   └── resources/
│       └── application.yml
└── test/
    └── java/com/restfulremedy/
        ├── controller/    # TranscriptControllerTest (MockMvc)
        └── service/       # TranscriptServiceTest
```

## Roadmap

- Persistence layer for storing analyzed transcripts
- Authentication and per-user API keys
- OpenAPI / Swagger documentation
- Streaming responses for long transcripts
- Confidence scores on returned medical codes

## Disclaimer

This project is a technical demonstration. It is **not** a certified medical-coding tool and must not be used for clinical decision-making or actual billing without expert review and proper regulatory compliance (HIPAA, etc.).
