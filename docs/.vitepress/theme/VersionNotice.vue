<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, withBase } from 'vitepress'
import { getVersionContext, linkForVersion } from './version-routing.mjs'

const route = useRoute()
const context = computed(() => getVersionContext(route.path))
const currentLink = computed(() => withBase(linkForVersion(route.path, 'v0.5.0-beta')))
</script>

<template>
  <div v-if="context.archived" class="sc-version-notice" role="note">
    <span>
      <strong>{{ context.english ? 'Archived documentation' : 'Documentación histórica' }} · {{ context.version }}</strong>
      {{ context.english ? '— content for this release.' : '— contenido de esta versión.' }}
    </span>
    <a :href="currentLink">{{ context.english ? 'Current documentation' : 'Documentación actual' }}</a>
  </div>
</template>
