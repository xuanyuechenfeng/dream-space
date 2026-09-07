<script setup lang="ts">
import { onUnmounted, ref, watch } from "vue";
import { evictCachedImage, getCachedImageSource, revokeCachedImageSource, type CachedImageSource } from "@/lib/imageCache";

const props = withDefaults(defineProps<{
  src?: string | null;
  alt?: string;
  width?: number;
  height?: number;
  decoding?: "sync" | "async" | "auto";
}>(), { src: "", alt: "", decoding: "async" });
const emit = defineEmits<{ load: [event: Event]; error: [event: Event] }>();

const placeholder = "data:image/gif;base64,R0lGODlhAQABAIAAAAAAAP///ywAAAAAAQABAAACAUwAOw==";
const renderedSrc = ref(placeholder);
const loading = ref(false);
let ownedSource: CachedImageSource | null = null;
let loadVersion = 0;

async function loadImage(url: string | null | undefined) {
  const version = ++loadVersion;
  revokeCachedImageSource(ownedSource);
  ownedSource = null;
  renderedSrc.value = placeholder;
  if (!url) {
    loading.value = false;
    return;
  }
  loading.value = true;
  const source = await getCachedImageSource(url);
  if (version !== loadVersion) {
    revokeCachedImageSource(source);
    return;
  }
  ownedSource = source.revokeObjectUrl ? source : null;
  renderedSrc.value = source.src || url;
  loading.value = false;
}

function handleError(event: Event) {
  if (ownedSource && props.src) {
    revokeCachedImageSource(ownedSource);
    ownedSource = null;
    renderedSrc.value = props.src;
    void evictCachedImage(props.src);
    return;
  }
  emit("error", event);
}

watch(() => props.src, url => { void loadImage(url); }, { immediate: true });
onUnmounted(() => { loadVersion += 1; revokeCachedImageSource(ownedSource); ownedSource = null; });
</script>

<template>
  <img
    :src="renderedSrc"
    :alt="props.alt"
    :width="props.width"
    :height="props.height"
    :decoding="props.decoding"
    :class="{ 'is-loading': loading }"
    :aria-busy="loading || undefined"
    @load="event => emit('load', event)"
    @error="handleError"
  />
</template>
