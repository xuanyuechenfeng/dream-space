<script setup lang="ts">
import { onMounted, ref } from "vue";
import { adminApi, type PricingRule } from "@/api/admin";
const rules = ref<PricingRule[]>([]); const error = ref("");
onMounted(async () => { try { rules.value = await adminApi.pricingRules(); } catch (e) { error.value = e instanceof Error ? e.message : "加载失败"; } });
</script>
<template><section class="admin-page"><header class="admin-page-header"><div><p class="admin-page-kicker">计费配置</p><h1>生成计费规则</h1><p>每张图的额度价格按版本发布，已提交任务保留规则快照。</p></div></header><p v-if="error" class="admin-error">{{ error }}</p><div class="admin-table-wrap"><table class="admin-table"><thead><tr><th>规则</th><th>分辨率</th><th>单张点数</th><th>生效窗口</th><th>状态</th></tr></thead><tbody><tr v-for="item in rules" :key="item.id"><td>{{ item.code }} v{{ item.version }}</td><td>{{ item.resolution }}</td><td>{{ item.unitCreditCost }}</td><td>{{ new Date(item.effectiveFrom).toLocaleString() }}</td><td>{{ item.status }}</td></tr><tr v-if="!rules.length"><td colspan="5">暂无规则</td></tr></tbody></table></div></section></template>
