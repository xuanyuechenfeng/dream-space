package com.dreamspace.common.persistence.generation;

import com.dreamspace.common.persistence.database.DatabaseEnums.CollectionMode;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;

/** Frozen, ordered description of the images a generation task must produce. */
public record ImageCollectionPlan(String schemaVersion, CollectionMode mode, JsonNode shared,
    List<ResultSlotPlan> slots, double confidence, List<String> unknowns) {
  public ImageCollectionPlan {
    if (schemaVersion == null || schemaVersion.isBlank()) schemaVersion = "collection-v2";
    if (mode == null) mode = CollectionMode.VARIATIONS;
    slots = slots == null ? List.of() : List.copyOf(slots);
    unknowns = unknowns == null ? List.of() : List.copyOf(unknowns);
    if (!Double.isFinite(confidence)) confidence = 0.0;
  }
}
