import { defineStore } from "pinia";
import { computed, ref } from "vue";
import { api, type GenerationDraft, type GenerationOptions, type GenerationQuota, type GenerationSession, type GenerationSessionSummary, type GenerationSubmitResponse, type GenerationTask } from "@/api/client";
import { dimensionsForRatio, resolutionOption } from "@/features/generation/generationDimensions";
import { createUuid } from "./uuid";

const blankDraft = (): GenerationDraft => ({ mode: "AUTO", prompt: "", imageIds: [], ratio: "1:1", resolution: "2K", width: 2048, height: 2048 });
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
  return JSON.stringify([conversation, value.mode, value.prompt, value.imageIds, value.ratio, value.resolution, value.width, value.height]);
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

const activeTaskStatuses = new Set(["queued", "generating", "retrying"]);
const terminalTaskStatuses = new Set(["succeeded", "partially_succeeded", "failed", "cancelled", "dead_lettered"]);

function isSubmitResponse(value: unknown): value is GenerationSubmitResponse {
  if (typeof value !== "object" || value === null) return false;
  const response = value as Partial<GenerationSubmitResponse>;
  return typeof response.session?.id === "string"
    && typeof response.task?.id === "string"
    && typeof response.task.sessionId === "string"
    && Array.isArray(response.session.tasks);
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
  const eventCursors = ref<Record<string, number>>({});
  const sources = new Map<string, EventSource>();
  const reconnectTimers = new Map<string, number>();
  const taskRefreshVersions = new Map<string, number>();
  const pendingSubmissionKeys = new Map<string, string>();
  let sessionTransition = 0;
  let resourceLoad = 0;
  let draftWriteVersion = 0;
  let sessionlessEpoch = 0;
  const estimatedCost = computed(() => resolutionOption(options.value?.resolutions ?? [], draft.value.resolution)?.unitCost ?? 1);

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
    for (const timer of reconnectTimers.values()) window.clearTimeout(timer);
    reconnectTimers.clear();
    for (const source of sources.values()) { source.onerror = null; source.close(); }
    sources.clear();
  }
  function clearSessionState() {
    closeEvents();
    draftWriteVersion += 1;
    taskRefreshVersions.clear();
    retryingTaskIds.value = new Set();
    active.value = null;
    draft.value = blankDraft();
    eventCursors.value = {};
    applyOptions();
  }
  function startNewSession() {
    sessionTransition += 1;
    sessionlessEpoch += 1;
    clearSessionState();
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
    for (const task of session.tasks) if (activeTaskStatuses.has(task.status)) connectEvents(task.id);
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
    const transition = sessionTransition;
    await api.generation.deleteSession(id);
    if (transition !== sessionTransition) return;
    sessions.value = sessions.value.filter(item => item.id !== id);
    if (active.value?.id === id) startNewSession();
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
    const writeVersion = ++draftWriteVersion;
    submitting.value = true;
    let fingerprint: string | null = null;
    try {
      // The API creates a session atomically when sessionId is omitted. This
      // keeps the first submit from creating an orphan session with no task.
      const sessionId = active.value?.id;
      fingerprint = submissionFingerprint(sessionId, sessionlessEpoch, snapshot);
      const idempotencyKey = pendingSubmissionKeys.get(fingerprint) ?? `web-${createUuid()}`;
      pendingSubmissionKeys.set(fingerprint, idempotencyKey);
      const result = await api.generation.submit({ ...snapshot, mode: "AUTO", ...(sessionId ? { sessionId } : {}), idempotencyKey });
      if (!isSubmitResponse(result)) throw new Error("生成接口返回数据无效，请稍后重试");
      pendingSubmissionKeys.delete(fingerprint);
      quota.value = result.quota;
      const summary = { id: result.session.id, title: result.session.title, thumbnailUrl: result.session.thumbnailUrl, createdAt: result.session.createdAt, updatedAt: result.session.updatedAt };
      sessions.value = sortSessions(sessions.value.some(item => item.id === summary.id)
        ? sessions.value.map(item => item.id === summary.id ? { ...item, ...summary } : item)
        : [summary, ...sessions.value]);
      if (transition !== sessionTransition || writeVersion !== draftWriteVersion) return result;
      active.value = result.session;
      draft.value = normalizeDraft(result.session.draft as Partial<GenerationDraft> | null);
      applyOptions();
      connectEvents(result.task.id); return result;
    } catch (cause) {
      if (fingerprint && isDefinitiveApiError(cause)) pendingSubmissionKeys.delete(fingerprint);
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
      const current = eventCursors.value[taskId] ?? 0;
      if (id && id <= current) return;
      try {
        const task = await refreshTask(taskId);
        if (!task) return;
        if (id) eventCursors.value[taskId] = Math.max(eventCursors.value[taskId] ?? 0, id);
        if (terminalTaskStatuses.has(task.status)) closeEvents(taskId);
      } catch {
        if (sources.get(taskId) === source) scheduleReconnect(taskId);
      }
    };
    for (const type of ["task.queued", "task.generating", "task.retrying", "task.input.moderated", "task.requirement_understood", "task.structure_planned", "task.visual_constraints_ready", "task.prompt_constructed", "task.generation_started", "task.evaluation_completed", "task.refinement_started", "task.generation_accepted", "task.output.moderated", "task.succeeded", "task.partially_succeeded", "task.failed", "task.cancelled", "task.dead_lettered"]) source.addEventListener(type, handle);
    source.onerror = () => { if (sources.get(taskId) === source) scheduleReconnect(taskId); };
  }
  return { options, quota, sessions, active, draft, loading, submitting, uploading, retryingTaskIds, error, estimatedCost, load, openSession, startNewSession, createSession, renameSession, removeSession, saveDraft, uploadReference, applyOptions, submit, refreshTask, cancel, retry, connectEvents, closeEvents };
});
