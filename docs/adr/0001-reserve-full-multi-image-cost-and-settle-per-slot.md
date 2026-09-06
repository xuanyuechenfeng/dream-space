# Reserve full multi-image cost and settle per result slot

For a multi-image Generation Task, reserve the full expected cost when the task is accepted, then consume one unit of that reservation as each Result Slot succeeds and release only the remainder after failure or cancellation. This temporarily locks more credits than incremental reservation, but guarantees that an accepted task can afford every requested result while preserving successful-result charges and making continuation reserve only the missing slots.
