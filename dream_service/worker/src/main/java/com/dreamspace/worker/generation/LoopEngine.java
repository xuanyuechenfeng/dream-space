package com.dreamspace.worker.generation;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.List;
import javax.imageio.ImageIO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Generates one image and verifies only that the provider payload is a decodable image. */
public class LoopEngine {
  private static final Logger log = LoggerFactory.getLogger(LoopEngine.class);
  private final GenerationWorkerStore store;
  private final ImageGenerationModel model;

  public LoopEngine(GenerationWorkerStore store, ImageGenerationModel model, int ignoredMaxIterations) {
    this.store = store;
    this.model = model;
  }

  /** Compatibility constructor; the quality evaluator is intentionally no longer invoked. */
  public LoopEngine(GenerationWorkerStore store, ImageGenerationModel model,
      QualityEvaluationModel ignoredEvaluator, int ignoredMaxIterations, double ignoredAcceptScore) {
    this(store, model, ignoredMaxIterations);
  }

  public List<ProviderImage> execute(WorkerTaskSnapshot task, GenerationPlanBundle plan, GenerationAttempt attempt) {
    task = plan.applyOutput(task);
    int iteration = 1;
    log.atInfo().addKeyValue("taskId", task.id()).addKeyValue("attempt", attempt.number())
        .addKeyValue("iteration", iteration).log("generation image request started");
    store.recordStage(task.id(), "task.generation_started", "image_generation", java.util.Map.of("iteration", iteration));
    ImageGenerationModel.ImageGenerationResponse response = model.generate(
        new ImageGenerationModel.ImageGenerationRequest(task, plan.promptPackage(), plan.targetImageId(),
            plan.referenceImageId(), null, iteration), attempt);
    List<ProviderImage> images = response.images() == null ? List.of() : response.images();
    validateTechnicalOutput(task, images);
    EvaluationReport integrity = new EvaluationReport(true, 1.0, List.of(), false,
        List.of("provider image decoded successfully"), "technical-integrity-v1");
    store.recordIteration(task.id(), iteration, GenerationHarness.hash(plan.promptPackage().positivePrompt()),
        "ACCEPTED", response.provider(), response.model(), response.providerRequestId(), integrity, null, null);
    store.recordStage(task.id(), "task.evaluation_completed", "integrity", java.util.Map.of("iteration", iteration,
        "score", integrity.score(), "semanticValidation", false));
    store.recordStage(task.id(), "task.generation_accepted", "accepted", java.util.Map.of("iteration", iteration));
    return images;
  }

  private static void validateTechnicalOutput(WorkerTaskSnapshot task, List<ProviderImage> images) {
    if (images.isEmpty() || images.size() > task.imageCount()) {
      throw new GenerationProviderException("PROVIDER_OUTPUT_INVALID", "image provider returned an invalid image count", false);
    }
    for (ProviderImage image : images) {
      if (image == null || image.data() == null || image.data().length == 0) {
        throw new GenerationProviderException("PROVIDER_OUTPUT_INVALID", "image provider returned empty image data", false);
      }
      try {
        if (ImageIO.read(new ByteArrayInputStream(image.data())) == null) {
          throw new IOException("image cannot be decoded");
        }
      } catch (IOException error) {
        throw new GenerationProviderException("PROVIDER_OUTPUT_INVALID", "provider image cannot be decoded", false, error);
      }
    }
  }
}
