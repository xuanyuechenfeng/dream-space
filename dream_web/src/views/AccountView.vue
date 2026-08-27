<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { Activity, ArrowDownLeft, ArrowUpRight, CalendarDays, CheckCircle2, Clock3, CreditCard, RefreshCw, ShoppingBag, Sparkles, WalletCards } from "lucide-vue-next";
import { api, type BillingAccount, type BillingLedgerItem, type BillingOrder, type BillingProduct } from "@/api/client";
import InspirationShell from "@/layouts/InspirationShell.vue";
type ActivityTab = "ledger" | "orders";

const account = ref<BillingAccount | null>(null);
const items = ref<BillingLedgerItem[]>([]);
const products = ref<BillingProduct[]>([]);
const orders = ref<BillingOrder[]>([]);
const activeTab = ref<ActivityTab>("ledger");
const error = ref("");
const loading = ref(true);
const refreshing = ref(false);
const buying = ref("");
const usedPercent = computed(() => {
  const total = account.value?.total ?? 0;
  return total ? Math.min(100, Math.round(((account.value?.used ?? 0) / total) * 100)) : 0;
});
const sortedProducts = computed(() => [...products.value].sort((a, b) => a.sortOrder - b.sortOrder));
function formatDate(value: string) { return new Intl.DateTimeFormat("zh-CN", { month: "short", day: "numeric", hour: "2-digit", minute: "2-digit" }).format(new Date(value)); }
function formatMoney(product: BillingProduct | BillingOrder) { return `${(product.amountMinor / 100).toFixed(2)} ${product.currency}`; }
function typeLabel(type: string) { return ({ GRANT: "额度发放", RESERVE: "生成预留", CONSUME: "生成消耗", RELEASE: "预留释放", REFUND: "订单退款" } as Record<string, string>)[type] ?? type; }
function isCredit(type: string) { return ["GRANT", "RELEASE", "REFUND"].includes(type); }
function signedAmount(item: BillingLedgerItem) { return `${isCredit(item.type) ? "+" : "-"}${item.amount}`; }
function statusLabel(status: string) { return ({ PAID: "已支付", PENDING: "待支付", CREATED: "待支付", CANCELLED: "已取消", EXPIRED: "已过期", REFUNDED: "已退款" } as Record<string, string>)[status] ?? status; }
function statusTone(status: string) { if (["PAID", "ACTIVE"].includes(status)) return "success"; if (["PENDING", "CREATED"].includes(status)) return "pending"; if (["CANCELLED", "EXPIRED", "REFUNDED"].includes(status)) return "muted"; return "neutral"; }
async function load() { const [a, l, p, o] = await Promise.all([api.account.account(), api.account.ledger(), api.account.products(), api.account.orders()]); account.value = a.account; items.value = l.items; products.value = p; orders.value = o.items; }
async function refresh() { refreshing.value = true; error.value = ""; try { await load(); } catch (e) { error.value = e instanceof Error ? e.message : "刷新失败，请稍后重试"; } finally { refreshing.value = false; } }
async function buy(product: BillingProduct) { buying.value = product.id; error.value = ""; try { await api.account.createOrder({ productId: product.id, quantity: 1, provider: "mock", idempotencyKey: `web-${product.id}-${Date.now()}` }); await load(); activeTab.value = "orders"; } catch (e) { error.value = e instanceof Error ? e.message : "订单创建失败"; } finally { buying.value = ""; } }
onMounted(async () => { try { await load(); } catch (e) { error.value = e instanceof Error ? e.message : "加载失败，请稍后重试"; } finally { loading.value = false; } });
</script>
<template>
  <InspirationShell>
    <section class="account-page">
      <header class="account-hero">
        <div><p class="eyebrow">ACCOUNT / BILLING</p><h1>额度与账单</h1><p class="account-hero-copy">管理创作额度，查看每一次生成与支付记录。</p></div>
        <button class="account-refresh" type="button" :disabled="loading || refreshing" @click="refresh"><RefreshCw :class="{ spin: refreshing }" :size="16" aria-hidden="true" /><span>{{ refreshing ? "刷新中" : "刷新数据" }}</span></button>
      </header>

      <p v-if="loading" class="account-feedback">正在加载账户信息...</p>
      <template v-else>
        <p v-if="error" class="account-error-banner" role="alert">{{ error }}</p>
        <template v-if="account">
        <section class="balance-panel" aria-labelledby="balance-title">
          <div class="balance-main">
            <div class="balance-heading"><span class="balance-icon"><WalletCards :size="19" aria-hidden="true" /></span><span>当前可用额度</span><span class="status-pill" :class="statusTone(account?.status ?? '')"><CheckCircle2 :size="13" aria-hidden="true" />{{ account?.status === "ACTIVE" ? "账户正常" : "暂有限制" }}</span></div>
            <div class="balance-value" id="balance-title">{{ account?.available ?? 0 }}<small>点</small></div>
            <div class="balance-meta"><span>总额度 {{ account?.total ?? 0 }} 点</span><span>已使用 {{ usedPercent }}%</span></div>
            <div class="balance-track" aria-hidden="true"><span :style="{ width: `${usedPercent}%` }" /></div>
          </div>
          <div class="balance-stats">
            <div><span class="stat-label"><Activity :size="15" aria-hidden="true" />已使用</span><strong>{{ account?.used ?? 0 }}<small>点</small></strong></div>
            <div><span class="stat-label"><Clock3 :size="15" aria-hidden="true" />预留中</span><strong>{{ account?.reserved ?? 0 }}<small>点</small></strong></div>
            <div><span class="stat-label"><CreditCard :size="15" aria-hidden="true" />账户标识</span><strong class="account-id">{{ account?.phoneMasked || account?.displayName || "已登录" }}</strong></div>
          </div>
        </section>

        <section class="purchase-section" aria-labelledby="purchase-title">
          <div class="section-heading"><div><p class="section-kicker">TOP UP</p><h2 id="purchase-title">购买额度</h2></div><span class="section-note">额度到账后可立即用于生成</span></div>
          <div v-if="sortedProducts.length" class="product-grid">
            <article v-for="(product, index) in sortedProducts" :key="product.id" class="product-card" :class="{ featured: index === 0 }">
              <div class="product-topline"><span class="product-code">{{ product.code }}</span><span v-if="index === 0" class="recommend-tag"><Sparkles :size="12" aria-hidden="true" />推荐</span></div>
              <h3>{{ product.name }}</h3><div class="product-credit">{{ product.creditAmount }}<small>点</small></div>
              <div class="product-price"><strong>{{ formatMoney(product) }}</strong><span>约 {{ (product.amountMinor / 100 / product.creditAmount).toFixed(3) }} / 点</span></div>
              <div class="product-detail"><span><CalendarDays :size="14" aria-hidden="true" />{{ product.validityDays ? `${product.validityDays} 天有效` : "长期有效" }}</span><span><ShoppingBag :size="14" aria-hidden="true" />一次性购买</span></div>
              <button class="purchase-btn" type="button" :disabled="buying === product.id" @click="buy(product)"><span>{{ buying === product.id ? "创建订单中..." : "购买此额度" }}</span><ArrowUpRight :size="16" aria-hidden="true" /></button>
            </article>
          </div>
          <p v-else class="inline-empty">暂无可购买产品</p>
        </section>

        <section class="activity-section" aria-labelledby="activity-title">
          <div class="section-heading activity-heading"><div><p class="section-kicker">ACTIVITY</p><h2 id="activity-title">账单记录</h2></div><span class="section-note">共 {{ activeTab === "ledger" ? items.length : orders.length }} 条记录</span></div>
          <div class="activity-tabs" role="tablist" aria-label="账单记录类型">
            <button class="activity-tab" :class="{ active: activeTab === 'ledger' }" type="button" role="tab" :aria-selected="activeTab === 'ledger'" @click="activeTab = 'ledger'"><Activity :size="16" aria-hidden="true" />额度流水</button>
            <button class="activity-tab" :class="{ active: activeTab === 'orders' }" type="button" role="tab" :aria-selected="activeTab === 'orders'" @click="activeTab = 'orders'"><CreditCard :size="16" aria-hidden="true" />支付订单</button>
          </div>
          <div class="table-shell" role="tabpanel">
            <table v-if="activeTab === 'ledger' && items.length"><thead><tr><th>时间</th><th>类型</th><th>点数变动</th><th>变动后余额</th><th>来源</th></tr></thead><tbody><tr v-for="item in items" :key="item.id"><td class="date-cell">{{ formatDate(item.createdAt) }}</td><td><span class="ledger-type"><ArrowDownLeft v-if="isCredit(item.type)" :size="14" /><ArrowUpRight v-else :size="14" />{{ typeLabel(item.type) }}</span></td><td class="amount-cell" :class="isCredit(item.type) ? 'positive' : 'negative'">{{ signedAmount(item) }}</td><td>{{ item.balanceAfter }} 点</td><td class="muted-cell">{{ item.sourceType || "系统调整" }}</td></tr></tbody></table>
            <table v-else-if="activeTab === 'orders' && orders.length"><thead><tr><th>订单号</th><th>产品</th><th>创建时间</th><th>金额</th><th>状态</th></tr></thead><tbody><tr v-for="order in orders" :key="order.orderNo"><td class="order-no">{{ order.orderNo }}</td><td><strong>{{ order.productName }}</strong><span class="table-subtext">{{ order.creditAmount * order.quantity }} 点</span></td><td class="date-cell">{{ formatDate(order.createdAt) }}</td><td><strong>{{ formatMoney(order) }}</strong></td><td><span class="status-pill" :class="statusTone(order.status)">{{ statusLabel(order.status) }}</span></td></tr></tbody></table>
            <div v-else class="table-empty"><CreditCard :size="22" aria-hidden="true" /><strong>{{ activeTab === "ledger" ? "暂无额度流水" : "暂无支付订单" }}</strong><span>{{ activeTab === "ledger" ? "购买或使用额度后，记录会显示在这里" : "购买额度后，订单详情会显示在这里" }}</span></div>
          </div>
        </section>
        </template>
      </template>
    </section>
  </InspirationShell>
</template>
