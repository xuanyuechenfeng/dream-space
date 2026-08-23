import type { GenerationRatio, GenerationResolution, GenerationResolutionOption } from "@/api/client";

export interface Dimensions {
  width: number;
  height: number;
}

const ratioParts: Partial<Record<GenerationRatio, readonly [number, number]>> = {
  "21:9": [21, 9],
  "16:9": [16, 9],
  "3:2": [3, 2],
  "4:3": [4, 3],
  "1:1": [1, 1],
  "3:4": [3, 4],
  "2:3": [2, 3],
  "9:16": [9, 16],
};

export function roundToStep(value: number, step: number) {
  return Math.max(step, Math.round(value / step) * step);
}

export function dimensionsForRatio(ratio: GenerationRatio, maxEdge: number, step: number): Dimensions | null {
  const parts = ratioParts[ratio];
  if (!parts) return null;
  const [ratioWidth, ratioHeight] = parts;
  if (ratioWidth >= ratioHeight) {
    return { width: maxEdge, height: roundToStep(maxEdge * ratioHeight / ratioWidth, step) };
  }
  return { width: roundToStep(maxEdge * ratioWidth / ratioHeight, step), height: maxEdge };
}

export function resizeCustomDimensions(
  width: number | null,
  height: number | null,
  maxEdge: number,
  step: number,
): Dimensions {
  if (!width || !height) return { width: maxEdge, height: maxEdge };
  const scale = maxEdge / Math.max(width, height);
  return {
    width: Math.min(maxEdge, roundToStep(width * scale, step)),
    height: Math.min(maxEdge, roundToStep(height * scale, step)),
  };
}

export function resolutionOption(
  resolutions: GenerationResolutionOption[],
  resolution: GenerationResolution,
) {
  return resolutions.find((item) => item.value === resolution) ?? null;
}

export function validateDimensions(
  ratio: GenerationRatio,
  width: number | null,
  height: number | null,
  minEdge: number,
  step: number,
  resolution: GenerationResolutionOption | null,
): string | null {
  if (ratio === "smart") return width == null && height == null ? null : "SMART_DIMENSIONS_FORBIDDEN";
  if (width == null || height == null) return "DIMENSIONS_REQUIRED";
  if (!Number.isInteger(width) || !Number.isInteger(height)) return "DIMENSIONS_INTEGER_REQUIRED";
  if (width < minEdge || height < minEdge) return "DIMENSIONS_TOO_SMALL";
  if (width % step !== 0 || height % step !== 0) return "DIMENSIONS_STEP_INVALID";
  if (!resolution) return "RESOLUTION_UNAVAILABLE";
  if (width > resolution.maxEdge || height > resolution.maxEdge || width * height > resolution.maxPixels) {
    return "DIMENSIONS_TOO_LARGE";
  }
  const parts = ratioParts[ratio];
  if (parts) {
    const [ratioWidth, ratioHeight] = parts;
    if (Math.abs(width * ratioHeight - height * ratioWidth) > step * Math.max(ratioWidth, ratioHeight)) {
      return "RATIO_MISMATCH";
    }
  }
  return null;
}
