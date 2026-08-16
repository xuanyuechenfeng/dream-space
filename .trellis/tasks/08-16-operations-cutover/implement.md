# Implementation checklist

1. Add Dockerfiles/Compose profiles and explicit non-secret configuration examples.
2. Implement dependency readiness, version info, structured logging, metrics, dashboards, and alerts.
3. Implement and test BullMQ bridge or dual-write/read with shared idempotency and status mapping.
4. Add database/object backup, restore, migration, lifecycle, canary, drain, and rollback scripts/runbooks.
5. Rehearse staging cutover and rollback with fixture parity and reconciliation reports.
6. Record release approval evidence; do not merge/cut traffic without explicit user approval.
