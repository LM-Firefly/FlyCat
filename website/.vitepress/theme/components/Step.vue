<template>
  <div class="vp-step">
    <div v-if="title" class="vp-step__title">{{ title }}</div>
    <div class="vp-step__body">
      <slot />
    </div>
  </div>
</template>

<script setup lang="ts">
defineProps<{
  title?: string
}>()
</script>
