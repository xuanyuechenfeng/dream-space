<script setup lang="ts">
import { Archive, ChevronLeft, ChevronRight, CircleAlert, Eye, LoaderCircle, Pencil, Plus, RefreshCw, Search, Upload, X } from "lucide-vue-next";
import { nextTick, onBeforeUnmount, onMounted, reactive, ref } from "vue";
import { useRouter } from "vue-router";
import { AdminApiError, adminApi, resolveAssetUrl, type Inspiration, type InspirationInput } from "@/api/admin";
import { useAdminAuthStore } from "@/stores/adminAuth";

const router = useRouter();
const auth = useAdminAuthStore();
const items = ref<Inspiration[]>([]);
const total = ref(0);
const page = ref(1);
const pageCount = ref(0);
const loading = ref(true);
const saving = ref(false);
const error = ref("");
const editing = ref<Inspiration | "create" | null>(null);
const filters = reactive({ query: "", status: "", category: "" });
const active = reactive({ ...filters });
const closeButton = ref<HTMLButtonElement>();
let opener: HTMLElement | null = null;

const emptyForm = (): InspirationInput => ({ slug: "", title: "", prompt: "", category: "portrait", imageUrl: "/inspiration/portrait-01.webp", thumbnailUrl: "/inspiration/portrait-01.webp", width: 1350, height: 2400, modelName: "image-4.7", ratio: "9:16", resolutionLabel: "1350 × 2400", authorDisplayName: "运营精选", sourceType: "internal", sourceName: "造梦空间", sourceUrl: null, licenseBasis: "内部生成素材", isAiGenerated: true, likeCount: 0, sortOrder: 0 });
const form = reactive<InspirationInput>(emptyForm());
const statusLabels: Record<string, string> = { draft: "草稿", published: "已发布", archived: "已下架" };
const categoryLabels: Record<string, string> = { portrait: "人像", photography: "摄影", anime: "动漫", illustration: "插画", design: "设计" };
const sourceLabels: Record<string, string> = { ai_public_gallery: "AI 公开灵感", licensed: "授权素材", internal: "内部素材" };

function formatDate(value: string | null) { return value ? new Intl.DateTimeFormat("zh-CN", { month: "2-digit", day: "2-digit", hour: "2-digit", minute: "2-digit", hour12: false }).format(new Date(value)) : "-"; }
function toForm(item: Inspiration): InspirationInput { const { id: _id, status: _status, publishedAt: _publishedAt, createdAt: _createdAt, ...value } = item; return value; }

async function handle(reason: unknown) {
  if (reason instanceof AdminApiError && reason.status === 401) { await auth.refresh(); await router.replace("/login"); }
  error.value = (reason as Error).message;
}
async function load() {
  loading.value = true; error.value = "";
  try { const data = await adminApi.inspirations({ ...active, page: page.value, pageSize: 20 }); items.value = data.items; total.value = data.total; pageCount.value = data.pageCount; }
  catch (reason) { await handle(reason); }
  finally { loading.value = false; }
}
function submitFilters() { Object.assign(active, filters); page.value = 1; void load(); }
function resetFilters() { Object.assign(filters, { query: "", status: "", category: "" }); submitFilters(); }
function changePage(value: number) { if (value >= 1 && value <= Math.max(1, pageCount.value)) { page.value = value; void load(); } }

async function open(item: Inspiration | "create", event?: MouseEvent) {
  opener = event?.currentTarget as HTMLElement || null;
  editing.value = item;
  Object.assign(form, item === "create" ? emptyForm() : toForm(item));
  await nextTick(); closeButton.value?.focus();
}
function close() { editing.value = null; nextTick(() => opener?.focus()); }
async function save() {
  if (!auth.canWrite) return;
  saving.value = true; error.value = "";
  try {
    if (editing.value === "create") await adminApi.createInspiration(form);
    else if (editing.value) await adminApi.updateInspiration(editing.value.id, form);
    close(); await load();
  } catch (reason) { await handle(reason); }
  finally { saving.value = false; }
}
async function toggle(item: Inspiration, event: MouseEvent) {
  opener = event.currentTarget as HTMLElement; saving.value = true; error.value = "";
  try { if (item.status === "published") await adminApi.unpublishInspiration(item); else await adminApi.publishInspiration(item); await load(); }
  catch (reason) { await handle(reason); }
  finally { saving.value = false; }
}
function onKey(event: KeyboardEvent) { if (event.key === "Escape" && editing.value) close(); }
onMounted(() => { window.addEventListener("keydown", onKey); void load(); });
onBeforeUnmount(() => window.removeEventListener("keydown", onKey));
</script>

<template>
  <main class="admin-page">
    <header class="admin-page-header"><div><p class="admin-page-kicker">内容运营</p><h1>灵感管理</h1><p>维护前台灵感广场的素材、来源与发布状态。</p></div><div class="admin-page-header-actions"><span v-if="!auth.canWrite" class="admin-readonly-badge">只读权限</span><button class="admin-icon-button bordered" type="button" aria-label="刷新灵感" title="刷新灵感" :disabled="loading" @click="load"><RefreshCw :class="{ spin: loading }" aria-hidden="true" /></button><button v-if="auth.canWrite" class="admin-button primary" type="button" @click="open('create', $event)"><Plus aria-hidden="true" />新增灵感</button></div></header>
    <form class="admin-filters admin-inspiration-filters" @submit.prevent="submitFilters"><label class="admin-search-field"><Search aria-hidden="true" /><input v-model="filters.query" aria-label="搜索灵感" placeholder="标题、slug、提示词或来源" /></label><label><span>状态</span><select v-model="filters.status"><option value="">全部状态</option><option v-for="(label, value) in statusLabels" :key="value" :value="value">{{ label }}</option></select></label><label><span>分类</span><select v-model="filters.category"><option value="">全部分类</option><option v-for="(label, value) in categoryLabels" :key="value" :value="value">{{ label }}</option></select></label><div class="admin-filter-actions"><button class="admin-button secondary" type="button" @click="resetFilters">重置</button><button class="admin-button primary" type="submit">查询</button></div></form>
    <div class="admin-list-summary"><span>共 {{ total }} 条灵感</span><span v-if="loading">正在更新…</span></div>
    <section v-if="error" class="admin-inline-error" role="alert"><CircleAlert aria-hidden="true" /><span>{{ error }}</span><button class="admin-button secondary" type="button" @click="load">重试</button></section>
    <section class="admin-table-region" :aria-busy="loading" aria-label="灵感列表"><table class="admin-table admin-inspiration-table"><thead><tr><th>灵感</th><th>分类</th><th>状态</th><th>来源</th><th>排序</th><th>更新时间</th><th class="admin-table-action-heading">操作</th></tr></thead><tbody><tr v-for="item in items" :key="item.id"><td><div class="admin-inspiration-identity"><img :src="resolveAssetUrl(item.thumbnailUrl)" alt="" /><span><strong>{{ item.title }}</strong><small>{{ item.slug }}</small></span></div></td><td>{{ categoryLabels[item.category] || item.category }}</td><td><span class="admin-status" :class="`inspiration-${item.status}`">{{ statusLabels[item.status] || item.status }}</span></td><td><span>{{ sourceLabels[item.sourceType] || item.sourceType }}</span><small>{{ item.sourceName }}</small></td><td>{{ item.sortOrder }}</td><td>{{ formatDate(item.updatedAt) }}</td><td class="admin-table-action-cell"><button class="admin-icon-button" type="button" :aria-label="`${auth.canWrite ? '编辑' : '查看'} ${item.title}`" @click="open(item, $event)"><component :is="auth.canWrite ? Pencil : Eye" aria-hidden="true" /></button><button v-if="auth.canWrite" class="admin-icon-button" type="button" :aria-label="item.status === 'published' ? `下架 ${item.title}` : `发布 ${item.title}`" :disabled="saving" @click="toggle(item, $event)"><component :is="item.status === 'published' ? Archive : Upload" aria-hidden="true" /></button></td></tr></tbody></table><div v-if="loading && !items.length" class="admin-empty-state"><LoaderCircle class="spin" aria-hidden="true" /><strong>正在加载灵感</strong></div><div v-else-if="!items.length" class="admin-empty-state"><Search aria-hidden="true" /><strong>没有符合条件的灵感</strong><span>调整筛选条件后重新查询。</span></div></section>
    <footer class="admin-pagination"><span>第 {{ page }} / {{ Math.max(1, pageCount) }} 页</span><div><button class="admin-icon-button bordered" type="button" aria-label="上一页" :disabled="loading || page <= 1" @click="changePage(page - 1)"><ChevronLeft aria-hidden="true" /></button><button class="admin-icon-button bordered" type="button" aria-label="下一页" :disabled="loading || page >= pageCount" @click="changePage(page + 1)"><ChevronRight aria-hidden="true" /></button></div></footer>

    <div v-if="editing" class="admin-drawer-backdrop" role="presentation" @mousedown.self="close"><aside class="admin-task-drawer admin-inspiration-drawer" role="dialog" aria-modal="true" aria-labelledby="inspirationEditorTitle"><header><div><p>{{ editing === 'create' ? '新增内容' : auth.canWrite ? '编辑内容' : '查看内容' }}</p><h2 id="inspirationEditorTitle">{{ editing === 'create' ? '新建灵感' : editing.title }}</h2></div><button ref="closeButton" class="admin-icon-button" type="button" aria-label="关闭编辑器" @click="close"><X aria-hidden="true" /></button></header>
      <form class="admin-inspiration-form" @submit.prevent="save"><div class="admin-inspiration-preview"><img :src="resolveAssetUrl(form.thumbnailUrl)" alt="灵感预览" /><span v-if="editing !== 'create'" class="admin-status" :class="`inspiration-${editing.status}`">{{ statusLabels[editing.status] }}</span></div>
        <div class="admin-form-grid two-columns"><label><span>slug</span><input v-model="form.slug" required minlength="2" maxlength="80" :disabled="!auth.canWrite" /></label><label><span>标题</span><input v-model="form.title" required minlength="2" maxlength="100" :disabled="!auth.canWrite" /></label></div>
        <label><span>提示词</span><textarea v-model="form.prompt" required maxlength="4000" :disabled="!auth.canWrite" /></label>
        <div class="admin-form-grid two-columns"><label><span>原图地址</span><input v-model="form.imageUrl" required :disabled="!auth.canWrite" /></label><label><span>缩略图地址</span><input v-model="form.thumbnailUrl" required :disabled="!auth.canWrite" /></label></div>
        <div class="admin-form-grid three-columns"><label><span>分类</span><select v-model="form.category" :disabled="!auth.canWrite"><option v-for="(label, value) in categoryLabels" :key="value" :value="value">{{ label }}</option></select></label><label><span>宽度</span><input v-model.number="form.width" type="number" min="1" max="10000" required :disabled="!auth.canWrite" /></label><label><span>高度</span><input v-model.number="form.height" type="number" min="1" max="10000" required :disabled="!auth.canWrite" /></label></div>
        <div class="admin-form-grid three-columns"><label><span>模型</span><input v-model="form.modelName" required :disabled="!auth.canWrite" /></label><label><span>比例</span><input v-model="form.ratio" required :disabled="!auth.canWrite" /></label><label><span>分辨率</span><input v-model="form.resolutionLabel" required :disabled="!auth.canWrite" /></label></div>
        <div class="admin-form-grid two-columns"><label><span>作者</span><input v-model="form.authorDisplayName" required :disabled="!auth.canWrite" /></label><label><span>来源类型</span><select v-model="form.sourceType" :disabled="!auth.canWrite"><option v-for="(label, value) in sourceLabels" :key="value" :value="value">{{ label }}</option></select></label></div>
        <div class="admin-form-grid two-columns"><label><span>来源名称</span><input v-model="form.sourceName" required :disabled="!auth.canWrite" /></label><label><span>来源链接</span><input v-model="form.sourceUrl" type="url" :disabled="!auth.canWrite" /></label></div>
        <label><span>授权依据</span><textarea v-model="form.licenseBasis" required maxlength="500" :disabled="!auth.canWrite" /></label>
        <div class="admin-form-grid three-columns"><label><span>点赞数</span><input v-model.number="form.likeCount" type="number" min="0" :disabled="!auth.canWrite" /></label><label><span>排序值</span><input v-model.number="form.sortOrder" type="number" min="0" :disabled="!auth.canWrite" /></label><label class="admin-checkbox-field"><input v-model="form.isAiGenerated" type="checkbox" :disabled="!auth.canWrite" /><span>AI 生成内容</span></label></div>
        <div class="admin-editor-actions"><button class="admin-button secondary" type="button" @click="close">{{ auth.canWrite ? '取消' : '关闭' }}</button><button v-if="auth.canWrite" class="admin-button primary" type="submit" :disabled="saving">{{ saving ? '保存中' : '保存' }}</button></div>
      </form></aside></div>
  </main>
</template>
