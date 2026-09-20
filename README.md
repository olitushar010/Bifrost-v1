# Bifrost --- Easy-to-Understand Overview

## What is Bifrost?

**Bifrost** is a small, high-performance event ingestion server written
in **Java 21**.

Its job is simple:

> **Receive events over HTTP → validate them → eventually store them in
> PostgreSQL.**

The project is intentionally built **without frameworks in v1.0**.

Instead of immediately using Spring Boot, Hibernate, connection pools,
etc., Bifrost uses low-level Java tools so the developer can understand
what those frameworks are doing internally.

### Main goal

Bifrost is mainly a learning project for understanding:

-   Network programming
-   HTTP request handling
-   Java concurrency
-   Virtual threads
-   JSON parsing
-   JDBC and database writes
-   Connection pooling
-   Performance and reliability

The philosophy is **"anti-vibe"**:

> Don't hide complexity behind frameworks before understanding the
> complexity underneath.

------------------------------------------------------------------------

# 1. How Bifrost Works

At a high level:

``` text
Client
  |
  | POST /events
  v
+-----------------------+
|   Ingestion Server    |
|   ServerSocket        |
+-----------+-----------+
            |
            v
+-----------------------+
| HTTP Parser           |
| - Read request        |
| - Check headers       |
| - Read body           |
| - Validate route      |
+-----------+-----------+
            |
            v
+-----------------------+
| Event Validation      |
| - Parse JSON          |
| - Check eventId       |
| - Check eventType     |
+-----------+-----------+
            |
            v
+-----------------------+
| Persistence Layer     |
| Raw JDBC              |
| Connection Pool       |
| Batch INSERT          |
+-----------+-----------+
            |
            v
       PostgreSQL
```

So the complete flow is:

**HTTP request → Parse → Validate → Store**

------------------------------------------------------------------------

# 2. Why No Frameworks in v1.0?

Normally, you might build this using:

``` text
Spring Boot
Spring MVC
Hibernate
HikariCP
Jackson
```

Those tools are useful, but they hide many implementation details.

Bifrost deliberately starts with:

``` text
ServerSocket
Virtual Threads
Manual HTTP parsing
Raw JDBC
Custom connection pool
PostgreSQL
```

This helps answer questions like:

-   How does a server accept connections?
-   How does HTTP actually work?
-   How are request headers read?
-   How do we protect the server from oversized requests?
-   How do virtual threads handle many connections?
-   How does JDBC talk to PostgreSQL?
-   Why do we need a connection pool?
-   What happens when many requests try to write to the database?

After understanding these fundamentals, frameworks become much easier to
understand.

------------------------------------------------------------------------

# 3. Bifrost v1.0 Architecture

Bifrost v1.0 has **three main layers**.

## Layer 1 --- Ingestion Node

This is the network-facing part of the application.

It uses:

``` java
ServerSocket
```

The server listens for incoming TCP connections.

For every connection, Bifrost creates a **virtual thread**:

``` java
Executors.newVirtualThreadPerTaskExecutor()
```

Conceptually:

``` text
Incoming Connection
        |
        v
Virtual Thread
        |
        v
Handle HTTP Request
```

### Why virtual threads?

Traditional Java servers often use platform threads.

Platform threads are relatively expensive.

Java 21 virtual threads are lightweight, so Bifrost can handle many
waiting network connections efficiently.

The idea is:

``` text
Connection 1 → Virtual Thread 1
Connection 2 → Virtual Thread 2
Connection 3 → Virtual Thread 3
...
Connection N → Virtual Thread N
```

Bifrost also uses **socket timeouts** so a client cannot keep a
connection open forever.

This helps protect against slow clients and **Slowloris-style attacks**.

------------------------------------------------------------------------

# 4. Layer 2 --- HTTP Parsing and Validation

Bifrost does not use Spring MVC or another HTTP framework.

It manually reads and parses HTTP requests.

For example:

``` http
POST /events HTTP/1.1
Host: localhost
Content-Type: application/json
Content-Length: 58

{"eventId":"abc-123","eventType":"login"}
```

The parser needs to understand:

-   HTTP method
-   Request path
-   Headers
-   Body
-   Content length
-   Content type

------------------------------------------------------------------------

## Supported Route

The main endpoint is:

``` http
POST /events
```

This is where events are sent.

Example:

``` bash
curl -X POST http://localhost:8080/events \
  -H "Content-Type: application/json" \
  -d '{"eventId":"abc-123","eventType":"login"}'
```

------------------------------------------------------------------------

# 5. Required Headers

Bifrost expects:

``` http
Content-Length: ...
Content-Type: application/json
```

### Content-Length

It tells Bifrost exactly how many **bytes** are in the request body.

For example:

``` http
Content-Length: 58
```

Bifrost uses byte-based reading rather than character-based reading.

This is important because:

``` text
Characters ≠ Bytes
```

especially when UTF-8 or other multi-byte characters are present.

------------------------------------------------------------------------

## Content-Type

Bifrost accepts JSON content types such as:

``` http
Content-Type: application/json
```

and:

``` http
Content-Type: application/json; charset=utf-8
```

The parser handles the content type in a case/parameter-tolerant way.

------------------------------------------------------------------------

# 6. HTTP Error Handling

Bifrost can return these HTTP errors:

| Status | Meaning |
| --- | --- |
| `400` | Bad request |
| `404` | Endpoint not found |
| `405` | HTTP method not allowed |
| `408` | Request timeout |
| `411` | Content-Length missing/invalid |
| `413` | Request body too large |
| `415` | Unsupported content type |
| `431` | Request headers too large |

For example:

``` text
POST /unknown
```

can result in:

``` http
404 Not Found
```

while:

``` text
Content-Length missing
```

can result in:

``` http
411 Length Required
```

------------------------------------------------------------------------

# 7. Security and Hardening Already Added

The HTTP layer has already received several important fixes.

## 7.1 Byte-accurate body reading

The body is read using **bytes**, not Java characters.

This prevents problems with multi-byte UTF-8 data.

------------------------------------------------------------------------

## 7.2 Content-Length validation

If `Content-Length` is missing or invalid:

``` text
→ 411 Length Required
```

This also prevents problems such as trying to allocate an array with an
invalid negative size.

------------------------------------------------------------------------

## 7.3 Header size limits

Bifrost limits both:

``` text
Individual header line size
```

and:

``` text
Total header size
```

If headers become too large:

``` text
→ 431 Request Header Fields Too Large
```

------------------------------------------------------------------------

## 7.4 Header parsing

Headers are split using:

``` java
split(":", 2)
```

instead of simply:

``` java
split(":")
```

Why?

Because a header value itself can contain `:`.

For example:

``` http
Some-Header: http://example.com
```

Splitting only twice preserves the value correctly.

------------------------------------------------------------------------

## 7.5 Explicit connection closing

Every response includes:

``` http
Connection: close
```

This makes the connection lifecycle explicit in the current
implementation.

------------------------------------------------------------------------

# 8. JSON Deserialization

Currently, after HTTP parsing, the JSON body is basically just text:

``` json
{"eventId":"abc-123","eventType":"login"}
```

A Java `String` does not understand that this represents an event.

The next step is to convert it into a Java object.

For example:

``` java
EventPayload event;
```

Conceptually:

``` text
JSON String
    |
    | Jackson
    v
EventPayload object
```

Example DTO:

``` java
class EventPayload {
    UUID eventId;
    String eventType;
    JsonNode payload;
}
```

This is **Phase 1.3** and is currently in progress.

------------------------------------------------------------------------

# 9. Business Validation

After converting JSON into an object, Bifrost needs to check whether the
event actually makes sense.

For example:

``` text
Is eventId present?
Is eventId a valid UUID?
Is eventType present?
Is eventType supported?
Is payload valid?
```

This will be **Phase 1.4**.

The important distinction is:

``` text
HTTP validation
        ↓
"Is this request structurally valid?"

Business validation
        ↓
"Is this event actually acceptable?"
```

------------------------------------------------------------------------

# 10. Persistence Layer --- PostgreSQL

Once an event passes validation, it eventually needs to be stored.

Bifrost will use:

``` text
Raw JDBC
    ↓
PostgreSQL
```

There will be no Hibernate or Spring Data in v1.0.

The application will explicitly execute SQL using:

``` java
PreparedStatement
```

------------------------------------------------------------------------

# 11. Planned Database Schema

The planned table contains:

| Column | Type | Purpose |
| --- | --- | --- |
| `internal_id` | `BIGSERIAL` | Internal sequential ID |
| `event_id` | `UUID` | Unique event identifier |
| `event_type` | `VARCHAR` | Type of event |
| `payload` | `JSONB` | Actual event data |

Conceptually:

``` text
events
--------------------------------
internal_id   BIGSERIAL
event_id      UUID
event_type    VARCHAR
payload       JSONB
--------------------------------
```

### Indexes

`event_id`:

``` text
UNIQUE INDEX
```

This helps with idempotency.

`event_type`:

``` text
B-Tree INDEX
```

This helps quickly find events by type.

`payload`:

``` text
GIN INDEX
```

This is planned for future JSONB queries.

------------------------------------------------------------------------

# 12. Why `internal_id` Exists

There are two different identifiers:

``` text
internal_id
event_id
```

### internal_id

This is generated by PostgreSQL.

Example:

``` text
1
2
3
4
5
...
```

It gives the database a simple sequential identifier.

### event_id

This comes from the event itself.

Example:

``` text
550e8400-e29b-41d4-a716-446655440000
```

It identifies the actual event and can be used for idempotency.

So:

``` text
internal_id → Database identity
event_id     → Event identity
```

------------------------------------------------------------------------

# 13. JDBC Connection Pool

Opening a new database connection for every request would be expensive.

Instead, Bifrost will maintain a bounded pool of connections.

Conceptually:

``` text
             Connection Pool
          +-------------------+
          | DB Connection 1   |
          | DB Connection 2   |
          | DB Connection 3   |
          | DB Connection 4   |
          +---------+---------+
                    |
                    v
              PostgreSQL
```

A request borrows a connection:

``` text
Request
   |
   v
Borrow connection
   |
   v
Execute SQL
   |
   v
Return connection
```

The pool will use a `Semaphore` to control how many database operations
can happen concurrently.

This is deliberately preferred over a `synchronized` design because
blocking synchronization can create undesirable interactions with
virtual threads.

------------------------------------------------------------------------

# 14. Batch Database Writes

Instead of sending one SQL operation at a time:

``` text
INSERT
INSERT
INSERT
INSERT
```

Bifrost plans to use JDBC batching:

``` java
statement.addBatch();
statement.executeBatch();
```

Conceptually:

``` text
Events
  |
  +-- Event 1
  +-- Event 2
  +-- Event 3
  +-- Event 4
        |
        v
    One batch
        |
        v
   PostgreSQL
```

This can reduce database round trips and improve throughput.

------------------------------------------------------------------------

# 15. Why the API Currently Returns `200 OK`

Currently, database writes are synchronous.

The request roughly follows:

``` text
Client
  ↓
Receive event
  ↓
Validate event
  ↓
Write to PostgreSQL
  ↓
Return response
```

Therefore:

``` http
200 OK
```

makes sense for the current implementation.

Later, when Bifrost has a real asynchronous queue:

``` text
Client
  ↓
Receive event
  ↓
Validate event
  ↓
Put event in queue
  ↓
202 Accepted
```

Then:

``` http
202 Accepted
```

would better represent:

> "I accepted your event for processing."

This change is intentionally postponed until there is an actual
asynchronous queue.

------------------------------------------------------------------------

# 16. Bifrost Roadmap

## Phase 1 --- The Gateway

**Goal: Build a reliable HTTP ingestion server.**

### Completed

-   [x] `ServerSocket` listener
-   [x] Virtual threads
-   [x] HTTP routing
-   [x] HTTP header parsing
-   [x] Request body reading
-   [x] Payload size limits
-   [x] Header size limits
-   [x] Socket timeouts
-   [x] HTTP error handling
-   [x] Byte-accurate request/response handling

### In progress

-   [ ] JSON → `EventPayload`
-   [ ] Business validation
-   [ ] Validate required fields
-   [ ] Validate supported `eventType`

------------------------------------------------------------------------

# Phase 2 --- The Vault

**Goal: Persist events reliably in PostgreSQL.**

-   [ ] Create PostgreSQL schema
-   [ ] Add `JSONB` payload
-   [ ] Implement `DatabaseManager`
-   [ ] Use raw JDBC
-   [ ] Use `PreparedStatement`
-   [ ] Implement connection pool
-   [ ] Use `Semaphore` for pool limits
-   [ ] Implement batch inserts

------------------------------------------------------------------------

# Phase 3 --- The Engine

**Goal: Make the server production-like.**

### Configuration

Move settings out of the source code:

``` text
Environment variables
.properties files
```

For example:

``` text
DB_URL
DB_USERNAME
DB_PASSWORD
SERVER_PORT
MAX_PAYLOAD_SIZE
```

### Logging

Replace:

``` java
System.out.println(...)
```

with structured logging.

Instead of:

``` text
Event received
```

log useful information such as:

``` text
timestamp
request_id
event_id
event_type
processing_time
status
```

### Graceful shutdown

When the application receives a shutdown signal:

``` text
Stop accepting new requests
        ↓
Finish active requests
        ↓
Close database connections
        ↓
Shutdown executors
        ↓
Exit
```

### Health check

Add an endpoint such as:

``` http
GET /health
```

which can report whether the application is healthy.

------------------------------------------------------------------------

# Phase 4 --- The Bridge

**Goal: Prepare Bifrost for Change Data Capture (CDC).**

The future architecture will use:

``` text
PostgreSQL
     |
     | WAL
     v
Debezium
     |
     v
CDC Events
```

To prepare for this:

-   [ ] Keep application writes append-only
-   [ ] Avoid application-level `UPDATE`
-   [ ] Avoid application-level `DELETE`
-   [ ] Configure PostgreSQL logical replication
-   [ ] Set appropriate `wal_level`
-   [ ] Configure replication identity

------------------------------------------------------------------------

# 17. Long-Term Architecture

Bifrost is intentionally evolving in stages.

``` text
v1.0
Raw Java
   |
   +-- ServerSocket
   +-- Virtual Threads
   +-- Manual HTTP
   +-- Raw JDBC
   +-- PostgreSQL

        ↓

v1.1
Frameworks
   |
   +-- Spring Boot
   +-- REST API
   +-- Spring Data
   +-- Hibernate

        ↓

v2.0
Asynchronous Architecture
   |
   +-- Kafka / RabbitMQ
   +-- Redis
   +-- Idempotency
   +-- Background processing

        ↓

v3.0
CDC / Synchronization
   |
   +-- PostgreSQL WAL
   +-- Debezium
   +-- Change events
```

The important idea is that each version introduces a new abstraction
**after understanding the lower-level implementation**.

------------------------------------------------------------------------

# 18. What You Are Actually Learning

Bifrost is more than an event API.

Each part teaches an important backend concept.

| Bifrost Component | What You Learn |
| --- | --- |
| `ServerSocket` | TCP/network programming |
| HTTP parser | How HTTP works internally |
| Virtual threads | Java concurrency |
| Socket timeout | Network security/reliability |
| Byte-based reading | Encoding and network I/O |
| Header limits | Defensive programming |
| JSON deserialization | Object mapping |
| Business validation | API design |
| Raw JDBC | Database communication |
| `PreparedStatement` | Safe SQL execution |
| Connection pool | Resource management |
| `Semaphore` | Concurrency control |
| Batch inserts | Database performance |
| PostgreSQL JSONB | Semi-structured data |
| Indexes | Database query performance |
| WAL | Database internals |
| Debezium | Change Data Capture |
| Kafka/RabbitMQ | Asynchronous systems |
| Redis idempotency | Distributed-system patterns |

------------------------------------------------------------------------

# 19. Important Open Decisions

## PostgreSQL JDBC driver

Before implementing Phase 2.2, pin a compatible `pgjdbc` version.

The project currently targets:

``` text
pgjdbc >= 42.6.0
```

because of the project's JDK 21 virtual-thread considerations.

This should be verified against the exact driver/JDK combination before
locking the dependency.

------------------------------------------------------------------------

## 200 vs 202

### Current:

``` text
Synchronous database write
        ↓
200 OK
```

### Future:

``` text
Queue event
        ↓
202 Accepted
        ↓
Process asynchronously
```

So the status code should change when the architecture actually becomes
asynchronous.

------------------------------------------------------------------------

# 20. Simple Mental Model

If all of Bifrost feels complicated, remember this:

``` text
             BIFROST

        1. RECEIVE
             ↓
       ServerSocket
             ↓
      Virtual Thread

        2. UNDERSTAND
             ↓
        HTTP Parser
             ↓
      Read headers/body

        3. VALIDATE
             ↓
        JSON → DTO
             ↓
      Business validation

        4. STORE
             ↓
          JDBC
             ↓
      Connection Pool
             ↓
        PostgreSQL

        5. SCALE
             ↓
      Batch Writes
             ↓
    Kafka / Redis later

        6. SYNCHRONIZE
             ↓
      PostgreSQL WAL
             ↓
        Debezium
```

------------------------------------------------------------------------