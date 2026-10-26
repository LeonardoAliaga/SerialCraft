<script setup lang="ts">
import { computed, watch } from 'vue'
import { useRoute, withBase } from 'vitepress'
import { translatedPath } from '../routes.mjs'

withDefaults(defineProps<{ mobile?: boolean }>(), { mobile: false })
const route = useRoute()
const english = computed(() => /^\/(?:en(?:\/|$)|versiones\/en(?:\/|$))/.test(route.path))
const language = computed(() => english.value ? 'es' : 'en')
const href = computed(() => withBase(translatedPath(route.path, language.value)))
// Archived English pages live outside /en/, so keep their document language
// correct after client-side navigation as well as in the generated HTML.
watch(() => route.path, () => {
  if (typeof document !== 'undefined') document.documentElement.lang = english.value ? 'en' : 'es'
}, { immediate: true, flush: 'post' })
</script>

<template>
  <a class="sc-language-switcher" :class="{ 'is-mobile': mobile }" :href="href"
     :hreflang="language" :lang="language" rel="alternate"
     :aria-label="english ? 'Leer esta documentación en español' : 'Read this documentation in English'">
    {{ english ? 'Español' : 'English' }}
  </a>
</template>

<style>
/* Replace the filename-based language menus with the explicit route map. */
.VPNavBarTranslations, .VPNavScreenTranslations, .VPNavBarExtra .translations { display: none !important; }
.sc-language-switcher {
  display: inline-flex; align-items: center; margin-left: 12px; padding: 6px 8px;
  white-space: nowrap; color: var(--vp-c-text-2); font-size: 13px;
}
.sc-language-switcher:hover { color: var(--vp-c-brand-1); }
.sc-language-switcher:focus-visible { outline: 2px solid var(--vp-c-brand-1); outline-offset: 2px; }
.sc-language-switcher.is-mobile { display: flex; margin: 12px 0; padding: 10px 0; }
@media (max-width: 767px) { .sc-language-switcher:not(.is-mobile) { display: none; } }
</style>
