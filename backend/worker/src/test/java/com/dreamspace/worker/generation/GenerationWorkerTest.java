package com.dreamspace.worker.generation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.dreamspace.persistence.database.DatabaseEnums.GenerationRatio;
import com.dreamspace.persistence.database.DatabaseEnums.GenerationResolution;
import com.dreamspace.persistence.queue.GenerationJob;
import com.dreamspace.persistence.storage.ObjectStorage;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.time.Duration;
import java.util.Base64;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.retry.TransientAiException;
import com.fasterxml.jackson.databind.ObjectMapper;

class GenerationWorkerTest {
  @Test void resolvesTheSameOutputDimensionsAsTheLegacyWorker() {
    assertThat(OutputDimensions.resolve(GenerationRatio.RATIO_16_9, GenerationResolution.K2))
        .isEqualTo(new OutputDimensions(2048, 1152));
    assertThat(OutputDimensions.resolve(GenerationRatio.RATIO_9_16, GenerationResolution.K4))
        .isEqualTo(new OutputDimensions(2304, 4096));
  }

  @Test void encodesRealWebpAndCleansBothObjects() {
    MemoryStorage storage = new MemoryStorage();
    GenerationOutputPipeline pipeline = new GenerationOutputPipeline(storage);
    WorkerTaskSnapshot task = task("plain prompt", 1);

    List<StoredGenerationResult> results = pipeline.persist(task,
        List.of(new ProviderImage(0, png(), "image/png", "fixture.png")));

    StoredGenerationResult result = results.getFirst();
    assertThat(result.width()).isEqualTo(2048);
    assertThat(result.height()).isEqualTo(1152);
    assertThat(result.thumbnailWidth()).isEqualTo(480);
    assertThat(result.mimeType()).isEqualTo("image/webp");
    assertThat(storage.values.get(result.objectKey()))
        .startsWith(new byte[] {'R', 'I', 'F', 'F'})
        .containsSequence(new byte[] {'W', 'E', 'B', 'P'});
    assertThat(result.checksumSha256()).hasSize(64);

    pipeline.cleanup(results);
    assertThat(storage.values).isEmpty();
  }

  @Test void leavesRetryableFailurePendingBeforeTheLastAttempt() {
    FakeStore store = new FakeStore(task("[mock-always-retryable-error]", 1));
    GenerationProcessor processor = processor(store, new DeterministicMockProvider(0));
    GenerationJob job = new GenerationJob(store.task.id(), store.task.id() + ":1", 1, 3, 1);

    assertThatThrownBy(() -> processor.process(job, new GenerationAttempt(job.attemptKey(), 1, 3)))
        .isInstanceOf(GenerationProviderException.class)
        .extracting("retryable").isEqualTo(true);
    assertThat(store.failureCode).isNull();
  }

  @Test void deadLettersAndReleasesAfterTheLastAttempt() {
    FakeStore store = new FakeStore(task("[mock-always-retryable-error]", 3));
    GenerationProcessor processor = processor(store, new DeterministicMockProvider(0));
    GenerationJob job = new GenerationJob(store.task.id(), store.task.id() + ":3", 3, 3, 1);

    GenerationProcessor.Outcome outcome = processor.process(job, new GenerationAttempt(job.attemptKey(), 3, 3));

    assertThat(outcome.status()).isEqualTo(GenerationProcessor.Status.FAILED);
    assertThat(store.failureCode).isEqualTo("PROVIDER_TEMPORARILY_UNAVAILABLE");
    assertThat(store.deadLetter).containsEntry("retryable", true).doesNotContainKey("prompt");
  }

  @Test void mapsStructuredSpringAiOutputAndTransientFailures() {
    ChatModel model = mock(ChatModel.class);
    String encoded = Base64.getEncoder().encodeToString(png());
    when(model.call(any(Prompt.class))).thenReturn(new ChatResponse(List.of(new Generation(
        new AssistantMessage("{\"images\":[{\"index\":0,\"data\":\"" + encoded + "\"}]}")))));
    OpenAiCompatibleGenerationProvider provider = new OpenAiCompatibleGenerationProvider(
        model, new ObjectMapper(), Duration.ofSeconds(1));

    List<ProviderImage> images = provider.generate(task("plain prompt", 1), new GenerationAttempt("task-1:1", 1, 3));

    assertThat(images).singleElement().satisfies(image -> {
      assertThat(image.index()).isZero();
      assertThat(image.data()).isEqualTo(png());
    });

    when(model.call(any(Prompt.class))).thenThrow(new TransientAiException("HTTP 429"));
    assertThatThrownBy(() -> provider.generate(task("plain prompt", 1), new GenerationAttempt("task-1:1", 1, 3)))
        .isInstanceOf(GenerationProviderException.class)
        .extracting("code", "retryable")
        .containsExactly("PROVIDER_TEMPORARILY_UNAVAILABLE", true);
  }

  @Test void rejectsModeratedInputWithoutCallingTheProvider() {
    FakeStore store = new FakeStore(task("[mock-reject-input]", 1));
    GenerationProvider provider = (task, attempt) -> { throw new AssertionError("provider must not be called"); };

    GenerationProcessor.Outcome outcome = processor(store, provider).process(
        new GenerationJob("task-1", "task-1:1", 1, 3, 1), new GenerationAttempt("task-1:1", 1, 3));

    assertThat(outcome.status()).isEqualTo(GenerationProcessor.Status.FAILED);
    assertThat(store.failureCode).isEqualTo("INPUT_MODERATION_REJECTED");
  }

  @Test void removesBothObjectsWhenThumbnailWriteFails() {
    MemoryStorage storage = new MemoryStorage();
    storage.failThumbnailWrite = true;
    GenerationOutputPipeline pipeline = new GenerationOutputPipeline(storage);

    assertThatThrownBy(() -> pipeline.persist(task("plain prompt", 1),
        List.of(new ProviderImage(0, png(), "image/png", "fixture.png"))))
        .isInstanceOf(IllegalStateException.class);
    assertThat(storage.values).isEmpty();
  }

  private static GenerationProcessor processor(FakeStore store, GenerationProvider provider) {
    return new GenerationProcessor(store, provider,
        new GenerationOutputPipeline(new MemoryStorage()), new DeterministicMockContentModerator());
  }

  private static WorkerTaskSnapshot task(String prompt, int attempts) {
    return new WorkerTaskSnapshot("task-1", "user-1", "session-1", prompt, "mock",
        GenerationRatio.RATIO_16_9, GenerationResolution.K2, 1, 1, attempts);
  }

  private static byte[] png() {
    BufferedImage image = new BufferedImage(32, 32, BufferedImage.TYPE_INT_RGB);
    var graphics = image.createGraphics();
    graphics.setColor(Color.CYAN);
    graphics.fillRect(0, 0, 32, 32);
    graphics.dispose();
    try (ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
      ImageIO.write(image, "png", bytes);
      return bytes.toByteArray();
    } catch (IOException error) {
      throw new IllegalStateException(error);
    }
  }

  private static final class MemoryStorage implements ObjectStorage {
    private final Map<String, byte[]> values = new HashMap<>();
    private boolean failThumbnailWrite;
    @Override public void put(String key, byte[] data, String contentType) {
      values.put(key, data);
      if (failThumbnailWrite && key.startsWith("thumbnails/")) throw new IllegalStateException("injected write failure");
    }
    @Override public Optional<ObjectData> get(String key) {
      return Optional.ofNullable(values.get(key)).map(value -> new ObjectData(value, "image/webp"));
    }
    @Override public void delete(String key) { values.remove(key); }
  }

  private static final class FakeStore implements GenerationWorkerStore {
    private final WorkerTaskSnapshot task;
    private String failureCode;
    private Map<String, Object> deadLetter;
    private FakeStore(WorkerTaskSnapshot task) { this.task = task; }
    @Override public Optional<WorkerTaskSnapshot> start(String taskId, GenerationAttempt attempt) { return Optional.of(task); }
    @Override public boolean recordModeration(String taskId, String stage, ContentModerator.Decision decision) { return true; }
    @Override public boolean succeed(String taskId, List<StoredGenerationResult> results) { return true; }
    @Override public boolean fail(String taskId, String code, String message, GenerationAttempt attempt,
        Map<String, Object> deadLetterPayload) {
      failureCode = code;
      deadLetter = deadLetterPayload;
      return true;
    }
  }
}
