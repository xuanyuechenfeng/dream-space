import { describe, expect, it } from "vitest";
import type { GenerationResult } from "@/api/client";
import { resultDialogUrl, resultPreviewUrl } from "./generationResultAssets";

const result: GenerationResult = {
  id: "result-1",
  index: 0,
  contentUrl: "/generation/results/result-1/content",
  thumbnailUrl: "/generation/results/result-1/thumbnail",
  width: 2048,
  height: 2048,
  mimeType: "image/png",
  byteSize: 1_000_000,
  isAiGenerated: true,
};

describe("generation result assets", () => {
  it("uses only the thumbnail for cards and the default dialog", () => {
    expect(resultPreviewUrl(result)).toBe(result.thumbnailUrl);
    expect(resultDialogUrl(result, false)).toBe(result.thumbnailUrl);
  });

  it("returns content only after the caller explicitly requests the original", () => {
    expect(resultDialogUrl(result, true)).toBe(result.contentUrl);
  });
});
