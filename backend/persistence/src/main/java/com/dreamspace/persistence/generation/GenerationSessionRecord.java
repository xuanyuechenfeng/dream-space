package com.dreamspace.persistence.generation;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;

public record GenerationSessionRecord(String id, String userId, String title, JsonNode draft,
    Instant createdAt, Instant updatedAt) {}
