import DefaultTheme from 'vitepress/theme'
import { h } from 'vue'
import CookiePreferences from './CookiePreferences.vue'

export default {
  extends: DefaultTheme,
  Layout: () => h(DefaultTheme.Layout, null, {
    'layout-bottom': () => h(CookiePreferences)
  })
}
