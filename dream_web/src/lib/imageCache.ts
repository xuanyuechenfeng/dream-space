const IMAGE_CACHE_NAME = "dream-space-images-v2";
const pendingImages = new Map<string, Promise<Blob | null>>();

export interface CachedImageSource {
  src: string;
  revokeObjectUrl: boolean;
}

function isCacheableUrl(url: string) {
  return /^(https?:|\/)/i.test(url) && !url.startsWith("//");
}

async function readImageResponse(url: string): Promise<Response | null> {
  if (!isCacheableUrl(url) || typeof caches === "undefined") {
    const response = await fetch(url, { credentials: "include" });
    return response.ok ? response : null;
  }

  let cache: Cache | null = null;
  try {
    cache = await caches.open(IMAGE_CACHE_NAME);
    const cached = await cache.match(url);
    if (cached?.ok) return cached;
  } catch {
    cache = null;
  }

  const response = await fetch(url, { credentials: "include" });
  if (!response.ok) return null;
  if (cache) {
    try { await cache.put(url, response.clone()); } catch { /* Quota/cache failures must not block rendering. */ }
  }
  return response;
}

/**
 * Reads an image from Cache Storage when available, fetching and populating it
 * on a miss. Failures intentionally return null so callers can use the source
 * URL as the browser-native fallback.
 */
export async function getImageBlob(url: string): Promise<Blob | null> {
  if (!url) return null;
  const pending = pendingImages.get(url);
  if (pending) return pending;
  const request = (async () => {
    try {
      const response = await readImageResponse(url);
      return response ? await response.blob() : null;
    } catch {
      return null;
    } finally {
      pendingImages.delete(url);
    }
  })();
  pendingImages.set(url, request);
  return request;
}

export async function getCachedImageSource(url: string): Promise<CachedImageSource> {
  if (!url) return { src: "", revokeObjectUrl: false };
  const blob = await getImageBlob(url);
  if (!blob || typeof URL.createObjectURL !== "function") return { src: url, revokeObjectUrl: false };
  return { src: URL.createObjectURL(blob), revokeObjectUrl: true };
}

export function revokeCachedImageSource(source: CachedImageSource | null | undefined) {
  if (source?.revokeObjectUrl && source.src) URL.revokeObjectURL(source.src);
}

export async function evictCachedImage(url: string) {
  if (!url || typeof caches === "undefined") return;
  try { await (await caches.open(IMAGE_CACHE_NAME)).delete(url); } catch { /* Cache access is optional. */ }
}

export async function clearCachedImages() {
  if (typeof caches === "undefined") return;
  try { await caches.delete(IMAGE_CACHE_NAME); } catch { /* Cache access is optional. */ }
}

export { IMAGE_CACHE_NAME };
