import { beforeEach, describe, expect, it, vi } from "vitest";
import { createPinia, setActivePinia } from "pinia";
import { api, type GenerationOptions, type GenerationSession, type GenerationTask } from "@/api/client";
import { useGenerationStore } from "./generation";

vi.mock("@/api/client", async () => {
  const actual = await vi.importActual<typeof import("@/api/client")>("@/api/client");
  return {
    ...actual,
    api: { generation: {
      options: vi.fn(), quota: vi.fn(), sessions: vi.fn(), session: vi.fn(),
      createSession: vi.fn(), draft: vi.fn(), submit: vi.fn(), preflight: vi.fn(),
      preflightStatus: vi.fn(), createFromPreflight: vi.fn(), uploadReference: vi.fn(),
      task: vi.fn(), cancel: vi.fn(), retry: vi.fn(), continueMissing: vi.fn(),
    } },
  };
});

class FakeEventSource {
  onerror: ((event?: Event) => void) | null = null;
  constructor(public readonly url: string) {}
  addEventListener() {}
  close() {}
}

const options: GenerationOptions = {
  modes: ["AUTO"],
  ratios: [{ value: "1:1", label: "1:1" }],
  resolutions: [{ value: "2K", label: "2K", maxEdge: 2048, maxPixels: 2048 * 2048, unitCost: 1, enabled: true }],
  dimensions: { minEdge: 512, step: 64 },
  referenceImages: { max: 2, maxBytes: 10_000_000, mimeTypes: ["image/png"] },
  imageCounts: { defaultMode: "AUTO", min: 1, max: 4, values: [1, 2, 3, 4] },
};
const quota = { total: 20, available: 16, reserved: 0, used: 4, remainingPercent: 80 };
const original: GenerationTask = {
  id: "task-1", sessionId: "session-1", status: "partially_succeeded", mode: "AUTO",
  prompt: "同一景色的春夏秋冬", imageIds: [], model: "image-4.7", ratio: "1:1",
  resolution: "2K", width: 2048, height: 2048, imageCount: 4, imageCountMode: "4",
  unitCost: 1, totalCost: 4, consumedCost: 2, successfulCount: 2, missingCount: 2,
  idempotencyKey: "original-key", currentIteration: 0, createdAt: "now", updatedAt: "now",
  results: [], slots: [],
};
const session: GenerationSession = {
  id: "session-1", title: "Seasons", createdAt: "now", updatedAt: "now",
  draft: { mode: "AUTO", prompt: "", imageIds: [], ratio: "1:1", resolution: "2K",
    width: 2048, height: 2048, imageCountMode: "AUTO", imageCount: null },
  tasks: [original],
};

describe("multi-image regeneration", () => {
  beforeEach(() => {
    setActivePinia(createPinia());
    vi.resetAllMocks();
    vi.stubGlobal("EventSource", FakeEventSource);
    vi.stubGlobal("window", globalThis);
    vi.mocked(api.generation.options).mockResolvedValue(options);
    vi.mocked(api.generation.quota).mockResolvedValue(quota);
    vi.mocked(api.generation.sessions).mockResolvedValue({ items: [] });
    vi.mocked(api.generation.session).mockResolvedValue(session);
  });

  it("regenerates all through ordinary preflight and creates an independent task", async () => {
    const regenerated = { ...original, id: "task-2", status: "queued", consumedCost: 0,
      successfulCount: 0, missingCount: 4, idempotencyKey: "new-key" };
    vi.mocked(api.generation.preflight).mockResolvedValue({
      status: "ready", planToken: "plan-token", expiresAt: "2099-01-01T00:00:00Z",
      collectionMode: "DIMENSIONAL", targetImageCount: 4,
      slots: [0, 1, 2, 3].map(index => ({ index, label: `季节 ${index + 1}`, role: "SEASON" })),
      output: { ratio: "1:1", resolution: "2K", width: 2048, height: 2048 },
      unitCost: 1, estimatedCost: 4, warnings: [],
    });
    vi.mocked(api.generation.createFromPreflight).mockResolvedValue({
      session: { ...session, tasks: [regenerated, original] }, task: regenerated, quota, replayed: false,
    });
    const store = useGenerationStore();
    await store.load("session-1");

    await store.regenerateAll("task-1");

    const preflight = vi.mocked(api.generation.preflight).mock.calls[0]?.[0];
    expect(preflight).toEqual(expect.objectContaining({
      sessionId: "session-1", prompt: original.prompt, imageCountMode: "4", imageCount: 4,
    }));
    expect(preflight).not.toHaveProperty("sourceTaskId");
    expect(preflight).not.toHaveProperty("taskId");
    expect(api.generation.createFromPreflight).toHaveBeenCalledWith({
      idempotencyKey: expect.any(String), planToken: "plan-token",
    });
    expect(store.active?.tasks.map(task => task.id)).toEqual(["task-2", "task-1"]);
  });

  it("reuses the continuation key after a lost response", async () => {
    const continued = { ...original, status: "queued" };
    vi.mocked(api.generation.continueMissing)
      .mockRejectedValueOnce(new Error("network timeout"))
      .mockResolvedValueOnce({
        session: { ...session, tasks: [continued] }, task: continued, quota, replayed: true,
      });
    const store = useGenerationStore();
    await store.load("session-1");

    await expect(store.continueMissing("task-1")).rejects.toThrow("network timeout");
    await store.continueMissing("task-1");

    const calls = vi.mocked(api.generation.continueMissing).mock.calls;
    expect(calls).toHaveLength(2);
    expect(calls[0]?.[1]).toBe(calls[1]?.[1]);
  });
});
