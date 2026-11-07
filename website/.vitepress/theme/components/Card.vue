<template>
  <a
    v-if="href"
    class="vp-card"
    :class="{ 'vp-card--horizontal': horizontal }"
    :href="normalizeHref(href)"
    :target="external ? '_blank' : undefined"
    :rel="external ? 'noopener noreferrer' : undefined"
  >
    <div class="vp-card__head">
      <component :is="iconComp" v-if="iconComp" class="vp-card__icon" :size="20" :stroke-width="1.8" />
      <div class="vp-card__title">{{ title }}</div>
    </div>
    <div class="vp-card__body">
      <slot />
    </div>
  </a>
  <div v-else class="vp-card" :class="{ 'vp-card--horizontal': horizontal }">
    <div class="vp-card__head">
      <component :is="iconComp" v-if="iconComp" class="vp-card__icon" :size="20" :stroke-width="1.8" />
      <div class="vp-card__title">{{ title }}</div>
    </div>
    <div class="vp-card__body">
      <slot />
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import {
  Box,
  Braces,
  Bug,
  Clock,
  Code,
  Cpu,
  Download,
  FileCode,
  House,
  Image,
  Monitor,
  PanelLeft,
  Rocket,
  Settings,
  Smartphone,
  SquareArrowDown,
  Store
} from '@lucide/vue'
import { normalizeHref } from '../utils'

const icons: Record<string, unknown> = {
  box: Box,
  braces: Braces,
  bug: Bug,
  clock: Clock,
  code: Code,
  cpu: Cpu,
  download: Download,
  'file-code': FileCode,
  house: House,
  image: Image,
  monitor: Monitor,
  'panel-left': PanelLeft,
  rocket: Rocket,
  settings: Settings,
  smartphone: Smartphone,
  'square-arrow-down': SquareArrowDown,
  store: Store
}

const props = defineProps<{
  title: string
  icon?: string
  href?: string
  horizontal?: boolean
}>()

const iconComp = computed(() => (props.icon ? icons[props.icon] : undefined))
const external = computed(() => !!props.href && (/^[a-zA-Z][a-zA-Z0-9+.-]*:/.test(props.href) || props.href.startsWith('//')))
</script>
