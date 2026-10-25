/* Single source of truth for the version visible in the browser URL.
   Works for both current /en/... pages and historical /versiones/en/... URLs. */
const versions = ['v0.4.6', 'v0.4.3', 'v0.3.6']
export { versions }

export function getVersionContext(input = '/') {
  const path = input.replace(/[?#].*$/, '').replace(/\.html$/, '').replace(/\/index$/, '/')
  const english = /^\/(?:en(?:\/|$)|versiones\/en(?:\/|$))/.test(path)
  const archive = path.match(/^\/versiones\/(?:en\/)?(v0\.4\.3|v0\.3\.6)(?:\/|$)/)
  const version = archive ? archive[1] : 'v0.4.6'
  const last = path.split('/').filter(Boolean).pop() || ''
  let section = 'guide'
  if (!last || last === version) section = 'index'
  else if (last === 'guide' || last === 'protocol') section = last
  else if (last === 'examples' || last === 'ejemplos') section = 'examples'
  else if ((path === '/en/' || path === '/') || path.endsWith('/index')) section = 'index'
  return { version, english, section, archived: version !== 'v0.4.6' }
}

export function linkForVersion(input, targetVersion) {
  if (!versions.includes(targetVersion)) throw new Error('Unknown version: ' + targetVersion)
  const { english, section } = getVersionContext(input)
  if (targetVersion === 'v0.4.6') {
    if (section === 'index') return english ? '/en/' : '/'
    if (section === 'examples') return english ? '/en/examples/' : '/ejemplos/'
    return (english ? '/en/' : '/') + section + '.html'
  }
  const base = '/versiones/' + (english ? 'en/' : '') + targetVersion + '/'
  if (section === 'index') return base
  if (section === 'examples') {
    if (targetVersion === 'v0.4.3') return base + (english ? 'examples.html' : 'ejemplos.html')
    return base + 'guide.html' // v0.3.6 has no archive examples page.
  }
  return base + section + '.html'
}
