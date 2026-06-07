import { defineConfig } from 'vitepress'

export default defineConfig({
  // Configuración compartida
  title: "SerialCraft",
  description: "Arduino to Minecraft Bridge",
  base: "/",
  head: [
    ['link', { rel: 'icon', type: 'image/png', href: '/favicon.png' }],
    [
      'script',
      { async: '', src: 'https://www.googletagmanager.com/gtag/js?id=G-DNHRBCWGDX' }
    ],
    [
      'script',
      {},
      `window.dataLayer = window.dataLayer || [];
      function gtag(){dataLayer.push(arguments);}
      gtag('js', new Date());
      gtag('config', 'G-DNHRBCWGDX');`
    ],
    // Google AdSense
    [
      'script',
      {
        async: '',
        src: 'https://pagead2.googlesyndication.com/pagead/js/adsbygoogle.js?client=ca-pub-9305749497490512',
        crossorigin: 'anonymous'
      }
    ]
  ],

  // Configuración de Idiomas
  locales: {
    root: {
      label: 'Español',
      lang: 'es',
      title: 'Guía SerialCraft',
      description: 'Conecta Arduino con Minecraft',
      themeConfig: {
        nav: [
          { text: 'Inicio', link: '/' },
          { text: 'Guía', link: '/guide' },
          { text: 'Referencia', link: '/protocol' },
          { text: 'Com. Bidireccional (Wi-Fi/USB)', link: '/bidirectional' }


          // Menú Versiones (Español)
          {
            text: 'v0.3.6 (Beta)',
            items: [
              { text: 'v0.3.6 (Actual)', link: '/guide' }, // <--- CAMBIO AQUÍ
              { text: 'Notas de Versión', link: 'https://github.com/leonardoaliaga/serialcraft/releases/tag/v0.3.6' },
              { text: 'Reportar Bug', link: 'https://github.com/leonardoaliaga/serialcraft/issues' }
            ]
          }
        ],
        sidebar: [
          {
            text: 'Introducción',
            items: [
              { text: 'Instalación', link: '/guide' },
              { text: 'Tu Primer Circuito', link: '/guide#tu-primer-circuito' }
            ]
          },
          {
            text: 'Arduino y Código',
            items: [
              { text: 'Protocolo', link: '/protocol' },
              { text: 'Comandos', link: '/protocol#comandos' }
              { text: 'Bidirectional Comm (Wi-Fi/USB)', link: '/en/bidirectional' }
            ]
          }
        ]
      }
    },
    en: {
      label: 'English',
      lang: 'en',
      link: '/en/',
      title: 'SerialCraft Guide',
      description: 'Connect Arduino with Minecraft',
      themeConfig: {
        nav: [
          { text: 'Home', link: '/en/' },
          { text: 'Guide', link: '/en/guide' },
          { text: 'Reference', link: '/en/protocol' },

          // Version Menu (English)
          {
            text: 'v0.3.6 (Beta)',
            items: [
              { text: 'v0.3.6 (Current)', link: '/en/guide' },
              { text: 'Release Notes', link: 'https://github.com/leonardoaliaga/serialcraft/releases/tag/v0.3.6' },
              { text: 'Report Bug', link: 'https://github.com/leonardoaliaga/serialcraft/issues' }
            ]
          }
        ],
        sidebar: [
          {
            text: 'Getting Started',
            items: [
              { text: 'Installation', link: '/en/guide' },
              { text: 'Your First Circuit', link: '/en/guide#your-first-circuit' }
            ]
          },
          {
            text: 'Arduino & Code',
            items: [
              { text: 'Protocol', link: '/en/protocol' },
              { text: 'Commands', link: '/en/protocol#commands' }
            ]
          }
        ]
      }
    }
  },

  themeConfig: {
    socialLinks: [
      { icon: 'github', link: 'https://github.com/leonardoaliaga/serialcraft' }
    ]
  }
})