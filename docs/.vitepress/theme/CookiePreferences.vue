<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useData } from 'vitepress'
const { lang } = useData()
const en = computed(() => lang.value.startsWith('en'))
const visible = ref(false)
const ready = ref(false)
const key = 'serialcraft.analytics-consent.v1'
const lifetime = 180 * 24 * 60 * 60 * 1000
let enabled = false
function readChoice(): boolean | null {
  try {
    const value = JSON.parse(localStorage.getItem(key) || 'null')
    return value && typeof value.analytics === 'boolean' && Number.isFinite(value.time)
      && value.time <= Date.now() && Date.now() - value.time < lifetime ? value.analytics : null
  } catch { return null }
}
function startAnalytics() {
  if (enabled) return
  enabled = true
  const w = window as any
  w['ga-disable-G-DNHRBCWGDX'] = false
  w.dataLayer = w.dataLayer || []
  w.gtag = function () { w.dataLayer.push(arguments) }
  w.gtag('consent', 'default', {
    analytics_storage: 'granted', ad_storage: 'denied',
    ad_user_data: 'denied', ad_personalization: 'denied'
  })
  w.gtag('js', new Date())
  w.gtag('config', 'G-DNHRBCWGDX')
  const script = document.createElement('script')
  script.async = true
  script.src = 'https://www.googletagmanager.com/gtag/js?id=G-DNHRBCWGDX'
  document.head.appendChild(script)
}
function removeAnalyticsCookies() {
  const domains = ['', location.hostname, '.' + location.hostname]
  const parts = location.hostname.split('.')
  if (parts.length > 2) domains.push('.' + parts.slice(-2).join('.'))
  for (const cookie of document.cookie.split(';')) {
    const name = cookie.split('=')[0].trim()
    if (name === '_ga' || name.startsWith('_ga_') || name === '_gid' || name.startsWith('_gat')) {
      for (const domain of domains)
        document.cookie = `${name}=; Max-Age=0; Path=/;${domain ? ' Domain=' + domain + ';' : ''} SameSite=Lax; Secure`
    }
  }
}
function choose(analytics: boolean) {
  try { localStorage.setItem(key, JSON.stringify({ analytics, time: Date.now() })) } catch { /* session choice still works */ }
  visible.value = false
  if (analytics) startAnalytics()
  else {
    const w = window as any
    w['ga-disable-G-DNHRBCWGDX'] = true
    if (w.gtag) w.gtag('consent', 'update', { analytics_storage: 'denied' })
    removeAnalyticsCookies()
    // Unload an already loaded tag; retain the saved refusal on the next page load.
    if (enabled) location.reload()
  }
}
onMounted(() => {
  ready.value = true
  const choice = readChoice()
  visible.value = choice === null
  if (choice === true) startAnalytics()
  else removeAnalyticsCookies()
})
</script>

<template>
  <button v-if="ready && !visible" class="sc-cookie-open" @click="visible = true">
    {{ en ? 'Cookie settings' : 'Configurar cookies' }}
  </button>
  <section v-if="visible" class="sc-cookie-panel" role="region" :aria-label="en ? 'Cookie preferences' : 'Preferencias de cookies'">
    <h2>{{ en ? 'Your privacy choices' : 'Tú eliges tus cookies' }}</h2>
    <p>{{ en ? 'With your permission, Google Analytics helps us understand how this documentation is used. You can reject analytics and keep browsing normally. Advertising is currently inactive.' : 'Con tu permiso, Google Analytics nos ayuda a conocer el uso de esta documentación. Puedes rechazar la analítica y seguir navegando normalmente. La publicidad está desactivada actualmente.' }}</p>
    <p>{{ en ? 'We save your choice in this browser for 180 days. You can change it at any time.' : 'Guardamos tu elección en este navegador durante 180 días. Puedes cambiarla cuando quieras.' }}
      <a :href="en ? '/en/privacy.html' : '/privacidad.html'">{{ en ? 'Privacy policy' : 'Política de privacidad' }}</a>
    </p>
    <div class="sc-cookie-actions">
      <button @click="choose(false)">{{ en ? 'Reject analytics' : 'Rechazar analítica' }}</button>
      <button @click="choose(true)">{{ en ? 'Accept analytics' : 'Aceptar analítica' }}</button>
    </div>
  </section>
</template>

<style scoped>
.sc-cookie-panel { position: fixed; bottom: 16px; left: 16px; right: 16px; margin: auto; max-width: 660px; z-index: 100; padding: 22px; border: 1px solid var(--vp-c-divider); border-radius: 14px; background: var(--vp-c-bg); color: var(--vp-c-text-1); box-shadow: 0 8px 32px #0003; max-height: 80vh; overflow: auto; }
h2 { font-size: 18px; font-weight: 700; margin-bottom: 10px; }
p { font-size: 14px; line-height: 1.6; margin-bottom: 12px; }
a { text-decoration: underline; color: var(--vp-c-brand-1); }
.sc-cookie-actions { display: flex; flex-wrap: wrap; gap: 12px; }
button { padding: 10px 16px; border: 1px solid var(--vp-c-divider); border-radius: 8px; font-size: 14px; background: var(--vp-c-bg-soft); color: var(--vp-c-text-1); cursor: pointer; }
button:focus-visible, a:focus-visible { outline: 3px solid var(--vp-c-brand-1); outline-offset: 3px; }
.sc-cookie-actions button { flex: 1; min-width: 170px; }
.sc-cookie-open { position: fixed; bottom: 12px; left: 12px; z-index: 99; box-shadow: 0 2px 8px #0002; }
</style>
