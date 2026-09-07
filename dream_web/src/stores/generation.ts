import { defineStore } from "pinia";
import { computed, ref } from "vue";
import { api, type GenerationDraft, type GenerationOptions, type GenerationQuota, type GenerationSession, type GenerationSessionSummary, type GenerationSubmitResponse, type GenerationTask, type GenerationPreflightResponse, type GenerationPreflightReady } from "@/api/client";
import { dimensionsForRatio, resolutionOption } from "@/features/generation/generationDimensions";
import { createUuid } from "./uuid";

const blankDraft = (): GenerationDraft => ({ mode: "AUTO", prompt: "", imageIds: [], ratio: "1:1", resolution: "2K", width: 2048, height: 2048, imageCountMode: "AUTO", imageCount: null });
function normalizeDraft(value?: Partial<GenerationDraft> | null): GenerationDraft {
  const next = { ...blankDraft(), ...(value ?? {}), mode: "AUTO" as const };
  next.imageIds = Array.isArray(next.imageIds) ? [...new Set(next.imageIds)].slice(0, 2) : [];
  return next;
}

function submissionSnapshot(value: GenerationDraft): GenerationDraft {
  const normalized = normalizeDraft(value);
  return { ...normalized, prompt: normalized.prompt.trim(), imageIds: [...normalized.imageIds] };
}

function submissionFingerprint(sessionId: string | undefined, sessionlessEpoch: number, value: GenerationDraft): string {
  const conversation = sessionId ? ["session", sessionId] : ["new", sessionlessEpoch];
  return JSON.stringify([conversation, value.mode, value.prompt, value.imageIds, value.ratio, value.resolution, value.width, value.height, value.imageCountMode ?? "AUTO", value.imageCount ?? null]);
}

function sortSessions(items: GenerationSessionSummary[]): GenerationSessionSummary[] {
  return [...items].sort((a, b) => {
    const created = Date.parse(a.createdAt) - Date.parse(b.createdAt);
    return created || a.id.localeCompare(b.id);
  });
}

function isDefinitiveApiError(error: unknown): boolean {
  if (typeof error !== "object" || error === null) return false;
  const status = (error as { status?: unknown }).status;
  return typeof status === "number" && status >= 400 && status < 500 && status !== 408;
}

function submissionErrorMessage(error: unknown): string {
  const code = typeof error === "object" && error !== null && "code" in error
    ? String((error as { code?: unknown }).code ?? "") : "";
  const message = error instanceof Error ? error.message : "";
  const safeMessage = message.replaceAll("预规划", "生成准备")
    .replaceAll("等待规划", "正在准备生成")
    .replaceAll("规划超时", "生成准备未完成")
    .replaceAll("临时任务", "生成任务").replaceAll("临时项", "生成任务")
    .replaceAll("不计费", "");
  if (safeMessage.startsWith("生成准备失败：") || safeMessage.startsWith("请补充图片要求：")) return safeMessage;
  if (code === "GENERATION_PREFLIGHT_EXPIRED") return "这次生成准备已过期，请重新提交";
  if (code === "GENERATION_PREFLIGHT_CONSUMED") return "这次生成已提交，请查看会话中的任务";
  if (code === "PLANNING_OUTPUT_INVALID" || code === "GENERATION_COLLECTION_PLANNING_FAILED") return "生成要求解析失败，请重试";
  return safeMessage || "生成未完成，请重试";
}

const activeTaskStatuses = new Set(["queued", "generating", "retrying"]);
const terminalTaskStatuses = new Set(["succeeded", "partially_succeeded", "failed", "cancelled", "dead_lettered"]);
const quotaChangingEventTypes = new Set(["task.result.succeeded", "task.execution.released",
  "task.succeeded", "task.partially_succeeded", "task.failed", "task.cancelled"]);

function isSubmitResponse(value: unknown): value is GenerationSubmitResponse {
  if (typeof value !== "object" || value === null) return false;
  const response = value as Partial<GenerationSubmitResponse>;
  return typeof response.session?.id === "string"
    && typeof response.task?.id === "string"
    && typeof response.task.sessionId === "string"
    && Array.isArray(response.session.tasks);
}

export interface PendingGenerationSubmission {
  id: string;
  sessionId: string;
  prompt: string;
  imageIds?: string[];
  ratio: GenerationDraft["ratio"];
  resolution: GenerationDraft["resolution"];
  width: number | null;
  height: number | null;
  imageCountMode: GenerationDraft["imageCountMode"];
  imageCount: number | null;
  status: "preparing" | "failed";
  error: string;
  startedAt: string;
  fingerprint?: string;
  idempotencyKey?: string;
  preflightId?: string;
  planToken?: string;
}

const pendingSubmissionStorageKey = "dream-space:generation:pending-submission";

function pendingSubmissionKey(sessionId: string): string {
  return `${pendingSubmissionStorageKey}:${sessionId}`;
}

function pendingStorage(): Storage | null {
  try { return typeof window === "undefined" ? null : window.sessionStorage; }
  catch { return null; }
}

function isPersistedPending(value: unknown, sessionId: string): value is PendingGenerationSubmission {
  if (typeof value !== "object" || value === null) return false;
  const pending = value as Partial<PendingGenerationSubmission>;
  return typeof pending.id === "string" && pending.id.length > 0 && pending.sessionId === sessionId;
}

function readPersistedPending(sessionId?: string): PendingGenerationSubmission | null {
  if (!sessionId) return null;
  const storage = pendingStorage();
  if (!storage) return null;
  try {
    const scoped = JSON.parse(storage.getItem(pendingSubmissionKey(sessionId)) || "null");
    if (isPersistedPending(scoped, sessionId)) return scoped;
    // Migrate the former singleton value only when its ownership is explicit.
    const legacy = JSON.parse(storage.getItem(pendingSubmissionStorageKey) || "null");
    if (!isPersistedPending(legacy, sessionId)) return null;
    storage.setItem(pendingSubmissionKey(sessionId), JSON.stringify(legacy));
    storage.removeItem(pendingSubmissionStorageKey);
    return legacy;
  } catch { return null; }
}

function persistPending(value: PendingGenerationSubmission) {
  const storage = pendingStorage();
  if (!storage) return;
  try {
    storage.setItem(pendingSubmissionKey(value.sessionId), JSON.stringify({ ...value, planToken: undefined }));
  } catch { /* session storage can be unavailable in privacy mode */ }
}

function removePersistedPending(sessionId?: string) {
  if (!sessionId) return;
  const storage = pendingStorage();
  if (!storage) return;
  try {
    storage.removeItem(pendingSubmissionKey(sessionId));
    const legacy = JSON.parse(storage.getItem(pendingSubmissionStorageKey) || "null");
    if (isPersistedPending(legacy, sessionId)) storage.removeItem(pendingSubmissionStorageKey);
  } catch { /* session storage can be unavailable in privacy mode */ }
}

export const useGenerationStore = defineStore("generation", () => {
  const options = ref<GenerationOptions | null>(null);
  const quota = ref<GenerationQuota | null>(null);
  const sessions = ref<GenerationSessionSummary[]>([]);
  const active = ref<GenerationSession | null>(null);
  const draft = ref<GenerationDraft>(blankDraft());
  const loading = ref(false);
  const submitting = ref(false);
  const uploadsInFlight = ref(0);
  const uploading = computed(() => uploadsInFlight.value > 0);
  const retryingTaskIds = ref<Set<string>>(new Set());
  const error = ref("");
  const notice = ref("");
  const pendingSubmission = ref<PendingGenerationSubmission | null>(null);
  const eventCursors = ref<Record<string, number>>({});
  const sources = new Map<string, EventSource>();
  const reconnectTimers = new Map<string, number>();
  const taskRefreshVersions = new Map<string, number>();
  let activeTaskRefreshTimer: number | undefined;
  const pendingSubmissionKeys = new Map<string, string>();
  const pendingPlanTokens = new Map<string, string>();
  const pendingContinuationKeys = new Map<string, string>();
  let sessionTransition = 0;
  let resourceLoad = 0;
  let draftWriteVersion = 0;
  let sessionlessEpoch = 0;
  let cancelPreflightWait: (() => void) | null = null;
  const estimatedCost = computed(() => (resolutionOption(options.value?.resolutions ?? [], draft.value.resolution)?.unitCost ?? 1) * (draft.value.imageCount ?? 1));

  function restorePersistedPending(sessionId = active.value?.id) {
    const persisted = readPersistedPending(sessionId);
    if (!persisted) return false;
    pendingSubmission.value = persisted;
    if (persisted.idempotencyKey && persisted.fingerprint) pendingSubmissionKeys.set(persisted.fingerprint, persisted.idempotencyKey);
    if (persisted.planToken && persisted.fingerprint) pendingPlanTokens.set(persisted.fingerprint, persisted.planToken);
    return true;
  }

  async function resumePendingSubmission(): Promise<GenerationSubmitResponse | null> {
    const sessionId = active.value?.id;
    const pending = pendingSubmission.value ?? readPersistedPending(sessionId);
    if (!pending || pending.status !== "preparing" || pending.sessionId !== sessionId) return null;
    pendingSubmission.value = pending;
    draft.value = normalizeDraft({ mode: "AUTO", prompt: pending.prompt,
      imageIds: pending.imageIds ?? [], ratio: pending.ratio, resolution: pending.resolution,
      width: pending.width ?? undefined, height: pending.height ?? undefined,
      imageCountMode: pending.imageCountMode, imageCount: pending.imageCount });
    return submit();
  }

  async function waitForPreflight(initial: GenerationPreflightResponse): Promise<GenerationPreflightReady> {
    if (initial.status === "ready" && "planToken" in initial) return initial;
    if (!("id" in initial)) throw new Error("生成准备返回数据无效，请重试");
    const terminal = new Set(["failed", "needs_clarification", "superseded", "expired", "consumed"]);
    const resolveStatus = (current: GenerationPreflightResponse): GenerationPreflightReady | null => {
      if (current.status === "ready" && "planToken" in current) return current;
      if (terminal.has(current.status)) {
        const details = ("errorDetails" in current && current.errorDetails ? current.errorDetails : "请检查描述和图片参数后重试")
          .replaceAll("预规划", "生成准备").replaceAll("等待规划", "正在准备生成")
          .replaceAll("规划超时", "生成准备未完成").replaceAll("临时任务", "生成任务")
          .replaceAll("临时项", "生成任务").replaceAll("不计费", "");
        const message = current.status === "needs_clarification"
          ? `请补充图片要求：${details}`
          : current.status === "expired" ? "生成准备已过期，请重新提交"
            : current.status === "consumed" ? "这次生成已提交，请查看会话中的任务"
              : `生成准备失败：${details}`;
        const code = "errorCode" in current && current.errorCode
          ? current.errorCode : `GENERATION_PREFLIGHT_${current.status.toUpperCase()}`;
        throw Object.assign(new Error(message), { status: 422, code });
      }
      return null;
    };
    const immediate = resolveStatus(initial);
    if (immediate) return immediate;
    return await new Promise<GenerationPreflightReady>((resolve, reject) => {
      const source = new EventSource(initial.eventsUrl, { withCredentials: true });
      let settled = false;
      let refreshing = false;
      let pollTimer: number | undefined;
      let cancel: (() => void) | null = null;
      const finish = (callback: () => void) => {
        if (settled) return;
        settled = true;
        if (pollTimer !== undefined) window.clearInterval(pollTimer);
        source.onerror = null;
        source.close();
        if (cancelPreflightWait === cancel) cancelPreflightWait = null;
        callback();
      };
      cancel = () => finish(() => reject(Object.assign(new Error("生成准备已取消"), {
        status: 499, code: "GENERATION_PREPARATION_CANCELLED",
      })));
      cancelPreflightWait = cancel;
      const refresh = async () => {
        if (settled || refreshing) return;
        refreshing = true;
        try {
          const ready = resolveStatus(await api.generation.preflightStatus(initial.id));
          if (ready) finish(() => resolve(ready));
        } catch (cause) {
          // A dropped connection or transient server error must not terminate a
          // still-running preparation. Definitive HTTP errors remain terminal.
          if (isDefinitiveApiError(cause)) finish(() => reject(cause));
        } finally {
          refreshing = false;
        }
      };
      pollTimer = window.setInterval(() => { void refresh(); }, 5_000);
      for (const type of ["preflight.ready", "preflight.needs_clarification", "preflight.failed",
        "preflight.superseded", "preflight.expired", "preflight.consumed"]) {
        source.addEventListener(type, () => { void refresh(); });
      }
      source.onerror = () => { void refresh(); };
      void refresh();
    });
  }

  function closeEvents(taskId?: string) {
    if (taskId) {
      const timer = reconnectTimers.get(taskId);
      if (timer !== undefined) window.clearTimeout(timer);
      reconnectTimers.delete(taskId);
      const source = sources.get(taskId);
      if (source) source.onerror = null;
      source?.close();
      sources.delete(taskId);
      return;
    }
    stopActiveTaskRefresh();
    for (const timer of reconnectTimers.values()) window.clearTimeout(timer);
    reconnectTimers.clear();
    for (const source of sources.values()) { source.onerror = null; source.close(); }
    sources.clear();
  }
  function stopActiveTaskRefresh() {
    if (activeTaskRefreshTimer !== undefined) window.clearInterval(activeTaskRefreshTimer);
    activeTaskRefreshTimer = undefined;
  }
  async function refreshActiveTasks() {
    const tasks = active.value?.tasks.filter(task => activeTaskStatuses.has(task.status)) ?? [];
    if (!tasks.length) {
      stopActiveTaskRefresh();
      return;
    }
    await Promise.all(tasks.map(task => refreshTask(task.id).catch(() => undefined)));
  }
  function startActiveTaskRefresh() {
    stopActiveTaskRefresh();
    if (!(active.value?.tasks.some(task => activeTaskStatuses.has(task.status)))) return;
    activeTaskRefreshTimer = window.setInterval(() => { void refreshActiveTasks(); }, 5_000);
  }
  function clearSessionState() {
    // Close only this page's local wait. The server-side preflight and its
    // persisted recovery identifiers remain available to the next store load.
    cancelPreflightWait?.();
    cancelPreflightWait = null;
    closeEvents();
    stopActiveTaskRefresh();
    draftWriteVersion += 1;
    taskRefreshVersions.clear();
    retryingTaskIds.value = new Set();
    notice.value = "";
    pendingSubmission.value = null;
    active.value = null;
    draft.value = blankDraft();
    eventCursors.value = {};
    applyOptions();
  }
  function startNewSession() {
    sessionTransition += 1;
    sessionlessEpoch += 1;
    clearSessionState();
    pendingSubmission.value = null;
    loading.value = false;
    error.value = "";
  }
  async function load(sessionId?: string) {
    const transition = ++sessionTransition;
    const resourceRequest = ++resourceLoad;
    clearSessionState();
    loading.value = true;
    try {
      const [nextOptions, nextQuota, nextSessions] = await Promise.all([api.generation.options(), api.generation.quota(), api.generation.sessions()]);
      if (resourceRequest !== resourceLoad) return;
      options.value = nextOptions; quota.value = nextQuota; sessions.value = sortSessions(nextSessions.items); applyOptions();
      if (transition !== sessionTransition) return;
      if (sessionId) {
        const session = await api.generation.session(sessionId);
        if (transition !== sessionTransition) return;
        activateSession(session);
      }
      if (pendingSubmission.value?.status === "preparing" && pendingSubmission.value.sessionId === active.value?.id) {
        void resumePendingSubmission().catch(() => { /* submit state remains recoverable in session storage */ });
      }
      error.value = "";
    } catch (e) {
      if (transition !== sessionTransition) return;
      clearSessionState();
      error.value = e instanceof Error ? e.message : "Generation unavailable";
    } finally {
      if (transition === sessionTransition) loading.value = false;
    }
  }
  function activateSession(session: GenerationSession) {
    closeEvents();
    draftWriteVersion += 1;
    taskRefreshVersions.clear();
    active.value = session; draft.value = normalizeDraft(session.draft as Partial<GenerationDraft> | null); applyOptions(); eventCursors.value = {};
    pendingSubmission.value = readPersistedPending(session.id);
    for (const task of session.tasks) if (activeTaskStatuses.has(task.status)) connectEvents(task.id);
    startActiveTaskRefresh();
  }
  async function openSession(id: string): Promise<GenerationSession | null> {
    const transition = ++sessionTransition;
    clearSessionState();
    loading.value = true;
    error.value = "";
    try {
      const session = await api.generation.session(id);
      if (transition !== sessionTransition) return null;
      activateSession(session);
      if (pendingSubmission.value?.status === "preparing") {
        void resumePendingSubmission().catch(() => { /* keep the recoverable pending state */ });
      }
      return session;
    } catch (cause) {
      if (transition !== sessionTransition) return null;
      clearSessionState();
      error.value = cause instanceof Error ? cause.message : "Generation unavailable";
      throw cause;
    } finally {
      if (transition === sessionTransition) loading.value = false;
    }
  }
  async function createSession(initialDraft: GenerationDraft = blankDraft()) { const nextDraft = normalizeDraft(initialDraft); const session = await api.generation.createSession(nextDraft); closeEvents(); sessions.value = sortSessions([{ ...session }, ...sessions.value]); active.value = session; draft.value = normalizeDraft(session.draft as Partial<GenerationDraft> | null); applyOptions(); }
  async function renameSession(id: string, title: string) {
    const transition = sessionTransition;
    const session = await api.generation.renameSession(id, title);
    if (transition !== sessionTransition) return;
    sessions.value = sessions.value.map(item => item.id === id ? { ...item, title: session.title, updatedAt: session.updatedAt } : item);
    if (active.value?.id === id) active.value = { ...active.value, title: session.title, updatedAt: session.updatedAt };
  }
  async function removeSession(id: string) {
    const transition = ++sessionTransition;
    if (active.value?.id === id) closeEvents();
    try {
      await api.generation.deleteSession(id);
    } catch (cause) {
      // A server-side activity guard is authoritative. Refresh the session
      // before surfacing the error so queued/generating tasks cannot remain
      // invisible in the timeline after a failed delete attempt.
      if (transition === sessionTransition) {
        try {
          const latest = await api.generation.session(id);
          if (transition === sessionTransition) {
            activateSession(latest);
            sessions.value = sessions.value.map(item => item.id === id
              ? { ...item, title: latest.title, updatedAt: latest.updatedAt } : item);
          }
        } catch {
          // Preserve the original delete error when the refresh also fails.
        }
      }
      throw cause;
    }
    if (transition !== sessionTransition) return;
    sessions.value = sessions.value.filter(item => item.id !== id);
    // Reconcile with the server so a stale client cannot show a deleted row
    // as if it were persisted, or retain a row after a concurrent change.
    try {
      const latest = await api.generation.sessions();
      if (transition !== sessionTransition) return;
      sessions.value = sortSessions(latest.items);
    } catch {
      // The delete already committed. Keep the optimistic removal when a
      // follow-up history refresh is temporarily unavailable.
    }
    if (active.value?.id === id) {
      active.value = null;
      draft.value = blankDraft();
      applyOptions();
      eventCursors.value = {};
    }
  }
  async function saveDraft() {
    if (!active.value || submitting.value) return;
    const transition = sessionTransition;
    const sessionId = active.value.id;
    const writeVersion = ++draftWriteVersion;
    const snapshot = normalizeDraft(draft.value);
    const session = await api.generation.draft(sessionId, snapshot);
    if (transition !== sessionTransition || active.value?.id !== sessionId || writeVersion !== draftWriteVersion) return;
    active.value = session;
    draft.value = normalizeDraft(session.draft as Partial<GenerationDraft> | null);
    sessions.value = sessions.value.map(item => item.id === session.id ? { ...item, title: session.title, updatedAt: session.updatedAt } : item);
  }
  async function uploadReference(file: File) {
    const transition = sessionTransition;
    const sessionId = active.value?.id;
    uploadsInFlight.value += 1;
    try {
      const upload = await api.generation.uploadReference(file);
      if (transition !== sessionTransition || active.value?.id !== sessionId) return false;
      draft.value.imageIds = [...draft.value.imageIds, upload.id].slice(0, options.value?.referenceImages.max ?? 2);
      await saveDraft();
      return true;
    } catch (cause) {
      if (transition !== sessionTransition || active.value?.id !== sessionId) return false;
      throw cause;
    } finally {
      uploadsInFlight.value -= 1;
    }
  }
  function applyOptions() {
    const selected = resolutionOption(options.value?.resolutions ?? [], draft.value.resolution);
    if (!selected?.enabled) {
      const fallback = options.value?.resolutions.find(item => item.enabled);
      if (fallback) draft.value.resolution = fallback.value;
    }
    if (draft.value.ratio !== "smart" && draft.value.ratio !== "custom") {
      const current = resolutionOption(options.value?.resolutions ?? [], draft.value.resolution);
      const size = current ? dimensionsForRatio(draft.value.ratio, current.maxEdge, options.value?.dimensions.step ?? 64) : null;
      if (size) Object.assign(draft.value, size);
    }
  }
  async function submit(): Promise<GenerationSubmitResponse> {
    if (uploadsInFlight.value > 0) throw new Error("素材正在上传，请稍后再生成");
    if (submitting.value) throw new Error("生成请求正在提交，请稍后重试");
    const snapshot = submissionSnapshot(draft.value);
    const transition = sessionTransition;
    submitting.value = true;
    notice.value = "";
    let fingerprint: string | null = null;
    let pendingId = "";
    try {
      let sessionId = active.value?.id;
      if (!sessionId) {
        const created = await api.generation.createSession(snapshot);
        if (transition !== sessionTransition) {
          throw Object.assign(new Error("生成准备已取消"), { status: 499, code: "GENERATION_PREPARATION_CANCELLED" });
        }
        const summary = { id: created.id, title: created.title, thumbnailUrl: created.thumbnailUrl,
          createdAt: created.createdAt, updatedAt: created.updatedAt };
        sessions.value = sortSessions([summary, ...sessions.value.filter(item => item.id !== created.id)]);
        activateSession(created);
        draft.value = normalizeDraft(snapshot);
        sessionId = created.id;
      }
      const writeVersion = ++draftWriteVersion;
      const persistedPending = pendingSubmission.value?.sessionId === sessionId ? pendingSubmission.value : null;
      fingerprint = persistedPending?.fingerprint ?? submissionFingerprint(sessionId, sessionlessEpoch, snapshot);
      const idempotencyKey = persistedPending?.idempotencyKey
        ?? pendingSubmissionKeys.get(fingerprint) ?? `web-${createUuid()}`;
      pendingSubmissionKeys.set(fingerprint, idempotencyKey);
      const persisted = persistedPending?.fingerprint === fingerprint ? persistedPending : null;
      pendingId = persistedPending?.id ?? `pending-${createUuid()}`;
      pendingSubmission.value = {
        id: pendingId, sessionId, prompt: snapshot.prompt, ratio: snapshot.ratio,
        resolution: snapshot.resolution, width: snapshot.width, height: snapshot.height,
        imageIds: [...snapshot.imageIds], imageCountMode: snapshot.imageCountMode ?? "AUTO", imageCount: snapshot.imageCount ?? null,
        status: "preparing", error: "", startedAt: new Date().toISOString(),
        fingerprint, idempotencyKey, preflightId: persisted?.preflightId, planToken: persisted?.planToken,
      };
      persistPending(pendingSubmission.value);
      draft.value.prompt = "";
      let result: GenerationSubmitResponse;
      if (options.value?.imageCounts && typeof api.generation.preflight === "function"
          && typeof api.generation.createFromPreflight === "function") {
        const countMode = snapshot.imageCountMode ?? "AUTO";
        let planToken = pendingPlanTokens.get(fingerprint);
        if (!planToken) {
          const preflight = pendingSubmission.value?.preflightId
            ? await api.generation.preflightStatus(pendingSubmission.value.preflightId)
            : await api.generation.preflight({ ...snapshot,
              imageCountMode: countMode, imageCount: countMode === "AUTO" ? null : Number(countMode),
              draftKey: fingerprint, sessionId, idempotencyKey: `preflight-${idempotencyKey}` });
          if (pendingSubmission.value?.id === pendingId && "id" in preflight) {
            pendingSubmission.value = { ...pendingSubmission.value, preflightId: preflight.id };
            persistPending(pendingSubmission.value);
          }
          const ready = await waitForPreflight(preflight);
          notice.value = ready.warnings.join("；");
          planToken = ready.planToken;
          pendingPlanTokens.set(fingerprint, planToken);
          if (pendingSubmission.value?.id === pendingId) {
            pendingSubmission.value = { ...pendingSubmission.value, planToken };
            persistPending(pendingSubmission.value);
          }
        }
        result = await api.generation.createFromPreflight({ idempotencyKey, planToken, sessionId });
      } else {
        result = await api.generation.submit({ ...snapshot, mode: "AUTO", sessionId, idempotencyKey });
      }
      if (!isSubmitResponse(result)) throw new Error("生成接口返回数据无效，请稍后重试");
      pendingSubmissionKeys.delete(fingerprint);
      pendingPlanTokens.delete(fingerprint);
      removePersistedPending(sessionId);
      if (pendingSubmission.value?.id === pendingId) pendingSubmission.value = null;
      quota.value = result.quota;
      const summary = { id: result.session.id, title: result.session.title, thumbnailUrl: result.session.thumbnailUrl, createdAt: result.session.createdAt, updatedAt: result.session.updatedAt };
      sessions.value = sortSessions(sessions.value.some(item => item.id === summary.id)
        ? sessions.value.map(item => item.id === summary.id ? { ...item, ...summary } : item)
        : [summary, ...sessions.value]);
      if (transition !== sessionTransition || writeVersion !== draftWriteVersion) return result;
      const composerPrompt = draft.value.prompt;
      active.value = result.session;
      draft.value = normalizeDraft(result.session.draft as Partial<GenerationDraft> | null);
      draft.value.prompt = composerPrompt;
      applyOptions();
      connectEvents(result.task.id);
      startActiveTaskRefresh();
      return result;
    } catch (cause) {
      if (fingerprint && isDefinitiveApiError(cause)) {
        pendingSubmissionKeys.delete(fingerprint);
        pendingPlanTokens.delete(fingerprint);
      }
      if (pendingSubmission.value?.id === pendingId && transition === sessionTransition) {
        pendingSubmission.value = { ...pendingSubmission.value, status: "failed",
          error: submissionErrorMessage(cause) };
        persistPending(pendingSubmission.value);
      }
      throw cause;
    } finally { submitting.value = false; }
  }
  async function refreshTask(id: string): Promise<GenerationTask | undefined> {
    const transition = sessionTransition;
    const sessionId = active.value?.id;
    if (!sessionId) return;
    const refreshVersion = (taskRefreshVersions.get(id) ?? 0) + 1;
    taskRefreshVersions.set(id, refreshVersion);
    const task = await api.generation.task(id);
    // EventSource callbacks can outlive a local session reset. Do not let a
    // response from the old conversation write into the new empty state.
    if (transition !== sessionTransition || active.value?.id !== sessionId || task.sessionId !== sessionId || taskRefreshVersions.get(id) !== refreshVersion) return;
    active.value.tasks = active.value.tasks.map(item => item.id === task.id ? task : item);
    return task;
  }
  function clearPendingSubmission() {
    removePersistedPending(pendingSubmission.value?.sessionId ?? active.value?.id);
    pendingSubmission.value = null;
  }
  function restorePendingSubmission() {
    if (!pendingSubmission.value) return false;
    const sessionId = pendingSubmission.value.sessionId;
    draft.value.prompt = pendingSubmission.value.prompt;
    pendingSubmission.value = null;
    removePersistedPending(sessionId);
    return true;
  }
  async function cancel(id: string) {
    const transition = sessionTransition;
    const sessionId = active.value?.id;
    if (!sessionId) return;
    const task = await api.generation.cancel(id);
    if (transition !== sessionTransition || active.value?.id !== sessionId || task.sessionId !== sessionId) return;
    taskRefreshVersions.set(id, (taskRefreshVersions.get(id) ?? 0) + 1);
    active.value.tasks = active.value.tasks.map(item => item.id === task.id ? task : item);
    closeEvents(id);
    const nextQuota = await api.generation.quota();
    if (transition !== sessionTransition || active.value?.id !== sessionId) return;
    quota.value = nextQuota;
  }
  async function retry(id: string) {
    if (retryingTaskIds.value.has(id)) return;
    const transition = sessionTransition;
    const sessionId = active.value?.id;
    if (!sessionId) return;
    retryingTaskIds.value = new Set([...retryingTaskIds.value, id]);
    try {
      const result = await api.generation.retry(id);
      if (transition !== sessionTransition || active.value?.id !== sessionId || result.session.id !== sessionId || result.task.sessionId !== sessionId) return;
      taskRefreshVersions.set(id, (taskRefreshVersions.get(id) ?? 0) + 1);
      active.value = result.session;
      quota.value = result.quota;
      connectEvents(result.task.id);
    } finally {
      const next = new Set(retryingTaskIds.value);
      next.delete(id);
      retryingTaskIds.value = next;
    }
  }
  async function continueMissing(id: string) {
    if (typeof api.generation.continueMissing !== "function") return retry(id);
    if (retryingTaskIds.value.has(id)) return;
    retryingTaskIds.value = new Set([...retryingTaskIds.value, id]);
    const transition = sessionTransition;
    const sessionId = active.value?.id;
    const idempotencyKey = pendingContinuationKeys.get(id) ?? `continue-${id}-${createUuid()}`;
    pendingContinuationKeys.set(id, idempotencyKey);
    try {
      const result = await api.generation.continueMissing(id, idempotencyKey);
      pendingContinuationKeys.delete(id);
      if (transition !== sessionTransition || active.value?.id !== sessionId || result.session.id !== sessionId) return;
      active.value = result.session; quota.value = result.quota; connectEvents(result.task.id);
    } catch (cause) {
      if (isDefinitiveApiError(cause)) pendingContinuationKeys.delete(id);
      throw cause;
    } finally { const next = new Set(retryingTaskIds.value); next.delete(id); retryingTaskIds.value = next; }
  }
  async function regenerateAll(id: string) {
    const task = active.value?.tasks.find(item => item.id === id); if (!task) return;
    if (retryingTaskIds.value.has(id)) return;
    retryingTaskIds.value = new Set([...retryingTaskIds.value, id]);
    draft.value = normalizeDraft({ mode: "AUTO", prompt: task.prompt, imageIds: task.imageIds,
      ratio: task.ratio, resolution: task.resolution, width: task.width, height: task.height,
      imageCountMode: String(task.imageCount) as GenerationDraft["imageCountMode"], imageCount: task.imageCount });
    const fingerprint = submissionFingerprint(active.value?.id, sessionlessEpoch, submissionSnapshot(draft.value));
    pendingSubmissionKeys.delete(fingerprint);
    pendingPlanTokens.delete(fingerprint);
    try {
      return await submit();
    } finally {
      const next = new Set(retryingTaskIds.value);
      next.delete(id);
      retryingTaskIds.value = next;
    }
  }
  function scheduleReconnect(taskId: string) {
    closeEvents(taskId);
    const timer = window.setTimeout(() => {
      reconnectTimers.delete(taskId);
      const task = active.value?.tasks.find(item => item.id === taskId);
      if (task && activeTaskStatuses.has(task.status)) connectEvents(taskId);
    }, 1000);
    reconnectTimers.set(taskId, timer);
  }
  function connectEvents(taskId: string) {
    closeEvents(taskId);
    const cursor = eventCursors.value[taskId] ?? 0;
    const url = `/api/dream_web/generation/tasks/${encodeURIComponent(taskId)}/events?after=${cursor}`;
    const source = new EventSource(url, { withCredentials: true });
    sources.set(taskId, source);
    const handle = async (event: Event) => {
      if (sources.get(taskId) !== source) return;
      const message = event as MessageEvent;
      const id = Number(message.lastEventId || 0);
      const eventType = message.type || "";
      const current = eventCursors.value[taskId] ?? 0;
      if (id && id <= current) return;
      try {
        const task = await refreshTask(taskId);
        if (!task) return;
        if (quotaChangingEventTypes.has(eventType)) {
          const nextQuota = await api.generation.quota();
          if (sources.get(taskId) !== source) return;
          quota.value = nextQuota;
        }
        if (id) eventCursors.value[taskId] = Math.max(eventCursors.value[taskId] ?? 0, id);
        if (terminalTaskStatuses.has(task.status)) closeEvents(taskId);
        startActiveTaskRefresh();
      } catch {
        if (sources.get(taskId) === source) scheduleReconnect(taskId);
      }
    };
    for (const type of ["task.queued", "task.generating", "task.retrying", "task.input.moderated", "task.requirement_understood", "task.structure_planned", "task.visual_constraints_ready", "task.prompt_constructed", "task.generation_started", "task.evaluation_completed", "task.refinement_started", "task.generation_accepted", "task.output.moderated", "task.execution.queued", "task.slot.started", "task.slot.retrying", "task.result.succeeded", "task.slot.failed", "task.execution.released", "task.succeeded", "task.partially_succeeded", "task.failed", "task.cancelled", "task.dead_lettered"]) source.addEventListener(type, handle);
    source.onerror = () => { if (sources.get(taskId) === source) scheduleReconnect(taskId); };
  }
  restorePersistedPending();
  return { options, quota, sessions, active, draft, loading, submitting, uploading, retryingTaskIds, error, notice, pendingSubmission, estimatedCost, load, openSession, startNewSession, createSession, renameSession, removeSession, saveDraft, uploadReference, applyOptions, submit, resumePendingSubmission, clearPendingSubmission, restorePendingSubmission, refreshTask, cancel, retry, continueMissing, regenerateAll, connectEvents, closeEvents };
});
