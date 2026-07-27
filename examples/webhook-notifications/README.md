# Webhook Notifications — Local Development

Production-like environment for building the CloudEvents webhook feature:

- **PostgreSQL** — SQL storage with transactional outbox (required; H2 dev default does not match prod)
- **WireMock** — mock webhook subscriber at `http://localhost:9999/hook`
- **Registry UI** — browse artifacts at `http://localhost:8889`
- **Registry API** — run from source via `quarkus:dev` on `http://localhost:8080`

## Prerequisites

- Docker (Docker Desktop on Windows)
- Java 21+ and Maven (use `./mvnw` from repo root)
- Port **5432**, **8080**, **8889**, and **9999** available

## Quick start

### 1. Start infrastructure

From the repository root:

```bash
docker compose -f examples/webhook-notifications/docker-compose-dev.yaml up -d
```

Wait until Postgres is healthy:

```bash
docker compose -f examples/webhook-notifications/docker-compose-dev.yaml ps
```

### 2. Build registry (first time or after dependency changes)

```bash
./mvnw clean install -DskipTests -pl app -am
```

### 3. Run registry in dev mode

```bash
cd app
../mvnw quarkus:dev -Dquarkus.profile=webhooks-dev
```

The `webhooks-dev` profile is defined in
[`app/src/main/resources/application-webhooks-dev.properties`](../../app/src/main/resources/application-webhooks-dev.properties).

### 4. Verify

```bash
curl http://localhost:8080/apis/registry/v3/system/info
curl http://localhost:9999/__admin/health
```

Open the UI: http://localhost:8889

### 5. Mock webhook subscriber

Register this URL when the subscription API is implemented:

```
http://host.docker.internal:9999/hook
```

From the registry JVM on the host, use:

```
http://localhost:9999/hook
```

WireMock logs all received requests. Inspect via:

```bash
curl http://localhost:9999/__admin/requests
```

## Stop infrastructure

```bash
docker compose -f examples/webhook-notifications/docker-compose-dev.yaml down
```

Add `-v` to remove the Postgres volume and reset the database.

## Windows (PowerShell)

Same commands; use `mvnw.cmd` instead of `./mvnw`:

```powershell
docker compose -f examples/webhook-notifications/docker-compose-dev.yaml up -d
.\mvnw.cmd clean install -DskipTests -pl app -am
cd app
..\mvnw.cmd quarkus:dev "-Dquarkus.profile=webhooks-dev"
```

## Relationship to the feature plan

| Component | Role |
| --------- | ---- |
| PostgreSQL | Same storage as production SQL variant; outbox + webhook tables |
| `quarkus:dev` + `webhooks-dev` profile | Fast iteration on your branch (not the snapshot Docker image) |
| WireMock | Stand-in for subscriber; same tool used in integration tests |
| UI | Manual artifact/version operations to trigger CDI `SqlOutboxEvent` fanout |

As webhook config properties are implemented (Phase 1+), uncomment them in
`application-webhooks-dev.properties`.
