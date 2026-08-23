import { afterEach, describe, expect, it, vi } from "vitest";
import { adminApi, AdminApiError, resolveAssetUrl } from "./admin";

afterEach(() => vi.restoreAllMocks());

describe("admin API contracts", () => {
  it("maps protected result paths through the API proxy", () => {
    expect(resolveAssetUrl("/manage_web/tasks/results/result-1/thumbnail")).toBe("/api/manage_web/tasks/results/result-1/thumbnail");
    expect(resolveAssetUrl("https://cdn.example.test/image.webp")).toBe("https://cdn.example.test/image.webp");
  });

  it("maps static assets to the mounted application base", () => {
    expect(resolveAssetUrl("/inspiration/portrait-01.webp")).toBe("/manage_web/inspiration/portrait-01.webp");
  });

  it("sends updatedAt for optimistic publish", async () => {
    const fetchMock = vi.spyOn(globalThis, "fetch").mockResolvedValue(new Response(JSON.stringify({ id: "i1" }), { status: 200, headers: { "Content-Type": "application/json" } }));
    await adminApi.publishInspiration({ id: "i1", updatedAt: "2026-08-17T00:00:00Z" } as never);
    const init = fetchMock.mock.calls[0]?.[1];
    expect(JSON.parse(String(init?.body))).toEqual({ updatedAt: "2026-08-17T00:00:00Z" });
    expect((init?.headers as Record<string, string>)["Content-Type"]).toBe("application/json");
  });

  it("turns an API error envelope into AdminApiError", async () => {
    vi.spyOn(globalThis, "fetch").mockResolvedValue(new Response(JSON.stringify({ code: "OPTIMISTIC_CONFLICT", message: "stale" }), { status: 409, headers: { "Content-Type": "application/json" } }));
    await expect(adminApi.inspiration("i1")).rejects.toEqual(expect.objectContaining<Partial<AdminApiError>>({ status: 409, code: "OPTIMISTIC_CONFLICT" }));
  });
});
