package com.dreamspace.worker.generation;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.List;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.content.Media;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.retry.TransientAiException;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.util.MimeTypeUtils;
import com.dreamspace.worker.observability.WorkerMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ChatQualityEvaluationModel implements QualityEvaluationModel {
  private static final Logger log = LoggerFactory.getLogger(ChatQualityEvaluationModel.class);
  private static final String SYSTEM = "You are the production image quality evaluator. Return one strict JSON object only. "
      + "Evaluate technical validity, required text, structure, visual style, colors, layout and policy. "
      + "Do not invent facts. accepted is true only when all hard constraints pass. "
      + "If repairable is true, refinement is required and must contain a non-empty instruction string, targetSections (array of strings), changes (array of strings), preserve (array of strings), and reasonCodes (array of strings). instruction is the complete actionable repair request for the image model; changes must contain the same repair actions in structured form. Preserve user facts, ratio, dimensions and approved content. "
      + "The top-level fields are exactly accepted (boolean), score (number from 0 to 1), violations (array of strings), "
      + "repairable (boolean), evidence (array of strings), evaluatorVersion (string), and optional refinement (object); "
      + "refinement has exactly instruction, targetSections, changes, preserve and reasonCodes; do not return any other field.";
  private final ChatModel model;
  private final ObjectMapper json;
  private final ReferenceImageLoader references;
  private final WorkerMetrics metrics;
  private final String modelName;

  public ChatQualityEvaluationModel(ChatModel model, ObjectMapper json, ReferenceImageLoader references,
      WorkerMetrics metrics, String modelName) {
    this.model = model;
    this.json = json.copy().enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    this.references = references;
    this.metrics = metrics;
    this.modelName = modelName;
  }

  @Override
  public EvaluationResult evaluate(WorkerTaskSnapshot task, GenerationPlanBundle plan,
      List<ProviderImage> images, int iteration) {
    long started = metrics.startModel();
    log.atInfo().addKeyValue("taskId", task.id()).addKeyValue("iteration", iteration)
        .addKeyValue("model", modelName).addKeyValue("imageCount", images.size())
        .log("quality evaluation model request started");
    try {
      JsonNode root = call(task, plan, images, iteration);
      JsonNode reportNode = normalizeReport(root);
      if (reportNode.isObject()) ((ObjectNode) reportNode).remove("refinement");
      EvaluationReport report = json.treeToValue(reportNode, EvaluationReport.class);
      if (report == null || report.score() < 0 || report.score() > 1 || report.evaluatorVersion() == null
          || report.evaluatorVersion().isBlank()) {
        throw invalid("quality evaluator returned invalid score or version");
      }
      RefinementPatch patch = root.hasNonNull("refinement")
          ? parseRefinement(root.get("refinement"), report) : null;
      if (report.repairable() && patch == null) throw invalid("repairable evaluation has no refinement patch");
      if (!report.repairable() && patch != null) throw invalid("non-repairable evaluation returned a refinement patch");
      log.atInfo().addKeyValue("taskId", task.id()).addKeyValue("iteration", iteration)
          .addKeyValue("score", report.score()).addKeyValue("accepted", report.accepted())
          .addKeyValue("repairable", report.repairable()).log("quality evaluation model response parsed");
      return new EvaluationResult(report, patch);
    } catch (GenerationProviderException error) {
      log.atWarn().addKeyValue("taskId", task.id()).addKeyValue("iteration", iteration)
          .addKeyValue("errorCode", error.code()).addKeyValue("retryable", error.retryable())
          .log("quality evaluation model request failed");
      throw error;
    } catch (TransientAiException error) {
      log.atWarn().addKeyValue("taskId", task.id()).addKeyValue("iteration", iteration)
          .addKeyValue("errorCode", "EVALUATION_TEMPORARILY_UNAVAILABLE")
          .log("quality evaluation model is temporarily unavailable");
      throw new GenerationProviderException("EVALUATION_TEMPORARILY_UNAVAILABLE",
          "quality evaluator is temporarily unavailable", true, error);
    } catch (Exception error) {
      log.atError().addKeyValue("taskId", task.id()).addKeyValue("iteration", iteration)
          .addKeyValue("errorCode", "EVALUATION_OUTPUT_INVALID")
          .addKeyValue("exceptionType", error.getClass().getSimpleName())
          .log("quality evaluation model response could not be parsed", error);
      throw new GenerationProviderException("EVALUATION_OUTPUT_INVALID",
          "quality evaluator output is invalid", false, error);
    } finally {
      metrics.stopModel(started, "planning", modelName, "quality_evaluation");
    }
  }

  private JsonNode call(WorkerTaskSnapshot task, GenerationPlanBundle plan,
      List<ProviderImage> images, int iteration) {
    String text = "iteration=" + iteration + "\ntaskMode=" + task.mode()
        + "\nrequestedRatio=" + task.ratio() + "\nrequestedResolution=" + task.resolution()
        + "\nrequestedWidth=" + task.width() + "\nrequestedHeight=" + task.height()
        + "\nRequirementBrief=" + write(plan.requirement())
        + "\nStructurePlan=" + write(plan.structure())
        + "\nVisualSpec=" + write(plan.visual())
        + "\nPromptPackage=" + write(plan.promptPackage())
        + "\nimageCount=" + images.size();
    List<Media> media = new ArrayList<>();
    for (ProviderImage image : images) media.add(media(image.sourceName(), image.data(), image.mimeType()));
    int index = 1;
    for (String imageId : task.imageIds()) addReference(media, task, imageId, "input-image-" + index++);
    var response = model.call(new Prompt(List.of(new SystemMessage(SYSTEM),
        UserMessage.builder().text(text).media(media).build()),
        OpenAiChatOptions.builder().timeout(ModelTimeouts.DETECTION).build()));
    String responseText = response == null || response.getResult() == null || response.getResult().getOutput() == null
        ? null : response.getResult().getOutput().getText();
    String responsePreview = preview(responseText);
    log.atInfo().addKeyValue("taskId", task.id()).addKeyValue("iteration", iteration)
        .addKeyValue("responseLength", responseText == null ? 0 : responseText.length())
        .addKeyValue("responsePreview", responsePreview)
        .log("quality evaluation model raw response received (response=" + responsePreview + ")");
    if (responseText == null || responseText.isBlank()) {
      throw new GenerationProviderException("EVALUATION_EMPTY_RESPONSE", "quality evaluator returned no response", true);
    }
    try { return json.readTree(responseText); }
    catch (Exception error) { throw invalid("quality evaluator returned non-JSON output"); }
  }

  static JsonNode normalizeReport(JsonNode root) {
    if (root == null || !root.isObject()) return root;
    ObjectNode normalized = (ObjectNode) root.deepCopy();
    JsonNode evidence = normalized.get("evidence");
    if (evidence == null || evidence.isNull() || evidence.isArray() || !evidence.isObject()) return normalized;
    ArrayNode flattened = normalized.arrayNode();
    evidence.fields().forEachRemaining(entry -> appendEvidence(flattened, entry.getKey(), entry.getValue()));
    normalized.set("evidence", flattened);
    log.atWarn().addKeyValue("field", "evidence")
        .log("quality evaluator returned object for string array; normalized compatibility shape");
    return normalized;
  }

  private RefinementPatch parseRefinement(JsonNode node, EvaluationReport report) {
    if (node == null || !node.isObject()) throw invalid("refinement must be an object");
    ObjectNode normalized = (ObjectNode) node.deepCopy();
    JsonNode instruction = normalized.get("instruction");
    if (instruction == null || !instruction.isTextual() || instruction.asText().isBlank()) {
      throw invalid("repairable evaluation refinement requires a non-empty instruction");
    }
    // Keep compatible instruction-only responses usable during prompt rollout.
    JsonNode changes = normalized.get("changes");
    if (changes == null || changes.isNull()) {
      ArrayNode fallback = normalized.arrayNode();
      fallback.add(instruction.asText().trim());
      normalized.set("changes", fallback);
    }
    if (!normalized.has("targetSections")) normalized.set("targetSections", normalized.arrayNode());
    if (!normalized.has("preserve")) normalized.set("preserve", normalized.arrayNode());
    if (!normalized.has("reasonCodes")) {
      ArrayNode reasonCodes = normalized.arrayNode();
      if (report.violations() != null) report.violations().stream()
          .filter(value -> value != null && !value.isBlank()).forEach(reasonCodes::add);
      normalized.set("reasonCodes", reasonCodes);
    }
    try {
      return json.treeToValue(normalized, RefinementPatch.class);
    } catch (Exception error) {
      throw invalid("quality evaluator returned an invalid refinement patch");
    }
  }
  private static void appendEvidence(ArrayNode target, String category, JsonNode value) {
    if (value.isArray()) {
      for (JsonNode item : value) target.add(category + ": " + evidenceText(item));
      return;
    }
    target.add(category + ": " + evidenceText(value));
  }

  private static String evidenceText(JsonNode value) {
    return value.isTextual() ? value.asText() : value.toString();
  }

  private static String preview(String body) {
    if (body == null) return "<null>";
    String compact = body.replaceAll("\\s+", " ");
    return compact.length() <= 4096 ? compact : compact.substring(0, 4096) + "...";
  }

  private void addReference(List<Media> media, WorkerTaskSnapshot task, String id, String name) {
    ReferenceImage image = references.load(task.userId(), id);
    media.add(media(name, image.bytes(), image.mimeType()));
  }

  private static Media media(String name, byte[] bytes, String mime) {
    return Media.builder().id(name).name(name).mimeType(MimeTypeUtils.parseMimeType(mime))
        .data(new ByteArrayResource(bytes)).build();
  }

  private String write(Object value) {
    try { return json.writeValueAsString(value); }
    catch (Exception error) { throw new IllegalStateException("quality evaluation input encoding failed", error); }
  }

  private static GenerationProviderException invalid(String message) {
    return new GenerationProviderException("EVALUATION_OUTPUT_INVALID", message, false);
  }
}
