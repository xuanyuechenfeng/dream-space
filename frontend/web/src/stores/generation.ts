import { defineStore } from "pinia";
import { computed, ref } from "vue";
import { api, type GenerationDraft, type GenerationOptions, type GenerationQuota, type GenerationSession, type GenerationSessionSummary, type GenerationSubmitResponse } from "@/api/client";

const blankDraft = (): GenerationDraft => ({ prompt: "", model: "image-4.7", ratio: "1:1", resolution: "2K", imageCount: 1, referenceImageUrls: [] });

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
  const estimatedCost = computed(() => draft.value.imageCount * (draft.value.resolution === "4K" ? 2 : (options.value?.costPerImage ?? 1)));

  function closeEvents(taskId?: string) {
    if (taskId) { sources.get(taskId)?.close(); sources.delete(taskId); return; }
    for (const source of sources.values()) source.close(); sources.clear();
  }
  async function load(sessionId?: string) {
    loading.value = true;
    try {
      const [nextOptions, nextQuota, nextSessions] = await Promise.all([api.generation.options(), api.generation.quota(), api.generation.sessions()]);
      options.value = nextOptions; quota.value = nextQuota; sessions.value = nextSessions.items;
      if (sessionId) await openSession(sessionId);
      else if (sessions.value[0]) await openSession(sessions.value[0].id);
      else active.value = null;
      error.value = "";
    } catch (e) { error.value = e instanceof Error ? e.message : "Generation unavailable"; }
    finally { loading.value = false; }
  }
  async function openSession(id: string) {
    const session = await api.generation.session(id); active.value = session; draft.value = { ...blankDraft(), ...(session.draft ?? {}) }; eventCursors.value = {};
    for (const task of session.tasks) if (task.status === "queued" || task.status === "generating") connectEvents(task.id);
  }
  async function createSession() { const session = await api.generation.createSession(blankDraft()); sessions.value = [{ ...session }, ...sessions.value]; active.value = session; draft.value = blankDraft(); }
  async function renameSession(id: string, title: string) { const session = await api.generation.renameSession(id, title); active.value = session; sessions.value = sessions.value.map(item => item.id === id ? { ...item, title: session.title, updatedAt: session.updatedAt } : item); }
  async function removeSession(id: string) { await api.generation.deleteSession(id); sessions.value = sessions.value.filter(item => item.id !== id); closeEvents(); active.value = null; draft.value = blankDraft(); }
  async function saveDraft() { if (!active.value) return; const session = await api.generation.draft(active.value.id, draft.value); active.value = session; sessions.value = sessions.value.map(item => item.id === session.id ? { ...item, title: session.title, updatedAt: session.updatedAt } : item); }
  async function uploadReference(file: File) { const upload = await api.generation.uploadReference(file); draft.value.referenceImageUrls = [...draft.value.referenceImageUrls, upload.url]; await saveDraft(); }
  async function submit(): Promise<GenerationSubmitResponse> {
    if (!active.value) { await createSession(); }
    submitting.value = true;
    try {
      const result = await api.generation.submit({ ...draft.value, sessionId: active.value?.id, idempotencyKey: `web-${crypto.randomUUID()}` });
      active.value = result.session; quota.value = result.quota; sessions.value = sessions.value.map(item => item.id === result.session.id ? { ...item, title: result.session.title, updatedAt: result.session.updatedAt } : item);
      connectEvents(result.task.id); return result;
    } finally { submitting.value = false; }
  }
  async function refreshTask(id: string) { if (!active.value) return; const task = await api.generation.task(id); active.value.tasks = active.value.tasks.map(item => item.id === task.id ? task : item); return task; }
  async function cancel(id: string) { const task = await api.generation.cancel(id); if (active.value) active.value.tasks = active.value.tasks.map(item => item.id === task.id ? task : item); quota.value = await api.generation.quota(); closeEvents(); }
  async function retry(id: string) { const result = await api.generation.retry(id); active.value = result.session; quota.value = result.quota; connectEvents(result.task.id); }
  function connectEvents(taskId: string) {
    closeEvents();
    closeEvents(taskId);
    const cursor = eventCursors.value[taskId] ?? 0;
    const url = `/api/generation/tasks/${encodeURIComponent(taskId)}/events?after=${cursor}`;
    const source = new EventSource(url, { withCredentials: true });
    sources.set(taskId, source);
    const handle = async (event: Event) => { const message = event as MessageEvent; const id = Number(message.lastEventId || 0); const current = eventCursors.value[taskId] ?? 0; if (id && id <= current) return; eventCursors.value[taskId] = Math.max(current, id); await refreshTask(taskId); const task = active.value?.tasks.find(item => item.id === taskId); if (task && ["succeeded", "partially_succeeded", "failed", "cancelled"].includes(task.status)) closeEvents(taskId); };
    for (const type of ["task.queued", "task.generating", "task.retrying", "task.input.moderated", "task.output.moderated", "task.succeeded", "task.partially_succeeded", "task.failed", "task.cancelled", "task.dead_lettered"]) source.addEventListener(type, handle);
    source.onerror = () => { closeEvents(taskId); window.setTimeout(() => { const task = active.value?.tasks.find(item => item.id === taskId); if (task && ["queued", "generating"].includes(task.status)) connectEvents(taskId); }, 1000); };
  }
  return { options, quota, sessions, active, draft, loading, submitting, error, estimatedCost, load, openSession, createSession, renameSession, removeSession, saveDraft, uploadReference, submit, refreshTask, cancel, retry, connectEvents, closeEvents };
});
