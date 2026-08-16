# Implementation checklist

1. Add worker configuration, queue consumer lifecycle, group initialization, reclaim scheduler, and dead-letter repository.
2. Add state machine, pipeline step interfaces, retry classification, and event writer.
3. Implement Spring AI ChatModel adapter, provider output decoder, deterministic mock, and WireMock tests.
4. Implement ImagePipeline, moderation ports, atomic main/thumbnail storage, checksum metadata, and cleanup.
5. Implement settlement and reconciliation services with injected-failure tests for every compensation boundary.
6. Run worker unit/WireMock/Testcontainers tests, readiness checks, and credential/bak immutability scans.
