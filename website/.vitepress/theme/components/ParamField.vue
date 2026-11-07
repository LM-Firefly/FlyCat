<template>
  <div class="param-field">
    <div class="param-field__head">
      <code class="param-field__name">{{ path }}</code>
      <span v-if="type" class="param-field__type">{{ type }}</span>
      <span v-if="props.default" class="param-field__default">默认 {{ props.default }}</span>
    </div>
    <div class="param-field__body">
      <slot />
    </div>
  </div>
</template>

<script setup lang="ts">
const props = defineProps<{
  path: string
  type?: string
  default?: string
}>()
</script>
