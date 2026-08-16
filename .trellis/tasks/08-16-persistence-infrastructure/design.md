# Persistence and Infrastructure Design

## Boundaries

`dream-space-persistence` owns database access and infrastructure adapters. API and worker modules depend on ports and records from this module; controllers and model-processing code do not construct SQL, Redis commands, filesystem paths, or S3 requests directly.

```text
API/Worker application services
        |
        +--> domain ports (repositories, GenerationQueue, ObjectStorage)
        |          |
        |          +--> MyBatis mappers + transaction services
        |          +--> Redis Streams adapter
        |          +--> LocalObjectStorage / S3ObjectStorage
        |
        +--> readiness contributors
```

## PostgreSQL and MyBatis

- Copy the existing Prisma migration SQL byte-for-byte into `backend/persistence/src/main/resources/db/migration/` using the same timestamped ordering. A lightweight migration runner records applied versions in `schema_migrations`; it executes only when explicitly enabled by an operations command, never as a side effect of application startup.
- Configure MyBatis with underscore-to-camel disabled globally and map every column explicitly in XML or annotated result maps. This keeps names such as `last_attempt_key`, `thumbnail_object_key`, and `window_key` visible in review.
- Use `JsonNode` with a Jackson `TypeHandler` for `draft`, `reference_image_urls`, `payload`, and `details`. Enum handlers convert Java enum constants to the exact database values, including `smart`, `21:9`, `2K`, and `4K`.
- Mapper packages are `com.dreamspace.persistence.auth`, `.inspiration`, `.generation`, `.quota`, and `.admin`. Records are immutable and do not expose MyBatis implementation types.
- Quota mutations run in a database transaction and lock the account row (`SELECT ... FOR UPDATE`). Unique idempotency keys make retrying reserve/settlement/release safe. Task cancellation and worker claim use conditional `UPDATE ... WHERE status = ...` statements.

## Redis Streams

`RedisGenerationQueue` uses `StringRedisTemplate` and a typed `GenerationJob(taskId)`. Startup creates consumer group `generation-workers` on stream `generation` with `MKSTREAM` semantics. Producers add only the task id and a stable message id (`taskId`); consumers acknowledge after the task transaction commits. Pending entries are reclaimed with `XAUTOCLAIM` after a configurable idle timeout. A poison message is dead-lettered in PostgreSQL after three attempts and then acknowledged.

## Object storage

`ObjectKeyPolicy` accepts only:

```text
references/<id>/<file>.webp
results/<taskId>/<resultId>.webp
thumbnails/<taskId>/<resultId>.webp
```

It rejects absolute paths, `..`, backslashes, unknown prefixes, and non-WebP extensions. `LocalObjectStorage` writes to a sibling temporary file and uses `ATOMIC_MOVE` with a non-atomic fallback in the same directory. `S3ObjectStorage` uses AWS SDK v2 and an S3-compatible endpoint; signed GET URLs are limited by configuration. `get` reports not-found distinctly and `delete` treats not-found as success.

## Configuration and readiness

`DreamSpaceProperties` is bound under `dream-space` and validates mode-specific settings. Environment aliases retain the legacy names documented in `docs/design/05-data-and-infrastructure.md`. API readiness checks PostgreSQL, Redis, and the selected object store; worker readiness checks the same dependencies plus the model provider configuration. Local/mock profiles may use loopback defaults, while production requires explicit URLs and credentials.

## Testing strategy

- Pure unit tests: key policy, JSON/enum handlers, properties validation, local atomic write/delete.
- WireMock tests: S3-compatible PUT/GET/DELETE/signing behavior and Redis-independent HTTP semantics.
- Testcontainers tests (opt-in when Docker is available): PostgreSQL migration/schema contract, MyBatis CRUD, Redis group/pending reclaim, and concurrent quota idempotency.
- Existing API/worker smoke tests remain the first regression gate; no test may read from `bak/` at runtime.
