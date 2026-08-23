package com.dreamspace.worker.generation;

import com.dreamspace.common.persistence.queue.GenerationJob;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import com.dreamspace.worker.observability.WorkerMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class GenerationProcessor {
  private static final Logger log = LoggerFactory.getLogger(GenerationProcessor.class);
  private final GenerationWorkerStore store;
  private final ImageGenerationModel imageModel;
  private final GenerationHarness harness;
  private final LoopEngine loop;
  private final GenerationOutputPipeline output;
  private final ContentModerator moderator;
  private final WorkerMetrics metrics;

  @Autowired
  public GenerationProcessor(GenerationWorkerStore store, ImageGenerationModel imageModel,
      GenerationHarness harness, LoopEngine loop, GenerationOutputPipeline output, ContentModerator moderator,
      WorkerMetrics metrics) {
    this.store = store;
    this.imageModel = imageModel;
    this.harness = harness;
    this.loop = loop;
    this.output = output;
    this.moderator = moderator;
    this.metrics = metrics;
  }

  public Outcome process(GenerationJob job, GenerationAttempt attempt) {
    WorkerTaskSnapshot task;
    try {
      task = store.start(job.taskId(), attempt).orElse(null);
    } catch (RuntimeException error) {
      log.atError().addKeyValue("taskId", job.taskId()).addKeyValue("attempt", attempt.number())
          .addKeyValue("stage", "claim").log("generation task claim failed", error);
      throw error;
    }
    if (task == null) {
      log.atWarn().addKeyValue("taskId", job.taskId()).addKeyValue("attempt", attempt.number())
          .log("generation task was not claimable; delivery will be acknowledged");
      return new Outcome(job.taskId(), Status.IGNORED);
    }
    log.atInfo().addKeyValue("taskId", task.id()).addKeyValue("attempt", attempt.number())
        .addKeyValue("imageCount", task.imageCount()).addKeyValue("imageInputs", task.imageIds().size())
        .addKeyValue("ratio", task.ratio()).addKeyValue("resolution", task.resolution())
        .log("generation task claimed");
    List<StoredGenerationResult> stored = List.of();
    String stage = "input_moderation";
    try {
      logStage(task.id(), attempt, "input_moderation", "started");
      ContentModerator.Decision input = moderator.moderateInput(task);
      if (!store.recordModeration(task.id(), "input", input)) {
        logStage(task.id(), attempt, "input_moderation", "state_update_ignored");
        return new Outcome(task.id(), Status.IGNORED);
      }
      log.atInfo().addKeyValue("taskId", task.id()).addKeyValue("attempt", attempt.number())
          .addKeyValue("approved", input.approved()).addKeyValue("code", input.code())
          .log("generation input moderation completed");
      if (!input.approved()) {
        return failed(task.id(), "INPUT_MODERATION_REJECTED",
            "提示词或参考内容未通过审核，请修改后重试", attempt, null);
      }

      stage = "planning";
      logStage(task.id(), attempt, stage, "started");
      GenerationPlanBundle plan = harness.execute(task, attempt);
      task = plan.applyOutput(task);
      log.atInfo().addKeyValue("taskId", task.id()).addKeyValue("attempt", attempt.number())
          .addKeyValue("intent", plan.requirement().intent()).addKeyValue("ratio", task.ratio())
          .addKeyValue("width", task.width()).addKeyValue("height", task.height())
          .log("generation planning completed");
      stage = "image_generation";
      logStage(task.id(), attempt, stage, "started");
      List<ProviderImage> images = loop.execute(task, plan, attempt);
      log.atInfo().addKeyValue("taskId", task.id()).addKeyValue("attempt", attempt.number())
          .addKeyValue("resultCount", images.size()).log("generation image model and integrity validation completed");
      ContentModerator.Decision outputDecision = new ContentModerator.Decision(!images.isEmpty(),
          images.isEmpty() ? "IMAGE_EVALUATION_REJECTED" : null);
      for (ProviderImage image : images) {
        stage = "output_moderation";
        logStage(task.id(), attempt, stage, "started");
        ContentModerator.Decision decision = moderator.moderateOutput(task, image);
        if (!decision.approved()) {
          outputDecision = decision;
          break;
        }
      }
      if (!store.recordModeration(task.id(), "output", outputDecision)) return new Outcome(task.id(), Status.IGNORED);
      log.atInfo().addKeyValue("taskId", task.id()).addKeyValue("attempt", attempt.number())
          .addKeyValue("approved", outputDecision.approved()).addKeyValue("code", outputDecision.code())
          .log("generation output moderation completed");
      if (!outputDecision.approved()) {
        return failed(task.id(), "OUTPUT_MODERATION_REJECTED",
            "生成结果未达到质量或安全要求，额度已返还，请调整描述后重试", attempt, null);
      }

      stage = "output_persistence";
      logStage(task.id(), attempt, stage, "started");
      stored = output.persist(task, images);
      log.atInfo().addKeyValue("taskId", task.id()).addKeyValue("attempt", attempt.number())
          .addKeyValue("resultCount", stored.size()).log("generation output persisted");
      if (!store.succeed(task.id(), stored)) {
        output.cleanup(stored);
        return new Outcome(task.id(), Status.IGNORED);
      }
      metrics.recordAttempt("generation", "succeeded", "none");
      log.atInfo().addKeyValue("taskId", task.id()).addKeyValue("attempt", attempt.number())
          .log("generation task completed successfully");
      return new Outcome(task.id(), Status.SUCCEEDED);
    } catch (GenerationProviderException error) {
      output.cleanup(stored);
      boolean willRetry = error.retryable() && attempt.number() < attempt.maxAttempts();
      if (willRetry) {
        log.atWarn().addKeyValue("taskId", task.id()).addKeyValue("attempt", attempt.number())
            .addKeyValue("stage", stage).addKeyValue("errorCode", error.code())
            .addKeyValue("retryable", true).log("generation task attempt failed", error);
        throw error;
      }
      log.atError().addKeyValue("taskId", task.id()).addKeyValue("attempt", attempt.number())
          .addKeyValue("stage", stage).addKeyValue("errorCode", error.code())
          .addKeyValue("retryable", error.retryable()).log("generation task failed permanently", error);
      Map<String, Object> deadLetter = error.retryable()
          ? Map.of("provider", imageModel.getClass().getSimpleName(), "retryable", true,
              "errorCode", error.code(), "schemaVersion", job.schemaVersion())
          : null;
      String message = failureMessage(error);
      metrics.recordAttempt("generation", "failed", error.code());
      if (deadLetter != null) metrics.recordDeadLetter(error.code());
      return failed(task.id(), error.code(), message, attempt, deadLetter);
    } catch (RuntimeException error) {
      output.cleanup(stored);
      log.atError().addKeyValue("taskId", task.id()).addKeyValue("attempt", attempt.number())
          .addKeyValue("stage", stage)
          .addKeyValue("errorCode", "GENERATION_FAILED")
          .log("generation task failed unexpectedly", error);
      metrics.recordAttempt("generation", "failed", "GENERATION_FAILED");
      return failed(task.id(), "GENERATION_FAILED", "图片生成失败，额度已返还，请重新提交", attempt, null);
    }
  }

  private void logStage(String taskId, GenerationAttempt attempt, String stage, String status) {
    log.atInfo().addKeyValue("taskId", taskId).addKeyValue("attempt", attempt.number())
        .addKeyValue("stage", stage).addKeyValue("status", status).log("generation stage");
  }

  public Outcome rejectInvalidMessage(GenerationJob job, GenerationAttempt attempt) {
    return failed(job.taskId(), "QUEUE_MESSAGE_INVALID", "生成任务消息格式无效，额度已返还", attempt,
        Map.of("schemaVersion", job.schemaVersion(), "attemptNumber", attempt.number()));
  }

  private Outcome failed(String taskId, String code, String message, GenerationAttempt attempt,
      Map<String, Object> deadLetter) {
    try {
      boolean persisted = store.fail(taskId, code, message, attempt, deadLetter);
      if (!persisted) log.atError().addKeyValue("taskId", taskId).addKeyValue("attempt", attempt.number())
          .addKeyValue("errorCode", code).log("generation failure could not be persisted");
      return new Outcome(taskId, persisted ? Status.FAILED : Status.IGNORED);
    } catch (RuntimeException error) {
      log.atError().addKeyValue("taskId", taskId).addKeyValue("attempt", attempt.number())
          .addKeyValue("errorCode", code).log("generation failure persistence threw an exception", error);
      throw error;
    }
  }

  private static String failureMessage(GenerationProviderException error) {
    if (error.retryable()) return "图片生成多次失败，额度已返还，请稍后重试";
    String code = error.code();
    if (code != null && (code.contains("PROVIDER") || code.contains("TEMPORARILY_UNAVAILABLE"))) {
      return "模型服务暂时不可用，额度已返还，请稍后重试";
    }
    return "模型服务无法处理当前请求，额度已返还，请调整参数后重试";
  }

  public enum Status { SUCCEEDED, FAILED, IGNORED }
  public record Outcome(String taskId, Status status) {}
}
