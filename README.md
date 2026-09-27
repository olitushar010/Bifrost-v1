# Bifrost

A high-throughput event ingestion engine, built from raw JDK 21 primitives — no frameworks — to master network I/O, concurrency, and database write performance before adopting higher-level abstractions.

**Engineering philosophy:** "anti-vibe." v1.0 uses raw `ServerSocket`, JDK 21 Virtual Threads, hand-written HTTP parsing, and raw JDBC — deliberately, to understand what frameworks do under the hood before using them.

## Architectural evolution

| Version | Focus |
|---|---|
| **v1.0 (current)** | Bare-metal foundation — raw sockets, virtual threads, manual HTTP parsing, custom JDBC pooling, PostgreSQL |
| v1.1 | Framework layer — Spring Boot REST API, Spring Data JPA/Hibernate |
| v2.0 | Asynchronous decoupling — Kafka/RabbitMQ, Redis idempotency checks |
| v3.0 | Synchronization engine — PostgreSQL logical decoding via Debezium (CDC — deliberately kept in scope) |

## v1.0 system topography

```
Client
  |  POST /events
  v
Ingestion node        ServerSocket, one virtual thread per connection
  |
  v
HTTP parser/validator  Routing, headers, byte-safe body read
  |
  v
JSON deserialization   Jackson -> EventPayload DTO
  |
  v
Business validation    eventId present, eventType recognized
  |
  v
Persistence layer      JDBC, connection pool, batch writes  [not started]
  |
  v
PostgreSQL              [not started]
```

## API contract

| | |
|---|---|
| Endpoint | `POST /events` |
| Required headers | `Content-Length`, `Content-Type: application/json` (charset suffix tolerated) |
| Success | `200 OK` — will become `202 Accepted` once writes are actually queued, not synchronous |
| Errors | `400`, `404`, `405`, `408`, `411`, `413`, `415`, `431` |

## Event contract (`EventPayload`)

| Field | Type | Rule |
|---|---|---|
| `eventId` | String | required, non-blank |
| `eventType` | String | required, must be in the recognized set (currently just `USER_CLICK`, per spec) |
| `payload` | `Map<String, Object>` | flexible event data |

`timestamp` is intentionally not yet a field — deferred, not forgotten; add it deliberately when needed.

## Storage schema (PostgreSQL, planned)

| Column | Type | Constraint | Purpose |
|---|---|---|---|
| `internal_id` | BIGSERIAL | PK (clustered) | DB-generated sequential identity, WAL tracking |
| `event_id` | UUID | Unique index | Event identity, idempotency |
| `event_type` | VARCHAR | B-Tree index | Fast filtering |
| `payload` | JSONB | GIN index (future) | Flexible event data |

## Roadmap

### Phase 1: The Gateway — Completed
- [x] 1.1 Network listener
- [x] 1.2 Protocol parser — routing, header/body handling, security hardening (see below)
- [x] 1.3 Deserialization — Jackson -> `EventPayload`
- [x] 1.4 Business validation — required fields, recognized `eventType`

**Hardening applied to 1.1/1.2 (architect review):** byte-accurate body/response length, charset-tolerant `Content-Type`, `411` on missing/invalid `Content-Length` (prevents crash), `split(":", 2)`, explicit `Connection: close`, cumulative + per-line header size caps (`431` via custom exception).

### Phase 2: The Vault — In Progress
- [x] 2.1 PostgreSQL schema — `bifrost` database + `events` table created, matches README schema, unique constraint on `event_id` verified manually (duplicate insert correctly rejected)
- [ ] 2.2 Raw JDBC (`PreparedStatement`) — `pgjdbc 42.6.0` pinned and connection verified via `DbConfig`/`DatabaseConnection`; actual event `INSERT` not yet written (pending)
- [x] 2.3 Connection pooling — **using HikariCP**, not a custom `Semaphore`-based pool as originally planned (deliberate decision, see below); tested pool exhaustion/timeout and connection reuse, pool size set to 10

### Phase 3: The Engine — not started
- [ ] 3.1 Externalized configuration
- [ ] 3.2 Structured logging
- [ ] 3.3 Graceful shutdown + health check endpoint

### Phase 4: The Bridge (CDC) — not started
- [ ] 4.1 Append-only enforcement
- [ ] 4.2 `wal_level = logical` + replication identity for Debezium

## Open decisions
- `200` vs `202`: revisit once Phase 2.3 introduces a real queue.
- `pgjdbc` version must be confirmed against actual JDK 21 setup before Phase 2.2.
- CDC (Phase 4) considered and deliberately kept — schema is already designed for it at no extra cost.
- **Connection pooling: HikariCP chosen over a custom `Semaphore`-based pool.** Original plan was to hand-build one for the concurrency-primitives learning value; decided against it since HikariCP is the same tool Spring Boot (v1.1) defaults to, so the time is better spent there — and the equivalent bounded-concurrency practice is still covered later via the Rate Limiter LLD exercise.
- `DbConfig` now fails fast with a clear message on missing/invalid env vars, instead of a bare `NumberFormatException`/`NullPointerException`.
- `ConnectionPool`'s explicit `shutdown()`/`close()` call is deliberately deferred to Phase 3.3 (JVM shutdown hook), not added ad hoc.

## Tech stack
Java 21, Maven, Jackson, PostgreSQL (planned)

## Why this project exists
Personal learning project building backend fundamentals concurrency, raw networking, JDBC, CDC from first principles.
