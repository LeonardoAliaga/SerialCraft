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
          { text: 'Ejemplos', link: '/ejemplos/' },
          // Menú Versiones (Español)
          {
            text: 'v0.4.3 (Beta)',
            items: [
              { text: 'v0.4.3 (Actual)', link: '/guide' },
              { text: 'v0.3.6 (Antigua)', link: '/versiones/v0.3.6/guide' },
              { text: 'Notas de Versión', link: 'https://github.com/leonardoaliaga/serialcraft/releases/tag/v0.4.3' },
              { text: 'Reportar Bug', link: 'https://github.com/leonardoaliaga/serialcraft/issues' }
            ]
          }
        ],
        sidebar: [
          {
            text: 'Introducción',
            items: [
              { text: 'Instalación', link: '/guide#instalacion' },
              { text: 'Configuración de la conexión', link: '/guide#configuracion-de-la-conexion' },
              { text: 'Tu primer circuito', link: '/guide#tu-primer-circuito-bidireccional' },
              { text: 'Límites conocidos', link: '/guide#limites-conocidos-de-esta-version' }
            ]
          },
          {
            text: 'Hardware y código',
            items: [
              { text: 'Protocolo bidireccional', link: '/protocol' },
              { text: 'Escala unificada 0-255', link: '/protocol#_2-escala-unificada-0-255' },
              { text: 'Wi-Fi y emparejamiento', link: '/protocol#_5-wi-fi-el-mod-es-el-servidor' },
              { text: 'Cambios respecto a 0.3.x', link: '/protocol#_8-cambios-respecto-a-0-3-x' }
            ]
          },
          {
            text: 'Ejemplos y pruebas',
            items: [
              { text: 'Índice de ejemplos', link: '/ejemplos/' },
              { text: 'Arduino Uno R3 (USB)', link: '/ejemplos/#_2-arduino-uno-r3-usb' },
              { text: 'ESP32 (Wi-Fi)', link: '/ejemplos/#_3-esp32-wi-fi' },
              { text: 'Arduino Uno Q', link: '/ejemplos/#_4-arduino-uno-q-bridge-python' },
              { text: 'Rutina de prueba', link: '/ejemplos/#_5-rutina-de-prueba' },
              { text: 'Prompts para esquemas', link: '/ejemplos/esquemas-ia' }
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
          { text: 'Examples', link: '/en/examples/' },
          // Version Menu (English)
          {
            text: 'v0.4.3 (Beta)',
            items: [
              { text: 'v0.4.3 (Current)', link: '/en/guide' },
              { text: 'v0.3.6 (Legacy)', link: '/versiones/en/v0.3.6/guide' },
              { text: 'Release Notes', link: 'https://github.com/leonardoaliaga/serialcraft/releases/tag/v0.4.3' },
              { text: 'Report Bug', link: 'https://github.com/leonardoaliaga/serialcraft/issues' }
            ]
          }
        ],
        sidebar: [
          {
            text: 'Getting Started',
            items: [
              { text: 'Installation', link: '/en/guide#installation' },
              { text: 'Connection setup', link: '/en/guide#connection-setup' },
              { text: 'Your first circuit', link: '/en/guide#your-first-bidirectional-circuit' },
              { text: 'Known limits', link: '/en/guide#known-limits-in-this-version' }
            ]
          },
          {
            text: 'Hardware & Code',
            items: [
              { text: 'Bidirectional protocol', link: '/en/protocol' },
              { text: 'Unified 0-255 scale', link: '/en/protocol#_2-unified-0-255-scale' },
              { text: 'Wi-Fi and pairing', link: '/en/protocol#_5-wi-fi-the-mod-is-the-server' },
              { text: 'Changes from 0.3.x', link: '/en/protocol#_8-changes-from-0-3-x' }
            ]
          },
          {
            text: 'Examples & Testing',
            items: [
              { text: 'Example index', link: '/en/examples/' },
              { text: 'Arduino Uno R3 (USB)', link: '/en/examples/#_2-arduino-uno-r3-usb' },
              { text: 'ESP32 (Wi-Fi)', link: '/en/examples/#_3-esp32-wi-fi' },
              { text: 'Arduino Uno Q', link: '/en/examples/#_4-arduino-uno-q-bridge-python' },
              { text: 'Test routine', link: '/en/examples/#_5-test-routine' },
              { text: 'Wiring diagram prompts', link: '/en/examples/wiring-prompts' }
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