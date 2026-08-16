# Operations and cutover design

## Deployment

Build independent immutable images for API, Worker, web, and admin. PostgreSQL, Redis, and MinIO/S3 are external stateful services in production. All configuration enters via environment/secret mounts; images contain no `.env` or credentials. API and Worker advertise version, Git SHA, profile, and schema version.

## Health and observability

`/health/live` checks only process/runtime. `/health/ready` verifies PostgreSQL, Redis, selected object storage, migrations, and for Worker the provider configuration. Structured logs carry requestId/taskId but redact prompt, phone, token, provider payload, and signed URL. Metrics cover the gates in the PRD.

## Cutover

Freeze contracts, backup PostgreSQL/object metadata, deploy new readers, enable bridge/dual-write with idempotency keys, compare shadow results, canary traffic, then expand. Rollback stops new writers, drains/reconciles tasks, restores old routing, and preserves all new task/ledger facts. Database migrations used during the window must be backward compatible.
