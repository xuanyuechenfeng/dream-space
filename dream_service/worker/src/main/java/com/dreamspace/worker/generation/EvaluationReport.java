package com.dreamspace.worker.generation;

import java.util.List;

public record EvaluationReport(boolean accepted, double score, List<String> violations,
    boolean repairable, List<String> evidence, String evaluatorVersion) {}
