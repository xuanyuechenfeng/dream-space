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

## Target Image Count

The number of image results a User expects from one Generation Task, from 1 through 4. A manually selected count takes precedence; otherwise an explicit count in the User's prompt is used, with 1 as the default when no count is stated.

## Result Slot

A stable, ordered position for one image result within a Generation Task. A task has one Result Slot for each unit of its Target Image Count, and the slot identity remains stable across continuation attempts.

## Slot Settlement

The final credit outcome for one Result Slot. A successful slot consumes its reserved credits exactly once; credits reserved for slots that do not succeed are released.

## Slot Attempt

A bounded effort to fill one Result Slot, including automatic recovery from transient provider errors and any quality refinement allowed for that slot. It fails only after those recovery opportunities are exhausted.

## Full Regeneration

A User-requested operation that creates a new Generation Task from the prior task's generation inputs and full Target Image Count. The prior task and its results remain unchanged; Full Regeneration is not a continuation attempt.

## Generation Cancellation

A User-requested stop to a Generation Task that preserves successful Result Slots and their Slot Settlements while abandoning unfinished slots. Credits reserved for unfinished slots are released, and those slots may later be continued.

## Image Collection Plan

The frozen, set-level interpretation of a multi-image Generation Task. It defines shared facts and visual constraints plus the ordered Slot Intents for independent alternatives, variations along explicit dimensions, or a structured sequence.

## Slot Intent

The distinct purpose and content scope assigned to one Result Slot by an Image Collection Plan. It preserves the set's shared constraints while defining what that slot alone must communicate.

## Collection Preflight

A short-lived, non-billable planning result produced before a Generation Task is created. It resolves the Image Collection Plan, Target Image Count, slot summaries and estimated cost so the User can correct ambiguity before credits are reserved.

## Plan Token

A single-use reference to a valid Collection Preflight. It binds the frozen plan to the normalized generation inputs and expires if unused, preventing a stale or replayed preflight from creating a task or reserving credits.

## Collection Clarification

A pre-task outcome indicating that an Image Collection Plan cannot be formed without missing facts or an arbitrary choice between plausible slot structures. It asks the User to revise the input and never creates a Generation Task or reserves credits.
