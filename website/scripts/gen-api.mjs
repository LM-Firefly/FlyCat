#!/usr/bin/env node
/**
 * 从 api/openapi.json 生成 API 参考文档与侧栏（VitePress）。
 *
 * 输出：
 *   website/api/index.md                    概览（全部接口索引）
 *   website/api/<group>.md                  各分组接口页（由 tags 分组）
 *   website/public/api/openapi.json         调试台（Swagger UI）运行时用的拷贝
 *   website/.vitepress/generated/api-sidebar.mjs  侧栏配置
 *
 * 生成的 .md 不入库（见 .gitignore），请勿手动编辑；
 * 修改 api/openapi.json 后重新运行 `npm run gen:api` 即可。
 */
import { copyFileSync, mkdirSync, readFileSync, rmSync, writeFileSync } from 'node:fs'
import { dirname, join, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const root = resolve(dirname(fileURLToPath(import.meta.url)), '..')
const specPath = join(root, 'api', 'openapi.json')
const apiDir = join(root, 'api')
const publicApiDir = join(root, 'public', 'api')
const generatedDir = join(root, '.vitepress', 'generated')

const spec = JSON.parse(readFileSync(specPath, 'utf8'))

/** 分组顺序与页面标识（与 api/openapi.json 的 tags 对应） */
const GROUPS = [
  { tag: '日志', slug: 'logs', desc: '实时日志的流式订阅与一次性查询。' },
  { tag: '流量信息', slug: 'traffic', desc: '查询上行 / 下行流量与速率。' },
  { tag: '内存信息', slug: 'memory', desc: '查询内核内存占用。' },
  { tag: '版本信息', slug: 'version', desc: '查询内核版本与元信息。' },
  { tag: '缓存', slug: 'cache', desc: '清空 FakeIP / DNS 缓存。' },
  { tag: '运行配置', slug: 'configs', desc: '读取、修改、重载运行配置，以及 Geo 数据更新。' },
  { tag: '更新', slug: 'upgrade', desc: '升级内核、外部 UI 与 Geo 数据。' },
  { tag: '策略组', slug: 'groups', desc: '策略组查询、切换与延迟测试。' },
  { tag: '代理', slug: 'proxies', desc: '代理（节点）查询、切换、删除与延迟测试。' },
  { tag: '代理集合', slug: 'providers', desc: '代理集合（proxy-providers）查询、更新与健康检查。' },
  { tag: '规则', slug: 'rules', desc: '规则查询与启停。' },
  { tag: '规则集合', slug: 'rule-providers', desc: '规则集合（rule-providers）查询与更新。' },
  { tag: '连接', slug: 'connections', desc: '活动连接查询与关闭。' },
  { tag: '域名查询', slug: 'dns', desc: '使用内核内置解析器查询域名记录。' },
  { tag: '存储', slug: 'storage', desc: '内核持久化存储（store）键值读写。' },
  { tag: 'DEBUG', slug: 'debug', desc: '调试接口。' }
]

const HTTP_METHODS = ['get', 'put', 'post', 'delete', 'patch', 'head', 'options']
const esc = (s = '') => String(s).replace(/\|/g, '\\|').replace(/\r?\n+/g, ' ').trim()

function resolveRef(node) {
  if (node && typeof node === 'object' && node.$ref && spec.components) {
    const path = node.$ref.replace(/^#\//, '').split('/')
    let cur = spec.components
    for (const seg of path) cur = cur?.[seg]
    return cur ?? node
  }
  return node
}

function fmtType(schema) {
  const s = resolveRef(schema)
  if (!s || typeof s !== 'object') return ''
  if (s.enum) return s.enum.map((v) => `\`${v}\``).join(' \\| ')
  if (s.type === 'array') return `${fmtType(s.items) || 'any'}[]`
  if (s.type === 'object') return s.additionalProperties ? 'map' : 'object'
  if (s.oneOf) return 'oneOf'
  if (s.allOf) return 'allOf'
  return s.type || 'any'
}

function anchorId(op) {
  return (op.method.toLowerCase() + '-' + op.path).replace(/[^a-zA-Z0-9]+/g, '-').replace(/^-|-$/g, '')
}

// ---------- 收集操作 ----------
const ops = []
for (const [path, item] of Object.entries(spec.paths)) {
  for (const [method, op] of Object.entries(item)) {
    if (!HTTP_METHODS.includes(method)) continue
    const tag = (op.tags && op.tags[0]) || '其他'
    ops.push({ path, method: method.toUpperCase(), op, item, tag })
  }
}

const knownTags = new Set(GROUPS.map((g) => g.tag))
const extraTags = [...new Set(ops.map((o) => o.tag))].filter((t) => !knownTags.has(t))
for (const tag of extraTags) {
  GROUPS.push({ tag, slug: tag.toLowerCase().replace(/[^a-z0-9]+/g, '-').replace(/^-|-$/g, '') || 'misc', desc: '' })
}

// ---------- 渲染辅助 ----------
function renderParams(params) {
  const rows = params.map((raw) => {
    const p = resolveRef(raw)
    const schema = resolveRef(p.schema) || {}
    const type = fmtType(schema) || 'string'
    const def = schema.default !== undefined ? `\`${schema.default}\`` : ''
    return `| \`${p.name}\` | ${p.in} | ${type} | ${p.required ? '是' : '否'} | ${def} | ${esc(p.description)} |`
  })
  return [
    '### 参数',
    '',
    '| 名称 | 位置 | 类型 | 必填 | 默认 | 说明 |',
    '| --- | --- | --- | --- | --- | --- |',
    ...rows,
    ''
  ].join('\n')
}

function renderBody(op) {
  const rb = resolveRef(op.requestBody)
  if (!rb) return ''
  const content = rb.content?.['application/json']
  const schema = resolveRef(content?.schema) || {}
  const lines = ['### 请求体', '']
  if (rb.description) lines.push(esc(rb.description), '')
  const props = schema.properties
    ? Object.entries(schema.properties).map(([name, raw]) => {
        const s = resolveRef(raw) || {}
        const required = Array.isArray(schema.required) && schema.required.includes(name)
        return `| \`${name}\` | ${fmtType(s) || 'any'} | ${required ? '是' : '否'} | ${esc(s.description)} |`
      })
    : []
  if (props.length) {
    lines.push('| 字段 | 类型 | 必填 | 说明 |', '| --- | --- | --- | --- |', ...props, '')
  } else if (schema.type) {
    lines.push(`类型：\`${fmtType(schema)}\``, '')
  }
  const example = content?.example ?? schema.example
  if (example !== undefined) {
    lines.push('示例：', '', '```json', JSON.stringify(example, null, 2), '```', '')
  }
  return lines.join('\n')
}

function renderResponses(op) {
  const lines = ['### 响应', '', '| 状态码 | 说明 |', '| --- | --- |']
  const entries = Object.entries(op.responses || {})
  for (const [code, raw] of entries) {
    const r = resolveRef(raw)
    lines.push(`| \`${code}\` | ${esc(r.description)} |`)
  }
  lines.push('')
  for (const [code, raw] of entries) {
    const r = resolveRef(raw)
    const content = r.content?.['application/json']
    const example = content?.example
    const schema = resolveRef(content?.schema) || {}
    const props = schema.properties
      ? Object.entries(schema.properties).map(([name, ps]) => {
          const s = resolveRef(ps) || {}
          return `| \`${name}\` | ${fmtType(s) || 'any'} | ${esc(s.description)} |`
        })
      : []
    if (props.length) {
      lines.push(`**\`${code}\` 响应字段**`, '', '| 字段 | 类型 | 说明 |', '| --- | --- | --- |', ...props, '')
    }
    if (example !== undefined) {
      lines.push(`**\`${code}\` 示例**`, '', '```json', JSON.stringify(example, null, 2), '```', '')
    }
  }
  return lines.join('\n')
}

function renderOpPage(group) {
  const groupOps = ops.filter((o) => o.tag === group.tag)
  const lines = [
    '---',
    `title: ${group.tag}`,
    `description: ${group.desc || group.tag}`,
    '---',
    '',
    `# ${group.tag}`,
    ''
  ]
  if (group.desc) lines.push(group.desc, '')
  lines.push(
    '## 接口索引',
    '',
    '| 方法 | 路径 | 说明 |',
    '| --- | --- | --- |'
  )
  for (const o of groupOps) {
    lines.push(
      `| \`${o.method}\` | [\`${o.path}\`](#${anchorId(o)}) | ${esc(o.op.summary)} |`
    )
  }
  lines.push('')
  for (const o of groupOps) {
    const params = [...(o.item.parameters || []), ...(o.op.parameters || [])]
    lines.push(`## ${o.method} ${o.path} {#${anchorId(o)}}`, '')
    if (o.op.summary) lines.push(`> ${esc(o.op.summary)}`, '')
    if (o.op.description) lines.push(esc(o.op.description), '')
    lines.push(`[在调试台中打开此接口](/api/playground?op=${anchorId(o)})`, '')
    if (params.length) lines.push(renderParams(params))
    lines.push(renderBody(o.op))
    lines.push(renderResponses(o.op))
    lines.push('---', '')
  }
  // 去掉末尾多余的分隔线
  if (lines.at(-1) === '') lines.pop()
  if (lines.at(-1) === '---') lines.pop()
  return lines.join('\n') + '\n'
}

function renderIndex() {
  const lines = [
    '---',
    'title: API 参考',
    'description: Mihomo 外部控制器 RESTful API 参考',
    '---',
    '',
    '# API 参考',
    '',
    '> ' + esc(spec.info?.description || ''),
    '',
    '本站的 API 文档由 [`api/openapi.json`](https://github.com/LM-Firefly/FlyCat/blob/Moes/website/api/openapi.json) 自动生成；交互式调试请前往 [调试台](./playground)。',
    '',
    '## 接口总览',
    '',
    '| 分组 | 方法 | 路径 | 说明 |',
    '| --- | --- | --- | --- |'
  ]
  for (const g of GROUPS) {
    const groupOps = ops.filter((o) => o.tag === g.tag)
    groupOps.forEach((o, i) => {
      const groupCell = i === 0 ? `[${g.tag}](./${g.slug})` : ''
      lines.push(
        `| ${groupCell} | \`${o.method}\` | [\`${o.path}\`](./${g.slug}#${anchorId(o)}) | ${esc(o.op.summary)} |`
      )
    })
  }
  return lines.join('\n') + '\n'
}

// ---------- 写文件 ----------
mkdirSync(publicApiDir, { recursive: true })
mkdirSync(generatedDir, { recursive: true })

// 清理旧的生成页面（保留 playground.md 等手写文件）
for (const g of GROUPS) {
  rmSync(join(apiDir, `${g.slug}.md`), { force: true })
}
rmSync(join(apiDir, 'index.md'), { force: true })

writeFileSync(join(apiDir, 'index.md'), renderIndex(), 'utf8')
for (const g of GROUPS) {
  writeFileSync(join(apiDir, `${g.slug}.md`), renderOpPage(g), 'utf8')
}

copyFileSync(specPath, join(publicApiDir, 'openapi.json'))

const sidebar = [
  {
    text: 'API 参考',
    items: [
      { text: '概览', link: '/api/' },
      { text: '调试台 (Try it)', link: '/api/playground' },
      ...GROUPS.map((g) => ({ text: g.tag, link: `/api/${g.slug}` }))
    ]
  }
]
writeFileSync(
  join(generatedDir, 'api-sidebar.mjs'),
  '// 由 scripts/gen-api.mjs 自动生成，请勿手动编辑。\nexport default ' +
    JSON.stringify(sidebar, null, 2) +
    '\n',
  'utf8'
)

console.log(
  `[gen-api] 生成 ${GROUPS.length} 个分组页面（${ops.length} 个接口）→ website/api/，` +
    `侧栏 → website/.vitepress/generated/api-sidebar.mjs`
)
