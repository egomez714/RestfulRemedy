# RestfulRemedy

A Spring Boot REST API that turns unstructured doctor-visit transcripts into structured, **interoperable medical data** using Anthropic's Claude and the [HL7 FHIR R4](https://hl7.org/fhir/R4/) standard. Send in raw clinical notes, get back either a Claude-extracted JSON payload (medications with [RxNorm](https://www.nlm.nih.gov/research/umls/rxnorm/), diagnoses with [ICD-10](https://www.cdc.gov/nchs/icd/icd10cm.htm), observations with [LOINC](https://loinc.org/)) or a fully-formed, validated FHIR R4 Bundle ready to drop into an EHR.

## Why

Clinical notes are messy free text, but billing, analytics, and electronic health records need structured codes. RestfulRemedy bridges that gap with a single REST call — and demonstrates a full production-shaped Java service: typed config, validation, JPA persistence, centralized error handling, structural FHIR validation, containerization, and Kubernetes manifests.

## Features

- **`POST /api/transcripts`** — extract structured clinical JSON (patient, encounter, medications, diagnoses, observations, summary)
- **`POST /api/transcripts/fhir`** — convert the same transcript into a validated **FHIR R4 Bundle** (`application/fhir+json`)
- **`GET /api/health`** — liveness/readiness endpoint
- **Persistent audit trail** — every analyzed transcript is saved to Postgres (raw transcript + Claude JSON + FHIR JSON)
- **Structural FHIR validation** via HAPI's `FhirInstanceValidator`
- **Validated input** with Jakarta Bean Validation
- **Centralized error handling** with `@RestControllerAdvice` — clean 4xx/5xx responses for upstream API failures, rate limits, and timeouts
- **Containerized** with a multi-stage, non-root Docker image
- **Production-ready deploy** — `docker-compose` for local, Kubernetes manifests for cluster
- **Unit-tested** controller, service, and FHIR layers

## Architecture

```
┌─────────────┐    POST transcript     ┌──────────────────┐
│   Client    │ ─────────────────────► │ TranscriptCtrl   │
└─────────────┘                        └────────┬─────────┘
                                                │
                       ┌────────────────────────┼──────────────────────┐
                       ▼                        ▼                      ▼
              ┌────────────────┐       ┌────────────────┐    ┌──────────────────┐
              │TranscriptSvc   │       │  FhirService   │    │TranscriptRepo    │
              │ → Anthropic API│       │ → HAPI FHIR R4 │    │ → Postgres (JPA) │
              └────────────────┘       │ → validate     │    └──────────────────┘
                                       └────────────────┘
```

## Tech Stack

| Layer            | Choice                                          |
|------------------|-------------------------------------------------|
| Language         | Java 21                                         |
| Framework        | Spring Boot 3.5 (Web, Validation, Data JPA)     |
| HTTP client      | Spring `RestClient`                             |
| LLM              | Anthropic Claude (Sonnet 4)                     |
| Interoperability | HAPI FHIR 7.6 (R4 structures + validator)       |
| Database         | PostgreSQL 16 (H2 in tests)                     |
| Build            | Maven (wrapper included)                        |
| Testing          | JUnit 5, Mockito, Spring MockMvc                |
| Container        | Multi-stage Docker, non-root runtime user       |
| Orchestration    | Kubernetes manifests + `docker-compose`         |
| Boilerplate      | Lombok, SLF4J                                   |

## Prerequisites

- Java 21 (JDK) — only required if running locally without Docker
- An [Anthropic API key](https://console.anthropic.com/)
- One of:
  - **Docker + Docker Compose** (easiest — handles Postgres for you), or
  - **Local Postgres 16** if running the app directly via `./mvnw`

## Configuration

All configuration is environment-driven so the same image runs locally, in Compose, and in Kubernetes.

| Variable            | Default                                              | Description                  |
|---------------------|------------------------------------------------------|------------------------------|
| `CLAUDE_KEY`        | _(required)_                                         | Anthropic API key            |
| `DATABASE_URL`      | `jdbc:postgresql://localhost:5432/restfulremedy`     | JDBC URL                     |
| `DATABASE_USERNAME` | `restfulremedy`                                      | Postgres user                |
| `DATABASE_PASSWORD` | `restfulremedy`                                      | Postgres password            |

## Getting Started

### Option 1 — Docker Compose (recommended)

Spins up the app + Postgres together, with a healthcheck gating app startup until the DB is ready.

```bash
git clone https://github.com/egomez714/RestfulRemedy.git
cd RestfulRemedy
export CLAUDE_KEY=sk-ant-...
docker compose up --build
```

The API is available at `http://localhost:8080`.

### Option 2 — Local JVM

Requires a running Postgres on `localhost:5432` with database/user `restfulremedy`.

```bash
export CLAUDE_KEY=sk-ant-...
./mvnw spring-boot:run
```

### Option 3 — Kubernetes (Minikube)

```bash
# 1. Point your Docker CLI at Minikube's daemon and build the image inside it
eval $(minikube docker-env)
docker build -t restfulremedy:latest .

# 2. Edit k8s/secrets.yaml — set your CLAUDE_KEY

# 3. Apply manifests
kubectl apply -f k8s/namespace.yaml
kubectl apply -f k8s/secrets.yaml
kubectl apply -f k8s/configmap.yaml
kubectl apply -f k8s/postgres.yaml
kubectl apply -f k8s/app.yaml

# 4. Reach the service
minikube service restfulremedy -n restfulremedy
```

## Usage

### Health check

```bash
curl http://localhost:8080/api/health
```

```json
{ "status": "UP", "service": "RestfulRemedy" }
```

### Analyze a transcript → structured JSON

```bash
curl -X POST http://localhost:8080/api/transcripts \
  -H "Content-Type: application/json" \
  -d '{
    "transcript": "John Doe, 58-year-old male, presents for routine follow-up. BP 138/88. Prescribed Lisinopril 10mg daily for hypertension."
  }'
```

```json
{
  "patient": { "name": "John Doe", "age": 58, "gender": "male" },
  "encounter": { "type": "office visit", "reason": "routine follow-up" },
  "medications": [
    { "name": "Lisinopril", "dosage": "10mg daily", "rxnorm_code": "104377" }
  ],
  "diagnoses": [
    { "description": "Essential hypertension", "icd10_code": "I10" }
  ],
  "observations": [
    { "description": "Blood pressure", "value": "138/88 mmHg", "loinc_code": "85354-9" }
  ],
  "summary": "Routine follow-up visit with new Lisinopril prescription for hypertension."
}
```

### Analyze a transcript → FHIR R4 Bundle

```bash
curl -X POST http://localhost:8080/api/transcripts/fhir \
  -H "Content-Type: application/json" \
  -d '{ "transcript": "..." }'
```

Returns a validated FHIR R4 `Bundle` (`application/fhir+json`) containing linked `Patient`, `Encounter`, `Composition`, `MedicationStatement`, `Condition`, and `Observation` resources. Resources reference each other via `urn:uuid` `fullUrl`s, so the bundle is self-contained and portable.

### Error responses

| Status | When                                          |
|--------|-----------------------------------------------|
| 400    | Missing or blank `transcript` field           |
| 502    | Upstream Anthropic API returned 4xx/5xx       |
| 504    | Request to Anthropic API timed out            |
| 500    | Unexpected server error                       |

All errors return `{ "error": "..." }`.

## Persistence

Every successful analysis is saved to the `transcript_records` table:

| Column           | Type          | Notes                                    |
|------------------|---------------|------------------------------------------|
| `id`             | `UUID`        | Primary key                              |
| `raw_transcript` | `TEXT`        | The original input                       |
| `claude_json`    | `TEXT`        | Claude's structured response             |
| `fhir_json`      | `TEXT`        | FHIR Bundle JSON (null for plain endpoint) |
| `created_at`     | `TIMESTAMP`   | Set on insert                            |

Schema is auto-migrated by Hibernate (`ddl-auto: update`) for dev. In production, swap this for [Flyway](https://flywaydb.org/) or [Liquibase](https://www.liquibase.org/).

## Running Tests

```bash
./mvnw test
```

Tests use an embedded H2 database, so no Postgres is required. Coverage includes:

- Controller routes (MockMvc): health, JSON endpoint, FHIR endpoint, validation failures, error mapping
- `TranscriptService`: Claude request shape, JSON parsing, error paths
- `FhirService`: bundle structure, resource references, code system bindings, validator wiring

## Project Structure

```
.
├── Dockerfile                # multi-stage build, non-root runtime
├── docker-compose.yml        # app + postgres with healthcheck gate
├── k8s/
│   ├── namespace.yaml
│   ├── configmap.yaml        # non-secret env (DB URL, user)
│   ├── secrets.yaml          # CLAUDE_KEY + DB password (template)
│   ├── postgres.yaml         # Deployment, PVC, headless Service
│   └── app.yaml              # Deployment + NodePort Service, probes
├── pom.xml
└── src/
    ├── main/
    │   ├── java/com/restfulremedy/
    │   │   ├── RestfulRemedyApplication.java
    │   │   ├── config/         # AnthropicConfig — RestClient + @ConfigurationProperties
    │   │   ├── controller/     # TranscriptController — REST endpoints
    │   │   ├── dto/            # TranscriptRequest — validated payload
    │   │   ├── entity/         # TranscriptRecord — JPA entity
    │   │   ├── exception/      # GlobalExceptionHandler — @RestControllerAdvice
    │   │   ├── prompts/        # TranscriptPrompts — Claude system prompt
    │   │   ├── repository/     # TranscriptRepository — Spring Data JPA
    │   │   └── service/        # TranscriptService, FhirService
    │   └── resources/
    │       └── application.yml
    └── test/
        └── java/com/restfulremedy/
            ├── controller/     # TranscriptControllerTest
            └── service/        # TranscriptServiceTest, FhirServiceTest
```

## Roadmap

- Flyway/Liquibase migrations instead of `ddl-auto`
- Authentication and per-user API keys
- OpenAPI / Swagger documentation
- Streaming responses for long transcripts
- Confidence scores on returned medical codes
- Terminology-server-backed FHIR validation (verify RxNorm/ICD-10/LOINC codes actually exist)

## Disclaimer

This project is a technical demonstration. It is **not** a certified medical-coding tool and must not be used for clinical decision-making or actual billing without expert review and proper regulatory compliance (HIPAA, etc.). The Kubernetes secret manifest is a template — never commit real credentials; use sealed-secrets, external-secrets, or `kubectl create secret` in real environments.
