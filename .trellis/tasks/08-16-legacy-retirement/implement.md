# Implementation checklist

1. Verify cutover completion, approval, elapsed rollback window, reconciliation, backups, and restore drill evidence.
2. Inventory legacy ingress, deployments, workers, schedulers, queues, bridge, secrets, images, and owners.
3. Disable components in the approved staged order with observation and rollback points.
4. Run full smoke/visual/generation/quota/admin/monitoring checks after retirement.
5. Archive release evidence and update runbooks without changing or deleting `bak/`.
