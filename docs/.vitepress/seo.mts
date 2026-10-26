import { existsSync } from 'node:fs'
import { resolve } from 'node:path'
import type { HeadConfig, TransformContext } from 'vitepress'

import { origin, pairs, canonicalUrl as url } from './routes.mjs'

export function seoHead({ pageData, siteConfig }: TransformContext): HeadConfig[] {
  const path = pageData.relativePath
  if (path === '404.md') return [['meta', { name: 'robots', content: 'noindex' }]]
  const en = path.startsWith('en/') || path.startsWith('versiones/en/')
  const title = /SerialCraft/i.test(pageData.title || '') ? pageData.title : `${pageData.title || 'Documentation'} | SerialCraft`
  const description = pageData.description || (en
    ? 'Learn to connect Arduino and ESP32 to Minecraft with SerialCraft: installation, circuits, code and troubleshooting.'
    : 'Aprende a conectar Arduino y ESP32 con Minecraft usando SerialCraft: instalación, circuitos, código y solución de problemas.')
  const canonical = url(path)
  // Publicly available raster previews for social crawlers (SVG is not reliable here).
  const socialImage = origin + '/og/serialcraft-' + (en ? 'en' : 'es') + '-v2.png'
  const socialImageAlt = en
    ? 'SerialCraft: The Bridge Between Worlds — Arduino, ESP32, Raspberry Pi Pico and Minecraft Java'
    : 'SerialCraft: El puente entre mundos — Arduino, ESP32, Raspberry Pi Pico y Minecraft Java'
  const socialDescription = (path === 'index.md' || path === 'en/index.md')
    ? (en
      ? 'Connect Arduino, ESP32, Raspberry Pi Pico and more hardware to Minecraft Java. Guides, examples and tutorials.'
      : 'Conecta Arduino, ESP32, Raspberry Pi Pico y más hardware con Minecraft Java. Guías, ejemplos y tutoriales.')
    : description
  const tags: HeadConfig[] = [
    ['link', { rel: 'canonical', href: canonical }],
    ['meta', { property: 'og:type', content: 'website' }],
    ['meta', { property: 'og:site_name', content: 'SerialCraft' }],
    ['meta', { property: 'og:url', content: canonical }],
    ['meta', { property: 'og:title', content: title }],
    ['meta', { property: 'og:description', content: socialDescription }],
    ['meta', { property: 'og:locale', content: en ? 'en_US' : 'es_PE' }],
    ['meta', { property: 'og:locale:alternate', content: en ? 'es_PE' : 'en_US' }],
    ['meta', { property: 'og:image', content: socialImage }],
    ['meta', { property: 'og:image:secure_url', content: socialImage }],
    ['meta', { property: 'og:image:type', content: 'image/png' }],
    ['meta', { property: 'og:image:width', content: '1200' }],
    ['meta', { property: 'og:image:height', content: '630' }],
    ['meta', { property: 'og:image:alt', content: socialImageAlt }],
    ['meta', { name: 'twitter:card', content: 'summary_large_image' }],
    ['meta', { name: 'twitter:image', content: socialImage }],
    ['meta', { name: 'twitter:image:alt', content: socialImageAlt }],
    ['meta', { name: 'twitter:title', content: title }],
    ['meta', { name: 'twitter:description', content: socialDescription }]
  ]
  const pair = pairs.find(p => p.includes(path))
  if (pair && pair.every(p => existsSync(resolve(siteConfig.srcDir, p)))) {
    for (const [i, language] of ['es', 'en'].entries())
      tags.push(['link', { rel: 'alternate', hreflang: language, href: url(pair[i]) }])
    tags.push(['link', { rel: 'alternate', hreflang: 'x-default', href: url(pair[0]) }])
  }
  if (path === 'index.md' || path === 'en/index.md') {
    const repository = 'https://github.com/LeonardoAliaga/SerialCraft'
    const graph = {
      '@context': 'https://schema.org',
      '@graph': [
        { '@type': 'WebSite', '@id': origin + '/#website', name: 'SerialCraft',
          url: origin + '/', alternateName: 'SerialCraft Mod', inLanguage: ['es', 'en'], sameAs: [repository, 'https://modrinth.com/mod/serialcraft'] },
        { '@type': 'SoftwareSourceCode', '@id': origin + '/#project', name: 'SerialCraft',
          description: 'Open-source Minecraft Fabric mod connecting Arduino and ESP32 with Minecraft through USB serial and local TCP.',
          url: origin + '/', codeRepository: repository, programmingLanguage: 'Java', version: '0.5.0-beta', inLanguage: ['es', 'en', 'zh-CN'],
          license: 'https://github.com/LeonardoAliaga/SerialCraft/blob/main/LICENSE',
          author: { '@type': 'Person', name: 'Leonardo Aliaga', url: 'https://github.com/LeonardoAliaga' },
          sameAs: [repository, 'https://modrinth.com/mod/serialcraft'] }
      ]
    }
    tags.push(['script', { type: 'application/ld+json' }, JSON.stringify(graph).replace(/</g, '\\u003c')])
  }
  return tags
}
