<script setup lang="ts">
import { onMounted, ref } from "vue";
import { adminApi, type AuditEvent, type Page } from "@/api/admin";
const page = ref<Page<AuditEvent>>({ items: [], total: 0, page: 1, pageSize: 30, pageCount: 0 }); const error = ref("");
onMounted(async () => { try { page.value = await adminApi.auditEvents({ page: 1, pageSize: 30 }); } catch (e) { error.value = e instanceof Error ? e.message : "加载失败"; } });
</script>
<template><section class="admin-page"><header class="admin-page-header"><div><p class="admin-page-kicker">合规记录</p><h1>操作审计</h1><p>记录用户、额度、规则、产品和退款操作。</p></div></header><p v-if="error" class="admin-error">{{ error }}</p><div class="admin-table-wrap"><table class="admin-table"><thead><tr><th>时间</th><th>操作人</th><th>动作</th><th>对象</th><th>原因</th></tr></thead><tbody><tr v-for="item in page.items" :key="item.id"><td>{{ new Date(item.createdAt).toLocaleString() }}</td><td>{{ item.actorId }}</td><td>{{ item.action }}</td><td>{{ item.subjectType }} / {{ item.subjectId }}</td><td>{{ item.reason || '-' }}</td></tr><tr v-if="!page.items.length"><td colspan="5">暂无审计记录</td></tr></tbody></table></div></section></template>
