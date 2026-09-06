package com.dreamspace.worker.generation;

import java.util.List;

public interface ImageGenerationModel {
  ImageGenerationResponse generate(ImageGenerationRequest request, GenerationAttempt attempt);

  record ImageGenerationRequest(WorkerTaskSnapshot task, PromptPackage promptPackage,
      String targetImageId, String referenceImageId, RefinementPatch refinement, int iteration,
      String executionId, Integer slotIndex, Integer slotAttempt, List<String> inputImageIds) {
    public ImageGenerationRequest(WorkerTaskSnapshot task, PromptPackage promptPackage,
        String targetImageId, String referenceImageId, RefinementPatch refinement, int iteration) {
      this(task, promptPackage, targetImageId, referenceImageId, refinement, iteration,
          null, null, null, List.of());
    }

    public ImageGenerationRequest {
      inputImageIds = inputImageIds == null ? List.of() : List.copyOf(inputImageIds);
    }
  }
  record ImageGenerationResponse(List<ProviderImage> images, String provider, String model,
      String providerRequestId) {}
}
