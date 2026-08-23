import { beforeEach, describe, expect, it, vi } from "vitest";
import { createPinia, setActivePinia } from "pinia";
import { api, type GenerationDraft, type GenerationOptions, type GenerationSession, type GenerationTask } from "@/api/client";
import { useGenerationStore } from "./generation";

vi.mock("@/api/client", async () => {
  const actual = await vi.importActual<typeof import("@/api/client")>("@/api/client");
  return { ...actual, api: { generation: { options: vi.fn(), quota: vi.fn(), sessions: vi.fn(), session: vi.fn(), createSession: vi.fn(), draft: vi.fn(), submit: vi.fn() } } };
});

class FakeEventSource {
  static instances: FakeEventSource[] = [];
  closed = false;
  private listeners = new Map<string, (event: Event) => void>();
  constructor(public readonly url: string) { FakeEventSource.instances.push(this); }
  addEventListener(type: string, listener: (event: Event) => void) { this.listeners.set(type, listener); }
  close() { this.closed = true; }
}

const options: GenerationOptions = { modes: ["AUTO"], ratios: [{ value: "1:1", label: "1:1" }], resolutions: [{ value: "2K", label: "2K", maxEdge: 2048, maxPixels: 2048 * 2048, unitCost: 1, enabled: true }], dimensions: { minEdge: 512, step: 64 }, referenceImages: { max: 2, maxBytes: 10_000_000, mimeTypes: ["image/png"] } };
const quota = { total: 10, available: 10, reserved: 0, used: 0, remainingPercent: 100 };
const baseDraft: GenerationDraft = { mode: "AUTO", prompt: "", imageIds: [], ratio: "1:1", resolution: "2K", width: 2048, height: 2048 };
const session: GenerationSession = { id: "session-1", title: "New creation", draft: baseDraft, createdAt: "now", updatedAt: "now", tasks: [] };
const task: GenerationTask = { id: "task-1", sessionId: session.id, status: "queued", mode: "AUTO", prompt: "A tree", imageIds: [], model: "image-4.7", ratio: "1:1", resolution: "2K", width: 2048, height: 2048, imageCount: 1, unitCost: 1, totalCost: 1, idempotencyKey: "test-key-1", currentIteration: 0, createdAt: "now", updatedAt: "now", results: [] };

describe("generation submission flow", () => {
  beforeEach(() => {
    setActivePinia(createPinia());
    vi.clearAllMocks();
    FakeEventSource.instances = [];
    vi.stubGlobal("EventSource", FakeEventSource);
    vi.mocked(api.generation.options).mockResolvedValue(options);
    vi.mocked(api.generation.quota).mockResolvedValue(quota);
    vi.mocked(api.generation.sessions).mockResolvedValue({ items: [] });
    vi.mocked(api.generation.createSession).mockResolvedValue(session);
    vi.mocked(api.generation.submit).mockResolvedValue({ session: { ...session, draft: baseDraft, tasks: [task] }, task, quota, replayed: false });
  });

  it("submits without creating an empty session first", async () => {
    const store = useGenerationStore();
    await store.load();
    store.draft.prompt = "  A tree  ";
    await store.submit();

    expect(api.generation.createSession).not.toHaveBeenCalled();
    expect(api.generation.submit).toHaveBeenCalledWith(expect.objectContaining({ prompt: "A tree" }));
    expect(vi.mocked(api.generation.submit).mock.calls[0]?.[0]).not.toHaveProperty("sessionId");
    expect(store.sessions).toEqual([expect.objectContaining({ id: session.id })]);
    expect(store.draft.prompt).toBe("");
    expect(store.draft.imageIds).toEqual([]);
  });

  it("rejects a session-only response as a failed task submission", async () => {
    const store = useGenerationStore();
    await store.load();
    store.draft.prompt = "A tree";
    vi.mocked(api.generation.submit).mockResolvedValueOnce(session as never);

    await expect(store.submit()).rejects.toThrow("生成接口返回数据无效");
  });

  it("reuses the idempotency key after an unknown network failure", async () => {
    const store = useGenerationStore();
    await store.load();
    store.draft.prompt = "A tree";
    vi.mocked(api.generation.submit).mockRejectedValueOnce(new Error("network timeout"));
    await expect(store.submit()).rejects.toThrow("network timeout");
    vi.mocked(api.generation.submit).mockResolvedValueOnce({ session: { ...session, tasks: [task] }, task, quota, replayed: true });
    await store.submit();

    const calls = vi.mocked(api.generation.submit).mock.calls;
    expect(calls[0]?.[0].idempotencyKey).toBe(calls[1]?.[0].idempotencyKey);
  });

  it("keeps SSE connections for multiple active tasks", async () => {
    const store = useGenerationStore();
    await store.load();
    store.draft.prompt = "A tree";
    await store.submit();
    store.draft.prompt = "A house";
    vi.mocked(api.generation.submit).mockResolvedValueOnce({ session: { ...session, tasks: [task, { ...task, id: "task-2", prompt: "A house" }] }, task: { ...task, id: "task-2", prompt: "A house" }, quota, replayed: false });
    await store.submit();

    expect(FakeEventSource.instances).toHaveLength(2);
    expect(FakeEventSource.instances.every(source => !source.closed)).toBe(true);
  });
});
