package com.dreamspace.worker.generation;

import java.util.List;

public interface ImageGenerationModel {
  ImageGenerationResponse generate(ImageGenerationRequest request, GenerationAttempt attempt);

  record ImageGenerationRequest(WorkerTaskSnapshot task, PromptPackage promptPackage,
      String targetImageId, String referenceImageId,
      RefinementPatch refinement, int iteration) {}
  record ImageGenerationResponse(List<ProviderImage> images, String provider, String model,
      String providerRequestId) {}
}
