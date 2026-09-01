<script setup lang="ts">
import { AlertCircle, ArrowUp, Check, ChevronDown, ChevronUp, Clock3, Download, Image, Link, LoaderCircle, Menu, PanelLeftClose, Pause, Pencil, Plus, SlidersHorizontal, SquarePen, Trash2, Unlink, X } from "lucide-vue-next";
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import InspirationShell from "@/layouts/InspirationShell.vue";
import { useGenerationStore } from "@/stores/generation";
import { usePreferencesStore } from "@/stores/preferences";
import { api, type GenerationDraft, type GenerationRatio, type GenerationResolution, type GenerationSessionSummary, type GenerationTask } from "@/api/client";
import { dimensionsForRatio, resizeCustomDimensions, resolutionOption, roundToStep, validateDimensions } from "./generationDimensions";

const props = defineProps<{ sessionId?: string }>();
const route = useRoute();
const router = useRouter();
const preferences = usePreferencesStore();
const generation = useGenerationStore();
const collapsed = ref(false);
const mobileHistoryOpen = ref(false);
const mobileHistoryToggle = ref<HTMLButtonElement | null>(null);
const mobileHistoryClose = ref<HTMLButtonElement | null>(null);
const sessionSidebar = ref<HTMLElement | null>(null);
const composerShell = ref<HTMLElement | null>(null);
const focusBeforeHistory = ref<HTMLElement | null>(null);
let primaryNavWasInert = false;
let primaryNavInertCaptured = false;
const editingId = ref<string | null>(null);
const editingTitle = ref("");
const attachmentFileInput = ref<HTMLInputElement | null>(null);
const parameterPopover = ref<HTMLElement | null>(null);
const parameterTrigger = ref<HTMLElement | null>(null);
const previewUrl = ref<string | null>(null);
const localError = ref("");
const parametersOpen = ref(false);
const sizeLocked = ref(true);
const expandedPrompts = ref(new Set<string>());
const timeline = ref<HTMLElement | null>(null);
const promptInput = ref<HTMLTextAreaElement | null>(null);
const promptFocused = ref(false);
const promptHistory = ref<string[]>([]);
let viewTransition = 0;
const language = computed(() => preferences.language);
const text = computed(() => language.value === "zh" ? {
  create: "创作", history: "历史会话", recentSessions: "最近会话", newSession: "新建会话", rename: "重命名", remove: "删除会话", emptyTitle: "开始一段新的创作", emptyCopy: "输入描述，模型会自动判断是生成新图还是调整已有图片。", prompt: "描述画面和素材关系，例如保留主体并参考另一张素材的配色", imageGeneration: "图片生成", attachment: "添加素材", material: "素材", parameters: "生成参数", ratio: "图片比例", smart: "智能", resolution: "分辨率", dimensions: "尺寸", aiDimensions: "由 AI 确定", lockRatio: "锁定比例", unlockRatio: "解除比例锁定", uploaded: "已上传图片", expected: "预计", credits: "点额度", cancel: "取消生成", retry: "重新生成", edit: "再次编辑", download: "下载", queued: "正在排队", generating: "正在生成，通常需要几十秒。", completed: "生成完成", failed: "生成失败，额度已返还。", failedShort: "生成失败", cancelled: "已取消，额度已返还。", noTasks: "当前会话暂无任务", confirmDelete: "删除会话？", deleteCopy: "会话中的任务记录也会被删除，此操作无法撤销。", cancelAction: "取消", save: "保存", uploading: "上传中...", input: "用户输入", output: "生成结果", preview: "预览生成中", pause: "暂停生成", outputUnavailable: "暂时没有可展示的结果", expandPrompt: "展开提示词", collapsePrompt: "收起提示词", recent: "最近使用", historyHint: "从历史提示词继续", sendHint: "Enter 发送 · Shift + Enter 换行", assistant: "Dream Space", closeHistory: "关闭历史会话"
  } : {
  create: "Create", history: "Conversation history", recentSessions: "Recent conversations", newSession: "New conversation", rename: "Rename", remove: "Delete conversation", emptyTitle: "Start a new creation", emptyCopy: "Describe the result or change you want; the model will infer the intent.", prompt: "Describe the image and how the attached materials should be used", imageGeneration: "Image generation", attachment: "Add material", material: "Material", parameters: "Generation settings", ratio: "Aspect ratio", smart: "Smart", resolution: "Resolution", dimensions: "Dimensions", aiDimensions: "Determined by AI", lockRatio: "Lock ratio", unlockRatio: "Unlock ratio", uploaded: "Uploaded image", expected: "Estimated", credits: "credits", cancel: "Cancel generation", retry: "Run again", edit: "Edit again", download: "Download", queued: "Queued", generating: "Generating. This usually takes a few seconds.", completed: "Completed", failed: "Generation failed and credits were returned.", failedShort: "Generation failed", cancelled: "Cancelled. Credits returned.", noTasks: "No tasks in this conversation", confirmDelete: "Delete conversation?", deleteCopy: "Tasks in this conversation will also be deleted.", cancelAction: "Cancel", save: "Save", uploading: "Uploading...", input: "Your prompt", output: "Generated result", preview: "Generating preview", pause: "Pause generation", outputUnavailable: "No result available yet", expandPrompt: "Expand prompt", collapsePrompt: "Collapse prompt", recent: "Recent prompts", historyHint: "Continue from history", sendHint: "Enter to send · Shift + Enter for a new line", assistant: "Dream Space", closeHistory: "Close conversation history"
  });

const active = computed(() => generation.active);
const draft = computed({ get: () => generation.draft, set: value => { generation.draft = value; } });
const hasDetail = computed(() => Boolean(active.value));
const sortedSessions = computed(() => [...generation.sessions].sort((a, b) => {
  const created = Date.parse(a.createdAt) - Date.parse(b.createdAt);
  return created || a.id.localeCompare(b.id);
}));
const sortedTasks = computed(() => [...(active.value?.tasks ?? [])].sort((a, b) => {
  const created = Date.parse(a.createdAt) - Date.parse(b.createdAt);
  return created || a.id.localeCompare(b.id);
}));
const starterPrompts = computed(() => language.value === "zh" ? ["自然光下的安静编辑肖像", "带有柔雾的电影感山脉", "极简主义建筑与长阴影"] : ["A quiet editorial portrait in natural afternoon light", "A cinematic mountain landscape with soft mist", "Minimalist architecture with long shadows"]);
const recentPrompts = computed(() => {
  const fromSession = [...sortedTasks.value].reverse().map(task => task.prompt.trim());
  return [...promptHistory.value, ...fromSession].filter(Boolean).filter((prompt, index, list) => list.indexOf(prompt) === index).slice(0, 5);
});
const isTerminal = (status: string) => ["succeeded", "partially_succeeded", "failed", "cancelled"].includes(status);
const assetUrl = (url?: string | null) => url ? (url.startsWith("http") || url.startsWith("data:") ? url : `/api${url}`) : "";
const referenceAsset = (id?: string | null) => id ? assetUrl(id.startsWith("/") ? id : `/dream_web/uploads/references/${id}/content`) : "";
const modeLabel = () => language.value === "zh" ? "AI 自动识别" : "AI inferred";
const taskDimensions = (task: GenerationTask) => task.width && task.height ? `${task.width} x ${task.height}` : text.value.aiDimensions;
const shouldCollapsePrompt = (prompt: string) => prompt.split(/\r?\n/).length > 8 || prompt.length > 480;
const isPromptExpanded = (taskId: string) => expandedPrompts.value.has(taskId);
function togglePrompt(taskId: string) {
  const next = new Set(expandedPrompts.value);
  if (next.has(taskId)) next.delete(taskId); else next.add(taskId);
  expandedPrompts.value = next;
}
function resizePrompt() {
  const element = promptInput.value;
  if (!element) return;
  element.style.height = "auto";
  element.style.height = `${Math.min(element.scrollHeight, 180)}px`;
}
function onPromptKeydown(event: KeyboardEvent) {
  if (event.key !== "Enter" || event.shiftKey || event.isComposing) return;
  event.preventDefault();
  if (!generation.submitting && draft.value.prompt.trim()) void submit();
}
function useRecentPrompt(prompt: string) {
  draft.value.prompt = prompt;
  promptFocused.value = true;
  void nextTick(() => { resizePrompt(); promptInput.value?.focus(); });
}
function rememberPrompt(prompt: string) {
  const clean = prompt.trim();
  if (!clean) return;
  promptHistory.value = [clean, ...promptHistory.value.filter(item => item !== clean)].slice(0, 12);
  localStorage.setItem("dream-space-prompt-history", JSON.stringify(promptHistory.value));
}
const taskDraft = (task: GenerationTask): GenerationDraft => ({ mode: "AUTO", prompt: task.prompt, imageIds: task.imageIds, ratio: task.ratio as GenerationRatio, resolution: task.resolution as GenerationResolution, width: task.width, height: task.height });
const selectedResolution = computed(() => resolutionOption(generation.options?.resolutions ?? [], draft.value.resolution));
const dimensionErrorCode = computed(() => validateDimensions(draft.value.ratio, draft.value.width, draft.value.height, generation.options?.dimensions.minEdge ?? 512, generation.options?.dimensions.step ?? 64, selectedResolution.value));
const dimensionError = computed(() => {
  const code = dimensionErrorCode.value;
  if (!code) return "";
  const zh: Record<string, string> = { DIMENSIONS_REQUIRED: "请输入宽度和高度", DIMENSIONS_INTEGER_REQUIRED: "宽高必须为整数", DIMENSIONS_TOO_SMALL: "宽高不能小于 512px", DIMENSIONS_STEP_INVALID: "宽高必须为 64 的整数倍", DIMENSIONS_TOO_LARGE: "尺寸超过当前分辨率限制", RATIO_MISMATCH: "宽高与所选比例不一致", RESOLUTION_UNAVAILABLE: "当前分辨率不可用", SMART_DIMENSIONS_FORBIDDEN: "智能比例不接受手动尺寸" };
  const en: Record<string, string> = { DIMENSIONS_REQUIRED: "Enter width and height", DIMENSIONS_INTEGER_REQUIRED: "Dimensions must be integers", DIMENSIONS_TOO_SMALL: "Dimensions must be at least 512px", DIMENSIONS_STEP_INVALID: "Dimensions must use 64px increments", DIMENSIONS_TOO_LARGE: "Dimensions exceed this resolution", RATIO_MISMATCH: "Dimensions do not match the selected ratio", RESOLUTION_UNAVAILABLE: "Resolution is unavailable", SMART_DIMENSIONS_FORBIDDEN: "Smart ratio cannot use manual dimensions" };
  return (language.value === "zh" ? zh : en)[code] ?? code;
});

function selectRatio(ratio: GenerationRatio) {
  draft.value.ratio = ratio;
  sizeLocked.value = ratio !== "custom";
  if (ratio === "smart") { draft.value.width = null; draft.value.height = null; return; }
  const current = selectedResolution.value;
  const size = current ? dimensionsForRatio(ratio, current.maxEdge, generation.options?.dimensions.step ?? 64) : null;
  if (size) Object.assign(draft.value, size);
}
function selectResolution(resolution: GenerationResolution) {
  const next = resolutionOption(generation.options?.resolutions ?? [], resolution);
  if (!next?.enabled) return;
  draft.value.resolution = resolution;
  if (draft.value.ratio === "smart") return;
  const step = generation.options?.dimensions.step ?? 64;
  const size = draft.value.ratio === "custom"
    ? resizeCustomDimensions(draft.value.width, draft.value.height, next.maxEdge, step)
    : dimensionsForRatio(draft.value.ratio, next.maxEdge, step);
  if (size) Object.assign(draft.value, size);
}
function updateDimension(axis: "width" | "height") {
  const value = draft.value[axis];
  const current = selectedResolution.value;
  const step = generation.options?.dimensions.step ?? 64;
  const min = generation.options?.dimensions.minEdge ?? 512;
  if (value == null || !Number.isFinite(value) || value < min || !current || value > current.maxEdge) return;
  draft.value[axis] = roundToStep(value, step);
  if (!sizeLocked.value || draft.value.ratio === "custom") { draft.value.ratio = "custom"; return; }
  const size = dimensionsForRatio(draft.value.ratio, current.maxEdge, step);
  if (!size) return;
  const factor = axis === "width" ? draft.value.width! / size.width : draft.value.height! / size.height;
  const other = axis === "width" ? "height" : "width";
  draft.value[other] = roundToStep(size[other] * factor, step);
}
function toggleSizeLock() { sizeLocked.value = !sizeLocked.value; if (!sizeLocked.value && draft.value.ratio !== "smart") draft.value.ratio = "custom"; }
function closeParametersOnOutsidePointer(event: PointerEvent) {
  if (!parametersOpen.value) return;
  const target = event.target;
  if (target instanceof Node && (parameterPopover.value?.contains(target) || parameterTrigger.value?.contains(target))) return;
  parametersOpen.value = false;
}
function focusableIn(element: HTMLElement | null) {
  if (!element) return [];
  return Array.from(element.querySelectorAll<HTMLElement>("a[href], button:not([disabled]), input:not([disabled]), textarea:not([disabled]), select:not([disabled]), [tabindex]:not([tabindex='-1'])")).filter(item => item.isConnected && (item.offsetWidth > 0 || item.offsetHeight > 0 || item.getClientRects().length > 0));
}
function closeMobileHistory() {
  if (!mobileHistoryOpen.value) return;
  mobileHistoryOpen.value = false;
  const restore = focusBeforeHistory.value;
  focusBeforeHistory.value = null;
  void nextTick(() => {
    if (restore?.isConnected) restore.focus();
    else mobileHistoryToggle.value?.focus();
  });
}
function openMobileHistory() {
  if (mobileHistoryOpen.value) return;
  focusBeforeHistory.value = document.activeElement instanceof HTMLElement ? document.activeElement : mobileHistoryToggle.value;
  mobileHistoryOpen.value = true;
}
function closePrimaryNavInert() {
  const nav = document.querySelector<HTMLElement>(".primary-nav");
  if (nav && primaryNavInertCaptured) nav.inert = primaryNavWasInert;
  primaryNavInertCaptured = false;
}
function handleViewportResize() {
  if (!mobileHistoryOpen.value || typeof window.matchMedia !== "function") return;
  if (window.matchMedia("(min-width: 768px)").matches) {
    mobileHistoryOpen.value = false;
    closePrimaryNavInert();
  }
}
function onMobileHistoryKeydown(event: KeyboardEvent) {
  if (!mobileHistoryOpen.value) return;
  if (event.key === "Escape") {
    event.preventDefault();
    closeMobileHistory();
    return;
  }
  if (event.key !== "Tab") return;
  const focusables = focusableIn(sessionSidebar.value);
  if (!focusables.length) {
    event.preventDefault();
    sessionSidebar.value?.focus();
    return;
  }
  const first = focusables[0]!;
  const last = focusables[focusables.length - 1]!;
  if (event.shiftKey && document.activeElement === first) {
    event.preventDefault();
    last.focus();
  } else if (!event.shiftKey && document.activeElement === last) {
    event.preventDefault();
    first.focus();
  }
}
function handleComposerFocusOut() {
  window.setTimeout(() => {
    if (!composerShell.value?.contains(document.activeElement)) promptFocused.value = false;
  }, 0);
}

async function restoreIntent(isCurrent: () => boolean) { const raw = sessionStorage.getItem("dream-space-restored-auth-intent"); if (!raw || !isCurrent()) return null; try { const intent = JSON.parse(raw) as { draft?: Partial<GenerationDraft>; referenceInspirationSlug?: string | null; submitOnRestore?: boolean }; if (!isCurrent()) return null; sessionStorage.removeItem("dream-space-restored-auth-intent"); const draftValue: GenerationDraft = { ...generation.draft, ...intent.draft, mode: "AUTO", imageIds: intent.draft?.imageIds ?? [] }; if (draftValue.imageIds.length === 0 && intent.referenceInspirationSlug) { const inspiration = await api.inspiration(intent.referenceInspirationSlug); if (!isCurrent()) return null; const response = await fetch(assetUrl(inspiration.imageUrl), { credentials: "include" }); if (!response.ok) throw new Error("参考图读取失败"); const blob = await response.blob(); const upload = await api.generation.uploadReference(new File([blob], `${inspiration.slug}.webp`, { type: blob.type || "image/webp" })); if (!isCurrent()) return null; draftValue.imageIds = [upload.id]; } if (!isCurrent()) return null; generation.draft = draftValue; generation.applyOptions(); if (generation.active) { await generation.saveDraft(); if (!isCurrent()) return null; } if (!intent.submitOnRestore || !isCurrent()) return null; const result = await generation.submit(); return isCurrent() && generation.active?.id === result.session.id ? result.session.id : null; } catch (cause) { if (isCurrent()) localError.value = cause instanceof Error ? cause.message : "无法恢复创作"; return null; } }
function scrollTimelineToEnd() {
  const element = timeline.value;
  if (!element) return;
  void nextTick(() => window.requestAnimationFrame(() => {
    // Keep the newest message visible without animating through historical messages.
    element.scrollTo({ top: element.scrollHeight, behavior: "auto" });
  }));
}
async function load() { const token = ++viewTransition; const expectedSessionId = props.sessionId; const expectedPath = route.fullPath; const isCurrent = () => token === viewTransition && props.sessionId === expectedSessionId && route.fullPath === expectedPath; await generation.load(expectedSessionId); if (!isCurrent()) return; const submittedSessionId = await restoreIntent(isCurrent); if (!isCurrent()) return; if (submittedSessionId && route.params.sessionId !== submittedSessionId) await router.replace(`/generate/${submittedSessionId}`); scrollTimelineToEnd(); }
async function openSession(id: string) { viewTransition += 1; localError.value = ""; try { const session = await generation.openSession(id); if (!session) return; closeMobileHistory(); if (route.params.sessionId !== id) await router.replace(`/generate/${id}`); scrollTimelineToEnd(); } catch (cause) { localError.value = cause instanceof Error ? cause.message : "Conversation unavailable"; } }
async function newSession() { if (generation.submitting) return; viewTransition += 1; generation.startNewSession(); closeMobileHistory(); localError.value = ""; parametersOpen.value = false; await router.replace("/generate"); }
function startRename(item: GenerationSessionSummary) { editingId.value = item.id; editingTitle.value = item.title; }
async function saveRename() { if (!editingId.value) return; try { await generation.renameSession(editingId.value, editingTitle.value); editingId.value = null; } catch (e) { localError.value = e instanceof Error ? e.message : "Rename failed"; } }
async function removeSession(item: GenerationSessionSummary) { if (!window.confirm(`${text.value.confirmDelete}\n${item.title}\n${text.value.deleteCopy}`)) return; viewTransition += 1; const deletingRouteSession = route.params.sessionId === item.id; try { await generation.removeSession(item.id); closeMobileHistory(); if (deletingRouteSession && route.params.sessionId === item.id) await router.replace("/generate"); } catch (e) { localError.value = e instanceof Error ? e.message : "Delete failed"; } }
async function submit() { localError.value = ""; if (!draft.value.prompt.trim()) { localError.value = text.value.prompt; return; } if (dimensionError.value) { localError.value = dimensionError.value; parametersOpen.value = true; return; } const submittedPrompt = draft.value.prompt; try { const result = await generation.submit(); if (generation.active?.id !== result.session.id) return; rememberPrompt(submittedPrompt); await router.replace(`/generate/${result.session.id}`); scrollTimelineToEnd(); } catch (e) { localError.value = e instanceof Error ? e.message : "Generation failed"; } }
async function cancel(task: GenerationTask) { try { await generation.cancel(task.id); } catch (e) { localError.value = e instanceof Error ? e.message : "Cancel failed"; } }
async function retry(task: GenerationTask) { try { await generation.retry(task.id); } catch (e) { localError.value = e instanceof Error ? e.message : "Retry failed"; } }
async function addReference(file: File) { localError.value = ""; if (draft.value.imageIds.length >= (generation.options?.referenceImages.max ?? 2)) { localError.value = language.value === "zh" ? "最多添加两张图片" : "You can add up to two images"; return; } try { await generation.uploadReference(file); } catch (e) { localError.value = e instanceof Error ? e.message : "Upload failed"; } finally { if (attachmentFileInput.value) attachmentFileInput.value.value = ""; } }
async function onFileChange(event: Event) { const files = Array.from((event.target as HTMLInputElement).files ?? []); for (const file of files.slice(0, 2)) await addReference(file); }
async function removeAttachment(index: number) { draft.value.imageIds = draft.value.imageIds.filter((_, itemIndex) => itemIndex !== index); await saveDraft(); }
async function saveDraft() { try { await generation.saveDraft(); } catch (e) { localError.value = e instanceof Error ? e.message : "Draft save failed"; } }
async function download(url: string, id: string) { try { const response = await fetch(assetUrl(url), { credentials: "include" }); if (!response.ok) throw new Error(text.value.download); const blob = URL.createObjectURL(await response.blob()); const anchor = document.createElement("a"); anchor.href = blob; anchor.download = `dream-space-${id}.png`; anchor.click(); URL.revokeObjectURL(blob); } catch (e) { localError.value = e instanceof Error ? e.message : text.value.download; } }
watch(mobileHistoryOpen, open => {
  if (open) {
    const nav = document.querySelector<HTMLElement>(".primary-nav");
    if (nav) {
      primaryNavWasInert = nav.inert;
      primaryNavInertCaptured = true;
      nav.inert = true;
    }
    void nextTick(() => (mobileHistoryClose.value ?? focusableIn(sessionSidebar.value)[0])?.focus());
  } else {
    closePrimaryNavInert();
    const restore = focusBeforeHistory.value;
    if (restore?.isConnected && document.activeElement !== restore) void nextTick(() => restore.focus());
  }
});
onMounted(() => {
  document.addEventListener("pointerdown", closeParametersOnOutsidePointer);
  document.addEventListener("keydown", onMobileHistoryKeydown);
  window.addEventListener("resize", handleViewportResize);
  try {
    const stored = JSON.parse(localStorage.getItem("dream-space-prompt-history") || "[]");
    if (Array.isArray(stored)) promptHistory.value = stored.filter((item): item is string => typeof item === "string").slice(0, 12);
  } catch { promptHistory.value = []; }
  void load();
});
watch(() => draft.value.prompt, () => { void nextTick(resizePrompt); });
watch(() => props.sessionId, id => { viewTransition += 1; if (id) { if (generation.active?.id !== id) void openSession(id); } else generation.startNewSession(); });
watch(() => {
  const tasks = active.value?.tasks ?? [];
  return `${active.value?.id ?? ""}|${tasks.map(task => `${task.id}:${task.status}:${task.results.length}:${task.updatedAt}`).join("|")}`;
}, scrollTimelineToEnd, { flush: "post" });
onUnmounted(() => {
  document.removeEventListener("pointerdown", closeParametersOnOutsidePointer);
  document.removeEventListener("keydown", onMobileHistoryKeydown);
  window.removeEventListener("resize", handleViewportResize);
  closePrimaryNavInert();
  generation.closeEvents();
});
</script>

<template>
  <InspirationShell active-page="generate">
    <main class="generation-page" :class="{ 'sidebar-collapsed': collapsed, 'mobile-history-open': mobileHistoryOpen }">
      <aside id="generation-history-dialog" ref="sessionSidebar" class="session-sidebar" :role="mobileHistoryOpen ? 'dialog' : undefined" :aria-modal="mobileHistoryOpen ? 'true' : undefined" :aria-label="mobileHistoryOpen ? text.history : undefined" :tabindex="mobileHistoryOpen ? -1 : undefined">
        <div class="sidebar-heading"><span>{{ text.create }}</span><button v-if="mobileHistoryOpen" ref="mobileHistoryClose" class="icon-btn mobile-history-close" type="button" :aria-label="text.closeHistory" @click="closeMobileHistory"><X aria-hidden="true" /></button><button class="icon-btn sidebar-collapse" type="button" :aria-label="text.create" @click="collapsed = true"><PanelLeftClose aria-hidden="true" /></button></div>
        <button class="action-btn new-session" type="button" :disabled="generation.submitting" @click="newSession"><SquarePen aria-hidden="true" />{{ text.newSession }}</button>
        <div class="session-list" :aria-label="text.history">
          <div class="session-list-label">{{ text.recentSessions }}</div>
          <div v-for="item in sortedSessions" :key="item.id" class="session-row" :class="{ active: active?.id === item.id }">
            <span v-if="item.thumbnailUrl" class="session-thumb"><img :src="assetUrl(item.thumbnailUrl)" alt="" /></span>
            <template v-if="editingId === item.id"><input v-model="editingTitle" class="session-name-input" :aria-label="text.rename" @keydown.enter="saveRename" @keydown.esc="editingId = null" /><div class="session-inline-actions"><button class="session-inline-action" type="button" :aria-label="text.save" @click="saveRename"><Check aria-hidden="true" /></button><button class="session-inline-action" type="button" :aria-label="text.cancelAction" @click="editingId = null"><X aria-hidden="true" /></button></div></template>
            <template v-else><button class="session-item" type="button" @click="openSession(item.id)" @dblclick="startRename(item)">{{ item.title }}</button><div class="session-row-actions"><button class="session-row-action" type="button" :aria-label="text.rename" :title="text.rename" @click="startRename(item)"><Pencil aria-hidden="true" /></button><button class="session-row-action danger" type="button" :aria-label="text.remove" :title="text.remove" @click="removeSession(item)"><Trash2 aria-hidden="true" /></button></div></template>
          </div>
        </div>
      </aside>
      <button v-if="mobileHistoryOpen" class="mobile-history-backdrop" type="button" tabindex="-1" aria-hidden="true" @click="closeMobileHistory" />
      <section class="generation-main" :class="{ 'is-empty': !hasDetail }" :aria-hidden="mobileHistoryOpen ? 'true' : undefined" :inert="mobileHistoryOpen">
        <button v-if="collapsed" class="icon-btn sidebar-expand" type="button" :aria-label="text.create" @click="collapsed = false"><PanelLeftClose aria-hidden="true" /></button>
        <header class="generation-header">
          <button ref="mobileHistoryToggle" class="icon-btn mobile-history-toggle" type="button" :aria-label="text.history" :aria-controls="mobileHistoryOpen ? 'generation-history-dialog' : undefined" :aria-expanded="mobileHistoryOpen" @click="mobileHistoryOpen ? closeMobileHistory() : openMobileHistory()"><Menu aria-hidden="true" /></button>
          <div class="generation-header-title"><span class="generation-header-kicker">{{ text.assistant }}</span><strong>{{ active?.title || text.emptyTitle }}</strong></div>
          <button class="icon-btn header-new-session" type="button" :aria-label="text.newSession" :title="text.newSession" :disabled="generation.submitting" @click="newSession"><SquarePen aria-hidden="true" /></button>
        </header>
        <div ref="timeline" class="timeline">
          <LoaderCircle v-if="generation.loading" class="spin" aria-label="Loading" />
          <section v-else-if="!hasDetail" class="empty-session"><h1>{{ text.emptyTitle }}</h1><p class="empty-session-copy">{{ text.emptyCopy }}</p><div class="starter-prompts"><button v-for="prompt in starterPrompts" :key="prompt" class="starter-prompt" type="button" @click="useRecentPrompt(prompt)"><span>{{ prompt }}</span></button></div></section>
          <template v-else><div v-if="sortedTasks.length === 0" class="generation-empty">{{ text.noTasks }}</div>
            <template v-else>
              <h1 class="date-heading">{{ active?.title }}</h1>
              <article v-for="task in sortedTasks" :key="task.id" class="task">
                <header class="task-card-header">
                  <span class="task-agent"><span class="agent-dot" />{{ text.assistant }}</span><span class="task-meta"><span>{{ task.ratio }} · {{ task.resolution }} · {{ taskDimensions(task) }}</span><span v-if="task.totalCost">{{ task.totalCost }} {{ text.credits }}</span><span class="task-time"><Clock3 aria-hidden="true" />{{ new Date(task.createdAt).toLocaleTimeString(language === 'zh' ? 'zh-CN' : 'en-US', { hour: '2-digit', minute: '2-digit' }) }}</span></span>
                </header>
                <div class="task-conversation">
                  <section class="task-input" :aria-label="text.input">
                    <div class="message-label user-message-label">{{ text.input }}</div>
                    <div class="input-bubble"><p class="prompt-copy" :class="{ expanded: isPromptExpanded(task.id) }">{{ task.prompt }}</p><button v-if="shouldCollapsePrompt(task.prompt)" class="prompt-toggle" type="button" :aria-expanded="isPromptExpanded(task.id)" :aria-label="isPromptExpanded(task.id) ? text.collapsePrompt : text.expandPrompt" :title="isPromptExpanded(task.id) ? text.collapsePrompt : text.expandPrompt" @click="togglePrompt(task.id)"><ChevronUp v-if="isPromptExpanded(task.id)" aria-hidden="true" /><ChevronDown v-else aria-hidden="true" /><span>{{ isPromptExpanded(task.id) ? text.collapsePrompt : text.expandPrompt }}</span></button><span class="task-params">{{ modeLabel() }} · {{ task.ratio }} · {{ task.resolution }} · {{ taskDimensions(task) }}</span></div>
                    <div v-if="task.imageIds.length" class="task-reference-list"><span class="task-reference-count">{{ text.material }} {{ task.imageIds.length }}</span><div class="task-reference-thumbs"><img v-for="(imageId, index) in task.imageIds" :key="imageId" :src="referenceAsset(imageId)" :alt="`${text.material} ${index + 1}`" /></div></div>
                  </section>
                  <section class="task-output" :aria-label="text.output">
                    <div class="message-label assistant-message-label">{{ text.output }}</div>
                    <div v-if="task.status === 'queued' || task.status === 'generating'" class="generation-placeholder"><div class="placeholder-grid" aria-hidden="true"><span /><span /><span /><span /></div><div class="placeholder-spinner"><LoaderCircle class="spin" aria-hidden="true" /></div><div class="placeholder-status">{{ task.status === 'queued' ? text.queued : text.preview }}</div><span class="placeholder-dimensions">{{ taskDimensions(task) }} · {{ task.ratio }}</span><button class="placeholder-pause" type="button" :aria-label="text.cancel" :title="text.cancel" @click="cancel(task)"><Pause aria-hidden="true" /></button></div>
                    <div v-else-if="task.results.length" class="result-grid"><div v-for="result in task.results" :key="result.id" class="result-item"><button class="result-preview" type="button" :aria-label="text.preview" @click="previewUrl = assetUrl(result.contentUrl)"><img :src="assetUrl(result.contentUrl)" :alt="text.output" :width="result.width" :height="result.height" decoding="async" @load="scrollTimelineToEnd" /></button><button class="result-download" type="button" :aria-label="text.download" :title="text.download" @click="download(result.contentUrl, result.id)"><Download aria-hidden="true" /></button></div></div>
                    <div v-else class="generation-placeholder is-unavailable" :class="`is-${task.status}`"><AlertCircle aria-hidden="true" /><span>{{ task.errorMessage || (task.status === 'cancelled' ? text.cancelled : text.failedShort) }}</span></div>
                  </section>
                </div>
                <div v-if="isTerminal(task.status)" class="task-actions"><button class="action-btn" type="button" @click="draft = taskDraft(task)">{{ text.edit }}</button><button class="action-btn" type="button" :disabled="generation.retryingTaskIds.has(task.id)" :aria-busy="generation.retryingTaskIds.has(task.id)" @click="retry(task)"><LoaderCircle v-if="generation.retryingTaskIds.has(task.id)" class="spin" aria-hidden="true" />{{ text.retry }}</button></div>
                <button v-if="task.status === 'queued' || task.status === 'generating'" class="action-btn task-cancel" type="button" @click="cancel(task)">{{ text.cancel }}</button>
              </article>
            </template>
          </template>
        </div>
        <section ref="composerShell" class="composer composer-shell is-expanded" aria-label="Image generation" @focusout="handleComposerFocusOut">
          <div v-if="parametersOpen" ref="parameterPopover" class="parameter-popover"><div class="param-section"><div class="param-heading"><span>{{ text.ratio }}</span><span class="size-preview">{{ draft.ratio === 'smart' ? text.aiDimensions : `${draft.width} x ${draft.height}px` }}</span></div><div class="option-grid"><button v-for="option in generation.options?.ratios ?? []" :key="option.value" class="option-btn" :class="{ active: draft.ratio === option.value }" type="button" @click="selectRatio(option.value)">{{ option.value === 'smart' ? text.smart : option.label }}</button></div></div><div class="param-section"><div class="param-heading"><span>{{ text.resolution }}</span></div><div class="option-grid resolution-grid"><button v-for="option in generation.options?.resolutions ?? []" :key="option.value" class="option-btn" :class="{ active: draft.resolution === option.value }" type="button" :disabled="!option.enabled" :title="option.disabledReason || ''" @click="selectResolution(option.value)">{{ option.label }}</button></div></div><div v-if="draft.ratio !== 'smart'" class="param-section"><div class="param-heading"><span>{{ text.dimensions }}</span><button class="icon-btn" type="button" :aria-label="sizeLocked ? text.unlockRatio : text.lockRatio" :title="sizeLocked ? text.unlockRatio : text.lockRatio" @click="toggleSizeLock"> <Link v-if="sizeLocked" aria-hidden="true" /><Unlink v-else aria-hidden="true" /></button></div><div class="dimension-fields"><label>W<input v-model.number="draft.width" type="number" min="512" step="64" @blur="updateDimension('width')" /></label><span>×</span><label>H<input v-model.number="draft.height" type="number" min="512" step="64" @blur="updateDimension('height')" /></label><span>PX</span></div><p v-if="dimensionError" class="dimension-error">{{ dimensionError }}</p></div></div><div class="ref-strip"><div v-for="(imageId, index) in draft.imageIds" :key="imageId" class="ref-item"><img class="ref-thumb" :src="referenceAsset(imageId)" :alt="`${text.material} ${index + 1}`" /><span class="ref-label">{{ text.material }} {{ index + 1 }}</span><button class="ref-remove" type="button" :aria-label="`${text.material} ${index + 1}`" @click="removeAttachment(index)"><X aria-hidden="true" /></button></div></div>
          <div class="prompt-row"><button class="upload-btn" type="button" :aria-label="text.attachment" :disabled="generation.submitting" @click="attachmentFileInput?.click()"><Plus aria-hidden="true" /></button><input ref="attachmentFileInput" class="visually-hidden" type="file" multiple accept="image/jpeg,image/png,image/webp" @change="onFileChange" /><textarea ref="promptInput" v-model="draft.prompt" rows="1" :placeholder="text.prompt" @focus="promptFocused = true" @blur="saveDraft(); handleComposerFocusOut()" @input="resizePrompt" @keydown="onPromptKeydown" /></div>
          <div v-if="promptFocused && !draft.prompt.trim() && recentPrompts.length" class="prompt-history" :aria-label="text.recent"><div class="prompt-history-heading"><Clock3 aria-hidden="true" /><span>{{ text.recent }}</span><small>{{ text.historyHint }}</small></div><button v-for="prompt in recentPrompts" :key="prompt" class="prompt-history-item" type="button" @click="useRecentPrompt(prompt)"><span>{{ prompt }}</span><ArrowUp aria-hidden="true" /></button></div>
          <div class="composer-footer"><span class="field-btn static-field"><Image aria-hidden="true" />{{ text.imageGeneration }}</span><button ref="parameterTrigger" class="field-btn parameter-trigger" type="button" :aria-expanded="parametersOpen" :aria-label="text.parameters" @click="parametersOpen = !parametersOpen"><SlidersHorizontal aria-hidden="true" />{{ text.parameters }}</button><span class="composer-hint">{{ text.sendHint }}</span><span class="cost-label">{{ text.expected }} {{ generation.estimatedCost }} {{ text.credits }}</span><button class="submit-btn" type="button" aria-label="Submit generation" :disabled="generation.submitting || generation.uploading || !draft.prompt.trim() || Boolean(dimensionError) || Boolean(generation.quota && generation.estimatedCost > generation.quota.available)" @click="submit"><LoaderCircle v-if="generation.submitting" class="spin" aria-hidden="true" /><ArrowUp v-else aria-hidden="true" /></button></div><div v-if="localError || generation.error" class="composer-error" role="alert">{{ localError || generation.error }}</div>
        </section>
      </section>
      <div v-if="previewUrl" class="image-preview" role="dialog" aria-modal="true" @mousedown.self="previewUrl = null"><img :src="previewUrl" alt="Generated result preview" /><button class="icon-btn image-preview-close" type="button" aria-label="Close" @click="previewUrl = null"><X aria-hidden="true" /></button></div>
    </main>
  </InspirationShell>
</template>
