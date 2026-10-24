# Real-Time Vehicle Telemetry and Fault-Tolerant Event Processing Engine

Backend service that accepts simulated vehicle telemetry, publishes each event to Apache Kafka, and lets a consumer apply configurable speeding and engine-overheating rules. Telemetry and alerts are stored in PostgreSQL. Duplicate Kafka deliveries do not create duplicate alerts. REST access is protected with JWT and role checks.

## Architecture

```
Client
  |  POST /api/telemetry
  v
Spring Boot REST API
  |
  v
Kafka producer  (message key = eventId)
  |
  v
Apache Kafka topic: vehicle-telemetry
  |
  v
Kafka consumer
  |
  v
Rule processing
  |-- Speeding rule
  |-- Engine overheating rule
  v
PostgreSQL
  |
  v
GET /api/alerts
```

`POST /api/telemetry` validates the payload and publishes it. It does not create alerts. The Kafka consumer is the only component that evaluates rules and writes alerts. PostgreSQL is the source of truth.

## Tech stack

- Java 17
- Spring Boot
- Apache Kafka (Spring Kafka)
- PostgreSQL
- Docker
- Spring Security and JWT
- JUnit 5
- Testcontainers

## Project structure

```
src/main/java/com/vehicle/telemetry/
  controller/     REST endpoints
  service/        ingestion, processing, alerts, login
  repository/     Spring Data JPA
  entity/         telemetry and alert tables
  dto/            request and response objects
  kafka/          producer and consumer
  security/       JWT filter and security rules
  config/         Kafka, rule, JWT, and user settings
  exception/      HTTP error mapping
  rules/          TelemetryRule, SpeedingRule, OverheatingRule
  simulator/      optional in-process telemetry generator
```

## Kafka flow

1. A valid `POST /api/telemetry` call is published to `vehicle-telemetry`.
2. The Kafka record key is the telemetry `eventId`.
3. The consumer group `vehicle-telemetry-group` reads the record.
4. `TelemetryProcessingService` stores the telemetry row and asks `RuleEngine` to evaluate `SpeedingRule` and `OverheatingRule`.
5. Each violation is stored as an alert.
6. The consumer acknowledges the record only after processing returns normally.
7. If PostgreSQL is unavailable, the consumer throws, the offset is not committed, and Spring Kafka retries the same record.

Thresholds are configuration, not literals inside the rule classes:

```yaml
vehicle.rules.speed-threshold: 120
vehicle.rules.temperature-threshold: 100
```

An alert is created only when the observed value is strictly greater than the threshold.

## Database schema

`telemetry`

| Column | Notes |
| --- | --- |
| id | primary key |
| event_id | unique |
| vehicle_id | indexed |
| timestamp | indexed |
| speed | kilometers per hour |
| engine_temperature | degrees Celsius |

`alert`

| Column | Notes |
| --- | --- |
| id | primary key |
| event_id | telemetry event id |
| vehicle_id | indexed |
| alert_type | `SPEEDING` or `ENGINE_OVERHEATING`, indexed |
| message | human-readable reason |
| timestamp | telemetry timestamp, indexed |

Unique constraint: `(event_id, alert_type)`.

The same telemetry event may therefore produce both a speeding alert and an overheating alert. It cannot produce two speeding alerts.

## Idempotency strategy

Kafka delivery is at least once. The consumer is safe under redelivery for three reasons:

1. Each insert runs in its own database transaction. A unique-constraint failure aborts only that write.
2. PostgreSQL rejects a second `telemetry.event_id` and a second `(event_id, alert_type)` pair. The consumer logs `duplicate event detection` and continues.
3. Any other database error is thrown. The listener does not acknowledge the record, so Kafka delivers it again after the database recovers. Rows that were committed before the failure stay unique; missing alerts are inserted on the next attempt.

Duplicate Kafka delivery does not create duplicate alerts because the database, not the consumer memory, enforces uniqueness.

## REST API

### Login

`POST /api/auth/login` is public.

```json
{
  "username": "user",
  "password": "user123"
}
```

```json
{
  "token": "<jwt>",
  "tokenType": "Bearer",
  "expiresIn": 3600
}
```

Send `Authorization: Bearer <jwt>` on later calls.

### Telemetry ingestion

`POST /api/telemetry`

Roles: `ROLE_USER`, `ROLE_ADMIN`

```json
{
  "eventId": "evt-1001",
  "vehicleId": "VH-001",
  "timestamp": "2026-10-24T12:30:00Z",
  "speed": 125.5,
  "engineTemperature": 105.0
}
```

Success is `202 Accepted`:

```json
{
  "eventId": "evt-1001",
  "status": "PUBLISHED"
}
```

Validation failures and malformed JSON return `400`. A Kafka publish failure returns `503`. Responses do not include stack traces.

Rejected input includes a missing or blank `eventId`, a missing or blank `vehicleId`, a missing or unparseable `timestamp`, a missing or negative `speed`, and a missing `engineTemperature`.

### Alert retrieval

`GET /api/alerts`

Role: `ROLE_ADMIN` only. `ROLE_USER` receives `403`. Missing or invalid tokens receive `401`.

Query parameters:

- `page` (default `0`)
- `size` (default `20`, maximum `100`)
- `vehicleId`
- `alertType` (`SPEEDING` or `ENGINE_OVERHEATING`)
- `from` and `to` as ISO-8601 timestamps

```json
{
  "content": [
    {
      "id": 1,
      "eventId": "evt-1001",
      "vehicleId": "VH-001",
      "alertType": "SPEEDING",
      "message": "Vehicle VH-001 speed 125.5 km/h exceeds threshold 120.0 km/h",
      "timestamp": "2026-10-24T12:30:00Z"
    }
  ],
  "page": 0,
  "size": 10,
  "totalElements": 1,
  "totalPages": 1
}
```

Results are ordered by timestamp, newest first.

## Authentication

Passwords from configuration are hashed with Spring Security's `BCryptPasswordEncoder` when the application starts. Users are held in memory. There is no external identity provider.

Default accounts:

| Username | Password | Role | Access |
| --- | --- | --- | --- |
| user | user123 | ROLE_USER | publish telemetry |
| admin | admin123 | ROLE_ADMIN | publish telemetry and read alerts |

Override them with `APP_USER_USERNAME`, `APP_USER_PASSWORD`, `APP_ADMIN_USERNAME`, and `APP_ADMIN_PASSWORD`. Override the signing key with `JWT_SECRET` (at least 32 bytes). Token lifetime is `JWT_EXPIRATION_MS` (default one hour).

Change these values before exposing the service.

## Running locally

Requirements: Java 17, Maven, and Docker.

Start PostgreSQL and Kafka:

```bash
docker compose up -d postgres kafka
```

The host application uses `localhost:5432` and `localhost:9092`.

```bash
mvn spring-boot:run
```

The API listens on port `8080`.

Generate sample normal and abnormal events through the same application:

```bash
mvn spring-boot:run -Dspring-boot.run.arguments=--vehicle.simulator.enabled=true
```

`vehicle.simulator.count` controls how many events are published. The generator cycles through normal speed, speeding, overheating, and both violations.

## Running tests

```bash
mvn test
```

Unit tests cover rule thresholds and request validation. Integration tests use Testcontainers to start PostgreSQL and Kafka. Docker must be running.

The integration tests cover:

- speeding and overheating alerts
- one event that violates both rules
- processing the same event twice
- consumer restart and offset replay
- database failure, no acknowledgement, then recovery without duplicate alerts
- authentication, authorization, pagination, and filtering

## Docker

`docker-compose.yml` runs only PostgreSQL 16, Apache Kafka in KRaft mode, and the Spring Boot application. Kafka does not use Zookeeper.

```bash
docker compose up --build
```

Inside Compose, the application reaches Kafka at `kafka:19092`. From the host machine, Kafka is `localhost:9092`.

Useful variables for the `app` service:

- `SPRING_DATASOURCE_URL`
- `SPRING_DATASOURCE_USERNAME`
- `SPRING_DATASOURCE_PASSWORD`
- `SPRING_KAFKA_BOOTSTRAP_SERVERS`
- `JWT_SECRET`
- `APP_USER_PASSWORD`
- `APP_ADMIN_PASSWORD`

## AWS EC2 deployment

This service is a single Docker Compose stack. It does not use Kubernetes or another orchestrator.

1. Launch an Amazon Linux or Ubuntu EC2 instance with Docker and the Docker Compose plugin installed.
2. Open security-group port `22` for administration and port `8080` for the API. Leave PostgreSQL `5432` and Kafka `9092` closed to the public. They are used only on the instance.
3. Copy this project onto the instance.
4. Export production settings before starting the stack:

```bash
export JWT_SECRET="replace-with-a-random-secret-of-at-least-32-bytes"
export APP_USER_PASSWORD="replace-user-password"
export APP_ADMIN_PASSWORD="replace-admin-password"
```

5. Build and run:

```bash
docker compose up -d --build
```

6. Confirm the API:

```bash
curl -s http://localhost:8080/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"'"$APP_ADMIN_PASSWORD"'"}'
```

The Compose file publishes the API on `8080`. PostgreSQL and Kafka stay on the Compose network for the application container. To run the jar on the host instead of in Compose, start only `postgres` and `kafka`, build with `mvn package`, and run `java -jar target/vehicle-telemetry.jar`.

## Example API requests

Login:

```bash
TOKEN=$(curl -s http://localhost:8080/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"admin123"}' | python3 -c 'import json,sys; print(json.load(sys.stdin)["token"])')
```

Publish telemetry that breaks both rules:

```bash
curl -s -X POST http://localhost:8080/api/telemetry \
  -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{
    "eventId": "evt-1001",
    "vehicleId": "VH-001",
    "timestamp": "2026-10-24T12:30:00Z",
    "speed": 125.5,
    "engineTemperature": 105.0
  }'
```

Read alerts after the consumer has processed the event:

```bash
curl -s "http://localhost:8080/api/alerts?vehicleId=VH-001&page=0&size=10" \
  -H "Authorization: Bearer $TOKEN"
```

Filter by type:

```bash
curl -s "http://localhost:8080/api/alerts?alertType=SPEEDING&page=0&size=10" \
  -H "Authorization: Bearer $TOKEN"
```

## Example alert response

```json
{
  "content": [
    {
      "id": 2,
      "eventId": "evt-1001",
      "vehicleId": "VH-001",
      "alertType": "ENGINE_OVERHEATING",
      "message": "Vehicle VH-001 engine temperature 105.0 C exceeds threshold 100.0 C",
      "timestamp": "2026-10-24T12:30:00Z"
    },
    {
      "id": 1,
      "eventId": "evt-1001",
      "vehicleId": "VH-001",
      "alertType": "SPEEDING",
      "message": "Vehicle VH-001 speed 125.5 km/h exceeds threshold 120.0 km/h",
      "timestamp": "2026-10-24T12:30:00Z"
    }
  ],
  "page": 0,
  "size": 10,
  "totalElements": 2,
  "totalPages": 1
}
```

## Failure recovery

If the consumer cannot persist an event, it logs `processing failure` and does not acknowledge the Kafka record. The error handler retries with backoff and does not commit a recovered failure. When PostgreSQL is available again, the same record is processed.

A failure halfway through an event is still safe. The telemetry row may already be committed, and one of two alerts may already exist. The retry ignores those unique rows and inserts only what is missing. Publishing or replaying `evt-1001` again still leaves one speeding alert and, when the temperature rule matches, one overheating alert.
