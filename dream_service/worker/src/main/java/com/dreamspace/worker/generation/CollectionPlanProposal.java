package com.dreamspace.worker.generation;

import com.dreamspace.common.persistence.database.DatabaseEnums.CollectionMode;
import com.dreamspace.common.persistence.generation.ImageCollectionPlan;
import com.dreamspace.common.persistence.generation.ResultSlotPlan;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;

/** Strict provider-facing proposal for an ordered multi-image collection. */
public record CollectionPlanProposal(String schemaVersion, CollectionMode mode, JsonNode shared,
    List<ResultSlotPlan> slots, double confidence, List<String> unknowns,
    boolean needsClarification, String clarificationReason) {
  public CollectionPlanProposal {
    schemaVersion = schemaVersion == null || schemaVersion.isBlank() ? "collection-v2" : schemaVersion.trim();
    mode = mode == null ? CollectionMode.VARIATIONS : mode;
    slots = slots == null ? List.of() : List.copyOf(slots);
    unknowns = unknowns == null ? List.of() : List.copyOf(unknowns);
    clarificationReason = clarificationReason == null ? "" : clarificationReason.trim();
    if (!Double.isFinite(confidence)) confidence = 0.0;
  }

  public ImageCollectionPlan freeze() {
    return new ImageCollectionPlan(schemaVersion, mode, shared, slots, confidence, unknowns);
  }
}
