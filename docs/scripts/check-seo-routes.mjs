import assert from 'node:assert/strict'
import { canonicalUrl, legacyRedirects, translatedPath } from '../.vitepress/routes.mjs'

assert.equal(canonicalUrl('guide.md'), 'https://serialcraft.xyz/guide')
assert.equal(canonicalUrl('en/index.md'), 'https://serialcraft.xyz/en/')
assert.equal(translatedPath('/tutoriales/telemetria.html', 'en'), '/en/tutorials/telemetry')
assert.equal(translatedPath('/en/tutorials/arduino-led', 'es'), '/tutoriales/arduino-led-minecraft')
assert.equal(translatedPath('/tutoriales/solucionar-errores', 'en'), '/en/tutorials/troubleshooting')
assert.equal(translatedPath('/tutoriales/digital-analogico.html', 'en'), '/en/tutorials/digital-analog')
assert.equal(translatedPath('/contacto.html', 'en'), '/en/contact')
assert.equal(translatedPath('/versiones/en/v0.3.6/guide.html', 'es'), '/versiones/v0.3.6/guide')
assert.equal(translatedPath('/versiones/v0.4.3/', 'en'), '/versiones/en/v0.4.3/')
assert.equal(translatedPath('/tutoriales/esp32-wifi', 'en'), '/en/tutorials/')
assert.equal(translatedPath('/guide#instalacion', 'en'), '/en/guide')
assert.equal(legacyRedirects['/en/contacto.html'], '/en/contact')
assert.equal(legacyRedirects['/en/tutoriales/telemetria.html'], '/en/tutorials/telemetry')
assert.equal(legacyRedirects['/tutorials/visualizer.html'], '/en/tutorials/visualizer')
console.log('Canonical, language and legacy route regression tests: OK')
