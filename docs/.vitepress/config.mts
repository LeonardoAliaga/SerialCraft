import { defineConfig } from 'vitepress'

export default defineConfig({
  // Configuración compartida
  title: "SerialCraft",
  description: "Arduino to Minecraft Bridge",
  base: "/",

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
          { text: 'Guía', link: '/guide' },       // <--- CAMBIO AQUÍ
          { text: 'Referencia', link: '/protocol' }, // <--- CAMBIO AQUÍ

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
              { text: 'Instalación', link: '/guide' }, // <--- CAMBIO AQUÍ
              { text: 'Tu Primer Circuito', link: '/guide#tu-primer-circuito' } // <--- CAMBIO AQUÍ
            ]
          },
          {
            text: 'Arduino y Código',
            items: [
              { text: 'Protocolo', link: '/protocol' }, // <--- CAMBIO AQUÍ
              { text: 'Comandos', link: '/protocol#comandos' } // <--- CAMBIO AQUÍ
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