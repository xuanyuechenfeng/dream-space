package com.dreamspace.worker.generation;

import java.util.List;

public record RefinementPatch(String instruction, List<String> targetSections, List<String> changes,
    List<String> preserve, List<String> reasonCodes) {
  public RefinementPatch {
    instruction = instruction == null ? "" : instruction.trim();
    targetSections = targetSections == null ? List.of() : List.copyOf(targetSections);
    changes = changes == null ? List.of() : List.copyOf(changes);
    preserve = preserve == null ? List.of() : List.copyOf(preserve);
    reasonCodes = reasonCodes == null ? List.of() : List.copyOf(reasonCodes);
  }
}