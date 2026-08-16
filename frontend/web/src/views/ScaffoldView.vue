<script setup lang="ts">
defineProps<{ title: string }>();
</script>

<template>
  <main class="scaffold-view">
    <p class="scaffold-brand">Dream Space</p>
    <h1>{{ title }}</h1>
  </main>
</template>
