# Dream Space Domain Glossary

This glossary defines the business language for user management, credits, pricing, billing, and payments.

## User

A person who owns a Dream Space account, generation sessions, tasks, assets, credits, and orders. An administrator is a separate `AdminUser` actor and is never represented as a User.

## Credit

An internal, non-cash unit consumed by generation tasks. Credits are not currency and cannot be treated as payment receipts. A credit balance is maintained by an append-only credit ledger and a locked account snapshot.

## Quota

The operational view of a User's credits: total, available, reserved, and used. Quota is a balance projection, not a financial account.

## Credit Grant

An immutable addition of credits to a User's quota. Grants may originate from an initial allowance, a paid product, a promotion, an administrator adjustment, or a refund. Every grant has a source and an audit reason.

## Credit Reservation

Credits moved from available to reserved when a generation task is accepted. A reservation is settled as consumption on success or released on failure/cancellation.

## Pricing Rule

A versioned rule that converts a billable operation into a credit cost. A rule is selected by operation, model, resolution, dimensions, and other request attributes. The selected rule version is copied to the task and ledger entry so historical charges never change when configuration changes.

## Credit Product

A purchasable package that grants a defined number of credits for a monetary price. Products are versioned by effective dates and are not the same as generation pricing rules.

## Order

The platform's commercial intent to buy a Credit Product. An order has a platform order number, amount, currency, lifecycle status, and an idempotency key.

## Payment Transaction

A provider-specific attempt or callback record associated with an Order. It records provider references, signature verification, and state transitions; it is not the source of truth for credit balance.

## Billing Statement

A user-facing, read-only projection of credit grants, reservations, consumption, releases, refunds, and order payments. It is derived from immutable records and does not become a second mutable ledger.

## Account Disablement

An administrative state that blocks new login and new billable operations while preserving historical tasks, ledger entries, and orders. It is distinct from deletion or anonymization.

## Admin Role

A named collection of permissions assigned to an `AdminUser`. A role is an operational responsibility, not a hardcoded authorization level.

## Admin Permission

An atomic `resource:action` capability checked by the server for a management operation. A permission is granted through roles rather than assigned directly to application routes by rank.

## AI Provider

An external service organization or endpoint that supplies one or more AI models. Credentials belong to the provider connection, not to a model or route.

## AI Model

A callable model offering registered under an AI Provider with declared generation capabilities and lifecycle status. The same model name under different providers represents different offerings.

## Model Route

A versioned policy that selects ordered AI Model targets for a generation stage and request context. The published route version is snapshotted onto a task attempt.

## Credit Adjustment

An administrator-initiated request to grant or debit credits with a reason, approval state, and immutable resulting ledger entry. It is not a direct edit of the quota snapshot.

## System Configuration

A versioned business or runtime policy value that can be safely changed online. Infrastructure locations and secrets are deployment configuration, not System Configuration.

## Audit Event

An append-only record of who attempted or completed a management action, on which subject, with what result and reason. It is distinct from application logs and mutable business records.

## Operations Metric

A time-bucketed business measurement used for operational decisions, such as active users or generation success rate. It is distinct from financial report facts and runtime telemetry.
