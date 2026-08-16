# Implement admin API and admin application

## Goal

Deliver admin task/inspiration APIs and the Vue admin parity UI with RBAC.

## Requirements

- Implement admin authentication, task/reconciliation read APIs, result resource access, inspiration CRUD, publish/unpublish, and server-side RBAC.
- Build the Vue admin shell, tasks table/detail drawer, reconciliation summary, inspiration editor drawer, filters, pagination, and responsive layouts to `bak/apps/admin`.
- Preserve the independent admin cookie/session, role matrix, redacted operator data, optimistic publish update, and documented response/error contracts.

## Acceptance Criteria

- [ ] Admin login/session/logout works independently from user auth; inactive admins and expired sessions are rejected.
- [ ] VIEWER can read tasks/results/reconciliation but receives 403 for inspiration writes; OPERATOR/ADMIN can publish according to the matrix.
- [ ] Task list/detail and reconciliation endpoints preserve pagination, filters, status, costs, events, and redaction rules.
- [ ] Inspiration CRUD validates required source/license fields and publish/unpublish uses optimistic conflict detection.
- [ ] Admin desktop/mobile screenshots and keyboard-accessible drawers/forms pass visual and responsive checks.

## Notes

- Keep `prd.md` focused on requirements, constraints, and acceptance criteria.
- Lightweight tasks can remain PRD-only.
- For complex tasks, add `design.md` for technical design and `implement.md` for execution planning before `task.py start`.
