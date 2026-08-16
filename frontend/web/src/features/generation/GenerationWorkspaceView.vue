<script setup lang="ts">
import { ArrowUp, Check, ChevronDown, ChevronUp, CircleCheck, Download, Image, LoaderCircle, PanelLeftClose, Pencil, Plus, SquarePen, Trash2, X } from "lucide-vue-next";
import { computed, onMounted, onUnmounted, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import InspirationShell from "@/layouts/InspirationShell.vue";
import { useGenerationStore } from "@/stores/generation";
import { usePreferencesStore } from "@/stores/preferences";
import type { GenerationSessionSummary, GenerationTask } from "@/api/client";

const props = defineProps<{ sessionId?: string }>();
const route = useRoute();
const router = useRouter();
const preferences = usePreferencesStore();
const generation = useGenerationStore();
const collapsed = ref(false);
const parameterOpen = ref(false);
const search = ref("");
const statusFilter = ref("all");
const modelFilter = ref("all");
const editingId = ref<string | null>(null);
const editingTitle = ref("");
const fileInput = ref<HTMLInputElement | null>(null);
const previewUrl = ref<string | null>(null);
const localError = ref("");
const language = computed(() => preferences.language);
const text = computed(() => language.value === "zh" ? {
  create: "创作", newSession: "新建会话", rename: "重命名", remove: "删除会话", emptyTitle: "开始一段新的创作", emptyCopy: "输入提示词，或从灵感页选择一张作品开始。", prompt: "描述你想生成的画面", imageGeneration: "图片生成", ratio: "画面比例", resolution: "分辨率", count: "生成数量", expected: "预计", credits: "点额度", search: "搜索任务", searchAction: "搜索", allModels: "全部模型", allStatuses: "全部状态", cancel: "取消生成", retry: "重新生成", edit: "编辑参数", download: "下载", queued: "正在排队", generating: "正在生成，通常需要几十秒。", completed: "生成完成", failed: "生成失败，额度已返还。", cancelled: "已取消，额度已返还。", noTasks: "当前筛选下没有任务", confirmDelete: "删除会话？", deleteCopy: "会话中的任务记录也会被删除，此操作无法撤销。", cancelAction: "取消", save: "保存", uploading: "上传中..."
} : {
  create: "Create", newSession: "New conversation", rename: "Rename", remove: "Delete conversation", emptyTitle: "Start a new creation", emptyCopy: "Describe an image or choose a work from inspiration.", prompt: "Describe the image you want", imageGeneration: "Image generation", ratio: "Aspect ratio", resolution: "Resolution", count: "Images", expected: "Estimated", credits: "credits", search: "Search tasks", searchAction: "Search", allModels: "All models", allStatuses: "All statuses", queued: "Queued", generating: "Generating. This usually takes a few seconds.", completed: "Completed", failed: "Generation failed and credits were returned.", cancelled: "Cancelled. Credits returned.", noTasks: "No tasks match these filters", confirmDelete: "Delete conversation?", deleteCopy: "Tasks in this conversation will also be deleted.", cancel: "Cancel generation", retry: "Run again", edit: "Edit parameters", download: "Download", cancelAction: "Cancel", save: "Save", uploading: "Uploading..."
});

const active = computed(() => generation.active);
const draft = computed({ get: () => generation.draft, set: value => { generation.draft = value; } });
const hasDetail = computed(() => Boolean(active.value));
const visibleTasks = computed(() => (active.value?.tasks ?? []).filter(task => {
  const query = search.value.trim().toLowerCase();
  return (!query || task.prompt.toLowerCase().includes(query)) && (statusFilter.value === "all" || task.status === statusFilter.value) && (modelFilter.value === "all" || task.model === modelFilter.value);
}));
const starterPrompts = computed(() => language.value === "zh" ? ["自然光下的安静编辑肖像", "带有柔雾的电影感山脉", "极简主义建筑与长阴影"] : ["A quiet editorial portrait in natural afternoon light", "A cinematic mountain landscape with soft mist", "Minimalist architecture with long shadows"]);
const isTerminal = (status: string) => ["SUCCEEDED", "PARTIALLY_SUCCEEDED", "FAILED", "CANCELLED"].includes(status);
const assetUrl = (url?: string | null) => url ? (url.startsWith("http") || url.startsWith("data:") ? url : `/api${url}`) : "";
const modelLabel = (model: string) => model === "image-4.7" ? "Image 4.7" : model === "image-5-lite" ? "Image 5.0 Lite" : model;

async function load() { await generation.load(props.sessionId); }
async function openSession(id: string) { await generation.openSession(id); if (route.params.sessionId !== id) await router.replace(`/generate/${id}`); }
async function newSession() { await generation.createSession(); if (generation.active) await router.replace(`/generate/${generation.active.id}`); }
function startRename(item: GenerationSessionSummary) { editingId.value = item.id; editingTitle.value = item.title; }
async function saveRename() { if (!editingId.value) return; try { await generation.renameSession(editingId.value, editingTitle.value); editingId.value = null; } catch (e) { localError.value = e instanceof Error ? e.message : "Rename failed"; } }
async function removeSession(item: GenerationSessionSummary) { if (!window.confirm(`${text.value.confirmDelete}\n${item.title}\n${text.value.deleteCopy}`)) return; try { await generation.removeSession(item.id); await router.replace("/generate"); } catch (e) { localError.value = e instanceof Error ? e.message : "Delete failed"; } }
async function submit() { localError.value = ""; if (!draft.value.prompt.trim() && draft.value.referenceImageUrls.length === 0) { localError.value = text.value.prompt; return; } try { const result = await generation.submit(); await router.replace(`/generate/${result.session.id}`); } catch (e) { localError.value = e instanceof Error ? e.message : "Generation failed"; } }
async function cancel(task: GenerationTask) { try { await generation.cancel(task.id); } catch (e) { localError.value = e instanceof Error ? e.message : "Cancel failed"; } }
async function retry(task: GenerationTask) { try { await generation.retry(task.id); } catch (e) { localError.value = e instanceof Error ? e.message : "Retry failed"; } }
async function addReference(file: File) { localError.value = ""; try { await generation.uploadReference(file); } catch (e) { localError.value = e instanceof Error ? e.message : "Upload failed"; } finally { if (fileInput.value) fileInput.value.value = ""; } }
function onFileChange(event: Event) { const file = (event.target as HTMLInputElement).files?.[0]; if (file) void addReference(file); }
async function saveDraft() { try { await generation.saveDraft(); } catch (e) { localError.value = e instanceof Error ? e.message : "Draft save failed"; } }
async function download(url: string, id: string) { try { const response = await fetch(assetUrl(url), { credentials: "include" }); if (!response.ok) throw new Error(text.value.download); const blob = URL.createObjectURL(await response.blob()); const anchor = document.createElement("a"); anchor.href = blob; anchor.download = `dream-space-${id}.webp`; anchor.click(); URL.revokeObjectURL(blob); } catch (e) { localError.value = e instanceof Error ? e.message : text.value.download; } }
onMounted(load);
watch(() => props.sessionId, id => { if (id) void generation.openSession(id); });
onUnmounted(generation.closeEvents);
</script>

<template>
  <InspirationShell active-page="generate">
    <main class="generation-page" :class="{ 'sidebar-collapsed': collapsed }">
      <aside class="session-sidebar">
        <div class="sidebar-heading"><span>{{ text.create }}</span><button class="icon-btn sidebar-collapse" type="button" :aria-label="text.create" @click="collapsed = true"><PanelLeftClose aria-hidden="true" /></button></div>
        <button class="action-btn new-session" type="button" @click="newSession"><SquarePen aria-hidden="true" />{{ text.newSession }}</button>
        <div class="session-list" :aria-label="text.create">
          <div v-for="item in generation.sessions" :key="item.id" class="session-row" :class="{ active: active?.id === item.id }">
            <span v-if="item.thumbnailUrl" class="session-thumb"><img :src="assetUrl(item.thumbnailUrl)" alt="" /></span>
            <template v-if="editingId === item.id"><input v-model="editingTitle" class="session-name-input" :aria-label="text.rename" @keydown.enter="saveRename" @keydown.esc="editingId = null" /><div class="session-inline-actions"><button class="session-inline-action" type="button" :aria-label="text.save" @click="saveRename"><Check aria-hidden="true" /></button><button class="session-inline-action" type="button" :aria-label="text.cancelAction" @click="editingId = null"><X aria-hidden="true" /></button></div></template>
            <template v-else><button class="session-item" type="button" @click="openSession(item.id)" @dblclick="startRename(item)">{{ item.title }}</button><div class="session-row-actions"><button class="session-row-action" type="button" :aria-label="text.rename" :title="text.rename" @click="startRename(item)"><Pencil aria-hidden="true" /></button><button class="session-row-action danger" type="button" :aria-label="text.remove" :title="text.remove" @click="removeSession(item)"><Trash2 aria-hidden="true" /></button></div></template>
          </div>
        </div>
      </aside>
      <section class="generation-main" :class="{ 'is-empty': !hasDetail }">
        <button v-if="collapsed" class="icon-btn sidebar-expand" type="button" :aria-label="text.create" @click="collapsed = false"><PanelLeftClose aria-hidden="true" /></button>
        <header class="generation-top"><div class="generation-search-control expanded"><div class="generation-search-panel"><input v-model="search" type="search" :aria-label="text.search" :placeholder="text.search" /></div></div><select v-model="modelFilter" class="field-btn generation-filter"><option value="all">{{ text.allModels }}</option><option v-for="model in generation.options?.models" :key="model" :value="model">{{ modelLabel(model) }}</option></select><select v-model="statusFilter" class="field-btn generation-filter"><option value="all">{{ text.allStatuses }}</option><option value="QUEUED">{{ text.queued }}</option><option value="GENERATING">{{ text.generating }}</option><option value="SUCCEEDED">{{ text.completed }}</option><option value="FAILED">{{ text.failed }}</option><option value="CANCELLED">{{ text.cancelled }}</option></select></header>
        <div class="timeline">
          <LoaderCircle v-if="generation.loading" class="spin" aria-label="Loading" />
          <section v-else-if="!hasDetail" class="empty-session"><h1>{{ text.emptyTitle }}</h1><p class="empty-session-copy">{{ text.emptyCopy }}</p><div class="starter-prompts"><button v-for="prompt in starterPrompts" :key="prompt" class="starter-prompt" type="button" @click="draft.prompt = prompt"><span>{{ prompt }}</span></button></div></section>
          <div v-else-if="visibleTasks.length === 0" class="generation-empty">{{ text.noTasks }}</div>
          <template v-else><h1 class="date-heading">{{ active?.title }}</h1><article v-for="task in visibleTasks" :key="task.id" class="task"><div class="task-prompt">{{ task.prompt }}<span class="task-params">{{ modelLabel(task.model) }} · {{ task.ratio }} · {{ task.resolution }} · {{ task.imageCount }}</span></div><div v-if="task.status === 'QUEUED' || task.status === 'GENERATING'" class="status-line processing"><LoaderCircle class="spin" aria-hidden="true" />{{ task.status === 'QUEUED' ? text.queued : text.generating }}</div><div v-if="task.status === 'QUEUED' || task.status === 'GENERATING'" class="progress-bar"><span /></div><button v-if="task.status === 'QUEUED' || task.status === 'GENERATING'" class="action-btn" type="button" @click="cancel(task)">{{ text.cancel }}</button><div v-if="task.status === 'FAILED'" class="status-line error">{{ task.errorMessage || text.failed }}</div><div v-if="task.status === 'CANCELLED'" class="status-line">{{ text.cancelled }}</div><div v-if="task.status === 'SUCCEEDED' || task.status === 'PARTIALLY_SUCCEEDED'" class="status-line"><CircleCheck aria-hidden="true" />{{ text.completed }} · {{ task.totalCost }} {{ text.credits }}</div><div v-if="task.results.length" class="result-grid"><div v-for="result in task.results" :key="result.id" class="result-item"><button class="result-preview" type="button" aria-label="Preview" @click="previewUrl = assetUrl(result.contentUrl)"><img :src="assetUrl(result.thumbnailUrl || result.contentUrl)" alt="Generated result" /></button><button class="result-download" type="button" :aria-label="text.download" @click="download(result.contentUrl, result.id)"><Download aria-hidden="true" /></button></div></div><div v-if="isTerminal(task.status)" class="task-actions"><button class="action-btn" type="button" @click="draft = { prompt: task.prompt, model: task.model, ratio: task.ratio, resolution: task.resolution, imageCount: task.imageCount, referenceImageUrls: task.referenceImageUrls }">{{ text.edit }}</button><button class="action-btn" type="button" @click="retry(task)">{{ text.retry }}</button></div></article></template>
        </div>
        <section class="composer composer-shell" :class="{ 'is-expanded': parameterOpen || Boolean(draft.prompt) }" aria-label="Image generation">
          <div v-if="parameterOpen && generation.options" class="parameter-popover"><div class="param-section"><div class="param-heading"><span>{{ text.ratio }}</span></div><div class="option-grid"><button v-for="ratio in generation.options.ratios" :key="ratio" class="option-btn" :class="{ active: draft.ratio === ratio }" type="button" @click="draft.ratio = ratio">{{ ratio === 'smart' ? 'Smart' : ratio }}</button></div></div><div class="param-section"><div class="param-heading"><span>{{ text.resolution }}</span></div><div class="option-grid resolution-grid"><button v-for="resolution in generation.options.resolutions" :key="resolution" class="option-btn" :class="{ active: draft.resolution === resolution }" type="button" @click="draft.resolution = resolution">{{ resolution }}</button></div></div><div class="param-section"><div class="param-heading"><span>{{ text.count }}</span></div><div class="option-grid count-grid"><button v-for="count in generation.options.imageCount.max" :key="count" class="option-btn" :class="{ active: draft.imageCount === count }" type="button" @click="draft.imageCount = count">{{ count }}</button></div></div></div>
          <div v-if="draft.referenceImageUrls.length" class="ref-strip"><div v-for="(url, index) in draft.referenceImageUrls" :key="`${url}-${index}`" class="ref-item"><img class="ref-thumb" :src="assetUrl(url)" alt="Reference" /><button class="ref-remove" type="button" aria-label="Remove reference" @click="draft.referenceImageUrls = draft.referenceImageUrls.filter((_, itemIndex) => itemIndex !== index)"><X aria-hidden="true" /></button></div></div>
          <div class="prompt-row"><button class="upload-btn" type="button" aria-label="Add reference" :disabled="generation.submitting" @click="fileInput?.click()"><Plus aria-hidden="true" /></button><input ref="fileInput" class="visually-hidden" type="file" accept="image/jpeg,image/png,image/webp" @change="onFileChange" /><textarea v-model="draft.prompt" rows="1" :placeholder="text.prompt" @blur="saveDraft" /></div>
          <div class="composer-footer"><span class="field-btn static-field"><Image aria-hidden="true" />{{ text.imageGeneration }}</span><select v-model="draft.model" class="field-btn model-select"><option v-for="model in generation.options?.models" :key="model" :value="model">{{ modelLabel(model) }}</option></select><button class="field-btn" type="button" :aria-expanded="parameterOpen" @click="parameterOpen = !parameterOpen"><span>{{ draft.ratio }}</span><span>·</span><span>{{ draft.resolution }}</span><span>·</span><span>{{ draft.imageCount }} {{ language === 'zh' ? '张' : 'images' }}</span><ChevronDown v-if="parameterOpen" aria-hidden="true" /><ChevronUp v-else aria-hidden="true" /></button><span class="cost-label">{{ text.expected }} {{ generation.estimatedCost }} {{ text.credits }}</span><button class="submit-btn" type="button" aria-label="Submit generation" :disabled="generation.submitting || (!draft.prompt.trim() && draft.referenceImageUrls.length === 0) || Boolean(generation.quota && generation.estimatedCost > generation.quota.available)" @click="submit"><LoaderCircle v-if="generation.submitting" class="spin" aria-hidden="true" /><ArrowUp v-else aria-hidden="true" /></button></div><div v-if="localError || generation.error" class="composer-error" role="alert">{{ localError || generation.error }}</div>
        </section>
      </section>
      <div v-if="previewUrl" class="image-preview" role="dialog" aria-modal="true" @mousedown.self="previewUrl = null"><img :src="previewUrl" alt="Generated result preview" /><button class="icon-btn image-preview-close" type="button" aria-label="Close" @click="previewUrl = null"><X aria-hidden="true" /></button></div>
    </main>
  </InspirationShell>
</template>
