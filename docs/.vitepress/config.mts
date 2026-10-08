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
            text: 'v0.4.6 (Beta)',
            items: [
              { text: 'v0.4.6 (Actual)', link: '/guide' },
              { text: 'v0.4.3 (Antigua)', link: '/versiones/v0.4.3/guide' },
              { text: 'v0.3.6 (Legado)', link: '/versiones/v0.3.6/guide' },
              { text: 'Notas de Versión', link: 'https://github.com/leonardoaliaga/serialcraft/releases' },
              { text: 'Reportar Bug', link: 'https://github.com/leonardoaliaga/serialcraft/issues' }
            ]
          }
        ],
        sidebar: {
          '/versiones/v0.4.3/': [
            {
              text: 'SerialCraft v0.4.3 (Beta)',
              items: [
                { text: 'Guía de la versión', link: '/versiones/v0.4.3/guide' },
                { text: 'Protocolo y Hardware', link: '/versiones/v0.4.3/protocol' },
                { text: 'Ejemplos y pruebas', link: '/versiones/v0.4.3/ejemplos' }
              ]
            },
            {
              text: 'Ejemplos v0.4.3',
              items: [
                { text: 'Arduino Uno R3 (USB)', link: '/versiones/v0.4.3/ejemplos#_2-arduino-uno-r3-usb' },
                { text: 'ESP32 (Wi-Fi)', link: '/versiones/v0.4.3/ejemplos#_3-esp32-wi-fi' },
                { text: 'Arduino Uno Q', link: '/versiones/v0.4.3/ejemplos#_4-arduino-uno-q-bridge-python' },
                { text: 'Rutina de prueba', link: '/versiones/v0.4.3/ejemplos#_5-rutina-de-prueba' }
              ]
            }
          ],
          '/versiones/v0.3.6/': [
            {
              text: 'SerialCraft v0.3.6 (Beta)',
              items: [
                { text: 'Guía de la versión', link: '/versiones/v0.3.6/guide' },
                { text: 'Protocolo de comunicación', link: '/versiones/v0.3.6/protocol' }
              ]
            }
          ],
          '/': [
            {
              text: 'Introducción',
              items: [
                { text: 'Instalación', link: '/guide#instalacion' },
                { text: 'Configuración de la conexión', link: '/guide#configuracion-de-la-conexion' },
                { text: 'Módulo de E/S', link: '/io-module' },
                { text: 'Tu primer circuito', link: '/guide#tu-primer-circuito-bidireccional' },
                { text: 'Banco de pruebas', link: '/guide#banco-de-pruebas-pestana-visualizar' },
                { text: 'Límites conocidos', link: '/guide#limites-conocidos-de-esta-version' }
              ]
            },
            {
              text: 'Hardware y código',
              items: [
                { text: 'Protocolo bidireccional', link: '/protocol' },
                { text: 'Escala unificada 0-255', link: '/protocol#_2-escala-unificada-0-255' },
                { text: 'Wi-Fi y emparejamiento', link: '/protocol#_5-wi-fi-el-mod-es-el-servidor' },
                { text: 'Cambios respecto a 0.3.x', link: '/protocol#_8-cambios-respecto-a-0-3-x' },
                { text: 'Cambios respecto a 0.4.3', link: '/protocol#_9-cambios-respecto-a-0-4-3' },
                { text: 'Telemetría del juego', link: '/protocol#_11-telemetria-del-juego-minecraft-➔-hardware' },
                { text: 'Identificación y reconexión sin token', link: '/protocol#_12-identificacion-de-la-placa-y-reconexion-sin-token' },
                { text: 'Probar con el generador', link: '/protocol#_13-probar-con-el-generador-pestana-visualizar' }
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
              ]
            }
          ]
        }
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
            text: 'v0.4.6 (Beta)',
            items: [
              { text: 'v0.4.6 (Current)', link: '/en/guide' },
              { text: 'v0.4.3 (Legacy)', link: '/versiones/en/v0.4.3/guide' },
              { text: 'v0.3.6 (Older)', link: '/versiones/en/v0.3.6/guide' },
              { text: 'Release Notes', link: 'https://github.com/leonardoaliaga/serialcraft/releases' },
              { text: 'Report Bug', link: 'https://github.com/leonardoaliaga/serialcraft/issues' }
            ]
          }
        ],
        sidebar: {
          '/versiones/en/v0.4.3/': [
            {
              text: 'SerialCraft v0.4.3 (Beta)',
              items: [
                { text: 'Version Guide', link: '/versiones/en/v0.4.3/guide' },
                { text: 'Protocol & Hardware', link: '/versiones/en/v0.4.3/protocol' },
                { text: 'Examples & Testing', link: '/versiones/en/v0.4.3/examples' }
              ]
            },
            {
              text: 'v0.4.3 Examples',
              items: [
                { text: 'Arduino Uno R3 (USB)', link: '/versiones/en/v0.4.3/examples#_2-arduino-uno-r3-usb' },
                { text: 'ESP32 (Wi-Fi)', link: '/versiones/en/v0.4.3/examples#_3-esp32-wi-fi' },
                { text: 'Arduino Uno Q', link: '/versiones/en/v0.4.3/examples#_4-arduino-uno-q-bridge-python' },
                { text: 'Test routine', link: '/versiones/en/v0.4.3/examples#_5-test-routine' }
              ]
            }
          ],
          '/versiones/en/v0.3.6/': [
            {
              text: 'SerialCraft v0.3.6 (Beta)',
              items: [
                { text: 'Version Guide', link: '/versiones/en/v0.3.6/guide' },
                { text: 'Communication Protocol', link: '/versiones/en/v0.3.6/protocol' }
              ]
            }
          ],
          '/en/': [
            {
              text: 'Getting Started',
              items: [
                { text: 'Installation', link: '/en/guide#installation' },
                { text: 'Connection setup', link: '/en/guide#connection-setup' },
                { text: 'Hardware I/O Module', link: '/en/io-module' },
                { text: 'Your first circuit', link: '/en/guide#your-first-bidirectional-circuit' },
                { text: 'Test bench', link: '/en/guide#test-bench-visualizer-tab' },
                { text: 'Known limits', link: '/en/guide#known-limits-in-this-version' }
              ]
            },
            {
              text: 'Hardware & Code',
              items: [
                { text: 'Bidirectional protocol', link: '/en/protocol' },
                { text: 'Unified 0-255 scale', link: '/en/protocol#_2-unified-0-255-scale' },
                { text: 'Wi-Fi and pairing', link: '/en/protocol#_5-wi-fi-the-mod-is-the-server' },
                { text: 'Changes from 0.3.x', link: '/en/protocol#_8-changes-from-0-3-x' },
                { text: 'Changes from 0.4.3', link: '/en/protocol#_9-changes-from-0-4-3' },
                { text: 'Game telemetry', link: '/en/protocol#_11-game-telemetry-minecraft-➔-hardware' },
                { text: 'Board identification & token-free reconnection', link: '/en/protocol#_12-board-identification-and-token-free-reconnection' },
                { text: 'Testing with the generator', link: '/en/protocol#_13-testing-with-the-generator-visualizer-tab' }
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
              ]
            }
          ]
        }
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
