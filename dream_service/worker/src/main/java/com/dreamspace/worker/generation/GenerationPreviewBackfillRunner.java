package com.dreamspace.worker.generation;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "dream-space.preview-backfill", name = "enabled", havingValue = "true")
public class GenerationPreviewBackfillRunner implements ApplicationRunner {
  private final GenerationPreviewBackfill backfill;
  private final int batchSize;
  private final int maxItems;
  private final String afterId;
  private final boolean dryRun;

  public GenerationPreviewBackfillRunner(GenerationPreviewBackfill backfill,
      @Value("${dream-space.preview-backfill.batch-size:50}") int batchSize,
      @Value("${dream-space.preview-backfill.max-items:10000}") int maxItems,
      @Value("${dream-space.preview-backfill.after-id:}") String afterId,
      @Value("${dream-space.preview-backfill.dry-run:true}") boolean dryRun) {
    this.backfill = backfill;
    this.batchSize = batchSize;
    this.maxItems = maxItems;
    this.afterId = afterId;
    this.dryRun = dryRun;
  }

  @Override
  public void run(ApplicationArguments args) {
    backfill.run(new GenerationPreviewBackfill.Options(batchSize, maxItems,
        afterId == null || afterId.isBlank() ? null : afterId, dryRun));
  }
}
