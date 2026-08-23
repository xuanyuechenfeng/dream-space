package com.dreamspace.worker.generation;

public record PlanningStageInput(WorkerTaskSnapshot task, RequirementBrief requirement,
    StructurePlan structure, VisualSpec visual) {}
