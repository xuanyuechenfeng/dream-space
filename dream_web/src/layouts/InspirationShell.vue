<script setup lang="ts">
import { Bell, ChevronRight, FileText, House, LogIn, LogOut, ScrollText, Settings, Sparkles, SunMoon, UserRound, X } from "lucide-vue-next";
import { computed, onMounted, onUnmounted, ref } from "vue";
import { RouterLink, useRouter } from "vue-router";
import { api } from "@/api/client";
import { useAuthStore } from "@/stores/auth";
import { usePreferencesStore, type Theme } from "@/stores/preferences";

withDefaults(defineProps<{ activePage?: "inspiration" | "generate" }>(), { activePage: "inspiration" });
const preferences = usePreferencesStore();
const auth = useAuthStore();
const router = useRouter();
const accountOpen = ref(false); const themeOpen = ref(false); const legalOpen = ref(false); const loggingOut = ref(false); const watermark = ref(true); const quota = ref<{ total: number; available: number; remainingPercent: number } | null>(null);
const themeLabels: Record<Theme, { zh: string; en: string }> = { system: { zh: "跟随系统", en: "System" }, light: { zh: "浅色", en: "Light" }, dark: { zh: "深色", en: "Dark" } };
const language = computed(() => preferences.language);
const text = computed(() => language.value === "zh" ? { inspiration: "灵感", generate: "生成", mine: "我的", notification: "通知（暂不可用）", settings: "设置", legal: "平台协议", changelog: "更新日志（暂不可用）", appearance: "外观", watermark: "AI 水印", login: "登录", logout: "退出登录", quota: "创作额度", cycle: "本周期剩余", remaining: `剩余 ${quota.value?.remainingPercent ?? 0}%`, cost: "每张预计 1 点" } : { inspiration: "Explore", generate: "Create", mine: "Account", notification: "Notifications (unavailable)", settings: "Settings", legal: "Platform terms", changelog: "Changelog (unavailable)", appearance: "Appearance", watermark: "AI watermark", login: "Sign in", logout: "Sign out", quota: "Creation quota", cycle: "Remaining this cycle", remaining: `${quota.value?.remainingPercent ?? 0}% remaining`, cost: "About 1 credit per image" });
function closeMenus(event: MouseEvent | KeyboardEvent) { if (event instanceof KeyboardEvent && event.key !== "Escape") return; const target = event.target as Node | null; if (target && (target as HTMLElement).closest?.(".account-menu, .account-avatar")) return; accountOpen.value = false; themeOpen.value = false; }
function openLogin() { accountOpen.value = false; router.push({ path: "/login", query: { returnTo: router.currentRoute.value.fullPath } }); }
async function logout() { loggingOut.value = true; try { await auth.logout(); accountOpen.value = false; } finally { loggingOut.value = false; } }
async function loadQuota() { if (!auth.session?.authenticated) { quota.value = null; return; } try { quota.value = await api.generation.quota(); } catch { quota.value = null; } }
onMounted(() => { window.addEventListener("mousedown", closeMenus); window.addEventListener("keydown", closeMenus); void loadQuota(); });
onUnmounted(() => { window.removeEventListener("mousedown", closeMenus); window.removeEventListener("keydown", closeMenus); });
</script>

<template>
  <div class="app" :data-language="language">
    <nav class="primary-nav" :aria-label="language === 'zh' ? '主导航' : 'Primary navigation'">
      <RouterLink class="brand-mark" to="/inspiration" aria-label="返回灵感" />
      <div class="nav-stack">
        <RouterLink class="nav-btn" :class="{ active: activePage === 'inspiration' }" to="/inspiration"><House aria-hidden="true" /><span>{{ text.inspiration }}</span></RouterLink>
        <RouterLink class="nav-btn" :class="{ active: activePage === 'generate' }" to="/generate"><Sparkles aria-hidden="true" /><span>{{ text.generate }}</span></RouterLink>
        <RouterLink class="nav-btn mobile-only" to="/login"><UserRound aria-hidden="true" /><span>{{ text.mine }}</span></RouterLink>
      </div>
      <div class="nav-spacer" />
      <div class="desktop-dock">
        <button class="dock-icon dock-tooltip" type="button" disabled :aria-label="text.notification" :data-tooltip="text.notification"><Bell aria-hidden="true" /></button>
        <div class="dock-divider" />
        <button class="account-avatar dock-tooltip" :class="{ 'logged-in': auth.session?.authenticated }" type="button" :aria-label="text.settings" :aria-expanded="accountOpen" :data-tooltip="text.settings" @mousedown.stop @click="accountOpen = !accountOpen"><Settings aria-hidden="true" /></button>
      </div>
    </nav>

    <section v-if="accountOpen" class="account-menu" aria-label="Account and settings" @mousedown.stop>
      <button class="menu-row" type="button" @click="legalOpen = true"><FileText aria-hidden="true" />{{ text.legal }}<ChevronRight class="menu-end" aria-hidden="true" /></button>
      <button class="menu-row" type="button" disabled><ScrollText aria-hidden="true" />{{ text.changelog }}<span class="menu-end">-</span></button>
      <button class="menu-row" type="button" :aria-expanded="themeOpen" @click="themeOpen = !themeOpen"><SunMoon aria-hidden="true" />{{ text.appearance }}<span class="menu-end">{{ themeLabels[preferences.theme][language] }}</span></button>
      <div v-if="themeOpen" class="theme-options"><button v-for="item in (['system', 'light', 'dark'] as Theme[])" :key="item" class="menu-row" :class="{ active: preferences.theme === item }" type="button" @click="preferences.setTheme(item)">{{ themeLabels[item][language] }}</button></div>
      <label class="menu-row"><Sparkles aria-hidden="true" />{{ text.watermark }}<input v-model="watermark" class="menu-switch" type="checkbox" /></label>
      <section v-if="auth.session?.authenticated" class="quota-panel" aria-label="Creation quota"><div class="quota-heading"><Sparkles aria-hidden="true" /><span class="quota-title"><strong>{{ text.quota }}</strong><small>{{ text.cycle }}</small></span><strong class="quota-value">{{ quota?.available ?? 0 }} / {{ quota?.total ?? 0 }}</strong></div><div class="quota-track"><span :style="{ width: `${quota?.remainingPercent ?? 0}%` }" /></div><div class="quota-meta"><span>{{ text.remaining }}</span><span>{{ text.cost }}</span></div></section>
      <button v-if="auth.session?.authenticated" class="menu-row" type="button" :disabled="loggingOut" @click="logout"><LogOut aria-hidden="true" /><span>{{ text.logout }}</span></button>
      <button v-else class="menu-row" type="button" @click="openLogin"><LogIn aria-hidden="true" /><span>{{ auth.loading ? '...' : text.login }}</span></button>
      <div class="menu-footer">造梦空间 Dream Space<br />让每一次生成都可控、可追溯。</div>
    </section>

    <div v-if="legalOpen" class="legal-backdrop" role="presentation" @mousedown.self="legalOpen = false">
      <section class="legal-dialog" role="dialog" aria-modal="true" aria-labelledby="platformLegalTitle">
        <header class="legal-header"><h2 id="platformLegalTitle">造梦空间用户协议</h2><button class="icon-btn" type="button" aria-label="关闭协议" @click="legalOpen = false"><X aria-hidden="true" /></button></header>
        <div class="legal-content"><h3>一、协议范围</h3><p>本协议适用于用户访问和使用造梦空间提供的 AI 图片生成、灵感浏览及相关服务。</p><h3>二、账号与使用规范</h3><p>用户应提供真实、合法的注册信息并妥善保管账号，不得传播违法、侵权或危害他人权益的内容。</p><h3>三、输入与生成内容</h3><p>用户应确保对上传的提示词、参考图片及其他素材拥有必要权利。AI 生成结果具有不确定性，请在公开使用前自行审核。</p><h3>四、个人信息保护</h3><p>平台仅在实现账号登录、任务处理、安全审计和服务改进所必要的范围内处理个人信息。</p></div>
      </section>
    </div>
    <main class="page"><slot /></main>
  </div>
</template>
