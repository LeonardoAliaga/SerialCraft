import { existsSync } from 'node:fs'
import { resolve } from 'node:path'
import type { HeadConfig, TransformContext } from 'vitepress'

const origin = 'https://serialcraft.xyz'
const pairs = [
  ['index.md', 'en/index.md'], ['guide.md', 'en/guide.md'],
  ['protocol.md', 'en/protocol.md'], ['io-module.md', 'en/io-module.md'],
  ['ejemplos/index.md', 'en/examples/index.md'], ['acerca-de.md', 'en/about.md'],
  ['contacto.md', 'en/contact.md'], ['privacidad.md', 'en/privacy.md'],
  ['tutoriales/index.md', 'en/tutorials/index.md'],
  ['tutoriales/arduino-led-minecraft.md', 'en/tutorials/arduino-led.md'],
  ['tutoriales/bloque-io.md', 'en/tutorials/io-block.md'],
  ['tutoriales/sensor-redstone.md', 'en/tutorials/potentiometer.md'],
  ['tutoriales/visualizador.md', 'en/tutorials/visualizer.md'],
  ['tutoriales/telemetria.md', 'en/tutorials/telemetry.md'],
  ['tutoriales/pruebas-automatizadas.md', 'en/tutorials/testing.md'],
  ...['v0.3.6', 'v0.4.3'].map(v =>
    [`versiones/${v}/index.md`, `versiones/en/${v}/index.md`]),
  ...['v0.3.6', 'v0.4.3'].flatMap(v => ['guide', 'protocol'].map(p =>
    [`versiones/${v}/${p}.md`, `versiones/en/${v}/${p}.md`])),
  ['versiones/v0.4.3/ejemplos.md', 'versiones/en/v0.4.3/examples.md']
]
const url = (path: string) => origin + '/' + path.replace(/(^|\/)index\.md$/, '$1').replace(/\.md$/, '.html')
export function seoHead({ pageData, siteConfig }: TransformContext): HeadConfig[] {
  const path = pageData.relativePath
  if (path === '404.md') return [['meta', { name: 'robots', content: 'noindex' }]]
  const en = path.startsWith('en/') || path.startsWith('versiones/en/')
  const title = `${pageData.title || 'SerialCraft'} | SerialCraft`
  const description = pageData.description || (en
    ? 'Learn to connect Arduino and ESP32 to Minecraft with SerialCraft: installation, circuits, code and troubleshooting.'
    : 'Aprende a conectar Arduino y ESP32 con Minecraft usando SerialCraft: instalación, circuitos, código y solución de problemas.')
  const canonical = url(path)
  // Publicly available raster previews for social crawlers (SVG is not reliable here).
  const socialImage = origin + '/og/serialcraft-' + (en ? 'en' : 'es') + '.png'
  const socialImageAlt = en
    ? 'SerialCraft: The Bridge Between Worlds — Arduino, ESP32 and Minecraft'
    : 'SerialCraft: El puente entre mundos — Arduino, ESP32 y Minecraft'
  const tags: HeadConfig[] = [
    ['link', { rel: 'canonical', href: canonical }],
    ['meta', { property: 'og:type', content: 'website' }],
    ['meta', { property: 'og:site_name', content: 'SerialCraft' }],
    ['meta', { property: 'og:url', content: canonical }],
    ['meta', { property: 'og:title', content: title }],
    ['meta', { property: 'og:description', content: description }],
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
    ['meta', { name: 'twitter:description', content: description }]
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
          url: origin + '/', inLanguage: ['es', 'en'], sameAs: [repository, 'https://modrinth.com/mod/serialcraft'] },
        { '@type': 'SoftwareSourceCode', '@id': origin + '/#project', name: 'SerialCraft',
          description: 'Open-source Minecraft Fabric mod connecting Arduino and ESP32 with Minecraft through USB serial and local TCP.',
          url: origin + '/', codeRepository: repository, programmingLanguage: 'Java',
          license: 'https://github.com/LeonardoAliaga/SerialCraft/blob/main/LICENSE',
          author: { '@type': 'Person', name: 'Leonardo Aliaga', url: 'https://github.com/LeonardoAliaga' },
          sameAs: [repository, 'https://modrinth.com/mod/serialcraft'] }
      ]
    }
    tags.push(['script', { type: 'application/ld+json' }, JSON.stringify(graph).replace(/</g, '\\u003c')])
  }
  return tags
}
