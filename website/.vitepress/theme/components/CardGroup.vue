<template>
  <div class="vp-card-group" :style="{ '--card-cols': cols ?? 2 }">
    <slot />
  </div>
</template>

<script setup lang="ts">
defineProps<{
  cols?: number
}>()
</script>
