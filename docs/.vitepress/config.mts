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
          // Menú Versiones (Español)
          {
            text: 'v0.4.3 (Beta)',
            items: [
              { text: 'v0.4.3 (Actual)', link: '/guide' },
              { text: 'v0.3.6 (Antigua)', link: '/v0.3.6/guide' },
              { text: 'Notas de Versión', link: 'https://github.com/leonardoaliaga/serialcraft/releases/tag/v0.4.3' },
              { text: 'Reportar Bug', link: 'https://github.com/leonardoaliaga/serialcraft/issues' }
            ]
          }
        ],
        sidebar: [
          {
            text: 'Introducción',
            items: [
              { text: 'Instalación', link: '/guide' },
              { text: 'Configuración de Conexión', link: '/guide#configuracion-de-conexion' },
              { text: 'Tu Primer Circuito', link: '/guide#tu-primer-circuito' }
            ]
          },
          {
            text: 'Hardware y Código',
            items: [
              { text: 'Protocolo Bidireccional', link: '/protocol' },
              { text: 'Implementación en Hardware', link: '/protocol#implementacion-en-hardware' }
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
            text: 'v0.4.3 (Beta)',
            items: [
              { text: 'v0.4.3 (Current)', link: '/en/guide' },
              { text: 'v0.3.6 (Legacy)', link: '/en/v0.3.6/guide' },
              { text: 'Release Notes', link: 'https://github.com/leonardoaliaga/serialcraft/releases/tag/v0.4.3' },
              { text: 'Report Bug', link: 'https://github.com/leonardoaliaga/serialcraft/issues' }
            ]
          }
        ],
        sidebar: [
          {
            text: 'Getting Started',
            items: [
              { text: 'Installation', link: '/en/guide' },
              { text: 'Connection Setup', link: '/en/guide#connection-setup' },
              { text: 'Your First Circuit', link: '/en/guide#your-first-circuit' }
            ]
          },
          {
            text: 'Hardware & Code',
            items: [
              { text: 'Bidirectional Protocol', link: '/en/protocol' },
              { text: 'Hardware Implementation', link: '/en/protocol#hardware-implementation' }
            ]
          }
        ]
      }
    }
  },

  themeConfig: {
    socialLinks: [
      { icon: 'github', link: 'https://github.com/leonardoaliaga/serialcraft' },
      { icon: 'instagram', link: 'https://instagram.com/aliaga1924' }
    ]
  }
})