<template>
  <div class="vp-mermaid" :class="{ 'is-fullscreen': fullscreen }">
    <div class="vp-mermaid__toolbar">
      <button class="vp-mermaid__btn" title="缩小" aria-label="缩小" @click="zoomBy(1 / 1.25)">
        <ZoomOut :size="15" />
      </button>
      <span class="vp-mermaid__pct">{{ Math.round(scale * 100) }}%</span>
      <button class="vp-mermaid__btn" title="放大" aria-label="放大" @click="zoomBy(1.25)">
        <ZoomIn :size="15" />
      </button>
      <button class="vp-mermaid__btn" title="适应宽度" aria-label="适应宽度" @click="fit()">
        <Frame :size="15" />
      </button>
      <button
        class="vp-mermaid__btn"
        :title="fullscreen ? '退出全屏' : '全屏'"
        :aria-label="fullscreen ? '退出全屏' : '全屏'"
        @click="toggleFullscreen"
      >
        <Minimize2 v-if="fullscreen" :size="15" />
        <Maximize2 v-else :size="15" />
      </button>
    </div>

    <div
      ref="canvasEl"
      class="vp-mermaid__canvas"
      :style="canvasStyle"
      @wheel="onWheel"
      @pointerdown="onPointerDown"
      @pointermove="onPointerMove"
      @pointerup="onPointerUp"
      @pointercancel="onPointerUp"
    >
      <div v-if="svg" class="vp-mermaid__svg" :style="svgStyle" v-html="svg" />
      <pre v-else class="vp-mermaid__fallback"><code>{{ code }}</code></pre>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import { Frame, Maximize2, Minimize2, ZoomIn, ZoomOut } from '@lucide/vue'

const props = defineProps<{
  code: string
}>()

const svg = ref('')
const svgW = ref(0)
const svgH = ref(0)
const scale = ref(1)
const x = ref(0)
const y = ref(0)
const fullscreen = ref(false)
const canvasEl = ref<HTMLElement>()

let userZoomed = false

const canvasStyle = computed(() =>
  fullscreen.value
    ? {}
    : {
        height: `${Math.max(220, Math.min(svgH.value * scale.value, viewportH() * 0.7))}px`
      }
)

const svgStyle = computed(() => ({
  width: `${svgW.value}px`,
  height: `${svgH.value}px`,
  transform: `translate(${x.value}px, ${y.value}px) scale(${scale.value})`,
  transformOrigin: '0 0'
}))

function viewportH(): number {
  return typeof window === 'undefined' ? 900 : window.innerHeight
}

function clamp(v: number, lo: number, hi: number) {
  return Math.min(hi, Math.max(lo, v))
}

/** 缩放到指定屏幕坐标处（保持该点不动） */
function zoomAt(clientX: number, clientY: number, factor: number) {
  const el = canvasEl.value
  if (!el || !svgW.value) return
  const rect = el.getBoundingClientRect()
  const cx = clientX - rect.left
  const cy = clientY - rect.top
  const ns = clamp(scale.value * factor, 0.15, 6)
  x.value = cx - (cx - x.value) * (ns / scale.value)
  y.value = cy - (cy - y.value) * (ns / scale.value)
  scale.value = ns
  userZoomed = true
}

function zoomBy(factor: number) {
  const el = canvasEl.value
  if (!el) return
  const rect = el.getBoundingClientRect()
  zoomAt(rect.left + rect.width / 2, rect.top + rect.height / 2, factor)
}

/** 适应宽度并居中；minScale 用于初始视图的可读性下限（超宽图不再缩到看不清） */
function fit(minScale = 0) {
  const el = canvasEl.value
  if (!el || !svgW.value) return
  const cw = el.clientWidth
  const s = clamp(Math.max(cw / svgW.value, minScale), 0.15, 3)
  scale.value = s
  x.value = (cw - svgW.value * s) / 2
  y.value = 0
  userZoomed = false
}

function onWheel(e: WheelEvent) {
  // 内联模式下纯滚动留给页面，Ctrl/⌘ + 滚轮缩放；全屏模式下滚轮即缩放
  if (!fullscreen.value && !e.ctrlKey && !e.metaKey) return
  e.preventDefault()
  zoomAt(e.clientX, e.clientY, e.deltaY < 0 ? 1.12 : 1 / 1.12)
}

let dragging = false
let startX = 0
let startY = 0
let baseX = 0
let baseY = 0

function onPointerDown(e: PointerEvent) {
  if (e.button !== 0) return
  dragging = true
  startX = e.clientX
  startY = e.clientY
  baseX = x.value
  baseY = y.value
  ;(e.currentTarget as HTMLElement).setPointerCapture(e.pointerId)
}

function onPointerMove(e: PointerEvent) {
  if (!dragging) return
  x.value = baseX + (e.clientX - startX)
  y.value = baseY + (e.clientY - startY)
  userZoomed = true
}

function onPointerUp() {
  dragging = false
}

function toggleFullscreen() {
  fullscreen.value = !fullscreen.value
  document.body.style.overflow = fullscreen.value ? 'hidden' : ''
  nextTick(() => fit())
}

function onKeydown(e: KeyboardEvent) {
  if (e.key === 'Escape' && fullscreen.value) toggleFullscreen()
}

function onResize() {
  if (!userZoomed) fit(0.6)
}

/**
 * 修正 foreignObject 标签框：
 * mermaid 对多行标签的测量有缺陷（标签框固定 120×42，宽/高都不随内容），且 VitePress 的 .vp-doc p 行高会进一步撑破预估高度，导致文字被裁。
 * 这里按实际内容尺寸重设 foreignObject / 标签居中 / 节点矩形。
 */
function normalizeLabelBoxes(svgEl: SVGSVGElement) {
  for (const node of svgEl.querySelectorAll('g.node')) {
    const labelG = node.querySelector('g.label') as SVGGElement | null
    const fo = labelG?.querySelector('foreignObject') as SVGForeignObjectElement | null
    const container = node.querySelector('rect.label-container') as SVGRectElement | null
    const div = fo?.querySelector('div') as HTMLElement | null
    if (!labelG || !fo || !div) continue
    const foW = parseFloat(fo.getAttribute('width') || '0')
    const foH = parseFloat(fo.getAttribute('height') || '0')
    const padX = container ? (parseFloat(container.getAttribute('width') || '0') - foW) / 2 : 16
    const padY = container ? (parseFloat(container.getAttribute('height') || '0') - foH) / 2 : 8
    // 归一化排版（页面样式会把 <p> 行高/边距放大）
    for (const p of div.querySelectorAll('p')) {
      const pe = p as HTMLElement
      pe.style.margin = '0'
      pe.style.lineHeight = '1.5'
    }
    div.style.display = 'block'
    div.style.width = 'max-content'
    div.style.maxWidth = 'none'
    const w = Math.ceil(div.offsetWidth)
    const h = Math.ceil(div.offsetHeight)
    if (w <= 0 || h <= 0) continue
    fo.setAttribute('width', String(w))
    fo.setAttribute('height', String(h))
    labelG.setAttribute('transform', `translate(${-w / 2}, ${-h / 2})`)
    if (container) {
      const rw = w + 2 * padX
      const rh = h + 2 * padY
      container.setAttribute('width', String(rw))
      container.setAttribute('height', String(rh))
      container.setAttribute('x', String(-rw / 2))
      container.setAttribute('y', String(-rh / 2))
    }
  }
}

onMounted(async () => {
  try {
    const mermaid = (await import('mermaid')).default
    // mermaid v12：htmlLabels 顶层键（flowchart.htmlLabels 已废弃）， SVG 文本标签可绕开 foreignObject 的多行标签测量缺陷
    mermaid.initialize({
      startOnLoad: false,
      securityLevel: 'loose',
      htmlLabels: false
    })
    const id = `vp-mermaid-${Math.random().toString(36).slice(2, 10)}`
    const { svg: rendered } = await mermaid.render(id, decodeURIComponent(props.code))
    svg.value = rendered
    await nextTick()
    const el = canvasEl.value?.querySelector('svg') as SVGSVGElement | null
    if (el) {
      el.style.maxWidth = 'none'
      normalizeLabelBoxes(el)
      // 修正标签框后重算内容包围盒，避免放大后的节点被 viewBox 裁掉
      const rootG = el.querySelector('g.root') as SVGGElement | null
      if (rootG) {
        const bb = rootG.getBBox()
        el.setAttribute('viewBox', `${bb.x} ${bb.y} ${bb.width} ${bb.height}`)
        svgW.value = bb.width
        svgH.value = bb.height
      } else {
        const vb = el.getAttribute('viewBox')?.split(/[\s,]+/).map(Number)
        if (vb && vb.length === 4 && vb[2] > 0 && vb[3] > 0) {
          svgW.value = vb[2]
          svgH.value = vb[3]
        }
      }
      fit(0.6)
    }
    document.addEventListener('keydown', onKeydown)
    window.addEventListener('resize', onResize)
  } catch (e) {
    console.error('[Mermaid] render failed:', e)
  }
})

onBeforeUnmount(() => {
  document.removeEventListener('keydown', onKeydown)
  window.removeEventListener('resize', onResize)
  document.body.style.overflow = ''
})
</script>

<style>
.vp-mermaid {
  position: relative;
  margin: 24px 0;
  border: 1px solid var(--vp-c-divider);
  border-radius: 12px;
  background-color: var(--vp-c-bg-soft);
  overflow: hidden;
}

.vp-mermaid.is-fullscreen {
  position: fixed;
  inset: 0;
  z-index: 300;
  display: flex;
  flex-direction: column;
  margin: 0;
  border: none;
  border-radius: 0;
  background-color: var(--vp-c-bg);
}

.vp-mermaid__toolbar {
  position: absolute;
  top: 10px;
  right: 10px;
  z-index: 10;
  display: flex;
  gap: 4px;
  align-items: center;
  padding: 4px;
  border: 1px solid var(--vp-c-divider);
  border-radius: 10px;
  background-color: var(--vp-c-bg);
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.1);
}

.vp-mermaid__btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  padding: 0;
  border: none;
  border-radius: 7px;
  background: transparent;
  color: var(--vp-c-text-2);
  cursor: pointer;
  transition: background 0.15s, color 0.15s;
}

.vp-mermaid__btn:hover {
  background-color: var(--vp-c-bg-soft);
  color: var(--vp-c-brand-1);
}

.vp-mermaid__pct {
  min-width: 42px;
  color: var(--vp-c-text-3);
  font-family: var(--vp-font-family-mono);
  font-size: 11px;
  text-align: center;
  user-select: none;
}

.vp-mermaid__canvas {
  position: relative;
  overflow: hidden;
  cursor: grab;
  touch-action: none;
}

.vp-mermaid__canvas:active {
  cursor: grabbing;
}

.vp-mermaid.is-fullscreen .vp-mermaid__canvas {
  flex: 1;
  height: auto;
}

.vp-mermaid__svg svg {
  display: block;
  width: 100%;
  height: 100%;
  max-width: none;
}

.vp-mermaid__fallback {
  margin: 0;
  padding: 16px;
  font-size: 12px;
}
</style>
