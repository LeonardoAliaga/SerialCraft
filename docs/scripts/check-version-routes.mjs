import assert from 'node:assert/strict'
import { getVersionContext, linkForVersion } from '../.vitepress/theme/version-routing.mjs'

const cases = [
  ['/', 'v0.4.6', false, 'index'],
  ['/en/', 'v0.4.6', true, 'index'],
  ['/en/protocol.html', 'v0.4.6', true, 'protocol'],
  ['/versiones/v0.3.6/', 'v0.3.6', false, 'index'],
  ['/versiones/en/v0.3.6/guide.html', 'v0.3.6', true, 'guide'],
  ['/versiones/en/v0.4.3/protocol.html', 'v0.4.3', true, 'protocol'],
  ['/versiones/v0.4.3/ejemplos.html', 'v0.4.3', false, 'examples']
]
for (const [path, version, english, section] of cases) {
  const value = getVersionContext(path)
  assert.deepEqual([value.version, value.english, value.section], [version, english, section], path)
}
assert.equal(linkForVersion('/en/protocol.html', 'v0.3.6'), '/versiones/en/v0.3.6/protocol.html')
assert.equal(linkForVersion('/versiones/en/v0.3.6/protocol.html', 'v0.4.6'), '/en/protocol.html')
assert.equal(linkForVersion('/versiones/v0.4.3/ejemplos.html', 'v0.3.6'), '/versiones/v0.3.6/guide.html')
assert.equal(linkForVersion('/versiones/en/v0.4.3/', 'v0.3.6'), '/versiones/en/v0.3.6/')
assert.equal(linkForVersion('/versiones/en/v0.3.6/', 'v0.4.6'), '/en/')
console.log('Version selector route tests: OK')
