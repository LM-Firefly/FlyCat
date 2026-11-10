<template>
  <div class="ap">
    <div v-if="!specReady" class="ap-loading">正在加载 API 定义…</div>
    <template v-else>
      <!-- 顶栏：接口选择 + 请求行 + 发送 -->
      <div class="ap-topbar">
        <div class="ap-opbox" :class="{ 'is-open': dropdownOpen }" @click="toggleDropdown">
          <span class="ap-badge" :class="badgeClass(selected.method)">{{ selected.method }}</span>
          <span class="ap-opname">{{ selected.summary }}</span>
          <ChevronDown class="ap-chev" :size="16" :stroke-width="2" />
          <div v-if="dropdownOpen" class="ap-dropdown" @click.stop>
            <div v-for="g in groups" :key="g.tag" class="ap-dropdown__group">
              <div class="ap-dropdown__grouptitle">{{ g.tag }}</div>
              <button
                v-for="o in g.ops"
                :key="o.key"
                type="button"
                class="ap-dropdown__item"
                :class="{ 'is-active': o.key === selectedKey }"
                :title="o.summary"
                @click="selectOp(o.key)"
              >
                <span class="ap-badge ap-badge--sm" :class="badgeClass(o.method)">{{ o.method }}</span>
                <span class="ap-dropdown__name">{{ o.summary }}</span>
                <Check v-if="o.key === selectedKey" class="ap-dropdown__check" :size="15" :stroke-width="2.5" />
              </button>
            </div>
          </div>
        </div>
        <div class="ap-urlbar">
          <span class="ap-badge ap-badge--sm" :class="badgeClass(selected.method)">{{ selected.method }}</span>
          <span class="ap-path">{{ selected.path }}</span>
        </div>
        <button class="ap-send" :disabled="sending" @click="send">
          {{ sending ? '发送中…' : '发送' }}
        </button>
      </div>
      <!-- 双栏 -->
      <div class="ap-cols">
        <!-- 左栏：请求参数 -->
        <div class="ap-col">
          <h3 class="ap-title">{{ selected.summary }}</h3>
          <section class="ap-card">
            <h4 class="ap-card__title">Server</h4>
            <div v-for="(v, name) in serverVars" :key="name" class="ap-row">
              <div class="ap-row__head">
                <code class="ap-row__name">{{ name }}</code>
                <span class="ap-dash">—</span>
              </div>
              <input v-model="v.value" class="ap-input" :placeholder="String(v.default ?? '')" />
              <p v-if="v.description" class="ap-row__desc">{{ v.description }}</p>
            </div>
          </section>
          <section class="ap-card">
            <h4 class="ap-card__title">授权</h4>
            <div class="ap-row">
              <div class="ap-row__head">
                <code class="ap-row__name">Authorization</code>
                <span class="ap-chip ap-chip--type">string&lt;bearer&gt;</span>
                <span class="ap-chip ap-chip--req">必填</span>
              </div>
              <input v-model="token" class="ap-input" placeholder="仅填 secret 或完整 Bearer 值" />
              <p class="ap-row__desc">{{ bearerDesc }}</p>
            </div>
          </section>
          <section v-if="pathParams.length" class="ap-card">
            <h4 class="ap-card__title">路径参数</h4>
            <div v-for="p in pathParams" :key="p.name" class="ap-row">
              <div class="ap-row__head">
                <code class="ap-row__name">{{ p.name }}</code>
                <span class="ap-chip ap-chip--type">{{ typeName(p.schema) }}</span>
                <span v-if="p.required" class="ap-chip ap-chip--req">必填</span>
              </div>
              <input v-model="paramValues[p.name]" class="ap-input" :placeholder="String(p.schema?.default ?? '')" />
              <p v-if="p.description" class="ap-row__desc">{{ p.description }}</p>
            </div>
          </section>
          <section v-if="queryParams.length" class="ap-card">
            <h4 class="ap-card__title">查询参数</h4>
            <div v-for="p in queryParams" :key="p.name" class="ap-row">
              <div class="ap-row__head">
                <code class="ap-row__name">{{ p.name }}</code>
                <span class="ap-chip ap-chip--type">{{ typeName(p.schema) }}</span>
                <span v-if="p.required" class="ap-chip ap-chip--req">必填</span>
              </div>
              <input v-model="paramValues[p.name]" class="ap-input" :placeholder="String(p.schema?.default ?? '')" />
              <p v-if="p.description" class="ap-row__desc">{{ p.description }}</p>
            </div>
          </section>
          <section v-if="headerParams.length" class="ap-card">
            <h4 class="ap-card__title">请求头参数</h4>
            <div v-for="p in headerParams" :key="p.name" class="ap-row">
              <div class="ap-row__head">
                <code class="ap-row__name">{{ p.name }}</code>
                <span class="ap-chip ap-chip--type">{{ typeName(p.schema) }}</span>
                <span v-if="p.required" class="ap-chip ap-chip--req">必填</span>
              </div>
              <input v-model="paramValues[p.name]" class="ap-input" />
              <p v-if="p.description" class="ap-row__desc">{{ p.description }}</p>
            </div>
          </section>
          <section v-if="hasBody" class="ap-card">
            <h4 class="ap-card__title">请求体</h4>
            <textarea v-model="bodyText" class="ap-textarea" rows="8" spellcheck="false" />
            <p v-if="bodyError" class="ap-row__desc ap-row__desc--error">{{ bodyError }}</p>
          </section>
        </div>
        <!-- 右栏：响应 / 代码片段 / 示例 -->
        <div class="ap-col">
          <section v-if="response" class="ap-card">
            <header class="ap-card__head">
              <span class="ap-status" :class="response.ok ? 'is-ok' : 'is-err'">
                {{ response.status }} - {{ response.statusText }}
              </span>
              <span class="ap-chip">Body</span>
              <span class="ap-meta">{{ response.time }} ms · {{ response.size }}</span>
              <button class="ap-iconbtn" title="复制" @click="copyText(response.body, 'response')">
                <Check v-if="copiedKey === 'response'" :size="15" />
                <Copy v-else :size="15" />
              </button>
            </header>
            <pre class="ap-code"><code v-html="response.highlighted" /></pre>
          </section>
          <section class="ap-card">
            <header class="ap-card__head">
              <span class="ap-card__headtitle">{{ selected.summary }}</span>
              <select v-model="snippetLang" class="ap-langs" aria-label="代码片段语言">
                <option>cURL</option>
                <option>JavaScript</option>
                <option>Python</option>
              </select>
              <button class="ap-iconbtn" title="复制" @click="copyText(snippet, 'snippet')">
                <Check v-if="copiedKey === 'snippet'" :size="15" />
                <Copy v-else :size="15" />
              </button>
            </header>
            <pre class="ap-code"><code>{{ snippet }}</code></pre>
          </section>
          <section v-for="ex in examples" :key="ex.id" class="ap-card">
            <header class="ap-card__head">
              <span class="ap-ex-status">{{ ex.status }}</span>
              <button class="ap-iconbtn" title="复制" @click="copyText(ex.text, ex.id)">
                <Check v-if="copiedKey === ex.id" :size="15" />
                <Copy v-else :size="15" />
              </button>
            </header>
            <pre class="ap-code"><code v-html="ex.highlighted" /></pre>
          </section>
        </div>
      </div>
    </template>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { withBase } from 'vitepress'
import { Check, Copy, ChevronDown } from '@lucide/vue'

interface OpEntry {
  key: string
  method: string
  path: string
  summary: string
  tag: string
  op: any
  item: any
}

const props = defineProps<{
  /** 初始选中的接口（锚点 id，如 get-configs） */
  op?: string
}>()

const HTTP_METHODS = ['get', 'put', 'post', 'delete', 'patch']

const spec = ref<any>(null)
const specReady = computed(() => !!spec.value)
const selectedKey = ref('')
const serverVars = reactive<Record<string, { value: string; default?: string; description?: string }>>({})
const token = ref('')
const bodyText = ref('')
const paramValues = reactive<Record<string, string>>({})
const sending = ref(false)
const snippetLang = ref('cURL')
const copiedKey = ref('')
const bodyError = ref('')
const dropdownOpen = ref(false)

const response = ref<{
  ok: boolean
  status: number
  statusText: string
  time: number
  size: string
  body: string
  highlighted: string
} | null>(null)

// ---------- spec 解析 ----------
function anchorId(method: string, path: string) {
  return (method.toLowerCase() + '-' + path).replace(/[^a-zA-Z0-9]+/g, '-').replace(/^-|-$/g, '')
}

const ops = computed<OpEntry[]>(() => {
  const list: OpEntry[] = []
  for (const [path, item] of Object.entries<any>(spec.value?.paths ?? {})) {
    for (const [method, op] of Object.entries<any>(item)) {
      if (!HTTP_METHODS.includes(method)) continue
      list.push({
        key: anchorId(method, path),
        method: method.toUpperCase(),
        path,
        summary: op.summary || `${method.toUpperCase()} ${path}`,
        tag: (op.tags && op.tags[0]) || '其他',
        op,
        item
      })
    }
  }
  return list
})

const groups = computed(() => {
  const map = new Map<string, OpEntry[]>()
  for (const o of ops.value) {
    if (!map.has(o.tag)) map.set(o.tag, [])
    map.get(o.tag)!.push(o)
  }
  return [...map.entries()].map(([tag, list]) => ({ tag, ops: list }))
})

const selected = computed<OpEntry>(() => ops.value.find((o) => o.key === selectedKey.value) ?? ops.value[0])

const bearerDesc = computed(
  () => spec.value?.components?.securitySchemes?.bearerAuth?.description ?? '在请求头中携带 Authorization: Bearer ${secret}。'
)

// ---------- 请求参数 ----------
function allParams(sel: OpEntry) {
  return [...(sel.item.parameters ?? []), ...(sel.op.parameters ?? [])]
}
const pathParams = computed(() => allParams(selected.value).filter((p: any) => p.in === 'path'))
const queryParams = computed(() => allParams(selected.value).filter((p: any) => p.in === 'query'))
const headerParams = computed(() => allParams(selected.value).filter((p: any) => p.in === 'header'))

const bodySchema = computed(() => selected.value.op.requestBody?.content?.['application/json'])
const hasBody = computed(() => !!bodySchema.value)

function initValues() {
  for (const k of Object.keys(paramValues)) delete paramValues[k]
  for (const p of allParams(selected.value)) {
    paramValues[p.name] = p.schema?.default !== undefined ? String(p.schema.default) : ''
  }
  const ex = bodySchema.value?.example ?? bodySchema.value?.schema?.example
  bodyText.value = ex !== undefined ? JSON.stringify(ex, null, 2) : ''
  bodyError.value = ''
  response.value = null
}

watch(selectedKey, initValues)

// ---------- URL 拼装 ----------
const serverBase = computed(() => {
  let url = String(spec.value?.servers?.[0]?.url ?? '')
  for (const [name, v] of Object.entries(serverVars)) {
    url = url.replaceAll(`{${name}}`, (v as any).value || '')
  }
  return url.replace(/\/$/, '')
})

const resolvedPath = computed(() => {
  let p = selected.value.path
  for (const param of pathParams.value) {
    p = p.replaceAll(`{${param.name}}`, encodeURIComponent(paramValues[param.name] ?? ''))
  }
  return p
})

const fullUrl = computed(() => {
  const qs = queryParams.value
    .filter((p: any) => paramValues[p.name])
    .map((p: any) => `${encodeURIComponent(p.name)}=${encodeURIComponent(paramValues[p.name])}`)
    .join('&')
  return serverBase.value + resolvedPath.value + (qs ? `?${qs}` : '')
})

const headers = computed(() => {
  const h: Record<string, string> = {}
  const t = token.value.trim()
  if (t) h['Authorization'] = /^bearer\s/i.test(t) ? t : `Bearer ${t}`
  for (const p of headerParams.value) {
    if (paramValues[p.name]) h[p.name] = paramValues[p.name]
  }
  return h
})

// ---------- 语法高亮 ----------
function highlightJson(text: string): string {
  try {
    const pretty = JSON.stringify(JSON.parse(text), null, 2)
    return pretty
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(
        /("(?:\\.|[^"\\])*")(\s*:)?|\b(true|false|null)\b|-?\d+(?:\.\d+)?(?:[eE][+-]?\d+)?/g,
        (m, str, colon, bool) => {
          if (str) {
            return colon ? `<span class="ap-j-key">${str}</span>${colon}` : `<span class="ap-j-str">${str}</span>`
          }
          if (bool) return `<span class="ap-j-bool">${m}</span>`
          return `<span class="ap-j-num">${m}</span>`
        }
      )
  } catch {
    return text.replace(/&/g, '&amp;').replace(/</g, '&lt;')
  }
}

// ---------- 发送 ----------
async function send() {
  if (hasBody.value) {
    try {
      if (bodyText.value.trim()) JSON.parse(bodyText.value)
      bodyError.value = ''
    } catch (e) {
      bodyError.value = `请求体不是合法 JSON：${e}`
      return
    }
  }
  sending.value = true
  const t0 = performance.now()
  try {
    const init: RequestInit = { method: selected.value.method, headers: headers.value }
    if (hasBody.value && bodyText.value.trim()) init.body = bodyText.value
    const res = await fetch(fullUrl.value, init)
    const text = await res.text()
    const time = Math.round(performance.now() - t0)
    const size = text.length > 1024 ? `${(text.length / 1024).toFixed(1)} KB` : `${text.length} B`
    response.value = {
      ok: res.ok,
      status: res.status,
      statusText: res.statusText || (res.ok ? 'OK' : 'Error'),
      time,
      size,
      body: text,
      highlighted: highlightJson(text)
    }
  } catch (e) {
    response.value = {
      ok: false,
      status: 0,
      statusText: '请求失败',
      time: Math.round(performance.now() - t0),
      size: '0 B',
      body: String(e),
      highlighted: `<span class="ap-j-err">${String(e).replace(/</g, '&lt;')}</span>`
    }
  } finally {
    sending.value = false
  }
}

// ---------- 代码片段 ----------
const snippet = computed(() => {
  const method = selected.value.method
  const url = fullUrl.value
  const headerLines = Object.entries(headers.value).map(([k, v]) => ({ k, v }))
  const body = hasBody.value && bodyText.value.trim() ? bodyText.value : ''
  if (snippetLang.value === 'JavaScript') {
    const lines = [`const res = await fetch('${url}', {`, `  method: '${method}',`]
    if (headerLines.length) {
      lines.push('  headers: {')
      for (const { k, v } of headerLines) lines.push(`    '${k}': '${v}',`)
      lines.push('  },')
    }
    if (body) lines.push(`  body: JSON.stringify(${body.replace(/\n/g, '\n  ')}),`)
    lines.push('})', "const data = await res.json()")
    return lines.join('\n')
  }
  if (snippetLang.value === 'Python') {
    const lines = ['import requests', '', `res = requests.${method.toLowerCase()}('${url}'`]
    if (headerLines.length) {
      lines[lines.length - 1] += ','
      lines.push('    headers={')
      for (const { k, v } of headerLines) lines.push(`        '${k}': '${v}',`)
      lines.push('    },')
    }
    if (body) lines.push(`    data='''${body}''',`)
    lines.push(')', 'print(res.json())')
    return lines.join('\n')
  }
  // cURL（默认）
  const lines = [`curl --request ${method} \\`, `  --url '${url}' \\`]
  for (const { k, v } of headerLines) lines.push(`  --header '${k}: ${v}' \\`)
  if (body) lines.push(`  --data '${body.replace(/\n\s*/g, ' ')}'`)
  if (lines.at(-1)!.endsWith(' \\')) lines[lines.length - 1] = lines[lines.length - 1].slice(0, -2)
  return lines.join('\n')
})

// ---------- 示例响应 ----------
const examples = computed(() => {
  const out: { id: string; status: string; text: string; highlighted: string }[] = []
  for (const [code, raw] of Object.entries<any>(selected.value.op.responses ?? {})) {
    const ex = raw?.content?.['application/json']?.example
    if (ex === undefined) continue
    const text = JSON.stringify(ex, null, 2)
    out.push({ id: `example-${code}`, status: code, text, highlighted: highlightJson(text) })
  }
  return out
})

// ---------- 工具 ----------
function typeName(schema: any): string {
  if (!schema) return 'string'
  if (schema.enum) return schema.enum.map((v: any) => v).join(' | ')
  if (schema.type === 'array') return `${typeName(schema.items) || 'any'}[]`
  return schema.type || 'any'
}

function badgeClass(method: string) {
  return `ap-badge--${method.toLowerCase()}`
}

function toggleDropdown() {
  dropdownOpen.value = !dropdownOpen.value
}

function selectOp(key: string) {
  selectedKey.value = key
  dropdownOpen.value = false
}

function onDocClick(e: MouseEvent) {
  if (!(e.target as Element)?.closest?.('.ap-opbox')) dropdownOpen.value = false
}

function onKeydown(e: KeyboardEvent) {
  if (e.key === 'Escape') dropdownOpen.value = false
}

onMounted(() => {
  document.addEventListener('click', onDocClick)
  document.addEventListener('keydown', onKeydown)
})
onBeforeUnmount(() => {
  document.removeEventListener('click', onDocClick)
  document.removeEventListener('keydown', onKeydown)
})

async function copyText(text: string, key: string) {
  try {
    await navigator.clipboard.writeText(text)
    copiedKey.value = key
    setTimeout(() => (copiedKey.value = ''), 1200)
  } catch {
    /* 剪贴板不可用时静默 */
  }
}

onMounted(async () => {
  const res = await fetch(withBase('/api/openapi.json'))
  spec.value = await res.json()
  const variables = spec.value?.servers?.[0]?.variables ?? {}
  for (const [name, v] of Object.entries<any>(variables)) {
    serverVars[name] = { value: String(v.default ?? ''), default: v.default, description: v.description }
  }
  const wanted = [new URLSearchParams(location.search).get('op') ?? '', props.op ?? ''].find(
    (k) => k && ops.value.some((o) => o.key === k)
  ) ?? ops.value[0]?.key
  selectedKey.value = wanted ?? ''
  if (wanted) initValues()
})
</script>

<style>
.ap {
  margin: 24px 0;
  --ap-panel: var(--vp-c-bg-soft);
  --ap-card: var(--vp-c-bg);
  --ap-border: var(--vp-c-divider);
  --ap-text: var(--vp-c-text-1);
  --ap-text-2: var(--vp-c-text-2);
  --ap-text-3: var(--vp-c-text-3);
  --ap-input-bg: var(--vp-c-bg-soft);
  --ap-code-bg: var(--vp-code-block-bg);
  --ap-green: #16a34a;
}

.dark .ap {
  --ap-panel: #18181b;
  --ap-card: #1c1c1f;
  --ap-border: #2c2c31;
  --ap-input-bg: #232327;
  --ap-code-bg: #141416;
  --ap-green: #22c55e;
}

.ap-loading {
  padding: 32px;
  border: 1px dashed var(--ap-border);
  border-radius: 16px;
  text-align: center;
  color: var(--ap-text-3);
}

/* ---------- 顶栏 ---------- */

.ap-topbar {
  display: flex;
  gap: 12px;
  align-items: center;
  padding: 16px;
  border: 1px solid var(--ap-border);
  border-radius: 16px 16px 0 0;
  background: var(--ap-panel);
}

.ap-opbox {
  position: relative;
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 240px;
  padding: 10px 36px 10px 12px;
  border: 1px solid var(--ap-border);
  border-radius: 12px;
  background: var(--ap-card);
  cursor: pointer;
  transition: border-color 0.15s;
}

.ap-opbox:hover,
.ap-opbox.is-open {
  border-color: var(--ap-green);
}

.ap-dropdown {
  position: absolute;
  top: calc(100% + 10px);
  left: 0;
  z-index: 100;
  min-width: 360px;
  max-width: 480px;
  max-height: 460px;
  padding: 8px;
  border: 1px solid var(--ap-border);
  border-radius: 14px;
  background: var(--ap-card);
  box-shadow: 0 16px 48px rgba(0, 0, 0, 0.2);
  overflow-y: auto;
  cursor: default;
}

.dark .ap-dropdown {
  box-shadow: 0 16px 48px rgba(0, 0, 0, 0.6);
}

.ap-dropdown__grouptitle {
  padding: 10px 10px 6px;
  color: var(--ap-text-3);
  font-size: 11.5px;
  font-weight: 700;
  letter-spacing: 0.06em;
}

.ap-dropdown__item {
  display: flex;
  align-items: center;
  gap: 10px;
  width: 100%;
  padding: 8px 10px;
  border: none;
  border-radius: 10px;
  background: transparent;
  color: var(--ap-text-2);
  font-size: 13.5px;
  text-align: left;
  cursor: pointer;
  transition: background 0.12s;
}

.ap-dropdown__item:hover {
  background: var(--ap-input-bg);
}

.ap-dropdown__item.is-active {
  background: rgba(22, 163, 74, 0.13);
  color: var(--ap-green);
  font-weight: 600;
}

.dark .ap-dropdown__item.is-active {
  background: rgba(34, 197, 94, 0.16);
}

.ap-dropdown__name {
  flex: 1;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.ap-dropdown__check {
  flex: none;
  color: var(--ap-green);
}

.ap-chev {
  position: absolute;
  right: 12px;
  color: var(--ap-text-3);
  pointer-events: none;
}

.ap-opbox .ap-opname {
  overflow: hidden;
  font-size: 14px;
  font-weight: 600;
  color: var(--ap-text);
  white-space: nowrap;
  text-overflow: ellipsis;
}

.ap-urlbar {
  display: flex;
  flex: 1;
  align-items: center;
  gap: 10px;
  min-width: 0;
  padding: 10px 14px;
  border: 1px solid var(--ap-border);
  border-radius: 12px;
  background: var(--ap-card);
}

.ap-path {
  overflow: hidden;
  font-family: var(--vp-font-family-mono);
  font-size: 14px;
  color: var(--vp-c-text-2);
  white-space: nowrap;
  text-overflow: ellipsis;
}

.ap-send {
  padding: 10px 22px;
  border: none;
  border-radius: 12px;
  background: var(--ap-green);
  color: #fff;
  font-size: 15px;
  font-weight: 700;
  cursor: pointer;
  transition: filter 0.15s;
}

.ap-send:hover {
  filter: brightness(1.08);
}

.ap-send:disabled {
  opacity: 0.6;
  cursor: default;
}

/* ---------- 方法徽章 ---------- */

.ap-badge {
  flex: none;
  padding: 3px 10px;
  border-radius: 8px;
  color: #fff;
  font-family: var(--vp-font-family-mono);
  font-size: 12px;
  font-weight: 700;
}

.ap-badge--sm {
  padding: 2px 8px;
  font-size: 11px;
}

.ap-badge--get { background: #16a34a; }
.ap-badge--post { background: #2563eb; }
.ap-badge--put { background: #d97706; }
.ap-badge--patch { background: #7c3aed; }
.ap-badge--delete { background: #dc2626; }

/* ---------- 双栏 ---------- */

.ap-cols {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 1.1fr);
  gap: 16px;
  padding: 16px;
  border: 1px solid var(--ap-border);
  border-top: none;
  border-radius: 0 0 16px 16px;
  background: var(--ap-panel);
}

@media (max-width: 960px) {
  .ap-cols {
    grid-template-columns: 1fr;
  }
  .ap-topbar {
    flex-wrap: wrap;
  }
}

.ap-col {
  display: flex;
  flex-direction: column;
  gap: 16px;
  min-width: 0;
}

.ap-title {
  margin: 0;
  border: none;
  font-size: 17px;
  font-weight: 700;
}

/* ---------- 卡片 ---------- */

.ap-card {
  padding: 18px;
  border: 1px solid var(--ap-border);
  border-radius: 14px;
  background: var(--ap-card);
}

.ap-card__title {
  margin: 0 0 14px;
  padding-bottom: 12px;
  border-bottom: 1px solid var(--ap-border);
  border-radius: 0;
  font-size: 15px;
  font-weight: 700;
}

.ap-card__head {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  align-items: center;
  margin-bottom: 12px;
}

.ap-card__headtitle {
  flex: 1;
  overflow: hidden;
  font-size: 14px;
  font-weight: 700;
  white-space: nowrap;
  text-overflow: ellipsis;
}

/* ---------- 参数行 ---------- */

.ap-row {
  padding: 12px 0;
  border-bottom: 1px solid var(--ap-border);
}

.ap-row:last-child {
  padding-bottom: 0;
  border-bottom: none;
}

.ap-row:first-of-type {
  padding-top: 0;
}

.ap-row__head {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
  margin-bottom: 8px;
}

.vp-doc code.ap-row__name,
.vp-doc .ap-row__name {
  padding: 0;
  border: none;
  background: transparent;
  font-family: var(--vp-font-family-mono);
  font-size: 14px;
  font-weight: 700;
  color: var(--ap-text);
}

.ap-dash {
  color: var(--ap-text-3);
}

.ap-chip {
  padding: 1px 8px;
  border-radius: 6px;
  background: var(--ap-input-bg);
  color: var(--ap-text-3);
  font-family: var(--vp-font-family-mono);
  font-size: 11.5px;
}

.ap-chip--type {
  color: var(--ap-text-2);
}

.ap-chip--req {
  background: rgba(220, 38, 38, 0.12);
  color: #dc2626;
}

.ap-row__desc {
  margin: 8px 0 0;
  color: var(--ap-text-3);
  font-size: 13px;
  line-height: 1.7;
}

.ap-row__desc--error {
  color: #dc2626;
}

/* ---------- 输入 ---------- */

.ap-input,
.ap-textarea {
  width: 100%;
  padding: 9px 12px;
  border: 1px solid var(--ap-border);
  border-radius: 10px;
  background: var(--ap-input-bg);
  color: var(--ap-text);
  font-family: var(--vp-font-family-mono);
  font-size: 13.5px;
  outline: none;
  transition: border-color 0.15s;
}

.ap-input:focus,
.ap-textarea:focus {
  border-color: var(--ap-green);
}

.ap-textarea {
  resize: vertical;
  line-height: 1.6;
}

/* ---------- 响应 / 代码 ---------- */

.ap-status {
  font-family: var(--vp-font-family-mono);
  font-size: 14px;
  font-weight: 700;
  white-space: nowrap;
}

.ap-status.is-ok {
  color: var(--ap-green);
}

.ap-status.is-err {
  color: #dc2626;
}

.ap-ex-status {
  position: relative;
  padding-bottom: 6px;
  color: var(--ap-green);
  font-family: var(--vp-font-family-mono);
  font-size: 14px;
  font-weight: 700;
}

.ap-ex-status::after {
  content: '';
  position: absolute;
  left: 0;
  bottom: 0;
  width: 24px;
  height: 2px;
  border-radius: 2px;
  background: var(--ap-green);
}

.ap-meta {
  flex: 1;
  color: var(--ap-text-3);
  font-family: var(--vp-font-family-mono);
  font-size: 11.5px;
  text-align: right;
  white-space: nowrap;
}

.ap-langs {
  padding: 4px 8px;
  border: 1px solid var(--ap-border);
  border-radius: 8px;
  background: var(--ap-input-bg);
  color: var(--ap-text-2);
  font-size: 12px;
  cursor: pointer;
}

.ap-iconbtn {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 5px;
  border: none;
  border-radius: 8px;
  background: transparent;
  color: var(--ap-text-3);
  cursor: pointer;
  transition: background 0.15s, color 0.15s;
}

.ap-iconbtn:hover {
  background: var(--ap-input-bg);
  color: var(--ap-text);
}

.ap-code {
  margin: 0;
  padding: 14px 16px;
  border-radius: 10px;
  background: var(--ap-code-bg);
  overflow-x: auto;
}

.ap-code code {
  padding: 0;
  border: none;
  background: transparent;
  color: var(--ap-text-2);
  font-family: var(--vp-font-family-mono);
  font-size: 12.5px;
  line-height: 1.7;
}

.ap-j-key { color: #0369a1; }
.ap-j-str { color: #c2410c; }
.ap-j-num { color: #1d4ed8; }
.ap-j-bool { color: #7c3aed; }
.ap-j-err { color: #dc2626; }

.dark .ap-j-key { color: #7dd3fc; }
.dark .ap-j-str { color: #fdba74; }
.dark .ap-j-num { color: #93c5fd; }
.dark .ap-j-bool { color: #c4b5fd; }
.dark .ap-j-err { color: #f87171; }
</style>
