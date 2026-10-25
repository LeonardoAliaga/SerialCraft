import DefaultTheme from 'vitepress/theme'
import { h } from 'vue'
import CookiePreferences from './CookiePreferences.vue'
import './home.css'

export default {
  extends: DefaultTheme,
  Layout: () => h(DefaultTheme.Layout, null, {
    'layout-bottom': () => h(CookiePreferences)
  })
}
