<script setup lang="ts">
import { CalendarDays, Check, ChevronDown, CloudSun, RefreshCw, Search, Sparkles, Trash2, X } from "lucide-vue-next";
import { computed, onMounted, onUnmounted, ref, watch } from "vue";
import { RouterLink } from "vue-router";
import { api, resolveAssetUrl, type InspirationPage } from "@/api/client";
import InspirationShell from "@/layouts/InspirationShell.vue";
import { usePreferencesStore } from "@/stores/preferences";

const preferences = usePreferencesStore();
const categories = [ { id: "portrait", zh: "人像", en: "Portrait" }, { id: "photography", zh: "摄影", en: "Photography" }, { id: "anime", zh: "动漫", en: "Anime" }, { id: "illustration", zh: "插画", en: "Illustration" }, { id: "design", zh: "设计", en: "Design" } ];
const category = ref("all"); const query = ref(""); const response = ref<InspirationPage | null>(null); const loading = ref(true); const error = ref(false); const searchOpen = ref(false); const languageOpen = ref(false); const requestVersion = ref(0); const history = ref<string[]>([]); let timer = 0; let controller: AbortController | null = null; let firstSlug = "";
const text = computed(() => preferences.language === "zh" ? { recommended: "推荐", search: "搜索主题、风格或提示词", history: "搜索历史", clear: "清空", empty: "没有找到相关灵感", emptyCopy: "尝试其他关键词，或返回推荐内容。", reset: "返回推荐", failed: "灵感暂时没有加载成功", retry: "重新加载", same: "做同款", focus: "把想象变成看得见的作品。" } : { recommended: "For you", search: "Search themes, styles or prompts", history: "Search history", clear: "Clear", empty: "No inspiration found", emptyCopy: "Try another keyword or return to recommended works.", reset: "Back to For you", failed: "Inspiration could not be loaded", retry: "Try again", same: "Recreate", focus: "Turn imagination into visible work." });
const date = computed(() => new Intl.DateTimeFormat(preferences.language === "zh" ? "zh-CN" : "en-US", { month: "short", day: "numeric", weekday: "short" }).format(new Date()));
function saveHistory(value: string) { const clean = value.trim(); if (!clean) return; history.value = [clean, ...history.value.filter((item) => item !== clean)].slice(0, 8); localStorage.setItem("dream-space-search-history", JSON.stringify(history.value)); }
function clearHistory() { history.value = []; localStorage.removeItem("dream-space-search-history"); }
function shuffle(data: InspirationPage) { const items = [...data.items]; for (let i = items.length - 1; i > 0; i--) { const j = Math.floor(Math.random() * (i + 1)); [items[i], items[j]] = [items[j]!, items[i]!]; } if (items.length > 1 && items[0]?.slug === firstSlug) [items[0], items[1]] = [items[1]!, items[0]!]; firstSlug = items[0]?.slug || ""; return { ...data, items }; }
async function load() { controller?.abort(); controller = new AbortController(); loading.value = true; error.value = false; const params = new URLSearchParams(); if (category.value !== "all") params.set("category", category.value); if (query.value.trim()) params.set("q", query.value.trim()); try { response.value = shuffle(await api.inspirations(params, controller.signal)); saveHistory(query.value); } catch (e) { if ((e as Error).name === "AbortError") return; error.value = true; } finally { loading.value = false; } }
function scheduleLoad() { window.clearTimeout(timer); timer = window.setTimeout(load, 220); }
function closePanels(event: MouseEvent | KeyboardEvent) { if (event instanceof KeyboardEvent && event.key !== "Escape") return; const target = event.target as HTMLElement | null; if (target?.closest(".search-wrap,.language-control")) return; searchOpen.value = false; languageOpen.value = false; }
watch([category, query, requestVersion], scheduleLoad);
onMounted(() => { try { history.value = JSON.parse(localStorage.getItem("dream-space-search-history") || "[]"); } catch { history.value = []; } load(); window.addEventListener("mousedown", closePanels); window.addEventListener("keydown", closePanels); });
onUnmounted(() => { controller?.abort(); window.clearTimeout(timer); window.removeEventListener("mousedown", closePanels); window.removeEventListener("keydown", closePanels); });
</script>

<template>
  <InspirationShell>
    <header class="toolbar">
      <button class="category-btn" :class="{ active: category === 'all' }" type="button" @click="category = 'all'">{{ text.recommended }}</button>
      <button v-for="item in categories" :key="item.id" class="category-btn" :class="{ active: category === item.id }" type="button" @click="category = item.id">{{ preferences.language === 'zh' ? item.zh : item.en }}</button>
      <div class="search-wrap">
        <Search aria-hidden="true" /><input v-model="query" class="search-input" :placeholder="text.search" @focus="searchOpen = true" />
        <button v-if="query" class="icon-btn search-clear" type="button" aria-label="清空搜索" @click="query = ''"><X aria-hidden="true" /></button>
        <div v-if="searchOpen" class="search-history-panel"><div class="search-history-header"><strong>{{ text.history }}</strong><button class="utility-button search-history-clear" type="button" @click="clearHistory"><Trash2 aria-hidden="true" />{{ text.clear }}</button></div><div v-if="history.length" class="search-history-list"><button v-for="item in history" :key="item" class="search-history-chip" type="button" @click="query = item; searchOpen = false">{{ item }}</button></div><div v-else class="search-history-empty">{{ text.focus }}</div></div>
      </div>
      <div class="toolbar-end"><div class="utility-bar"><span class="utility-meta motivation-button"><CloudSun aria-hidden="true" />{{ text.focus }}</span><span class="utility-divider motivation-divider" /><span class="utility-meta date-utility"><CalendarDays aria-hidden="true" />{{ date }}</span><span class="utility-divider date-divider" /><div class="language-control"><button class="utility-button language-trigger" type="button" :aria-expanded="languageOpen" @click="languageOpen = !languageOpen"><span>{{ preferences.language === 'zh' ? '中文' : 'EN' }}</span><ChevronDown aria-hidden="true" /></button><div v-if="languageOpen" class="language-menu"><button class="language-option" :class="{ active: preferences.language === 'en' }" type="button" @click="preferences.setLanguage('en'); languageOpen = false"><span>EN</span><span>English</span><Check aria-hidden="true" /></button><button class="language-option" :class="{ active: preferences.language === 'zh' }" type="button" @click="preferences.setLanguage('zh'); languageOpen = false"><span>中</span><span>中文</span><Check aria-hidden="true" /></button></div></div></div></div>
    </header>

    <section v-if="loading" class="masonry" aria-label="Loading inspiration"><div v-for="ratio in [0.72,1.2,0.82,1,0.68,1.35,0.9,1.1]" :key="ratio" class="art-skeleton" :style="{ aspectRatio: ratio }" /></section>
    <section v-else-if="error" class="empty-state" role="alert"><div><Sparkles aria-hidden="true" /><strong>{{ text.failed }}</strong><button class="action-btn" type="button" @click="requestVersion++"><RefreshCw aria-hidden="true" />{{ text.retry }}</button></div></section>
    <section v-else-if="response && !response.items.length" class="empty-state"><div><strong>{{ text.empty }}</strong><span>{{ text.emptyCopy }}</span><br /><br /><button class="action-btn" type="button" @click="query = ''; category = 'all'">{{ text.reset }}</button></div></section>
    <section v-else class="masonry" aria-label="Inspiration works"><RouterLink v-for="item in response?.items" :key="item.id" class="art-card" :to="`/inspiration/${item.slug}`"><img :src="resolveAssetUrl(item.thumbnailUrl)" :alt="item.title" :width="item.width" :height="item.height" loading="lazy" /><span class="art-overlay"><span class="art-meta"><span class="art-title">{{ item.title }}</span><span class="art-author">{{ item.authorDisplayName }}</span></span><span class="same-chip">{{ text.same }}</span></span></RouterLink></section>
  </InspirationShell>
</template>
