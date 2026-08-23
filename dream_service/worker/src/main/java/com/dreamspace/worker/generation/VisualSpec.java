package com.dreamspace.worker.generation;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.Map;

public record VisualSpec(String style, Map<String, Object> palette, Map<String, Object> layout,
    Map<String, Object> typography, String contrast, JsonNode negativeConstraints) {}
