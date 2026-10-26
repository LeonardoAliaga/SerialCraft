<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute, withBase } from 'vitepress'
import { versions, getVersionContext, linkForVersion } from './version-routing.mjs'

withDefaults(defineProps<{ mobile?: boolean }>(), { mobile: false })

const route = useRoute()
const details = ref<HTMLDetailsElement | null>(null)
const context = computed(() => getVersionContext(route.path))
const current = computed(() => context.value.version)
const english = computed(() => context.value.english)
const buttonLabel = computed(() => current.value + (current.value === 'v0.5.0-beta' ? ' (Beta)' : (english.value ? ' (Archive)' : ' (Archivo)')))
const entries = computed(() => versions.map(version => ({
  version,
  href: withBase(linkForVersion(route.path, version)),
  text: version === 'v0.5.0-beta'
    ? (english.value ? 'Current · Beta' : 'Actual · Beta')
    : (english.value ? 'Archived documentation' : 'Documentación histórica'),
  active: version === current.value
})))
watch(() => route.path, () => { if (details.value) details.value.open = false })
</script>

<template>
  <details ref="details" class="sc-version-switcher" :class="{ 'is-mobile': mobile }">
    <summary :aria-label="english ? 'Choose SerialCraft version' : 'Seleccionar versión de SerialCraft'">
      <span class="sc-version-label">{{ buttonLabel }}</span>
      <span class="sc-version-chevron" aria-hidden="true"></span>
    </summary>
    <nav class="sc-version-panel" :aria-label="english ? 'Available versions' : 'Versiones disponibles'">
      <div class="sc-version-panel-heading">{{ english ? 'Viewing version' : 'Versión que estás viendo' }}: <strong>{{ current }}</strong></div>
      <a v-for="entry in entries" :key="entry.version" :href="entry.href"
         class="sc-version-choice" :class="{ active: entry.active }"
         :aria-current="entry.active ? 'true' : undefined">
        <span>
          <strong>{{ entry.version }}</strong>
          <small>{{ entry.text }}</small>
        </span>
        <span v-if="entry.active" class="sc-version-check" aria-hidden="true">✓</span>
      </a>
      <div class="sc-version-panel-footer">
        <a href="https://github.com/LeonardoAliaga/SerialCraft/releases" target="_blank" rel="noopener noreferrer">
          {{ english ? 'Release notes' : 'Notas de versión' }}
        </a>
        <a href="https://github.com/LeonardoAliaga/SerialCraft/issues" target="_blank" rel="noopener noreferrer">
          {{ english ? 'Report a bug' : 'Reportar error' }}
        </a>
      </div>
    </nav>
  </details>
</template>
