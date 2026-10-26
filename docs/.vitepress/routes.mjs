// Public documentation routes shared by SEO, language navigation and build checks.
export const origin = 'https://serialcraft.xyz'
export const pairs = [
  ['index.md', 'en/index.md'], ['guide.md', 'en/guide.md'],
  ['protocol.md', 'en/protocol.md'], ['io-module.md', 'en/io-module.md'],
  ['ejemplos/index.md', 'en/examples/index.md'], ['acerca-de.md', 'en/about.md'],
  ['contacto.md', 'en/contact.md'], ['privacidad.md', 'en/privacy.md'],
  ['tutoriales/index.md', 'en/tutorials/index.md'],
  ['tutoriales/arduino-led-minecraft.md', 'en/tutorials/arduino-led.md'],
  ['tutoriales/bloque-io.md', 'en/tutorials/io-block.md'],
  ['tutoriales/sensor-redstone.md', 'en/tutorials/potentiometer.md'],
  ['tutoriales/digital-analogico.md', 'en/tutorials/digital-analog.md'],
  ['tutoriales/solucionar-errores.md', 'en/tutorials/troubleshooting.md'],
  ['tutoriales/visualizador.md', 'en/tutorials/visualizer.md'],
  ['tutoriales/telemetria.md', 'en/tutorials/telemetry.md'],
  ['tutoriales/pruebas-automatizadas.md', 'en/tutorials/testing.md'],
  ...['v0.3.6', 'v0.4.3'].map(v => [`versiones/${v}/index.md`, `versiones/en/${v}/index.md`]),
  ...['v0.3.6', 'v0.4.3'].flatMap(v => ['guide', 'protocol'].map(p =>
    [`versiones/${v}/${p}.md`, `versiones/en/${v}/${p}.md`])),
  ['versiones/v0.4.3/ejemplos.md', 'versiones/en/v0.4.3/examples.md']
]

export const canonicalPath = file => '/' + file
  .replace(/(^|\/)index\.md$/, '$1').replace(/\.md$/, '')
export const canonicalUrl = file => origin + canonicalPath(file)

// These obsolete public addresses have an equivalent replacement. Keep the
// redirect pages out of the sitemap; only the destinations should be indexed.
export const legacyRedirects = {
  '/en/tutoriales/solucionar-errores.html': '/en/tutorials/troubleshooting',
  '/tutorials/visualizer.html': '/en/tutorials/visualizer',
  '/en/tutoriales/digital-analogico.html': '/en/tutorials/digital-analog',
  '/en/tutoriales/telemetria.html': '/en/tutorials/telemetry',
  '/en/contacto.html': '/en/contact',
  '/ejemplos/esquemas-ia.html': '/ejemplos/'
}

function comparable(path) {
  return path.replace(/[?#].*$/, '').replace(/\.html$/, '')
    .replace(/\/index$/, '/').replace(/\/$/, '') || '/'
}

export function translatedPath(input, language) {
  if (!['es', 'en'].includes(language)) throw new Error('Unknown documentation language')
  const path = comparable(input)
  const pair = pairs.find(files => files.some(file => comparable(canonicalPath(file)) === path))
  if (pair) return canonicalPath(pair[language === 'en' ? 1 : 0])
  // A page without a translation links to the corresponding archive or section,
  // never to an invented filename. Anchors differ across translations.
  const archive = path.match(/^\/versiones\/(?:en\/)?(v0\.3\.6|v0\.4\.3)(?:\/|$)/)
  if (archive) return '/versiones/' + (language === 'en' ? 'en/' : '') + archive[1] + '/'
  if (/^\/(?:en\/)?(?:tutoriales|tutorials)(?:\/|$)/.test(path))
    return language === 'en' ? '/en/tutorials/' : '/tutoriales/'
  return language === 'en' ? '/en/' : '/'
}
