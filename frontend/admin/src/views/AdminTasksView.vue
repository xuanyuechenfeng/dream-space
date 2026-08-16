<script setup lang="ts">
import { ChevronLeft, ChevronRight, CircleAlert, CircleCheck, Eye, LoaderCircle, RefreshCw, Search, X } from "lucide-vue-next";
import { nextTick, onBeforeUnmount, onMounted, reactive, ref } from "vue";
import { useRouter } from "vue-router";
import { AdminApiError, adminApi, resolveAssetUrl, type ReconciliationResponse, type TaskDetail, type TaskSummary } from "@/api/admin";
import { useAdminAuthStore } from "@/stores/adminAuth";

const router = useRouter();
const auth = useAdminAuthStore();
const items = ref<TaskSummary[]>([]);
const total = ref(0);
const page = ref(1);
const pageCount = ref(0);
const loading = ref(true);
const detailLoading = ref(false);
const error = ref("");
const detail = ref<TaskDetail | null>(null);
const reconciliation = ref<ReconciliationResponse>({ items: [] });
const filters = reactive({ query: "", status: "", model: "", createdFrom: "", createdTo: "" });
const active = reactive({ ...filters });
const closeButton = ref<HTMLButtonElement>();
let opener: HTMLElement | null = null;

const statusLabels: Record<string, string> = { queued: "排队中", generating: "生成中", succeeded: "已完成", partially_succeeded: "部分完成", failed: "失败", cancelled: "已取消" };
const moderationLabels: Record<string, string> = { pending: "待审核", approved: "已通过", rejected: "已拒绝" };

function formatDate(value: string | null) {
  return value ? new Intl.DateTimeFormat("zh-CN", { month: "2-digit", day: "2-digit", hour: "2-digit", minute: "2-digit", hour12: false }).format(new Date(value)) : "-";
}

async function load() {
  loading.value = true;
  error.value = "";
  try {
    const [tasks, runs] = await Promise.all([adminApi.tasks({ ...active, page: page.value, pageSize: 20 }), adminApi.reconciliation()]);
    items.value = tasks.items; total.value = tasks.total; pageCount.value = tasks.pageCount; reconciliation.value = runs;
  } catch (reason) { await handle(reason); }
  finally { loading.value = false; }
}

async function handle(reason: unknown) {
  if (reason instanceof AdminApiError && reason.status === 401) { await auth.refresh(); await router.replace("/login"); }
  error.value = (reason as Error).message;
}

function submit() { Object.assign(active, filters); page.value = 1; void load(); }
function reset() { Object.assign(filters, { query: "", status: "", model: "", createdFrom: "", createdTo: "" }); submit(); }
function changePage(value: number) { if (value >= 1 && value <= Math.max(1, pageCount.value)) { page.value = value; void load(); } }

async function open(item: TaskSummary, event: MouseEvent) {
  opener = event.currentTarget as HTMLElement;
  detailLoading.value = true;
  try { detail.value = await adminApi.task(item.id); await nextTick(); closeButton.value?.focus(); }
  catch (reason) { await handle(reason); }
  finally { detailLoading.value = false; }
}
function close() { detail.value = null; nextTick(() => opener?.focus()); }
function onKey(event: KeyboardEvent) { if (event.key === "Escape" && detail.value) close(); }
onMounted(() => { window.addEventListener("keydown", onKey); void load(); });
onBeforeUnmount(() => window.removeEventListener("keydown", onKey));
</script>

<template>
  <main class="admin-page">
    <header class="admin-page-header">
      <div><p class="admin-page-kicker">生成运营</p><h1>生成任务</h1><p>跟踪任务状态、生成参数、消耗和结果。</p></div>
      <button class="admin-icon-button bordered" type="button" aria-label="刷新任务" title="刷新任务" :disabled="loading" @click="load"><RefreshCw :class="{ spin: loading }" aria-hidden="true" /></button>
    </header>

    <section v-if="reconciliation.items[0]" class="admin-reconciliation-strip" aria-label="最近额度对账">
      <div class="admin-reconciliation-heading"><CircleCheck aria-hidden="true" /><span><strong>额度对账</strong><small>{{ formatDate(reconciliation.items[0].completedAt) }}</small></span></div>
      <dl><div><dt>扫描</dt><dd>{{ reconciliation.items[0].scannedTasks }} 个任务</dd></div><div><dt>差异</dt><dd>{{ reconciliation.items[0].mismatchCount }}</dd></div><div><dt>已补偿</dt><dd>{{ reconciliation.items[0].repairedCount }}</dd></div><div :class="{ 'is-warning': reconciliation.items[0].findings.some(item => item.status === 'blocked') }"><dt>待处理</dt><dd>{{ reconciliation.items[0].findings.filter(item => item.status === 'blocked').length }}</dd></div></dl>
    </section>

    <form class="admin-filters" @submit.prevent="submit">
      <label class="admin-search-field"><Search aria-hidden="true" /><input v-model="filters.query" aria-label="搜索任务" placeholder="提示词、会话或手机号" /></label>
      <label><span>状态</span><select v-model="filters.status"><option value="">全部状态</option><option v-for="(label, value) in statusLabels" :key="value" :value="value">{{ label }}</option></select></label>
      <label><span>模型</span><select v-model="filters.model"><option value="">全部模型</option><option value="image-4.7">通用模型</option><option value="image-realistic">写实模型</option><option value="image-anime">动漫模型</option></select></label>
      <label><span>开始日期</span><input v-model="filters.createdFrom" type="date" /></label>
      <label><span>结束日期</span><input v-model="filters.createdTo" type="date" /></label>
      <div class="admin-filter-actions"><button class="admin-button secondary" type="button" @click="reset">重置</button><button class="admin-button primary" type="submit">查询</button></div>
    </form>

    <div class="admin-list-summary"><span>共 {{ total }} 条任务</span><span v-if="loading">正在更新…</span></div>
    <section v-if="error" class="admin-inline-error" role="alert"><CircleAlert aria-hidden="true" /><span>{{ error }}</span><button class="admin-button secondary" type="button" @click="load">重试</button></section>
    <section class="admin-table-region" :aria-busy="loading" aria-label="生成任务列表">
      <table class="admin-table"><thead><tr><th>任务 / 会话</th><th>用户</th><th>状态</th><th>模型</th><th>产出</th><th>消耗</th><th>创建时间</th><th class="admin-table-action-heading">操作</th></tr></thead>
        <tbody><tr v-for="item in items" :key="item.id"><td><strong class="admin-task-prompt">{{ item.prompt }}</strong><small>{{ item.sessionTitle }}</small></td><td>{{ item.userPhoneMasked }}</td><td><span class="admin-status" :class="item.status">{{ statusLabels[item.status] || item.status }}</span></td><td><span>{{ item.model }}</span><small>{{ item.ratio }} · {{ item.resolution }}</small></td><td>{{ item.resultCount }} / {{ item.imageCount }}</td><td>{{ item.totalCost }} 点</td><td>{{ formatDate(item.createdAt) }}</td><td class="admin-table-action-cell"><button class="admin-icon-button" type="button" :aria-label="`查看任务 ${item.id}`" title="查看详情" :disabled="detailLoading" @click="open(item, $event)"><Eye aria-hidden="true" /></button></td></tr></tbody>
      </table>
      <div v-if="loading && !items.length" class="admin-empty-state"><LoaderCircle class="spin" aria-hidden="true" /><strong>正在加载任务</strong></div>
      <div v-else-if="!items.length" class="admin-empty-state"><Search aria-hidden="true" /><strong>没有符合条件的任务</strong><span>调整筛选条件后重新查询。</span></div>
    </section>
    <footer class="admin-pagination"><span>第 {{ page }} / {{ Math.max(1, pageCount) }} 页</span><div><button class="admin-icon-button bordered" type="button" aria-label="上一页" :disabled="loading || page <= 1" @click="changePage(page - 1)"><ChevronLeft aria-hidden="true" /></button><button class="admin-icon-button bordered" type="button" aria-label="下一页" :disabled="loading || page >= pageCount" @click="changePage(page + 1)"><ChevronRight aria-hidden="true" /></button></div></footer>

    <div v-if="detail" class="admin-drawer-backdrop" role="presentation" @mousedown.self="close">
      <aside class="admin-task-drawer" role="dialog" aria-modal="true" aria-labelledby="taskDetailTitle">
        <header><div><p>任务详情</p><h2 id="taskDetailTitle">{{ detail.sessionTitle }}</h2></div><button ref="closeButton" class="admin-icon-button" type="button" aria-label="关闭详情" @click="close"><X aria-hidden="true" /></button></header>
        <div class="admin-task-drawer-body"><div class="admin-detail-status-line"><span class="admin-status" :class="detail.status">{{ statusLabels[detail.status] || detail.status }}</span><span>{{ detail.userPhoneMasked }}</span><span>{{ formatDate(detail.createdAt) }}</span></div>
          <section><h3>提示词</h3><p class="admin-detail-prompt">{{ detail.prompt }}</p></section>
          <dl class="admin-detail-grid"><div><dt>模型</dt><dd>{{ detail.model }}</dd></div><div><dt>规格</dt><dd>{{ detail.ratio }} · {{ detail.resolution }}</dd></div><div><dt>生成数量</dt><dd>{{ detail.imageCount }} 张</dd></div><div><dt>额度消耗</dt><dd>{{ detail.totalCost }} 点</dd></div><div><dt>执行次数</dt><dd>{{ detail.attempts }} 次</dd></div><div><dt>开始时间</dt><dd>{{ formatDate(detail.startedAt) }}</dd></div><div><dt>完成时间</dt><dd>{{ formatDate(detail.completedAt) }}</dd></div><div><dt>输入审核</dt><dd>{{ moderationLabels[detail.inputModerationStatus] }}</dd></div><div><dt>输出审核</dt><dd>{{ moderationLabels[detail.outputModerationStatus] }}</dd></div></dl>
          <section v-if="detail.errorMessage" class="admin-detail-error"><h3>失败信息</h3><p>{{ detail.errorMessage }}</p></section>
          <section v-if="detail.deadLetter" class="admin-detail-error"><h3>死信记录</h3><p>{{ detail.deadLetter.errorCode }} · {{ detail.deadLetter.attempts }} 次尝试</p></section>
          <section><h3>生成结果</h3><div v-if="detail.results.length" class="admin-result-grid"><img v-for="result in detail.results" :key="result.id" :src="resolveAssetUrl(result.thumbnailUrl)" :alt="`生成结果 ${result.index + 1}`" /></div><p v-else class="admin-muted">暂无生成结果。</p></section>
          <section><h3>任务 ID</h3><code>{{ detail.id }}</code></section>
        </div>
      </aside>
    </div>
  </main>
</template>
