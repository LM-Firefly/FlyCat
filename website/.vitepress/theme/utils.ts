import { withBase } from 'vitepress'

/**
 * 把以 `/` 开头的站内链接补成带 base 与 .html 的静态路径；
 * 外部链接原样返回。供自定义组件的 href 使用（markdown 外的链接不会被 VitePress 自动改写）。
 */
export function normalizeHref(href?: string): string | undefined {
  if (!href) return undefined
  if (/^[a-zA-Z][a-zA-Z0-9+.-]*:/.test(href) || href.startsWith('//')) return href
  const hashIdx = href.indexOf('#')
  const path = hashIdx >= 0 ? href.slice(0, hashIdx) : href
  const hash = hashIdx >= 0 ? href.slice(hashIdx) : ''
  let p = path
  if (!p) return withBase('/') + hash
  if (p.endsWith('/')) p += 'index.html'
  else if (!/\.[a-zA-Z0-9]+$/.test(p)) p += '.html'
  return withBase(p) + hash
}
