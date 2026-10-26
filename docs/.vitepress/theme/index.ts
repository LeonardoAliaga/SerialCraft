import DefaultTheme from 'vitepress/theme'
import { h } from 'vue'
import CookiePreferences from './CookiePreferences.vue'
import VersionSwitcher from './VersionSwitcher.vue'
import VersionNotice from './VersionNotice.vue'
import './home.css'
import './versions.css'

export default {
  extends: DefaultTheme,
  Layout: () => h(DefaultTheme.Layout, null, {
    'layout-bottom': () => h(CookiePreferences),
    'nav-bar-content-after': () => h(VersionSwitcher),
    'nav-screen-content-after': () => h(VersionSwitcher, { mobile: true }),
    'doc-before': () => h(VersionNotice)
  })
}
