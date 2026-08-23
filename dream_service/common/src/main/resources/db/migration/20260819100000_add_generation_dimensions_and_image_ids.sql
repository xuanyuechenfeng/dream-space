ALTER TYPE "GenerationRatio" ADD VALUE IF NOT EXISTS 'custom';

ALTER TABLE "GenerationTask"
  RENAME COLUMN "referenceImageUrls" TO "imageIds";

ALTER TABLE "GenerationTask"
  ADD COLUMN "width" INTEGER,
  ADD COLUMN "height" INTEGER;

ALTER TABLE "GenerationTask"
  ALTER COLUMN "imageIds" SET DEFAULT '[]'::JSONB,
  ALTER COLUMN "imageCount" SET DEFAULT 1;

ALTER TABLE "GenerationTask"
  DROP CONSTRAINT IF EXISTS "GenerationTask_imageCount_check",
  ADD CONSTRAINT "GenerationTask_imageCount_single_check" CHECK ("imageCount" = 1),
  ADD CONSTRAINT "GenerationTask_dimensions_check" CHECK (
    ("ratio" = 'smart' AND "width" IS NULL AND "height" IS NULL)
    OR ("ratio" <> 'smart' AND "width" IS NOT NULL AND "height" IS NOT NULL
      AND "width" >= 512 AND "height" >= 512
      AND "width" % 64 = 0 AND "height" % 64 = 0)
  );
