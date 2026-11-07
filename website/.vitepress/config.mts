import { defineConfig } from 'vitepress'
// 由 scripts/gen-api.mjs 生成（docs:dev / docs:build 会先运行生成器）
// @ts-ignore
import apiSidebar from './generated/api-sidebar.mjs'

const base = '/FlyCat/'

export default defineConfig({
  lang: 'zh-CN',
  title: 'FlyCat',
  description: '基于 mihomo 内核的开源 Android 客户端',
  base,
  lastUpdated: true,
  head: [
    ['link', { rel: 'icon', type: 'image/svg+xml', href: `${base}favicon.svg` }],
    ['meta', { name: 'theme-color', content: '#16a34a' }],
    ['meta', { name: 'robots', content: 'index, follow' }],
    ['meta', { name: 'description', content: '基于 mihomo 内核的开源 Android 客户端，提供代理管理与配置覆写等功能。' }],
    ['meta', { property: 'og:type', content: 'website' }],
    ['meta', { property: 'og:site_name', content: 'FlyCat' }],
    ['meta', { property: 'og:locale', content: 'zh_CN' }],
    ['meta', { property: 'og:title', content: 'FlyCat' }],
    ['meta', { property: 'og:description', content: '基于 mihomo 内核的开源 Android 客户端，提供代理管理与配置覆写等功能。' }],
    ['meta', { property: 'og:image', content: 'https://raw.githubusercontent.com/LM-Firefly/FlyCat/refs/heads/Moes/docs/logo.webp' }],
    ['meta', { name: 'twitter:card', content: 'summary_large_image' }],
    ['meta', { name: 'application-name', content: 'FlyCat' }],
    ['meta', { name: 'apple-mobile-web-app-title', content: 'FlyCat' }],
    ['meta', { name: 'apple-mobile-web-app-capable', content: 'yes' }],
    ['meta', { name: 'format-detection', content: 'telephone=no' }],
    ['meta', { name: 'referrer', content: 'origin' }],
    ['meta', { name: 'language', content: 'zh-CN' }],
    ['meta', { name: 'copyright', content: 'LM-Firefly' }]
  ],
  themeConfig: {
    logo: '/logo/logo.webp',
    siteTitle: 'FlyCat',
    nav: [
      { text: '介绍', link: '/' },
      { text: '应用指南', link: '/guide/app' },
      { text: '覆写', link: '/override/override' },
      { text: 'API', link: '/api/' },
      { text: '更新日志', link: '/update/history' }
    ],
    sidebar: {
      '/guide/': [
        {
          text: '指南',
          items: [{ text: '应用指南', link: '/guide/app' }]
        }
      ],
      '/override/': [
        {
          text: '覆写',
          items: [
            { text: '覆写概览', link: '/override/override' },
            { text: '语法参考', link: '/override/fields' }
          ]
        }
      ],
      '/update/': [
        {
          text: '更新',
          items: [{ text: '更新日志', link: '/update/history' }]
        }
      ],
      '/api/': apiSidebar
    },
    socialLinks: [{ icon: 'github', link: 'https://github.com/LM-Firefly/FlyCat' }],
    search: { provider: 'local' },
    outline: { label: '本页目录', level: [2, 3] },
    docFooter: { prev: '上一页', next: '下一页' },
    lastUpdated: { text: '最后更新' },
    darkModeSwitchLabel: '外观',
    sidebarMenuLabel: '菜单',
    returnToTopLabel: '回到顶部',
    footer: {
      message: 'Released under the AGPL-3.0 License',
      copyright: 'Copyright © LM-Firefly'
    }
  },
  markdown: {
    config(md: any) {
      const fence = md.renderer.rules.fence!.bind(md.renderer.rules)
      md.renderer.rules.fence = (tokens: any[], idx: number, options: any, env: any, self: any) => {
        const token = tokens[idx]
        if (token.info.trim() === 'mermaid') {
          return `<Mermaid code="${encodeURIComponent(token.content)}"></Mermaid>\n`
        }
        return fence(tokens, idx, options, env, self)
      }
    }
  }
})
