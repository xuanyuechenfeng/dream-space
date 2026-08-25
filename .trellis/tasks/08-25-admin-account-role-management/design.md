# 管理员账号与角色管理技术设计

## Boundary

API owns administrator and role lifecycle transactions. `AdminMapper` owns account/RBAC queries and mutations; `AdminPermissionDefinition` remains the server-owned permission catalog. The management SPA consumes typed API contracts and never becomes an authorization source.

## Data Model And Migration

Add a timestamped common migration that adds administrator lifecycle fields, backfills status from `active`, adds lookup indexes, seeds `admins:read`, `admins:write`, `roles:read`, and `roles:write`, and extends session invalidation for account/role status changes. The old `active` column remains the compatibility flag used by login queries.

## Service Contracts

Typed records expose masked phone, status, roles, last login, version, and disable metadata. Endpoints are:

| Method | Path | Permission | Behavior |
| --- | --- | --- | --- |
| GET | `/manage_web/admins` | `admins:read` | filter by query/status/role with pagination |
| POST | `/manage_web/admins` | `admins:write` | create invited account, idempotency key |
| PATCH | `/manage_web/admins/{id}` | `admins:write` | display/status update with version and reason |
| PUT | `/manage_web/admins/{id}/roles` | `admins:write` | replace role ids with version and reason |
| POST | `/manage_web/admins/{id}/revoke-sessions` | `admins:write` | revoke all sessions, reason required |
| GET | `/manage_web/roles` | `roles:read` | role list with permission/account counts |
| POST | `/manage_web/roles` | `roles:write` | create custom role |
| PATCH | `/manage_web/roles/{id}` | `roles:write` | update custom role with version |
| PUT | `/manage_web/roles/{id}/permissions` | `roles:write` | replace permission ids with version |
| GET | `/manage_web/permissions` | `roles:read` | registered permission catalog |

Validation errors are 400, policy denials 403, version/idempotency conflicts 409, and missing resources 404.

## Invariants And Transactions

1. Lock the target account/role row with `FOR UPDATE` before policy checks.
2. Reject self-disable and self-empty-role operations.
3. For an ADMIN role mutation, count active accounts with an effective ADMIN role in the same transaction; never reduce the count below one.
4. Validate all submitted role/permission ids and reject disabled/unknown entries before mutation.
5. Update with `WHERE id = ? AND version = ?`; zero rows returns `RESOURCE_VERSION_CONFLICT`.
6. Persist the audit event in the same transaction; RBAC triggers revoke sessions and increment revisions.

Role and permission replacement is delete/insert under a row lock. Account creation and session revoke accept `Idempotency-Key`.

## Frontend

Add `/admins` and `/roles` routes under `AdminShell`. Use typed `adminApi` methods and permission-gated links. The account view uses a list plus edit drawer; the role view uses a role list plus permission matrix. High-risk mutations use an in-app dialog with object, impact, reason and confirmation, with responsive table-to-stack behavior.

## Rollout And Rollback

Run the additive migration before deploying API code. Bootstrap permission rows are validated at startup. Rollback can disable new routes and continue using legacy role display/active login fields; do not delete new tables or rewrite applied migrations.
