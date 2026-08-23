import { describe, expect, it } from "vitest";
import { dimensionsForRatio, resizeCustomDimensions, validateDimensions } from "./generationDimensions";

const resolution = { value: "2K" as const, label: "2K", maxEdge: 2048, maxPixels: 2048 * 2048, unitCost: 1, enabled: true, disabledReason: null };

describe("generation dimensions", () => {
  it("derives stable 64-pixel dimensions for landscape and portrait ratios", () => {
    expect(dimensionsForRatio("16:9", 2048, 64)).toEqual({ width: 2048, height: 1152 });
    expect(dimensionsForRatio("9:16", 2048, 64)).toEqual({ width: 1152, height: 2048 });
  });

  it("scales a custom size to the selected resolution without changing its orientation", () => {
    expect(resizeCustomDimensions(1024, 2048, 4096, 64)).toEqual({ width: 2048, height: 4096 });
  });

  it("validates smart, step, limit and ratio constraints", () => {
    expect(validateDimensions("smart", null, null, 512, 64, resolution)).toBeNull();
    expect(validateDimensions("smart", 2048, 2048, 512, 64, resolution)).toBe("SMART_DIMENSIONS_FORBIDDEN");
    expect(validateDimensions("1:1", 2000, 2048, 512, 64, resolution)).toBe("DIMENSIONS_STEP_INVALID");
    expect(validateDimensions("16:9", 2048, 1024, 512, 64, resolution)).toBe("RATIO_MISMATCH");
    expect(validateDimensions("1:1", 2048, 2048, 512, 64, resolution)).toBeNull();
  });
});
