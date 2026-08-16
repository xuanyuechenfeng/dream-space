# Admin API and application design

## API

`AdminAuthController` owns `/admin/auth/*`; task endpoints are read-only for VIEWER and expose filtered pages and a detail projection. Inspiration endpoints use application services for validation, optimistic update, and publish state transitions. `AdminPermissionGuard` maps each operation to the role matrix and is executed after the admin principal filter.

## UI

`AdminShell` guards the session and renders the 236px desktop/72px collapsed navigation. `AdminTasksView` contains summary, filters, table, pagination, and a right detail drawer. `AdminInspirationsView` contains search/status/category filters and an editor drawer. UI role state hides commands but always handles a server 403.

## Safety

Result content is authorized through task ownership/administrator role and never accepts arbitrary object keys. Phone numbers and provider payloads are redacted. Publish/unpublish requires `updatedAt` or version match and returns a conflict for stale editors.
