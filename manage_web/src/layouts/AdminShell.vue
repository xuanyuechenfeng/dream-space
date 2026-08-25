<script setup lang="ts">
import { ClipboardList, Images, LogOut, PanelLeftClose, ShieldCheck, ShieldAlert, Users, CreditCard, SlidersHorizontal, ScrollText } from "lucide-vue-next";
import { ref } from "vue";
import { useRoute, useRouter } from "vue-router";
import { useAdminAuthStore } from "@/stores/adminAuth";

const auth = useAdminAuthStore();
const route = useRoute();
const router = useRouter();
const collapsed = ref(false);

async function logout() {
  await auth.logout();
  await router.replace("/login");
}
</script>

<template>
  <div class="admin-layout" :class="{ 'is-collapsed': collapsed }">
    <aside class="admin-sidebar">
      <div class="admin-sidebar-brand">
        <span class="admin-sidebar-logo" aria-hidden="true"><ShieldCheck /></span>
        <span class="admin-brand-text"><strong>造梦空间</strong><small>OPERATIONS</small></span>
        <button class="admin-icon-button admin-sidebar-toggle" type="button" :aria-label="collapsed ? '展开导航' : '收起导航'" @click="collapsed = !collapsed">
          <PanelLeftClose aria-hidden="true" />
        </button>
      </div>
      <nav aria-label="管理端导航">
        <RouterLink to="/tasks" :class="{ active: route.path.startsWith('/tasks') }"><ClipboardList aria-hidden="true" /><span>生成任务</span></RouterLink>
        <RouterLink to="/moderation" :class="{ active: route.path.startsWith('/moderation') }"><ShieldAlert aria-hidden="true" /><span>审核队列</span></RouterLink>
        <RouterLink to="/inspirations" :class="{ active: route.path.startsWith('/inspirations') }"><Images aria-hidden="true" /><span>灵感管理</span></RouterLink>
        <RouterLink to="/users" :class="{ active: route.path.startsWith('/users') }"><Users aria-hidden="true" /><span>用户管理</span></RouterLink>
        <RouterLink to="/billing/orders" :class="{ active: route.path.startsWith('/billing/orders') }"><CreditCard aria-hidden="true" /><span>支付订单</span></RouterLink>
        <RouterLink to="/billing/products" :class="{ active: route.path.startsWith('/billing/products') }"><SlidersHorizontal aria-hidden="true" /><span>额度产品</span></RouterLink>
        <RouterLink to="/billing/rules" :class="{ active: route.path.startsWith('/billing/rules') }"><SlidersHorizontal aria-hidden="true" /><span>计费规则</span></RouterLink>
        <RouterLink to="/audit-events" :class="{ active: route.path.startsWith('/audit-events') }"><ScrollText aria-hidden="true" /><span>操作审计</span></RouterLink>
      </nav>
      <div v-if="auth.session?.authenticated" class="admin-sidebar-account">
        <span class="admin-avatar">{{ auth.session.user.displayName.slice(0, 1) }}</span>
        <span class="admin-account-copy"><strong>{{ auth.session.user.displayName }}</strong><small>{{ auth.session.user.phoneMasked }}</small></span>
        <button class="admin-icon-button" type="button" aria-label="退出管理端" title="退出管理端" @click="logout"><LogOut aria-hidden="true" /></button>
      </div>
    </aside>
    <div class="admin-main"><RouterView /></div>
  </div>
</template>
