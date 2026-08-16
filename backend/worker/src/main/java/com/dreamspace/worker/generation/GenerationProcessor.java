package com.dreamspace.worker.generation;

import com.dreamspace.persistence.queue.GenerationJob;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class GenerationProcessor {
  private final GenerationWorkerStore store;
  private final GenerationProvider provider;
  private final GenerationOutputPipeline output;
  private final ContentModerator moderator;

  public GenerationProcessor(GenerationWorkerStore store, GenerationProvider provider,
      GenerationOutputPipeline output, ContentModerator moderator) {
    this.store = store;
    this.provider = provider;
    this.output = output;
    this.moderator = moderator;
  }

  public Outcome process(GenerationJob job, GenerationAttempt attempt) {
    WorkerTaskSnapshot task = store.start(job.taskId(), attempt).orElse(null);
    if (task == null) return new Outcome(job.taskId(), Status.IGNORED);
    List<StoredGenerationResult> stored = List.of();
    try {
      ContentModerator.Decision input = moderator.moderateInput(task);
      if (!store.recordModeration(task.id(), "input", input)) return new Outcome(task.id(), Status.IGNORED);
      if (!input.approved()) {
        return failed(task.id(), "INPUT_MODERATION_REJECTED",
            "提示词或参考内容未通过审核，请修改后重试", attempt, null);
      }

      List<ProviderImage> images = provider.generate(task, attempt);
      ContentModerator.Decision outputDecision = new ContentModerator.Decision(true, null);
      for (ProviderImage image : images) {
        ContentModerator.Decision decision = moderator.moderateOutput(task, image);
        if (!decision.approved()) { outputDecision = decision; break; }
      }
      if (!store.recordModeration(task.id(), "output", outputDecision)) return new Outcome(task.id(), Status.IGNORED);
      if (!outputDecision.approved()) {
        return failed(task.id(), "OUTPUT_MODERATION_REJECTED",
            "生成结果未通过审核，额度已返还，请调整提示词后重试", attempt, null);
      }

      stored = output.persist(task, images);
      if (!store.succeed(task.id(), stored)) {
        output.cleanup(stored);
        return new Outcome(task.id(), Status.IGNORED);
      }
      return new Outcome(task.id(), Status.SUCCEEDED);
    } catch (GenerationProviderException error) {
      output.cleanup(stored);
      if (error.retryable() && attempt.number() < attempt.maxAttempts()) throw error;
      Map<String, Object> deadLetter = error.retryable()
          ? Map.of("provider", provider.getClass().getSimpleName(), "retryable", true,
              "errorCode", error.code(), "schemaVersion", job.schemaVersion())
          : null;
      String message = error.retryable()
          ? "图片生成多次失败，额度已返还，请稍后重试"
          : "模型服务无法处理当前请求，额度已返还，请调整参数后重试";
      return failed(task.id(), error.code(), message, attempt, deadLetter);
    } catch (RuntimeException error) {
      output.cleanup(stored);
      return failed(task.id(), "GENERATION_FAILED", "图片生成失败，额度已返还，请重新提交", attempt, null);
    }
  }

  public Outcome rejectInvalidMessage(GenerationJob job, GenerationAttempt attempt) {
    return failed(job.taskId(), "QUEUE_MESSAGE_INVALID", "生成任务消息格式无效，额度已返还", attempt,
        Map.of("schemaVersion", job.schemaVersion(), "attemptNumber", attempt.number()));
  }

  private Outcome failed(String taskId, String code, String message, GenerationAttempt attempt,
      Map<String, Object> deadLetter) {
    return new Outcome(taskId, store.fail(taskId, code, message, attempt, deadLetter) ? Status.FAILED : Status.IGNORED);
  }

  public enum Status { SUCCEEDED, FAILED, IGNORED }
  public record Outcome(String taskId, Status status) {}
}
