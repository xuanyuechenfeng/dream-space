import { beforeEach, describe, expect, it, vi } from "vitest";
import { createPinia, setActivePinia } from "pinia";
import { api, type GenerationDraft, type GenerationOptions, type GenerationSession, type GenerationSubmitResponse, type GenerationTask } from "@/api/client";
import { useGenerationStore } from "./generation";

vi.mock("@/api/client", async () => {
  const actual = await vi.importActual<typeof import("@/api/client")>("@/api/client");
  return { ...actual, api: { generation: { options: vi.fn(), quota: vi.fn(), sessions: vi.fn(), session: vi.fn(), createSession: vi.fn(), renameSession: vi.fn(), draft: vi.fn(), deleteSession: vi.fn(), submit: vi.fn(), preflight: vi.fn(), preflightStatus: vi.fn(), createFromPreflight: vi.fn(), continueMissing: vi.fn(), uploadReference: vi.fn(), task: vi.fn(), cancel: vi.fn(), retry: vi.fn(), regenerateAll: vi.fn() } } };
});

class FakeEventSource {
  static instances: FakeEventSource[] = [];
  closed = false;
  onerror: ((event?: Event) => void) | null = null;
  private listeners = new Map<string, (event: Event) => void | Promise<void>>();
  constructor(public readonly url: string) { FakeEventSource.instances.push(this); }
  addEventListener(type: string, listener: (event: Event) => void | Promise<void>) { this.listeners.set(type, listener); }
  async emit(type: string, event: Event) { await this.listeners.get(type)?.(event); }
  emitError() { this.onerror?.(new Event("error")); }
  close() { this.closed = true; }
}

const options: GenerationOptions = { modes: ["AUTO"], ratios: [{ value: "1:1", label: "1:1" }], resolutions: [{ value: "2K", label: "2K", maxEdge: 2048, maxPixels: 2048 * 2048, unitCost: 1, enabled: true }], dimensions: { minEdge: 512, step: 64 }, referenceImages: { max: 2, maxBytes: 10_000_000, mimeTypes: ["image/png"] } };
const quota = { total: 10, available: 10, reserved: 0, used: 0, remainingPercent: 100 };
const baseDraft: GenerationDraft = { mode: "AUTO", prompt: "", imageIds: [], ratio: "1:1", resolution: "2K", width: 2048, height: 2048 };
const session: GenerationSession = { id: "session-1", title: "New creation", draft: baseDraft, createdAt: "now", updatedAt: "now", tasks: [] };
const task: GenerationTask = { id: "task-1", sessionId: session.id, status: "queued", mode: "AUTO", prompt: "A tree", imageIds: [], model: "image-4.7", ratio: "1:1", resolution: "2K", width: 2048, height: 2048, imageCount: 1, unitCost: 1, totalCost: 1, idempotencyKey: "test-key-1", currentIteration: 0, createdAt: "now", updatedAt: "now", results: [] };

function deferred<T>() {
  let resolve!: (value: T) => void;
  let reject!: (reason?: unknown) => void;
  const promise = new Promise<T>((resolvePromise, rejectPromise) => { resolve = resolvePromise; reject = rejectPromise; });
  return { promise, resolve, reject };
}

describe("generation submission flow", () => {
  beforeEach(() => {
    setActivePinia(createPinia());
    vi.resetAllMocks();
    FakeEventSource.instances = [];
    vi.stubGlobal("EventSource", FakeEventSource);
    vi.stubGlobal("window", globalThis);
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

  it("keeps the base route empty even when history exists", async () => {
    const historical: GenerationSession = { ...session, id: "history-1", title: "Earlier creation", tasks: [task] };
    vi.mocked(api.generation.sessions).mockResolvedValueOnce({ items: [{ id: historical.id, title: historical.title, thumbnailUrl: null, createdAt: historical.createdAt, updatedAt: historical.updatedAt }] });
    vi.mocked(api.generation.session).mockResolvedValueOnce(historical);

    const store = useGenerationStore();
    await store.load();

    expect(store.active).toBeNull();
    expect(api.generation.session).not.toHaveBeenCalled();
    expect(api.generation.createSession).not.toHaveBeenCalled();
    expect(store.sessions).toEqual([expect.objectContaining({ id: historical.id })]);
  });

  it("loads only the explicitly requested historical session", async () => {
    const requested = { ...session, id: "requested", tasks: [{ ...task, sessionId: "requested" }] };
    vi.mocked(api.generation.session).mockResolvedValueOnce(requested);
    const store = useGenerationStore();

    await store.load("requested");

    expect(api.generation.session).toHaveBeenCalledTimes(1);
    expect(api.generation.session).toHaveBeenCalledWith("requested");
    expect(store.active?.id).toBe("requested");
  });

  it("resets a conversation locally without creating an empty history row", async () => {
    const summary = { id: session.id, title: session.title, thumbnailUrl: null, createdAt: session.createdAt, updatedAt: session.updatedAt };
    vi.mocked(api.generation.sessions).mockResolvedValueOnce({ items: [summary] });
    vi.mocked(api.generation.session).mockResolvedValueOnce({ ...session, tasks: [task] });
    const store = useGenerationStore();
    await store.load("session-1");
    store.draft.prompt = "Draft text";
    store.startNewSession();

    expect(store.active).toBeNull();
    expect(store.draft.prompt).toBe("");
    expect(store.sessions).toEqual([summary]);
    expect(api.generation.createSession).not.toHaveBeenCalled();
    expect(FakeEventSource.instances.every(source => source.closed)).toBe(true);
  });

  it("removes the session from the server and reconciles history", async () => {
    const summary = { id: session.id, title: session.title, thumbnailUrl: null, createdAt: session.createdAt, updatedAt: session.updatedAt };
    vi.mocked(api.generation.sessions)
      .mockResolvedValueOnce({ items: [summary] })
      .mockResolvedValueOnce({ items: [] });
    vi.mocked(api.generation.session).mockResolvedValueOnce({ ...session, tasks: [task] });
    vi.mocked(api.generation.deleteSession).mockResolvedValueOnce(undefined);
    const store = useGenerationStore();

    await store.load(session.id);
    await store.removeSession(session.id);

    expect(api.generation.deleteSession).toHaveBeenCalledWith(session.id);
    expect(api.generation.sessions).toHaveBeenCalledTimes(2);
    expect(store.sessions).toEqual([]);
    expect(store.active).toBeNull();
  });

  it("does not resurrect a session when its load resolves after a reset", async () => {
    const pending = deferred<GenerationSession>();
    vi.mocked(api.generation.session).mockReturnValueOnce(pending.promise);
    const store = useGenerationStore();
    const loading = store.load("session-1");
    await vi.waitFor(() => expect(api.generation.session).toHaveBeenCalledWith("session-1"));
    store.startNewSession();
    pending.resolve({ ...session, tasks: [task] });
    await loading;

    expect(store.active).toBeNull();
    expect(store.loading).toBe(false);
    expect(FakeEventSource.instances).toHaveLength(0);
  });

  it("keeps global generation resources when a reset wins during initial loading", async () => {
    const pendingOptions = deferred<GenerationOptions>();
    const pendingQuota = deferred<typeof quota>();
    const pendingSessions = deferred<Awaited<ReturnType<typeof api.generation.sessions>>>();
    const summary = { id: session.id, title: session.title, thumbnailUrl: null, createdAt: session.createdAt, updatedAt: session.updatedAt };
    vi.mocked(api.generation.options).mockReturnValueOnce(pendingOptions.promise);
    vi.mocked(api.generation.quota).mockReturnValueOnce(pendingQuota.promise);
    vi.mocked(api.generation.sessions).mockReturnValueOnce(pendingSessions.promise);
    const store = useGenerationStore();

    const loading = store.load(session.id);
    store.startNewSession();
    pendingOptions.resolve(options);
    pendingQuota.resolve(quota);
    pendingSessions.resolve({ items: [summary] });
    await loading;

    expect(store.options).toEqual(options);
    expect(store.quota).toEqual(quota);
    expect(store.sessions).toEqual([summary]);
    expect(store.active).toBeNull();
    expect(api.generation.session).not.toHaveBeenCalled();
  });

  it("keeps the newest explicitly opened session when requests resolve out of order", async () => {
    const first = deferred<GenerationSession>();
    const second = deferred<GenerationSession>();
    vi.mocked(api.generation.session)
      .mockImplementationOnce(() => first.promise)
      .mockImplementationOnce(() => second.promise);
    const store = useGenerationStore();
    const firstLoad = store.openSession("first");
    const secondLoad = store.openSession("second");
    first.resolve({ ...session, id: "first", tasks: [{ ...task, sessionId: "first" }] });
    await firstLoad;
    expect(store.active).toBeNull();
    second.resolve({ ...session, id: "second", tasks: [{ ...task, sessionId: "second" }] });
    await secondLoad;

    expect(store.active?.id).toBe("second");
    expect(store.loading).toBe(false);
  });

  it("keeps a new-session reset empty when an earlier submit resolves later", async () => {
    const pending = deferred<GenerationSubmitResponse>();
    vi.mocked(api.generation.submit).mockReturnValueOnce(pending.promise);
    const store = useGenerationStore();
    await store.load();
    store.draft.prompt = "A tree";
    const submission = store.submit();
    await Promise.resolve();
    store.startNewSession();
    pending.resolve({ session: { ...session, tasks: [task] }, task, quota, replayed: false });
    await submission;

    expect(store.active).toBeNull();
    expect(store.draft.prompt).toBe("");
    expect(FakeEventSource.instances).toHaveLength(0);
  });

  it("does not attach an upload that resolves after starting a new session", async () => {
    const pending = deferred<Awaited<ReturnType<typeof api.generation.uploadReference>>>();
    vi.mocked(api.generation.session).mockResolvedValueOnce(session);
    vi.mocked(api.generation.uploadReference).mockReturnValueOnce(pending.promise);
    const store = useGenerationStore();
    await store.load(session.id);

    const upload = store.uploadReference(new File(["image"], "reference.png", { type: "image/png" }));
    await vi.waitFor(() => expect(api.generation.uploadReference).toHaveBeenCalledTimes(1));
    store.startNewSession();
    pending.resolve({ id: "reference-1", url: "/reference-1", filename: "reference.png", mimeType: "image/png", width: 32, height: 32, byteSize: 5, checksumSha256: "checksum" });

    await expect(upload).resolves.toBe(false);
    expect(store.active).toBeNull();
    expect(store.draft.imageIds).toEqual([]);
    expect(api.generation.draft).not.toHaveBeenCalled();
  });

  it("ignores an SSE task refresh that resolves after starting a new session", async () => {
    const pending = deferred<GenerationTask>();
    vi.mocked(api.generation.session).mockResolvedValueOnce({ ...session, tasks: [task] });
    vi.mocked(api.generation.task).mockReturnValueOnce(pending.promise);
    const store = useGenerationStore();
    await store.load(session.id);
    const source = FakeEventSource.instances[0];
    expect(source).toBeDefined();

    const emission = source!.emit("task.generating", { lastEventId: "1" } as MessageEvent);
    await vi.waitFor(() => expect(api.generation.task).toHaveBeenCalledWith(task.id));
    store.startNewSession();
    pending.resolve({ ...task, status: "generating" });

    await expect(emission).resolves.toBeUndefined();
    expect(store.active).toBeNull();
    expect(source!.closed).toBe(true);
  });

  it("keeps the newest SSE task refresh when responses resolve out of order", async () => {
    const older = deferred<GenerationTask>();
    const newer = deferred<GenerationTask>();
    vi.mocked(api.generation.session).mockResolvedValueOnce({ ...session, tasks: [task] });
    vi.mocked(api.generation.task)
      .mockImplementationOnce(() => older.promise)
      .mockImplementationOnce(() => newer.promise);
    const store = useGenerationStore();
    await store.load(session.id);
    vi.mocked(api.generation.quota).mockClear();
    const source = FakeEventSource.instances[0]!;

    const olderEmission = source.emit("task.generating", { lastEventId: "1" } as MessageEvent);
    await vi.waitFor(() => expect(api.generation.task).toHaveBeenCalledTimes(1));
    const newerEmission = source.emit("task.succeeded",
      { type: "task.succeeded", lastEventId: "2" } as MessageEvent);
    await vi.waitFor(() => expect(api.generation.task).toHaveBeenCalledTimes(2));
    newer.resolve({ ...task, status: "succeeded" });
    await newerEmission;
    older.resolve({ ...task, status: "generating" });
    await olderEmission;

    expect(store.active?.tasks[0]?.status).toBe("succeeded");
    await vi.waitFor(() => expect(api.generation.quota).toHaveBeenCalledTimes(1));
    expect(source.closed).toBe(true);
    store.connectEvents(task.id);
    expect(FakeEventSource.instances.at(-1)?.url).toContain("after=2");
  });

  it("reuses an SSE cursor when the corresponding task refresh fails", async () => {
    vi.useFakeTimers();
    try {
      vi.mocked(api.generation.session).mockResolvedValueOnce({ ...session, tasks: [task] });
      vi.mocked(api.generation.task)
        .mockRejectedValueOnce(new Error("temporary task refresh failure"))
        .mockResolvedValueOnce({ ...task, status: "succeeded" });
      const store = useGenerationStore();
      await store.load(session.id);
      const firstSource = FakeEventSource.instances[0]!;

      await firstSource.emit("task.succeeded", { lastEventId: "7" } as MessageEvent);
      expect(firstSource.closed).toBe(true);
      await vi.advanceTimersByTimeAsync(1000);

      const reconnected = FakeEventSource.instances[1]!;
      expect(reconnected.url).toContain("after=0");
      await reconnected.emit("task.succeeded", { lastEventId: "7" } as MessageEvent);
      expect(store.active?.tasks[0]?.status).toBe("succeeded");
      expect(reconnected.closed).toBe(true);
    } finally {
      vi.useRealTimers();
    }
  });

  it("cancels a pending SSE reconnect when the conversation is reset", async () => {
    vi.useFakeTimers();
    try {
      vi.mocked(api.generation.session).mockResolvedValueOnce({ ...session, tasks: [task] });
      const store = useGenerationStore();
      await store.load(session.id);
      const source = FakeEventSource.instances[0]!;

      source.emitError();
      expect(source.closed).toBe(true);
      store.startNewSession();
      await vi.advanceTimersByTimeAsync(1000);

      expect(FakeEventSource.instances).toHaveLength(1);
      expect(store.active).toBeNull();
    } finally {
      vi.useRealTimers();
    }
  });

  it("ignores a cancellation that resolves after starting a new session", async () => {
    const pending = deferred<GenerationTask>();
    vi.mocked(api.generation.session).mockResolvedValueOnce({ ...session, tasks: [task] });
    vi.mocked(api.generation.cancel).mockReturnValueOnce(pending.promise);
    const store = useGenerationStore();
    await store.load(session.id);

    const cancellation = store.cancel(task.id);
    await vi.waitFor(() => expect(api.generation.cancel).toHaveBeenCalledWith(task.id));
    store.startNewSession();
    pending.resolve({ ...task, status: "cancelled" });

    await expect(cancellation).resolves.toBeUndefined();
    expect(store.active).toBeNull();
    expect(api.generation.quota).toHaveBeenCalledTimes(1);
  });

  it("ignores a retry that resolves after starting a new session", async () => {
    const pending = deferred<GenerationSubmitResponse>();
    vi.mocked(api.generation.session).mockResolvedValueOnce({ ...session, tasks: [task] });
    vi.mocked(api.generation.retry).mockReturnValueOnce(pending.promise);
    const store = useGenerationStore();
    await store.load(session.id);

    const retrying = store.retry(task.id);
    await vi.waitFor(() => expect(api.generation.retry).toHaveBeenCalledWith(task.id));
    store.startNewSession();
    pending.resolve({ session: { ...session, tasks: [{ ...task, status: "generating" }] }, task: { ...task, status: "generating" }, quota: { ...quota, available: 9, reserved: 1 }, replayed: false });

    await expect(retrying).resolves.toBeUndefined();
    expect(store.active).toBeNull();
    expect(store.quota).toEqual(quota);
    expect(FakeEventSource.instances).toHaveLength(1);
    expect(FakeEventSource.instances.every(source => source.closed)).toBe(true);
  });

  it("does not allow duplicate retry requests for the same task", async () => {
    const failedTask = { ...task, status: "failed" };
    const pending = deferred<GenerationSubmitResponse>();
    vi.mocked(api.generation.session).mockResolvedValueOnce({ ...session, tasks: [failedTask] });
    vi.mocked(api.generation.retry).mockReturnValueOnce(pending.promise);
    const store = useGenerationStore();
    await store.load(session.id);

    const firstRetry = store.retry(task.id);
    const duplicateRetry = store.retry(task.id);
    expect(api.generation.retry).toHaveBeenCalledTimes(1);
    expect(store.retryingTaskIds.has(task.id)).toBe(true);
    pending.resolve({ session: { ...session, tasks: [{ ...task, status: "generating" }] }, task: { ...task, status: "generating" }, quota: { ...quota, available: 9, reserved: 1 }, replayed: false });
    await Promise.all([firstRetry, duplicateRetry]);

    expect(store.retryingTaskIds.has(task.id)).toBe(false);
    expect(FakeEventSource.instances).toHaveLength(1);
  });

  it("clears stale content and reports explicit-session load failures", async () => {
    const store = useGenerationStore();
    await store.load();
    store.active = { ...session, tasks: [task] };
    vi.mocked(api.generation.session).mockRejectedValueOnce(new Error("session unavailable"));

    await expect(store.openSession("missing")).rejects.toThrow("session unavailable");
    expect(store.active).toBeNull();
    expect(store.error).toBe("session unavailable");
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
    expect(store.restorePendingSubmission()).toBe(true);
    vi.mocked(api.generation.submit).mockResolvedValueOnce({ session: { ...session, tasks: [task] }, task, quota, replayed: true });
    await store.submit();

    const calls = vi.mocked(api.generation.submit).mock.calls;
    expect(calls[0]?.[0].idempotencyKey).toBe(calls[1]?.[0].idempotencyKey);
  });

  it("reuses the idempotency key after an unknown server failure", async () => {
    const store = useGenerationStore();
    await store.load();
    store.draft.prompt = "A tree";
    vi.mocked(api.generation.submit).mockRejectedValueOnce(Object.assign(new Error("publish failed"), { status: 500 }));
    await expect(store.submit()).rejects.toThrow("publish failed");
    expect(store.restorePendingSubmission()).toBe(true);
    vi.mocked(api.generation.submit).mockResolvedValueOnce({ session: { ...session, tasks: [task] }, task, quota, replayed: true });
    await store.submit();

    const calls = vi.mocked(api.generation.submit).mock.calls;
    expect(calls[0]?.[0].idempotencyKey).toBe(calls[1]?.[0].idempotencyKey);
  });

  it("reuses the ready plan token when preflight task creation loses its response", async () => {
    vi.mocked(api.generation.options).mockResolvedValueOnce({ ...options,
      imageCounts: { defaultMode: "AUTO", min: 1, max: 4, values: [1, 2, 3, 4] } });
    vi.mocked(api.generation.preflight).mockResolvedValueOnce({ status: "ready", planToken: "signed-plan",
      expiresAt: "2099-01-01T00:00:00Z", collectionMode: "VARIATIONS", targetImageCount: 1,
      slots: [{ index: 0, label: "Image 1", role: "VARIATION" }],
      output: { ratio: "1:1", resolution: "2K", width: 2048, height: 2048 },
      unitCost: 1, estimatedCost: 1, warnings: [] });
    vi.mocked(api.generation.createFromPreflight)
      .mockRejectedValueOnce(new Error("network timeout"))
      .mockResolvedValueOnce({ session: { ...session, tasks: [task] }, task, quota, replayed: true });
    const store = useGenerationStore();
    await store.load();
    store.draft.prompt = "A tree";

    await expect(store.submit()).rejects.toThrow("network timeout");
    expect(store.restorePendingSubmission()).toBe(true);
    await store.submit();

    expect(api.generation.preflight).toHaveBeenCalledTimes(1);
    const calls = vi.mocked(api.generation.createFromPreflight).mock.calls;
    expect(calls).toHaveLength(2);
    expect(calls[0]?.[0]).toEqual(calls[1]?.[0]);
  });

  it("exposes a preparing submission immediately and replaces it after task creation", async () => {
    vi.mocked(api.generation.options).mockResolvedValueOnce({ ...options,
      imageCounts: { defaultMode: "AUTO", min: 1, max: 4, values: [1, 2, 3, 4] } });
    const pending = deferred<Awaited<ReturnType<typeof api.generation.preflight>>>();
    vi.mocked(api.generation.preflight).mockReturnValueOnce(pending.promise);
    vi.mocked(api.generation.createFromPreflight).mockResolvedValueOnce({
      session: { ...session, draft: { ...baseDraft, prompt: "Generate four related posters" }, tasks: [task] }, task, quota, replayed: false,
    });
    const store = useGenerationStore();
    await store.load();
    store.draft.prompt = "  Generate four related posters  ";
    store.draft.imageCountMode = "AUTO";
    store.draft.imageCount = null;

    const submission = store.submit();

    expect(store.pendingSubmission).toEqual(expect.objectContaining({
      prompt: "Generate four related posters", imageCountMode: "AUTO", status: "preparing",
    }));
    expect(store.draft.prompt).toBe("");
    expect(store.active).toBeNull();
    expect(store.quota).toEqual(quota);

    pending.resolve({ status: "ready", planToken: "signed-plan", expiresAt: "2099-01-01T00:00:00Z",
      collectionMode: "VARIATIONS", targetImageCount: 4,
      slots: [0, 1, 2, 3].map(index => ({ index, label: `Image ${index + 1}`, role: "VARIATION" })),
      output: { ratio: "1:1", resolution: "2K", width: 2048, height: 2048 },
      unitCost: 1, estimatedCost: 4, warnings: [] });
    await submission;

    expect(store.pendingSubmission).toBeNull();
    expect(store.active?.id).toBe(session.id);
    expect(store.draft.prompt).toBe("");
  });

  it("restores the submitted prompt for editing after preparation fails", async () => {
    vi.mocked(api.generation.options).mockResolvedValueOnce({ ...options,
      imageCounts: { defaultMode: "AUTO", min: 1, max: 4, values: [1, 2, 3, 4] } });
    vi.mocked(api.generation.preflight).mockResolvedValueOnce({
      id: "preflight-failed", status: "failed", eventsUrl: "/events/preflight-failed",
      errorCode: "PLANNING_OUTPUT_INVALID", errorDetails: "集合规划输出格式不兼容",
    });
    const store = useGenerationStore();
    await store.load();
    store.draft.prompt = "Generate related posters";

    await expect(store.submit()).rejects.toThrow("生成准备失败：集合规划输出格式不兼容");
    expect(store.draft.prompt).toBe("");
    expect(store.restorePendingSubmission()).toBe(true);
    expect(store.draft.prompt).toBe("Generate related posters");
    expect(store.pendingSubmission).toBeNull();
  });

  it("waits for preflight SSE and exposes frozen planning warnings", async () => {
    vi.mocked(api.generation.options).mockResolvedValueOnce({ ...options,
      imageCounts: { defaultMode: "AUTO", min: 1, max: 4, values: [1, 2, 3, 4] } });
    vi.mocked(api.generation.preflight).mockResolvedValueOnce({
      id: "preflight-1", status: "queued", eventsUrl: "/api/dream_web/generation/preflights/preflight-1/events",
    });
    vi.mocked(api.generation.preflightStatus).mockResolvedValueOnce({
      status: "ready", planToken: "signed-plan", expiresAt: "2099-01-01T00:00:00Z",
      collectionMode: "VARIATIONS", targetImageCount: 3,
      slots: [0, 1, 2].map(index => ({ index, label: `Image ${index + 1}`, role: "VARIATION" })),
      output: { ratio: "1:1", resolution: "2K", width: 2048, height: 2048 },
      unitCost: 1, estimatedCost: 3, warnings: ["Using 3 images instead of the 2 requested in the prompt"],
    });
    vi.mocked(api.generation.createFromPreflight).mockResolvedValueOnce({
      session: { ...session, draft: baseDraft, tasks: [task] }, task, quota, replayed: false,
    });
    const store = useGenerationStore();
    await store.load();
    store.draft.prompt = "Generate two posters";
    store.draft.imageCountMode = "3";
    store.draft.imageCount = 3;

    const submission = store.submit();
    await vi.waitFor(() => expect(FakeEventSource.instances.some(source => source.url.includes("/preflights/"))).toBe(true));
    const preflightSource = FakeEventSource.instances.find(source => source.url.includes("/preflights/"))!;
    await preflightSource.emit("preflight.ready", new Event("preflight.ready"));
    await submission;

    expect(preflightSource.url).toBe("/api/dream_web/generation/preflights/preflight-1/events");
    expect(preflightSource.closed).toBe(true);
    expect(api.generation.preflightStatus).toHaveBeenCalledWith("preflight-1");
    expect(store.notice).toBe("Using 3 images instead of the 2 requested in the prompt");
  });

  it("continues waiting past the former client timeout until the server is ready", async () => {
    vi.useFakeTimers();
    try {
      vi.mocked(api.generation.options).mockResolvedValueOnce({ ...options,
        imageCounts: { defaultMode: "AUTO", min: 1, max: 4, values: [1, 2, 3, 4] } });
      vi.mocked(api.generation.preflight).mockResolvedValueOnce({
        id: "preflight-1", status: "queued", eventsUrl: "/api/dream_web/generation/preflights/preflight-1/events",
      });
      let ready = false;
      vi.mocked(api.generation.preflightStatus).mockImplementation(async () => ready ? {
        status: "ready", planToken: "signed-plan", expiresAt: "2099-01-01T00:00:00Z",
        collectionMode: "VARIATIONS", targetImageCount: 1,
        slots: [{ index: 0, label: "Image 1", role: "VARIATION" }],
        output: { ratio: "1:1", resolution: "2K", width: 2048, height: 2048 },
        unitCost: 1, estimatedCost: 1, warnings: [],
      } : {
        id: "preflight-1", status: "planning", eventsUrl: "/api/dream_web/generation/preflights/preflight-1/events",
      });
      vi.mocked(api.generation.createFromPreflight).mockResolvedValueOnce({
        session: { ...session, draft: baseDraft, tasks: [task] }, task, quota, replayed: false,
      });
      const store = useGenerationStore();
      await store.load();
      store.draft.prompt = "Generate four related posters";

      const submission = store.submit();
      let settled = false;
      void submission.finally(() => { settled = true; });
      expect(store.pendingSubmission).toEqual(expect.objectContaining({ status: "preparing" }));
      await vi.advanceTimersByTimeAsync(125_000);

      expect(settled).toBe(false);
      expect(store.pendingSubmission).toEqual(expect.objectContaining({ status: "preparing" }));
      ready = true;
      await vi.advanceTimersByTimeAsync(5_000);
      await submission;

      expect(settled).toBe(true);
      expect(store.pendingSubmission).toBeNull();
      expect(api.generation.preflightStatus).toHaveBeenCalledWith("preflight-1");
      expect(FakeEventSource.instances[0]?.closed).toBe(true);
    } finally {
      vi.useRealTimers();
    }
  });

  it("closes the preflight stream when the conversation changes", async () => {
    vi.mocked(api.generation.options).mockResolvedValueOnce({ ...options,
      imageCounts: { defaultMode: "AUTO", min: 1, max: 4, values: [1, 2, 3, 4] } });
    const pending = deferred<Awaited<ReturnType<typeof api.generation.preflight>>>();
    vi.mocked(api.generation.preflight).mockReturnValueOnce(pending.promise);
    const store = useGenerationStore();
    await store.load();
    store.draft.prompt = "A tree";

    const submission = store.submit();
    await vi.waitFor(() => expect(store.pendingSubmission?.status).toBe("preparing"));
    pending.resolve({ id: "preflight-1", status: "queued", eventsUrl: "/events/preflight-1" });
    await vi.waitFor(() => expect(FakeEventSource.instances).toHaveLength(1));
    const source = FakeEventSource.instances[0]!;
    store.startNewSession();

    expect(source.closed).toBe(true);
    await expect(submission).rejects.toThrow("生成准备已取消");
    expect(store.pendingSubmission).toBeNull();
  });

  it("starts a fresh preflight when retrying a terminal planning failure", async () => {
    vi.mocked(api.generation.options).mockResolvedValueOnce({ ...options,
      imageCounts: { defaultMode: "AUTO", min: 1, max: 4, values: [1, 2, 3, 4] } });
    vi.mocked(api.generation.preflight)
      .mockResolvedValueOnce({
        id: "preflight-failed", status: "failed", eventsUrl: "/events/preflight-failed",
        errorCode: "PLANNING_OUTPUT_INVALID", errorDetails: "集合规划输出格式不兼容",
      })
      .mockResolvedValueOnce({
        status: "ready", planToken: "signed-plan-retry", expiresAt: "2099-01-01T00:00:00Z",
        collectionMode: "VARIATIONS", targetImageCount: 1,
        slots: [{ index: 0, label: "Image 1", role: "VARIATION" }],
        output: { ratio: "1:1", resolution: "2K", width: 2048, height: 2048 },
        unitCost: 1, estimatedCost: 1, warnings: [],
      });
    vi.mocked(api.generation.createFromPreflight).mockResolvedValueOnce({
      session: { ...session, draft: baseDraft, tasks: [task] }, task, quota, replayed: false,
    });
    const store = useGenerationStore();
    await store.load();
    store.draft.prompt = "Generate related posters";

    await expect(store.submit()).rejects.toThrow("生成准备失败：集合规划输出格式不兼容");
    expect(store.restorePendingSubmission()).toBe(true);
    await store.submit();

    const calls = vi.mocked(api.generation.preflight).mock.calls;
    expect(calls).toHaveLength(2);
    expect(calls[0]?.[0].idempotencyKey).not.toBe(calls[1]?.[0].idempotencyKey);
  });

  it("maps an expired create response to user-facing preparation language", async () => {
    vi.mocked(api.generation.options).mockResolvedValueOnce({ ...options,
      imageCounts: { defaultMode: "AUTO", min: 1, max: 4, values: [1, 2, 3, 4] } });
    vi.mocked(api.generation.preflight).mockResolvedValueOnce({ status: "ready", planToken: "signed-plan",
      expiresAt: "2099-01-01T00:00:00Z", collectionMode: "VARIATIONS", targetImageCount: 1,
      slots: [{ index: 0, label: "Image 1", role: "VARIATION" }],
      output: { ratio: "1:1", resolution: "2K", width: 2048, height: 2048 },
      unitCost: 1, estimatedCost: 1, warnings: [] });
    vi.mocked(api.generation.createFromPreflight).mockRejectedValueOnce(
      Object.assign(new Error("预规划已过期，请重新提交"), { status: 400, code: "GENERATION_PREFLIGHT_EXPIRED" }));
    const store = useGenerationStore();
    await store.load();
    store.draft.prompt = "A tree";

    await expect(store.submit()).rejects.toThrow("预规划已过期，请重新提交");
    expect(store.pendingSubmission?.error).toBe("这次生成准备已过期，请重新提交");
  });

  it("uses a new idempotency key after explicitly starting a new conversation", async () => {
    const store = useGenerationStore();
    await store.load();
    store.draft.prompt = "A tree";
    vi.mocked(api.generation.submit).mockRejectedValueOnce(new Error("network timeout"));
    await expect(store.submit()).rejects.toThrow("network timeout");

    store.startNewSession();
    store.draft.prompt = "A tree";
    vi.mocked(api.generation.submit).mockResolvedValueOnce({ session: { ...session, tasks: [task] }, task, quota, replayed: false });
    await store.submit();

    const calls = vi.mocked(api.generation.submit).mock.calls;
    expect(calls[0]?.[0].idempotencyKey).not.toBe(calls[1]?.[0].idempotencyKey);
  });

  it("does not submit while a reference upload is in flight", async () => {
    const pending = deferred<Awaited<ReturnType<typeof api.generation.uploadReference>>>();
    vi.mocked(api.generation.uploadReference).mockReturnValueOnce(pending.promise);
    const store = useGenerationStore();
    await store.load();
    const upload = store.uploadReference(new File(["image"], "reference.png", { type: "image/png" }));
    store.draft.prompt = "A tree";

    expect(store.uploading).toBe(true);
    await expect(store.submit()).rejects.toThrow("素材正在上传");
    expect(api.generation.submit).not.toHaveBeenCalled();
    pending.resolve({ id: "reference-1", url: "/reference-1", filename: "reference.png", mimeType: "image/png", width: 32, height: 32, byteSize: 5, checksumSha256: "checksum" });
    await upload;
    expect(store.uploading).toBe(false);
    expect(store.draft.imageIds).toEqual(["reference-1"]);
  });

  it("ignores a draft save that resolves after a later submission", async () => {
    const pendingDraft = deferred<GenerationSession>();
    vi.mocked(api.generation.session).mockResolvedValueOnce(session);
    vi.mocked(api.generation.draft).mockReturnValueOnce(pendingDraft.promise);
    const store = useGenerationStore();
    await store.load(session.id);
    store.draft.prompt = "An old draft";
    const saving = store.saveDraft();
    await vi.waitFor(() => expect(api.generation.draft).toHaveBeenCalledTimes(1));

    store.draft.prompt = "A tree";
    await store.submit();
    pendingDraft.resolve({ ...session, draft: { ...baseDraft, prompt: "An old draft" }, tasks: [] });
    await saving;

    expect(store.active?.tasks).toEqual([task]);
    expect(store.draft.prompt).toBe("");
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
