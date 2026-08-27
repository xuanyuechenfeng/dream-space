# Design

## Scope

Only `dream_web/src/views/AccountView.vue` and account-specific styles in `dream_web/src/styles.css` are changed. Existing API types and endpoints remain the source of truth.

## Information Architecture

1. Page header with refresh action and account status.
2. Credit overview band with available balance, usage breakdown, and progress bar.
3. Product purchase grid with one recommended emphasis and clear unit economics.
4. Billing activity panel with `额度流水` and `支付订单` tabs.

## Interaction

- Local `activeTab` controls the two activity views.
- Refresh action reuses `load` and exposes `refreshing` state.
- Product buttons call the existing create-order function and refresh all data on success.
- Tables remain semantic and gain a scroll container on narrow screens.
