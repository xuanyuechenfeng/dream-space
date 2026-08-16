# Implement persistence and infrastructure adapters

## Goal

Implement PostgreSQL mappings, Redis/S3 adapters, configuration, and integration tests.

## Requirements

- Preserve the PostgreSQL contract from `bak/packages/db/prisma/schema.prisma` and its migrations: table names, column names, enum values, relations, indexes, unique constraints, cascade behavior, and JSON payload shape.
- Add versioned SQL migrations under the Java backend. Production startup must never create or update schema automatically; `ddl-auto` is prohibited.
- Add MyBatis repositories and explicit DTO/record mappings for `auth`, `inspiration`, `generation`, `quota`, and `admin`. JSON columns must map through Jackson `JsonNode` or explicit records.
- Add strongly typed configuration for `DATABASE_URL`, `REDIS_URL`, `OBJECT_STORAGE_MODE`, local storage, S3-compatible storage, auth TTLs, quota reconciliation, and generation queue settings. Missing required production settings must fail readiness.
- Implement a Redis Streams adapter for stream `generation` and consumer group `generation-workers`. The payload is `{taskId}`, task id is the idempotency key, maximum attempts is 3, and PostgreSQL remains the source of truth.
- Implement object storage ports and Local FS/S3-compatible adapters. Only the approved `references/`, `results/`, and `thumbnails/` key patterns are accepted. Local writes use a temporary file followed by atomic rename; deletes are idempotent; S3 supports put/get/delete and short-lived signed GET URLs.
- Provide health/readiness contributors for PostgreSQL, Redis, and object storage and wire them into API/worker profiles.
- Add unit and integration tests for enum/JSON mappings, transaction/uniqueness constraints, Redis consumer behavior, path traversal protection, atomic local writes, and S3 behavior using WireMock or Testcontainers.

## Acceptance Criteria

- [ ] `backend` builds on Java 21/Spring Boot 4 and all existing platform tests remain green.
- [ ] A clean PostgreSQL 17 instance can be migrated with the checked-in SQL and passes schema-contract checks against the Prisma baseline.
- [ ] MyBatis can read/write representative records from every domain group without renaming or lossy JSON/enum conversion.
- [ ] Reserve/consume/release operations enforce integer quota invariants and idempotency under concurrent requests.
- [ ] Redis Streams creates/uses the required group, publishes `{taskId}`, acknowledges only after durable processing, and supports pending reclaim without becoming the fact source.
- [ ] Local and S3 adapters reject invalid keys and path traversal; local writes are atomic and deletes are idempotent.
- [ ] `/health/ready` reports dependency failures accurately in API and worker profiles.
- [ ] `git diff --check`, credential scan, Maven tests, and Trellis validation pass; `bak/` is unchanged.

## Notes

- Keep `prd.md` focused on requirements, constraints, and acceptance criteria.
- This is a complex task; `design.md` and `implement.md` are mandatory before implementation is marked ready.
