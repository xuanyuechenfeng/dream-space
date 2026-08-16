<script setup lang="ts">
import { ArrowUp, Image, LoaderCircle, Plus } from "lucide-vue-next";
import { computed, ref } from "vue";
import InspirationShell from "@/layouts/InspirationShell.vue";
import { usePreferencesStore } from "@/stores/preferences";

defineProps<{ sessionId?: string }>();
const preferences = usePreferencesStore(); const prompt = ref(""); const model = ref("image-4.7"); const ratio = ref("1:1"); const resolution = ref("2K"); const imageCount = ref(1); const submitting = ref(false); const notice = ref("");
const text = computed(() => preferences.language === "zh" ? { emptyTitle: "开始一段新的创作", emptyCopy: "输入提示词，或从灵感页选择一张作品开始。", prompt: "描述你想生成的画面", generate: "图片生成", submit: "提交生成", expected: "预计", credits: "点额度" } : { emptyTitle: "Start a new creation", emptyCopy: "Describe an image or choose a work from inspiration.", prompt: "Describe the image you want", generate: "Image generation", submit: "Submit generation", expected: "Estimated", credits: "credits" });
function submit() { if (!prompt.value.trim()) return; submitting.value = true; notice.value = preferences.language === "zh" ? "已加入生成队列" : "Added to generation queue"; window.setTimeout(() => { submitting.value = false; }, 700); }
</script>

<template>
  <InspirationShell active-page="generate">
    <main class="generation-page"><section class="timeline"><section class="empty-session"><h1>{{ text.emptyTitle }}</h1><p class="empty-session-copy">{{ text.emptyCopy }}</p><div class="starter-prompts"><button class="starter-prompt" type="button" @click="prompt = 'A quiet editorial portrait in natural afternoon light'"><span>Portrait in natural light</span></button><button class="starter-prompt" type="button" @click="prompt = 'A cinematic mountain landscape with soft mist'"><span>Cinematic landscape</span></button></div></section></section><section class="composer composer-shell" aria-label="Image generation"><div class="prompt-row"><button class="upload-btn" type="button" aria-label="添加参考图"><Plus aria-hidden="true" /></button><textarea v-model="prompt" rows="1" :placeholder="text.prompt" /></div><div class="composer-footer"><span class="field-btn static-field"><Image aria-hidden="true" />{{ text.generate }}</span><select v-model="model" class="field-btn"><option value="image-4.7">Image 4.7</option><option value="image-5-lite">Image 5.0 Lite</option></select><button class="field-btn" type="button" @click="ratio = ratio === '1:1' ? '16:9' : '1:1'">{{ ratio }}</button><button class="field-btn" type="button" @click="resolution = resolution === '2K' ? '4K' : '2K'">{{ resolution }}</button><button class="field-btn" type="button" @click="imageCount = imageCount === 4 ? 1 : imageCount + 1">{{ imageCount }} images</button><span class="cost-label">{{ text.expected }} {{ imageCount }} {{ text.credits }}</span><button class="submit-btn" type="button" :disabled="submitting || !prompt.trim()" :aria-label="text.submit" @click="submit"><LoaderCircle v-if="submitting" class="spin" aria-hidden="true" /><ArrowUp v-else aria-hidden="true" /></button></div><div v-if="notice" class="composer-error" role="status">{{ notice }}</div></section></main>
  </InspirationShell>
</template>
