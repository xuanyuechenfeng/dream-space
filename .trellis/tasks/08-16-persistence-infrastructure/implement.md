# Implementation Plan

1. Lock dependency versions in the Maven parent and add persistence dependencies (MyBatis, JDBC/PostgreSQL, Redis, AWS SDK v2, Flyway-compatible migration support, Testcontainers).
2. Copy and checksum the Prisma migration SQL into the persistence resources; add a schema-contract test that verifies all expected migration files are present and ordered.
3. Add `DreamSpaceProperties`, URI parsing, profile defaults, and readiness contributors.
4. Add Jackson JSON and exact enum type handlers plus representative records and mapper interfaces/XML for each domain package.
5. Implement transaction-safe quota repository methods and conditional generation task/event persistence methods.
6. Implement `GenerationQueue`, Redis Streams producer/consumer primitives, group initialization, pending reclaim, and retry/dead-letter policy.
7. Implement `ObjectKeyPolicy`, local atomic storage, S3-compatible storage, and storage factory.
8. Add unit, WireMock, and Testcontainers tests with Docker-gated integration profiles.
9. Wire persistence configuration into API and worker startup/readiness and update application configuration documentation.
10. Run Maven tests, static checks, migration/resource checks, credential scan, and `bak/` immutability check; commit and open a PR against `feature/refactor-platform-scaffold`.
