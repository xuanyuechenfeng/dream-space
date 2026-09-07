import { afterEach, describe, expect, it, vi } from "vitest";
import { getCachedImageSource, getImageBlob, IMAGE_CACHE_NAME } from "./imageCache";

const originalCaches = globalThis.caches;

afterEach(() => {
  vi.restoreAllMocks();
  Object.defineProperty(globalThis, "caches", { configurable: true, value: originalCaches });
});

describe("image cache", () => {
  it("returns a cached response without fetching", async () => {
    const blob = new Blob(["cached"], { type: "image/webp" });
    const match = vi.fn().mockResolvedValue(new Response(blob, { status: 200 }));
    const open = vi.fn().mockResolvedValue({ match, put: vi.fn() });
    Object.defineProperty(globalThis, "caches", { configurable: true, value: { open } });
    const fetchSpy = vi.spyOn(globalThis, "fetch");

    await expect(getImageBlob("/api/result.webp")).resolves.toEqual(blob);
    expect(open).toHaveBeenCalledWith(IMAGE_CACHE_NAME);
    expect(match).toHaveBeenCalledWith("/api/result.webp");
    expect(fetchSpy).not.toHaveBeenCalled();
  });

  it("fetches and stores a cache miss", async () => {
    const match = vi.fn().mockResolvedValue(undefined);
    const put = vi.fn().mockResolvedValue(undefined);
    const open = vi.fn().mockResolvedValue({ match, put });
    Object.defineProperty(globalThis, "caches", { configurable: true, value: { open } });
    const response = new Response(new Blob(["fresh"], { type: "image/png" }), { status: 200 });
    const fetchSpy = vi.spyOn(globalThis, "fetch").mockResolvedValue(response);

    const result = await getImageBlob("/api/result.png");
    expect(result).toBeInstanceOf(Blob);
    expect(fetchSpy).toHaveBeenCalledWith("/api/result.png", { credentials: "include" });
    expect(put).toHaveBeenCalledWith("/api/result.png", expect.any(Response));
  });

  it("falls back cleanly when Cache Storage is unavailable", async () => {
    Object.defineProperty(globalThis, "caches", { configurable: true, value: undefined });
    vi.spyOn(globalThis, "fetch").mockResolvedValue(new Response(new Blob(["fallback"]), { status: 200 }));

    await expect(getImageBlob("/api/result.png")).resolves.toBeInstanceOf(Blob);
  });

  it("still fetches when opening Cache Storage fails", async () => {
    Object.defineProperty(globalThis, "caches", { configurable: true, value: { open: vi.fn().mockRejectedValue(new Error("denied")) } });
    const fetchSpy = vi.spyOn(globalThis, "fetch").mockResolvedValue(new Response(new Blob(["network"]), { status: 200 }));

    await expect(getImageBlob("/api/private-result.png")).resolves.toBeInstanceOf(Blob);
    expect(fetchSpy).toHaveBeenCalledOnce();
  });

  it("deduplicates concurrent reads for the same URL", async () => {
    Object.defineProperty(globalThis, "caches", { configurable: true, value: undefined });
    const fetchSpy = vi.spyOn(globalThis, "fetch").mockResolvedValue(new Response(new Blob(["shared"]), { status: 200 }));

    const [first, second] = await Promise.all([getImageBlob("/api/shared.png"), getImageBlob("/api/shared.png")]);
    expect(first).toEqual(second);
    expect(fetchSpy).toHaveBeenCalledOnce();
  });

  it("returns the original URL when the image request fails", async () => {
    Object.defineProperty(globalThis, "caches", { configurable: true, value: undefined });
    vi.spyOn(globalThis, "fetch").mockResolvedValue(new Response(null, { status: 503 }));

    await expect(getCachedImageSource("/api/unavailable.png")).resolves.toEqual({ src: "/api/unavailable.png", revokeObjectUrl: false });
  });
});
