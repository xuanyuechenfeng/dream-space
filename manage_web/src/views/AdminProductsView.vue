<script setup lang="ts">
import { onMounted, ref } from "vue";
import { adminApi, type BillingProduct } from "@/api/admin";
const products = ref<BillingProduct[]>([]); const error = ref(""); const loading = ref(false);
async function load() { loading.value = true; try { products.value = await adminApi.products(); } catch (e) { error.value = e instanceof Error ? e.message : "加载失败"; } finally { loading.value = false; } }
async function toggle(item: BillingProduct) { try { await adminApi.setProductStatus(item.id, item.status === "ACTIVE" ? "INACTIVE" : "ACTIVE"); await load(); } catch (e) { error.value = e instanceof Error ? e.message : "更新失败"; } }
onMounted(load);
</script>
<template><section class="admin-page"><header class="admin-page-header"><div><p class="admin-page-kicker">计费配置</p><h1>额度产品</h1><p>产品价格和点数被订单引用后保持快照。</p></div><button class="admin-action-button" type="button" @click="load">刷新</button></header><p v-if="error" class="admin-error">{{ error }}</p><div class="admin-table-wrap"><table class="admin-table"><thead><tr><th>产品</th><th>额度</th><th>价格</th><th>状态</th><th /></tr></thead><tbody><tr v-for="item in products" :key="item.id"><td><strong>{{ item.name }}</strong><small>{{ item.code }}</small></td><td>{{ item.creditAmount }} 点</td><td>{{ (item.amountMinor / 100).toFixed(2) }} {{ item.currency }}</td><td>{{ item.status }}</td><td><button class="admin-link-button" type="button" @click="toggle(item)">{{ item.status === 'ACTIVE' ? '下架' : '上架' }}</button></td></tr><tr v-if="!loading && !products.length"><td colspan="5">暂无产品</td></tr></tbody></table></div></section></template>
