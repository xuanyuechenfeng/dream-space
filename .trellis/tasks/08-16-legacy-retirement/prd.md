# Retire legacy runtime after cutover

## Goal

Remove obsolete runtime services only after cutover gates and preserve bak archive.

## Requirements

- Retire Node runtime services only after the documented rollback period, production acceptance, reconciliation, and explicit approval.
- Preserve `bak/` as the immutable source/reference archive and retain migrations, fixtures, release records, and restore runbooks.
- Remove obsolete deployment, bridge, scheduler, secrets, and traffic routes without deleting shared PostgreSQL/Redis/S3 data.

## Acceptance Criteria

- [ ] Operations cutover is complete, rollback period has ended, no open BLOCKED reconciliation findings remain, and user approval is recorded.
- [ ] Node API/Worker traffic, queues, schedulers, images, secrets, and bridge are disabled in a reversible staged sequence.
- [ ] PostgreSQL, object storage, audit/test evidence, and `bak/` archive remain intact and restorable.
- [ ] Post-retirement smoke, visual, generation, quota, admin, monitoring, and backup restore checks pass.

## Notes

- Keep `prd.md` focused on requirements, constraints, and acceptance criteria.
- Lightweight tasks can remain PRD-only.
- For complex tasks, add `design.md` for technical design and `implement.md` for execution planning before `task.py start`.
