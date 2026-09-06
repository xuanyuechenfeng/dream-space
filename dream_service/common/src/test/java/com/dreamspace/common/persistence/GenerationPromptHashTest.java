package com.dreamspace.common.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.dreamspace.common.persistence.generation.GenerationPromptHash;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class GenerationPromptHashTest {
  private final ObjectMapper json = new ObjectMapper();

  @Test
  void ignoresJsonObjectKeyOrderButPreservesArrayOrder() throws Exception {
    var first = json.readTree("""
        {"positivePrompt":"scene","modelInput":{"ratio":"1:1","slotIndex":0},"facts":["a","b"]}
        """);
    var reordered = json.readTree("""
        {"facts":["a","b"],"modelInput":{"slotIndex":0,"ratio":"1:1"},"positivePrompt":"scene"}
        """);
    var changed = json.readTree("""
        {"facts":["b","a"],"modelInput":{"slotIndex":0,"ratio":"1:1"},"positivePrompt":"scene"}
        """);

    assertThat(GenerationPromptHash.sha256(first))
        .isEqualTo(GenerationPromptHash.sha256(reordered))
        .isNotEqualTo(GenerationPromptHash.sha256(changed));
  }
}
