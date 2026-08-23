package com.dreamspace.worker.generation;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;

public record StructurePlan(StructureCanvas canvas, JsonNode readingOrder,
    List<java.util.Map<String, Object>> modules, List<java.util.Map<String, Object>> textBlocks,
    List<java.util.Map<String, Object>> chartSpecs, JsonNode layoutRules, String density) {
  public StructurePlan withOutput(WorkerTaskSnapshot task) {
    StructureCanvas current = canvas == null ? new StructureCanvas(null, null, null, null, null) : canvas;
    return new StructurePlan(new StructureCanvas(task.ratio().databaseValue(), current.composition(),
        task.resolution().databaseValue(), task.width(), task.height()), readingOrder, modules, textBlocks,
        chartSpecs, layoutRules, density);
  }
}