# Persistence module dependency map

## Research method

The mapping below is based on imports and symbol references from `backend/api` and `backend/worker`, plus the internal dependencies of every class currently under `backend/persistence`.

## Shared code -> common

Target package root: `com.dreamspace.common.persistence`.

| Current area | Contents | Why shared |
|---|---|---|
| `config` | `DreamSpaceProperties`, infrastructure bean configuration, readiness probe | Both applications load the same properties and infrastructure |
| `database` | `DatabaseEnums`, `DatabaseValue`, `DatabaseMigrationService` | Enums are used by both applications; migrations describe the shared schema |
| `generation` | `GenerationMapper` and all generation records | API creates/reads tasks; Worker claims/updates them |
| `quota` | Mapper interfaces, account record, transaction service | API reserves quota; Worker settles/refunds it |
| `queue` | Queue contract, job payload, Redis implementation | API publishes and Worker consumes the same message contract |
| `storage` | Storage contract, factory, local/S3 implementations, key policy | API uploads/reads results; Worker writes generated output |
| `typehandler` | Enum and JSON handlers | Shared MyBatis configuration requires them |
| `reconciliation` | `QuotaReconciliationRunRecord` only | Worker writes it and API admin views it |
| resources | All 12 `db/migration/*.sql` files | Both applications depend on one database schema; migration tooling needs one canonical classpath location |

The root compatibility class `com.dreamspace.persistence.DatabaseMigrationService` and unused `PersistenceBoundary` marker are not migrated. The canonical migration service is retained under the common database package.

## API-only code -> api

Target package root: `com.dreamspace.api.persistence`.

| Area | Contents |
|---|---|
| `admin` | `AdminApplicationMapper`, `AdminMapper`, and all admin records |
| `auth` | `AuthMapper`, `UserRecord`, `UserSessionRecord`, `VerificationCodeRecord` |
| `inspiration` | `InspirationMapper`, `InspirationRecord` |
| `upload` | `ReferenceUploadMapper`, `ReferenceUploadRecord` |

No Worker source references these types.

## Worker-only code -> worker

Target package root: `com.dreamspace.worker.persistence`.

| Area | Contents |
|---|---|
| `reconciliation` | `QuotaReconciliationMapper` |

The associated `QuotaReconciliationRunRecord` remains shared because the API admin service also reads it.

## Configuration implications

- The current `PersistenceConfiguration` scans all Mapper packages in both applications. After the move, it must be split into a shared infrastructure configuration plus application-local Mapper scan configurations.
- Shared Mapper scan: common `generation` and `quota` packages.
- API Mapper scan: API `admin`, `auth`, `inspiration`, and `upload` packages.
- Worker Mapper scan: Worker `reconciliation` package.
- The shared configuration remains explicitly imported by both application entry points because component scanning rooted at `com.dreamspace.api` or `com.dreamspace.worker` does not discover `com.dreamspace.common` automatically.
- `GenerationWorkerConfiguration` currently declares a second Redis queue bean. The migrated configuration must leave a single owner for that bean to avoid configuration-order-dependent duplication.

## Test migration

Move all tests currently under `backend/persistence/src/test` to `backend/common/src/test` with matching package updates. This includes property, enum, quota, storage, Redis, migration resource, and opt-in PostgreSQL/Testcontainers tests. Existing API and Worker tests stay in their modules and receive import updates.
