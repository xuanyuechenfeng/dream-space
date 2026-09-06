package com.dreamspace.common.persistence.generation;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;

/** One stable result position in an ImageCollectionPlan. */
public record ResultSlotPlan(int index, String label, String role, String intent,
    List<String> contentScope, List<String> variationConstraints, JsonNode prompt, JsonNode acceptance) {
  public ResultSlotPlan {
    if (index < 0 || index > 3) throw new IllegalArgumentException("slot index must be between 0 and 3");
    label = label == null ? null : label.trim();
    role = role == null ? null : role.trim();
    intent = intent == null ? null : intent.trim();
    contentScope = contentScope == null ? null : List.copyOf(contentScope);
    variationConstraints = variationConstraints == null ? null : List.copyOf(variationConstraints);
  }
}
