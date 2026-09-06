package com.dreamspace.common.persistence.generation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

/** Stable hash for a prompt stored in PostgreSQL JSONB, whose object key order is not preserved. */
public final class GenerationPromptHash {
  private GenerationPromptHash() {}

  public static String sha256(JsonNode prompt) {
    if (prompt == null) throw new IllegalArgumentException("prompt is required");
    try {
      byte[] digest = MessageDigest.getInstance("SHA-256")
          .digest(canonical(prompt).toString().getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(digest);
    } catch (java.security.NoSuchAlgorithmException error) {
      throw new IllegalStateException("SHA-256 is unavailable", error);
    }
  }

  private static JsonNode canonical(JsonNode value) {
    if (value.isObject()) {
      ObjectNode result = JsonNodeFactory.instance.objectNode();
      List<Map.Entry<String, JsonNode>> fields = new ArrayList<>();
      value.fields().forEachRemaining(fields::add);
      fields.sort(Comparator.comparing(Map.Entry::getKey));
      fields.forEach(field -> result.set(field.getKey(), canonical(field.getValue())));
      return result;
    }
    if (value.isArray()) {
      var result = JsonNodeFactory.instance.arrayNode();
      value.forEach(item -> result.add(canonical(item)));
      return result;
    }
    return value.deepCopy();
  }
}
