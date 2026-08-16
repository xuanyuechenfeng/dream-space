# Legacy retirement design

Retirement is a release operation, not code cleanup. It starts only after a completed operations task, explicit approval, elapsed rollback period, zero unexplained quota/task differences, and verified backups. Disable legacy ingress first, then producers, consumers, schedulers, bridge, images, and secrets. Observe each stage before the next. Do not drop tables, enums, Redis facts, buckets, migrations, or `bak/`.

Rollback before the final stage re-enables the last disabled component and routing. After final retirement, restoration uses the retained image digest, configuration inventory, database/object backup, and runbook rather than source reconstruction.
