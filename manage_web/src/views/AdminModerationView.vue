<script setup lang="ts">
import { Check, ChevronLeft, ChevronRight, CircleAlert, Eye, LoaderCircle, RefreshCw, Search, X } from "lucide-vue-next";
import { nextTick, onBeforeUnmount, onMounted, ref } from "vue";
import { useRouter } from "vue-router";
import { AdminApiError, adminApi, type ModerationCase, type ModerationDetail } from "@/api/admin";
import { useAdminAuthStore } from "@/stores/adminAuth";

const router = useRouter();
const auth = useAdminAuthStore();
const items = ref<ModerationCase[]>([]);
const detail = ref<ModerationDetail | null>(null);
const status = ref("PENDING");
const page = ref(1);
const pageCount = ref(1);
const total = ref(0);
const loading = ref(true);
const saving = ref(false);
const error = ref("");
const note = ref("");
const closeButton = ref<HTMLButtonElement>();
let opener: HTMLElement | null = null;

const labels: Record<string, string> = { PENDING: "待审核", REJECTED: "模型拒绝", APPEALED: "待申诉处理", APPROVED: "已通过" };
const role = () => auth.session?.authenticated ? auth.session.user.role : "viewer";
const canResolve = () => role() !== "viewer";
function date(value: string | null) { return value ? new Intl.DateTimeFormat("zh-CN", { month: "2-digit", day: "2-digit", hour: "2-digit", minute: "2-digit", hour12: false }).format(new Date(value)) : "-"; }
async function load() { loading.value = true; error.value = ""; try { const result = await adminApi.moderation.cases({ status: status.value, page: page.value, pageSize: 20 }); items.value = result.items; total.value = result.total; pageCount.value = Math.max(1, result.pageCount); } catch (cause) { await handle(cause); } finally { loading.value = false; } }
async function handle(cause: unknown) { if (cause instanceof AdminApiError && cause.status === 401) { await auth.refresh(); await router.replace("/login"); } error.value = cause instanceof Error ? cause.message : "加载审核队列失败"; }
async function open(item: ModerationCase, event: MouseEvent) { opener = event.currentTarget as HTMLElement; try { detail.value = await adminApi.moderation.detail(item.id); await nextTick(); closeButton.value?.focus(); } catch (cause) { await handle(cause); } }
function close() { detail.value = null; note.value = ""; nextTick(() => opener?.focus()); }
async function resolve(outcome: "APPROVED" | "REJECTED") { if (!detail.value || !canResolve()) return; saving.value = true; try { detail.value = await adminApi.moderation.resolve(detail.value.reviewCase.id, outcome, note.value); note.value = ""; await load(); } catch (cause) { await handle(cause); } finally { saving.value = false; } }
function keydown(event: KeyboardEvent) { if (event.key === "Escape" && detail.value) close(); }
onMounted(() => { window.addEventListener("keydown", keydown); void load(); });
onBeforeUnmount(() => window.removeEventListener("keydown", keydown));
</script>

<template>
  <main class="admin-page">
    <header class="admin-page-header"><div><p class="admin-page-kicker">内容安全</p><h1>审核队列</h1><p>处理模型拒绝、用户申诉和审核审计。</p></div><button class="admin-icon-button bordered" type="button" aria-label="刷新审核队列" title="刷新审核队列" :disabled="loading" @click="load"><RefreshCw :class="{ spin: loading }" aria-hidden="true" /></button></header>
    <form class="admin-filters" @submit.prevent="page = 1; load()"><label><span>状态</span><select v-model="status"><option v-for="(label, value) in labels" :key="value" :value="value">{{ label }}</option></select></label><div class="admin-filter-actions"><button class="admin-button primary" type="submit">查询</button></div></form>
    <div class="admin-list-summary"><span>共 {{ total }} 个案件</span><span v-if="loading">正在更新…</span></div>
    <section v-if="error" class="admin-inline-error" role="alert"><CircleAlert aria-hidden="true" /><span>{{ error }}</span><button class="admin-button secondary" type="button" @click="load">重试</button></section>
    <section class="admin-table-region" :aria-busy="loading" aria-label="审核案件列表"><table class="admin-table"><thead><tr><th>案件 / 任务</th><th>阶段</th><th>状态</th><th>原因</th><th>模型</th><th>创建时间</th><th>操作</th></tr></thead><tbody><tr v-for="item in items" :key="item.id"><td><strong class="admin-task-prompt">{{ item.id }}</strong><small>{{ item.taskId }}</small></td><td>{{ item.stage === "INPUT" ? "输入" : "输出" }}</td><td><span class="admin-status" :class="item.status.toLowerCase()">{{ labels[item.status] || item.status }}</span></td><td>{{ item.reasonCode }}</td><td><small>{{ item.model }} · {{ item.modelVersion }}</small></td><td>{{ date(item.createdAt) }}</td><td class="admin-table-action-cell"><button class="admin-icon-button" type="button" aria-label="查看审核案件" title="查看案件" @click="open(item, $event)"><Eye aria-hidden="true" /></button></td></tr></tbody></table><div v-if="loading && !items.length" class="admin-empty-state"><LoaderCircle class="spin" aria-hidden="true" /><strong>正在加载审核队列</strong></div><div v-else-if="!items.length" class="admin-empty-state"><Search aria-hidden="true" /><strong>当前没有审核案件</strong></div></section>
    <footer class="admin-pagination"><span>第 {{ page }} / {{ pageCount }} 页</span><div><button class="admin-icon-button bordered" type="button" aria-label="上一页" :disabled="loading || page <= 1" @click="page -= 1; load()"><ChevronLeft aria-hidden="true" /></button><button class="admin-icon-button bordered" type="button" aria-label="下一页" :disabled="loading || page >= pageCount" @click="page += 1; load()"><ChevronRight aria-hidden="true" /></button></div></footer>
    <div v-if="detail" class="admin-drawer-backdrop" role="presentation" @mousedown.self="close"><aside class="admin-task-drawer" role="dialog" aria-modal="true" aria-labelledby="moderationTitle"><header><div><p>审核案件</p><h2 id="moderationTitle">{{ detail.reviewCase.id }}</h2></div><button ref="closeButton" class="admin-icon-button" type="button" aria-label="关闭审核案件" @click="close"><X aria-hidden="true" /></button></header><div class="admin-task-drawer-body"><dl class="admin-detail-grid"><div><dt>任务</dt><dd>{{ detail.reviewCase.taskId }}</dd></div><div><dt>用户</dt><dd>{{ detail.reviewCase.userId }}</dd></div><div><dt>阶段</dt><dd>{{ detail.reviewCase.stage }}</dd></div><div><dt>状态</dt><dd>{{ labels[detail.reviewCase.status] || detail.reviewCase.status }}</dd></div><div><dt>原因</dt><dd>{{ detail.reviewCase.reasonCode }}</dd></div><div><dt>模型</dt><dd>{{ detail.reviewCase.model }} · {{ detail.reviewCase.modelVersion }}</dd></div></dl><section v-if="detail.appeal"><h3>用户申诉</h3><p class="admin-detail-prompt">{{ detail.appeal.reason }}</p></section><section><h3>处理备注</h3><textarea v-model="note" class="admin-inspiration-form" rows="4" maxlength="2000" :disabled="!canResolve()" placeholder="记录审核结论依据" /></section><div v-if="canResolve()" class="admin-editor-actions"><button class="admin-button secondary" type="button" :disabled="saving" @click="resolve('REJECTED')">拒绝</button><button class="admin-button primary" type="button" :disabled="saving" @click="resolve('APPROVED')"><Check aria-hidden="true" />通过</button></div><section><h3>审计记录</h3><ol><li v-for="event in detail.audit" :key="event.id">{{ event.action }} · {{ event.actorType }} · {{ date(event.createdAt) }}</li></ol></section></div></aside></div>
  </main>
</template>
