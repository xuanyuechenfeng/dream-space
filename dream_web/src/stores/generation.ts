import { defineStore } from "pinia";
import { computed, ref } from "vue";
import { api, type GenerationDraft, type GenerationOptions, type GenerationQuota, type GenerationSession, type GenerationSessionSummary, type GenerationSubmitResponse } from "@/api/client";
import { dimensionsForRatio, resolutionOption } from "@/features/generation/generationDimensions";

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

function submissionFingerprint(sessionId: string | undefined, value: GenerationDraft): string {
  return JSON.stringify([sessionId, value.mode, value.prompt, value.imageIds, value.ratio, value.resolution, value.width, value.height]);
}

function sortSessions(items: GenerationSessionSummary[]): GenerationSessionSummary[] {
  return [...items].sort((a, b) => {
    const created = Date.parse(a.createdAt) - Date.parse(b.createdAt);
    return created || a.id.localeCompare(b.id);
  });
}

function isDefinitiveApiError(error: unknown): boolean {
  return typeof error === "object" && error !== null && typeof (error as { status?: unknown }).status === "number";
}

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
  const error = ref("");
  const eventCursors = ref<Record<string, number>>({});
  const sources = new Map<string, EventSource>();
  const pendingSubmissionKeys = new Map<string, string>();
  const estimatedCost = computed(() => resolutionOption(options.value?.resolutions ?? [], draft.value.resolution)?.unitCost ?? 1);

  function closeEvents(taskId?: string) {
    if (taskId) { sources.get(taskId)?.close(); sources.delete(taskId); return; }
    for (const source of sources.values()) source.close(); sources.clear();
  }
  async function load(sessionId?: string) {
    loading.value = true;
    try {
      const [nextOptions, nextQuota, nextSessions] = await Promise.all([api.generation.options(), api.generation.quota(), api.generation.sessions()]);
      options.value = nextOptions; quota.value = nextQuota; sessions.value = sortSessions(nextSessions.items); applyOptions();
      if (sessionId) await openSession(sessionId);
      else if (sessions.value[0]) await openSession(sessions.value[0].id);
      else active.value = null;
      error.value = "";
    } catch (e) { error.value = e instanceof Error ? e.message : "Generation unavailable"; }
    finally { loading.value = false; }
  }
  async function openSession(id: string) {
    const session = await api.generation.session(id); closeEvents(); active.value = session; draft.value = normalizeDraft(session.draft as Partial<GenerationDraft> | null); applyOptions(); eventCursors.value = {};
    for (const task of session.tasks) if (task.status === "queued" || task.status === "generating") connectEvents(task.id);
  }
  async function createSession(initialDraft: GenerationDraft = blankDraft()) { const nextDraft = normalizeDraft(initialDraft); const session = await api.generation.createSession(nextDraft); closeEvents(); sessions.value = sortSessions([{ ...session }, ...sessions.value]); active.value = session; draft.value = normalizeDraft(session.draft as Partial<GenerationDraft> | null); applyOptions(); }
  async function renameSession(id: string, title: string) { const session = await api.generation.renameSession(id, title); active.value = session; sessions.value = sessions.value.map(item => item.id === id ? { ...item, title: session.title, updatedAt: session.updatedAt } : item); }
  async function removeSession(id: string) { await api.generation.deleteSession(id); sessions.value = sessions.value.filter(item => item.id !== id); closeEvents(); active.value = null; draft.value = blankDraft(); }
  async function saveDraft() { if (!active.value) return; const session = await api.generation.draft(active.value.id, normalizeDraft(draft.value)); active.value = session; draft.value = normalizeDraft(session.draft as Partial<GenerationDraft> | null); sessions.value = sessions.value.map(item => item.id === session.id ? { ...item, title: session.title, updatedAt: session.updatedAt } : item); }
  async function uploadReference(file: File) { const upload = await api.generation.uploadReference(file); draft.value.imageIds = [...draft.value.imageIds, upload.id].slice(0, options.value?.referenceImages.max ?? 2); await saveDraft(); }
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
    const snapshot = submissionSnapshot(draft.value);
    submitting.value = true;
    let fingerprint: string | null = null;
    try {
      // The API creates a session atomically when sessionId is omitted. This
      // keeps the first submit from creating an orphan session with no task.
      const sessionId = active.value?.id;
      fingerprint = submissionFingerprint(sessionId, snapshot);
      const idempotencyKey = pendingSubmissionKeys.get(fingerprint) ?? `web-${crypto.randomUUID()}`;
      pendingSubmissionKeys.set(fingerprint, idempotencyKey);
      const result = await api.generation.submit({ ...snapshot, mode: "AUTO", ...(sessionId ? { sessionId } : {}), idempotencyKey });
      if (!isSubmitResponse(result)) throw new Error("生成接口返回数据无效，请稍后重试");
      pendingSubmissionKeys.delete(fingerprint);
      active.value = result.session;
      draft.value = normalizeDraft(result.session.draft as Partial<GenerationDraft> | null);
      applyOptions();
      quota.value = result.quota;
      const summary = { id: result.session.id, title: result.session.title, thumbnailUrl: result.session.thumbnailUrl, createdAt: result.session.createdAt, updatedAt: result.session.updatedAt };
      sessions.value = sortSessions(sessions.value.some(item => item.id === summary.id)
        ? sessions.value.map(item => item.id === summary.id ? { ...item, ...summary } : item)
        : [summary, ...sessions.value]);
      connectEvents(result.task.id); return result;
    } catch (cause) {
      if (fingerprint && isDefinitiveApiError(cause)) pendingSubmissionKeys.delete(fingerprint);
      throw cause;
    } finally { submitting.value = false; }
  }
  async function refreshTask(id: string) { if (!active.value) return; const task = await api.generation.task(id); active.value.tasks = active.value.tasks.map(item => item.id === task.id ? task : item); return task; }
  async function cancel(id: string) { const task = await api.generation.cancel(id); if (active.value) active.value.tasks = active.value.tasks.map(item => item.id === task.id ? task : item); quota.value = await api.generation.quota(); closeEvents(id); }
  async function retry(id: string) { const result = await api.generation.retry(id); active.value = result.session; quota.value = result.quota; connectEvents(result.task.id); }
  function connectEvents(taskId: string) {
    closeEvents(taskId);
    const cursor = eventCursors.value[taskId] ?? 0;
    const url = `/api/dream_web/generation/tasks/${encodeURIComponent(taskId)}/events?after=${cursor}`;
    const source = new EventSource(url, { withCredentials: true });
    sources.set(taskId, source);
    const handle = async (event: Event) => { const message = event as MessageEvent; const id = Number(message.lastEventId || 0); const current = eventCursors.value[taskId] ?? 0; if (id && id <= current) return; eventCursors.value[taskId] = Math.max(current, id); await refreshTask(taskId); const task = active.value?.tasks.find(item => item.id === taskId); if (task && ["succeeded", "partially_succeeded", "failed", "cancelled"].includes(task.status)) closeEvents(taskId); };
    for (const type of ["task.queued", "task.generating", "task.retrying", "task.input.moderated", "task.requirement_understood", "task.structure_planned", "task.visual_constraints_ready", "task.prompt_constructed", "task.generation_started", "task.evaluation_completed", "task.refinement_started", "task.generation_accepted", "task.output.moderated", "task.succeeded", "task.partially_succeeded", "task.failed", "task.cancelled", "task.dead_lettered"]) source.addEventListener(type, handle);
    source.onerror = () => { closeEvents(taskId); window.setTimeout(() => { const task = active.value?.tasks.find(item => item.id === taskId); if (task && ["queued", "generating"].includes(task.status)) connectEvents(taskId); }, 1000); };
  }
  return { options, quota, sessions, active, draft, loading, submitting, error, estimatedCost, load, openSession, createSession, renameSession, removeSession, saveDraft, uploadReference, applyOptions, submit, refreshTask, cancel, retry, connectEvents, closeEvents };
});
