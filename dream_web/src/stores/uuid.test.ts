import { afterEach, describe, expect, it, vi } from "vitest";
import { createUuid } from "./uuid";

describe("createUuid", () => {
  afterEach(() => vi.unstubAllGlobals());

  it("uses randomUUID when available", () => {
    vi.stubGlobal("crypto", { randomUUID: vi.fn(() => "native-uuid") });

    expect(createUuid()).toBe("native-uuid");
  });

  it("falls back to getRandomValues when randomUUID is unavailable", () => {
    vi.stubGlobal("crypto", {
      getRandomValues: (bytes: Uint8Array) => {
        bytes.set(Uint8Array.from({ length: bytes.length }, (_, index) => index));
        return bytes;
      },
    });

    expect(createUuid()).toBe("00010203-0405-4607-8809-0a0b0c0d0e0f");
  });

  it("still returns a non-empty key when Web Crypto is unavailable", () => {
    vi.stubGlobal("crypto", undefined);

    expect(createUuid()).toMatch(/^[0-9a-f]+-[0-9a-f]+-[0-9a-f]+$/);
  });
});
