import type { GenerationResult } from "@/api/client";

export function resultPreviewUrl(result: GenerationResult) {
  return result.thumbnailUrl;
}

export function resultDialogUrl(result: GenerationResult, original: boolean) {
  return original ? result.contentUrl : result.thumbnailUrl;
}
