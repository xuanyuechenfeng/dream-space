<script setup lang="ts">
import {
  ArrowUp, Check, ChevronDown, ChevronUp, Copy, Download, Ellipsis, Heart, Image, ImagePlus,
  Link, LoaderCircle, Plus, RefreshCw, SlidersHorizontal, Unlink, X,
} from "lucide-vue-next";
import { computed, onMounted, onUnmounted, ref, watch } from "vue";
import { RouterLink, useRoute, useRouter } from "vue-router";
import {
  api, resolveAssetUrl, type GenerationDraft, type GenerationOptions, type GenerationRatio,
  type GenerationResolution, type Inspiration, type ReferenceUpload,
} from "@/api/client";
import {
  dimensionsForRatio, resizeCustomDimensions, resolutionOption, roundToStep, validateDimensions,
} from "@/features/generation/generationDimensions";
import InspirationShell from "@/layouts/InspirationShell.vue";
import { useAuthStore } from "@/stores/auth";
import { usePreferencesStore } from "@/stores/preferences";

const defaultOptions: GenerationOptions = {
  modes: ["AUTO"],
  ratios: ["smart", "21:9", "16:9", "3:2", "4:3", "1:1", "3:4", "2:3", "9:16"].map((value) => ({
    value: value as Exclude<GenerationRatio, "custom">,
    label: value === "smart" ? "智能" : value,
  })),
  resolutions: [
    { value: "2K", label: "高清 2K", maxEdge: 2048, maxPixels: 2048 * 2048, unitCost: 1, enabled: true, disabledReason: null },
    { value: "4K", label: "超清 4K", maxEdge: 4096, maxPixels: 4096 * 4096, unitCost: 2, enabled: false, disabledReason: "当前参数能力不可用" },
  ],
  dimensions: { minEdge: 512, step: 64 },
  referenceImages: { max: 2, maxBytes: 10 * 1024 * 1024, mimeTypes: ["image/jpeg", "image/png", "image/webp"] },
};

const blankDraft = (): GenerationDraft => ({
  mode: "AUTO", prompt: "", imageIds: [], ratio: "1:1", resolution: "2K", width: 2048, height: 2048,
});

const route = useRoute();
const router = useRouter();
const auth = useAuthStore();
const preferences = usePreferencesStore();
const inspiration = ref<Inspiration | null>(null);
const navigation = ref<Inspiration[]>([]);
const loading = ref(true);
const error = ref("");
const copied = ref(false);
const composerMode = ref<"same" | "reference" | null>(null);
const parameterPopover = ref<HTMLElement | null>(null);
const parameterTrigger = ref<HTMLElement | null>(null);
const attachmentFileInput = ref<HTMLInputElement | null>(null);
const parametersOpen = ref(false);
const sizeLocked = ref(true);
const uploading = ref(false);
const generationOptions = ref<GenerationOptions | null>(null);
const uploadedReferences = ref<ReferenceUpload[]>([]);
const draft = ref<GenerationDraft>(blankDraft());

const options = computed(() => generationOptions.value ?? defaultOptions);
const index = computed(() => navigation.value.findIndex((item) => item.slug === inspiration.value?.slug));
const previous = computed(() => index.value > 0 ? navigation.value[index.value - 1] : null);
const next = computed(() => index.value >= 0 && index.value < navigation.value.length - 1 ? navigation.value[index.value + 1] : null);
const selectedResolution = computed(() => resolutionOption(options.value.resolutions, draft.value.resolution));
const estimatedCost = computed(() => selectedResolution.value?.unitCost ?? 1);
const referenceCount = computed(() => uploadedReferences.value.length + (composerMode.value === "reference" ? 1 : 0));
const dimensionErrorCode = computed(() => validateDimensions(
  draft.value.ratio, draft.value.width, draft.value.height, options.value.dimensions.minEdge,
  options.value.dimensions.step, selectedResolution.value,
));
const dimensionError = computed(() => {
  const code = dimensionErrorCode.value;
  if (!code) return "";
  const zh: Record<string, string> = { DIMENSIONS_REQUIRED: "请输入宽度和高度", DIMENSIONS_INTEGER_REQUIRED: "宽高必须为整数", DIMENSIONS_TOO_SMALL: "宽高不能小于 512px", DIMENSIONS_STEP_INVALID: "宽高必须为 64 的整数倍", DIMENSIONS_TOO_LARGE: "尺寸超过当前分辨率限制", RATIO_MISMATCH: "宽高与所选比例不一致", RESOLUTION_UNAVAILABLE: "当前分辨率不可用", SMART_DIMENSIONS_FORBIDDEN: "智能比例不接受手动尺寸" };
  const en: Record<string, string> = { DIMENSIONS_REQUIRED: "Enter width and height", DIMENSIONS_INTEGER_REQUIRED: "Dimensions must be integers", DIMENSIONS_TOO_SMALL: "Dimensions must be at least 512px", DIMENSIONS_STEP_INVALID: "Dimensions must use 64px increments", DIMENSIONS_TOO_LARGE: "Dimensions exceed this resolution", RATIO_MISMATCH: "Dimensions do not match the selected ratio", RESOLUTION_UNAVAILABLE: "Resolution is unavailable", SMART_DIMENSIONS_FORBIDDEN: "Smart ratio cannot use manual dimensions" };
  return (preferences.language === "zh" ? zh : en)[code] ?? code;
});
const text = computed(() => preferences.language === "zh" ? {
  close: "关闭详情", previous: "上一个作品", next: "下一个作品", follow: "+ 关注", like: "点赞", more: "更多", ai: "内容由 AI 生成", promptLabel: "图片提示词", same: "做同款", reference: "用作参考图", copy: "复制提示词", copied: "已复制", download: "下载图片", generate: "图片生成", submit: "提交生成", failed: "作品不存在或暂时无法加载", attachment: "添加素材", material: "素材", parameters: "生成参数", ratio: "图片比例", smart: "智能", resolution: "分辨率", dimensions: "尺寸", aiDimensions: "由 AI 确定", lockRatio: "锁定比例", unlockRatio: "解除比例锁定", expected: "预计", credits: "点额度", prompt: "描述画面和素材关系，例如保留主体并参考另一张素材的配色", uploading: "上传中...", loginToUpload: "登录后可添加素材",
} : {
  close: "Close detail", previous: "Previous work", next: "Next work", follow: "+ Follow", like: "Like", more: "More", ai: "AI-generated content", promptLabel: "Image prompt", same: "Recreate", reference: "Use as reference", copy: "Copy prompt", copied: "Copied", download: "Download image", generate: "Image generation", submit: "Submit generation", failed: "This work is unavailable", attachment: "Add material", material: "Material", parameters: "Generation settings", ratio: "Aspect ratio", smart: "Smart", resolution: "Resolution", dimensions: "Dimensions", aiDimensions: "Determined by AI", lockRatio: "Lock ratio", unlockRatio: "Unlock ratio", expected: "Estimated", credits: "credits", prompt: "Describe the image and how the attached materials should be used", uploading: "Uploading...", loginToUpload: "Sign in to add materials",
});

function sourceDraft(detail: Inspiration): GenerationDraft {
  const ratio = options.value.ratios.some((item) => item.value === detail.ratio)
    ? detail.ratio as GenerationRatio
    : "custom";
  const inferredResolution: GenerationResolution = detail.resolutionLabel.toUpperCase().includes("4K") || Math.max(detail.width, detail.height) > 2048 ? "4K" : "2K";
  const resolution = resolutionOption(options.value.resolutions, inferredResolution)?.enabled ? inferredResolution : "2K";
  const selected = resolutionOption(options.value.resolutions, resolution);
  const step = options.value.dimensions.step;
  let width = roundToStep(detail.width, step);
  let height = roundToStep(detail.height, step);
  if (!selected || width > selected.maxEdge || height > selected.maxEdge || width * height > selected.maxPixels) {
    const resolved = dimensionsForRatio(ratio, selected?.maxEdge ?? 2048, step);
    width = resolved?.width ?? selected?.maxEdge ?? 2048;
    height = resolved?.height ?? selected?.maxEdge ?? 2048;
  }
  return { mode: "AUTO", prompt: detail.prompt, imageIds: [], ratio, resolution, width, height };
}

async function loadGenerationOptions() {
  try { generationOptions.value = await api.generation.options(); }
  catch { generationOptions.value = null; }
}

async function load() {
  loading.value = true;
  error.value = "";
  try {
    const slug = String(route.params.slug);
    const [detail, page] = await Promise.all([api.inspiration(slug), api.inspirations(new URLSearchParams())]);
    inspiration.value = detail;
    navigation.value = page.items;
    await loadGenerationOptions();
    draft.value = sourceDraft(detail);
    composerMode.value = null;
    parametersOpen.value = false;
    uploadedReferences.value = [];
  } catch {
    inspiration.value = null;
    navigation.value = [];
    error.value = text.value.failed;
  } finally {
    loading.value = false;
  }
}

async function copyPrompt() {
  if (!inspiration.value) return;
  try {
    await navigator.clipboard.writeText(inspiration.value.prompt);
    copied.value = true;
    window.setTimeout(() => copied.value = false, 1600);
  } catch {
    error.value = preferences.language === "zh" ? "复制失败，请手动选择提示词" : "Copy failed. Select the prompt manually.";
  }
}

function openComposer(mode: "same" | "reference") {
  if (!inspiration.value) return;
  composerMode.value = mode;
  draft.value = sourceDraft(inspiration.value);
  uploadedReferences.value = [];
  parametersOpen.value = false;
}

function selectRatio(ratio: GenerationRatio) {
  draft.value.ratio = ratio;
  sizeLocked.value = ratio !== "custom";
  if (ratio === "smart") {
    draft.value.width = null;
    draft.value.height = null;
    return;
  }
  const size = selectedResolution.value
    ? dimensionsForRatio(ratio, selectedResolution.value.maxEdge, options.value.dimensions.step)
    : null;
  if (size) Object.assign(draft.value, size);
}

function selectResolution(resolution: GenerationResolution) {
  const nextResolution = resolutionOption(options.value.resolutions, resolution);
  if (!nextResolution?.enabled) return;
  draft.value.resolution = resolution;
  if (draft.value.ratio === "smart") return;
  const step = options.value.dimensions.step;
  const size = draft.value.ratio === "custom"
    ? resizeCustomDimensions(draft.value.width, draft.value.height, nextResolution.maxEdge, step)
    : dimensionsForRatio(draft.value.ratio, nextResolution.maxEdge, step);
  if (size) Object.assign(draft.value, size);
}

function updateDimension(axis: "width" | "height") {
  const value = draft.value[axis];
  const current = selectedResolution.value;
  const step = options.value.dimensions.step;
  const min = options.value.dimensions.minEdge;
  if (value == null || !Number.isFinite(value) || value < min || !current || value > current.maxEdge) return;
  draft.value[axis] = roundToStep(value, step);
  if (!sizeLocked.value || draft.value.ratio === "custom") {
    draft.value.ratio = "custom";
    return;
  }
  const size = dimensionsForRatio(draft.value.ratio, current.maxEdge, step);
  if (!size) return;
  const factor = axis === "width" ? draft.value.width! / size.width : draft.value.height! / size.height;
  const other = axis === "width" ? "height" : "width";
  draft.value[other] = roundToStep(size[other] * factor, step);
}

function toggleSizeLock() {
  sizeLocked.value = !sizeLocked.value;
  if (!sizeLocked.value && draft.value.ratio !== "smart") draft.value.ratio = "custom";
}

function closeParametersOnOutsidePointer(event: PointerEvent) {
  if (!parametersOpen.value) return;
  const target = event.target;
  if (target instanceof Node && (parameterPopover.value?.contains(target) || parameterTrigger.value?.contains(target))) return;
  parametersOpen.value = false;
}

function pendingIntent(imageIds = draft.value.imageIds, submitOnRestore = false) {
  return {
    returnTo: "/generate",
    action: "generate",
    submitOnRestore,
    referenceInspirationSlug: composerMode.value === "reference" ? inspiration.value?.slug ?? null : null,
    draft: { ...draft.value, imageIds: [...imageIds] },
  };
}

function requestAttachment() {
  if (auth.session?.authenticated) {
    attachmentFileInput.value?.click();
    return;
  }
  sessionStorage.setItem("dream-space-pending-auth-intent", JSON.stringify(pendingIntent()));
  void router.push({ path: "/login", query: { returnTo: "/generate" } });
}

async function onFileChange(event: Event) {
  const files = Array.from((event.target as HTMLInputElement).files ?? []);
  if (!files.length) return;
  uploading.value = true;
  error.value = "";
  try {
    for (const file of files.slice(0, Math.max(0, options.value.referenceImages.max - referenceCount.value))) {
      const upload = await api.generation.uploadReference(file);
      uploadedReferences.value.push(upload);
      draft.value.imageIds.push(upload.id);
    }
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : text.value.uploading;
  } finally {
    uploading.value = false;
    if (attachmentFileInput.value) attachmentFileInput.value.value = "";
  }
}

function removeReference(id: string) {
  uploadedReferences.value = uploadedReferences.value.filter((item) => item.id !== id);
  draft.value.imageIds = draft.value.imageIds.filter((imageId) => imageId !== id);
}

async function submit() {
  if (!inspiration.value || !draft.value.prompt.trim()) return;
  if (dimensionError.value) {
    error.value = dimensionError.value;
    parametersOpen.value = true;
    return;
  }
  try {
    let imageIds = [...draft.value.imageIds];
    if (composerMode.value === "reference" && auth.session?.authenticated) {
      const response = await fetch(resolveAssetUrl(inspiration.value.imageUrl), { credentials: "include" });
      if (!response.ok) throw new Error(preferences.language === "zh" ? "参考图读取失败" : "Unable to load reference image");
      const blob = await response.blob();
      const upload = await api.generation.uploadReference(new File([blob], `${inspiration.value.slug}.webp`, { type: blob.type || "image/webp" }));
      imageIds = [upload.id, ...imageIds].slice(0, options.value.referenceImages.max);
    }
    const intent = pendingIntent(imageIds, true);
    sessionStorage.setItem("dream-space-pending-auth-intent", JSON.stringify(intent));
    if (!auth.session?.authenticated) {
      await router.push({ path: "/login", query: { returnTo: "/generate" } });
      return;
    }
    sessionStorage.setItem("dream-space-restored-auth-intent", JSON.stringify(intent));
    sessionStorage.removeItem("dream-space-pending-auth-intent");
    await router.push("/generate");
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : preferences.language === "zh" ? "生成准备失败" : "Unable to prepare generation";
  }
}

watch(() => route.params.slug, load);
onMounted(() => {
  document.addEventListener("pointerdown", closeParametersOnOutsidePointer);
  void load();
});
onUnmounted(() => document.removeEventListener("pointerdown", closeParametersOnOutsidePointer));
</script>

<template>
  <InspirationShell>
    <div v-if="loading" class="generation-loading">Loading...</div>
    <section v-else-if="error && !inspiration" class="empty-state" role="alert"><div><strong>{{ error }}</strong><RouterLink class="action-btn" to="/inspiration">{{ text.close }}</RouterLink></div></section>
    <template v-else-if="inspiration">
      <div class="detail-layout" :class="{ 'has-detail-composer': composerMode }">
        <section class="detail-stage" aria-label="Artwork image"><img class="detail-image" :src="resolveAssetUrl(inspiration.imageUrl)" :alt="inspiration.title" :width="inspiration.width" :height="inspiration.height" /><RouterLink class="icon-btn detail-close" to="/inspiration" :aria-label="text.close"><X aria-hidden="true" /></RouterLink><div class="pager"><button class="icon-btn" type="button" :disabled="!previous" :aria-label="text.previous" @click="previous && router.push(`/inspiration/${previous.slug}`)"><ChevronUp aria-hidden="true" /></button><button class="icon-btn" type="button" :disabled="!next" :aria-label="text.next" @click="next && router.push(`/inspiration/${next.slug}`)"><ChevronDown aria-hidden="true" /></button></div></section>
        <aside class="detail-panel"><div class="author-line"><div class="avatar">{{ inspiration.authorDisplayName.slice(0, 1).toUpperCase() }}</div><strong>{{ inspiration.authorDisplayName }}</strong><button class="follow-btn" type="button" disabled :aria-label="text.follow">{{ text.follow }}</button><div class="panel-actions"><button class="icon-btn" type="button" disabled :aria-label="text.like"><Heart aria-hidden="true" /></button><span>{{ inspiration.likeCount }}</span><button class="icon-btn" type="button" :aria-label="text.more"><Ellipsis aria-hidden="true" /></button></div></div><p class="detail-copy">{{ inspiration.title }}</p><p class="detail-date">{{ inspiration.publishedAt || '2026-07-31' }} · {{ text.ai }}</p><div class="prompt-label">{{ text.promptLabel }}</div><p class="prompt-text">{{ inspiration.prompt }}</p><div class="param-line"><span>{{ inspiration.modelName }}</span><span>|</span><span>{{ inspiration.ratio }}</span><span>|</span><span>{{ inspiration.resolutionLabel }}</span></div><div class="detail-actions"><button class="action-btn" type="button" @click="openComposer('same')"><RefreshCw aria-hidden="true" />{{ text.same }}</button><button class="action-btn" type="button" @click="openComposer('reference')"><ImagePlus aria-hidden="true" />{{ text.reference }}</button><button class="action-btn" type="button" @click="copyPrompt"><Check v-if="copied" aria-hidden="true" /><Copy v-else aria-hidden="true" />{{ copied ? text.copied : text.copy }}</button><a class="action-btn" :href="resolveAssetUrl(inspiration.imageUrl)" download><Download aria-hidden="true" />{{ text.download }}</a></div></aside>
      </div>

      <section v-if="composerMode" class="composer composer-shell detail-composer is-expanded" aria-label="Image generation">
        <div v-if="parametersOpen" ref="parameterPopover" class="parameter-popover">
          <div class="param-section"><div class="param-heading"><span>{{ text.ratio }}</span><span class="size-preview">{{ draft.ratio === 'smart' ? text.aiDimensions : `${draft.width} x ${draft.height}px` }}</span></div><div class="option-grid"><button v-for="option in options.ratios" :key="option.value" class="option-btn" :class="{ active: draft.ratio === option.value }" type="button" @click="selectRatio(option.value)">{{ option.value === 'smart' ? text.smart : option.label }}</button></div></div>
          <div class="param-section"><div class="param-heading"><span>{{ text.resolution }}</span></div><div class="option-grid resolution-grid"><button v-for="option in options.resolutions" :key="option.value" class="option-btn" :class="{ active: draft.resolution === option.value }" type="button" :disabled="!option.enabled" :title="option.disabledReason || ''" @click="selectResolution(option.value)">{{ option.label }}</button></div></div>
          <div v-if="draft.ratio !== 'smart'" class="param-section"><div class="param-heading"><span>{{ text.dimensions }}</span><button class="icon-btn" type="button" :aria-label="sizeLocked ? text.unlockRatio : text.lockRatio" :title="sizeLocked ? text.unlockRatio : text.lockRatio" @click="toggleSizeLock"><Link v-if="sizeLocked" aria-hidden="true" /><Unlink v-else aria-hidden="true" /></button></div><div class="dimension-fields"><label>W<input v-model.number="draft.width" type="number" min="512" step="64" @blur="updateDimension('width')" /></label><span>×</span><label>H<input v-model.number="draft.height" type="number" min="512" step="64" @blur="updateDimension('height')" /></label><span>PX</span></div><p v-if="dimensionError" class="dimension-error">{{ dimensionError }}</p></div>
        </div>

        <div v-if="referenceCount" class="ref-strip">
          <div v-if="composerMode === 'reference'" class="ref-item"><img class="ref-thumb" :src="resolveAssetUrl(inspiration.thumbnailUrl)" :alt="`${text.material} 1`" /><span class="ref-label">{{ text.material }} 1</span><button class="ref-remove" type="button" :aria-label="`${text.material} 1`" @click="composerMode = 'same'"><X aria-hidden="true" /></button></div>
          <div v-for="(item, index) in uploadedReferences" :key="item.id" class="ref-item"><img class="ref-thumb" :src="resolveAssetUrl(item.url)" :alt="`${text.material} ${index + (composerMode === 'reference' ? 2 : 1)}`" /><span class="ref-label">{{ text.material }} {{ index + (composerMode === 'reference' ? 2 : 1) }}</span><button class="ref-remove" type="button" :aria-label="`${text.material} ${index + 1}`" @click="removeReference(item.id)"><X aria-hidden="true" /></button></div>
        </div>
        <div class="prompt-row"><button class="upload-btn" type="button" :aria-label="auth.session?.authenticated ? text.attachment : text.loginToUpload" :title="auth.session?.authenticated ? text.attachment : text.loginToUpload" :disabled="uploading || referenceCount >= options.referenceImages.max" @click="requestAttachment"><LoaderCircle v-if="uploading" class="spin" aria-hidden="true" /><Plus v-else aria-hidden="true" /></button><input ref="attachmentFileInput" class="visually-hidden" type="file" multiple :accept="options.referenceImages.mimeTypes.join(',')" @change="onFileChange" /><textarea v-model="draft.prompt" rows="1" :placeholder="text.prompt" /></div>
        <div class="composer-footer"><span class="field-btn static-field"><Image aria-hidden="true" />{{ text.generate }}</span><button ref="parameterTrigger" class="field-btn parameter-trigger" type="button" :aria-expanded="parametersOpen" :aria-label="text.parameters" @click="parametersOpen = !parametersOpen"><SlidersHorizontal aria-hidden="true" />{{ text.parameters }}</button><span class="cost-label">{{ text.expected }} {{ estimatedCost }} {{ text.credits }}</span><button class="submit-btn" type="button" :disabled="!draft.prompt.trim() || Boolean(dimensionError) || uploading" :aria-label="text.submit" @click="submit"><ArrowUp aria-hidden="true" /></button></div>
        <div v-if="error" class="composer-error" role="alert">{{ error }}</div>
      </section>
    </template>
  </InspirationShell>
</template>
