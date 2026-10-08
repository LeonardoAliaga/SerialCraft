import { existsSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { join } from 'node:path'

// Un solo contrato para las capturas planeadas en Notion y en los tutoriales.
// El archivo multimedia se integra automáticamente cuando existe en docs/public/media/0.4.6/.
// Si falta, el marcador queda invisible para el lector (sin referencias rotas).
type MediaType = 'image' | 'video'
type MediaEntry = { kind: MediaType; file: string; alt: string; caption: string }
export const MEDIA_SLOTS: Record<string, MediaEntry> = {
  H01: { kind: 'image', file: "sc046-h01-setup-general.webp", alt: "Montaje completo de SerialCraft con Minecraft y hardware real", caption: "Vista general del equipo usado para los experimentos de SerialCraft." },
  H02: { kind: 'image', file: "sc046-h02-arduino-cableado.webp", alt: "Arduino Uno R3 conectado a un potenciómetro, un LED y una resistencia", caption: "Montaje del Arduino Uno R3: potenciómetro en A0, LED en D9 con resistencia y GND común." },
  H03: { kind: 'image', file: "sc046-h03-led-apagado.webp", alt: "LED físico apagado antes de accionar la señal de Minecraft", caption: "LED apagado antes de activar la redstone." },
  H04: { kind: 'image', file: "sc046-h04-led-encendido.webp", alt: "LED físico encendido después de accionar la redstone en Minecraft", caption: "LED encendido tras recibir un mensaje desde Minecraft." },
  H05: { kind: 'image', file: "sc046-h05-potenciometro-redstone.webp", alt: "Potenciómetro físico y señal de redstone respondiendo al girar la perilla", caption: "El valor enviado por Arduino controla la intensidad de redstone." },
  H06: { kind: 'image', file: "sc046-h06-esp32-cableado.webp", alt: "Cableado de un ESP32 con entradas a 3,3 voltios", caption: "Montaje ESP32, ADC1, GND y alimentación de 3,3 V." },
  G01: { kind: 'image', file: "sc046-g01-modulo-en-mundo.webp", alt: "Módulo de entrada y salida de SerialCraft en un mundo de Minecraft", caption: "Módulo de E/S v0.4.6-beta en el entorno de Minecraft." },
  G02: { kind: 'image', file: "sc046-g02-editor-hardware-minecraft.webp", alt: "Editor del Módulo de E/S configurado de hardware a Minecraft", caption: "Canal y dirección Hardware → Minecraft en el editor." },
  G03: { kind: 'image', file: "sc046-g03-editor-minecraft-hardware.webp", alt: "Editor del Módulo de E/S configurado de Minecraft a hardware", caption: "Canal y dirección Minecraft → Hardware en el editor." },
  G04: { kind: 'image', file: "sc046-g04-conectores-colores.webp", alt: "Conectores verde de entrada y rojo de salida del Módulo de E/S", caption: "Los colores identifican el rol de las caras configuradas." },
  G05: { kind: 'image', file: "sc046-g05-conector-inferior.webp", alt: "Selección del conector inferior del Módulo de E/S", caption: "El terminal inferior se selecciona desde arriba y comunica con el bloque inferior." },
  G06: { kind: 'image', file: "sc046-g06-diagnostico-rx-tx.webp", alt: "Panel de diagnóstico de señales RX, TX, Lectura y Salida", caption: "El panel informa sobre señales; TX no confirma que el actuador físico haya reaccionado." },
  G07: { kind: 'image', file: "sc046-g07-redstone-niveles.webp", alt: "Comparación de intensidad de redstone en niveles cero, ocho y quince", caption: "Comparación de intensidad de redstone a varios niveles." },
  G08: { kind: 'image', file: "sc046-g08-compuerta-or.webp", alt: "Ejemplo de compuerta OR en el Módulo de E/S", caption: "OR toma el valor máximo de las entradas activas." },
  G09: { kind: 'image', file: "sc046-g09-compuerta-and.webp", alt: "Ejemplo de compuerta AND con entradas de redstone", caption: "AND toma el valor mínimo e incluye las entradas cero." },
  G10: { kind: 'image', file: "sc046-g10-compuerta-xor.webp", alt: "Ejemplo de compuerta XOR con señales de redstone", caption: "XOR conserva el máximo solo cuando el número de entradas positivas es impar." },
  G12: { kind: 'image', file: "sc046-g12-servidor-wifi.webp", alt: "Pantalla de servidor Wi-Fi local con credenciales ocultas", caption: "Servidor TCP local de SerialCraft: ocultar el token de emparejamiento." },
  G13: { kind: 'image', file: "sc046-g13-eventos-habilitados.webp", alt: "Pestaña Eventos de SerialCraft con telemetría activada", caption: "Activación de un canal mc_ desde la Laptop." },
  G14: { kind: 'image', file: "sc046-g14-telemetria-consola.webp", alt: "Consola con mensajes reales de telemetría de Minecraft", caption: "Ejemplo de líneas mc_hunger o mc_time recibidas por el dispositivo." },
  G15: { kind: 'image', file: "sc046-g15-visualizador-tiempo.webp", alt: "Línea de tiempo del visualizador con señales de SerialCraft", caption: "Evolución de muestras reales registradas por el visualizador." },
  G16: { kind: 'image', file: "sc046-g16-vista-sensor.webp", alt: "Vista Sensor que compara lectura cruda y redstone", caption: "Comparación de escala del hardware y redstone en la vista Sensor." },
  G17: { kind: 'image', file: "sc046-g17-generador.webp", alt: "Generador de señales usando un canal dedicado", caption: "El generador se utiliza en un canal independiente del Módulo de E/S." },
  G18: { kind: 'image', file: "sc046-g18-hud-f7.webp", alt: "HUD de depuración F7 con contadores RX y TX", caption: "Contadores de tráfico y descartes disponibles en el HUD de diagnóstico." },
  D01: { kind: 'image', file: "sc046-d01-java25.webp", alt: "Terminal con Java 25 configurado", caption: "Verificación local de la versión de Java para compilar SerialCraft." },
  D02: { kind: 'image', file: "sc046-d02-gradle-build.webp", alt: "Gradle completando la tarea build con éxito", caption: "Salida de Gradle build y pruebas unitarias." },
  D03: { kind: 'image', file: "sc046-d03-gametest.webp", alt: "Ejecución de Minecraft GameTest sobre un mundo temporal", caption: "Resultado de GameTests para el comportamiento de redstone del mod." },
  D04: { kind: 'image', file: "sc046-d04-github-actions.webp", alt: "GitHub Actions con el workflow Build and test", caption: "Comprobación automática en la rama test, asociada a un commit concreto." },
  I01: { kind: 'image', file: "sc046-i01-mapa-senales.webp", alt: "Diagrama de mapeo entre ADC, protocolo SerialCraft y redstone", caption: "Conversión entre ADC 0–1023, protocolo 0–255 y redstone 0–15." },
  I02: { kind: 'image', file: "sc046-i02-flujo-diagnostico.webp", alt: "Diagrama de diagnóstico de conexión de extremo a extremo", caption: "Comprueba hardware, transporte, protocolo, canal y redstone en orden." },
  V01: { kind: 'video', file: "sc046-v01-led.mp4", alt: "Demostración real de Minecraft encendiendo un LED físico", caption: "Demo: palanca en Minecraft → LED en Arduino." },
  V02: { kind: 'video', file: "sc046-v02-potenciometro.mp4", alt: "Demostración de un potenciómetro controlando redstone", caption: "Demo: potenciómetro físico → redstone." },
  V03: { kind: 'video', file: "sc046-v03-modulo-io.mp4", alt: "Recorrido por el editor y diagnóstico del Módulo de E/S", caption: "Video del Módulo de E/S, sus caras y su diagnóstico." },
  V04: { kind: 'video', file: "sc046-v04-esp32.mp4", alt: "Conexión de un ESP32 a SerialCraft por una red local", caption: "Demo de conexión ESP32 ↔ Minecraft en LAN." },
  V05: { kind: 'video', file: "sc046-v05-visualizador.mp4", alt: "Visualizador y generador de ondas de SerialCraft en uso", caption: "Demostración del visualizador y de un generador en un canal separado." },
  V06: { kind: 'video', file: "sc046-v06-telemetria.mp4", alt: "Telemetría mc_ del juego recibida por un microcontrolador", caption: "Demo de estado de Minecraft transmitido al hardware." },
  V07: { kind: 'video', file: "sc046-v07-pruebas.mp4", alt: "Comandos Gradle y pruebas automatizadas de SerialCraft", caption: "Compilación, JUnit, GameTests y GitHub Actions de un commit específico." },
}
const mediaRoot = fileURLToPath(new URL('../public/media/0.4.6/', import.meta.url))
const safe = (value: string) => value.replace(/&/g, '&amp;').replace(/"/g, '&quot;').replace(/</g, '&lt;').replace(/>/g, '&gt;')

export function registerMediaSlots(md: any) {
  md.core.ruler.after('block', 'serialcraft-media-slots', (state: any) => {
    for (const token of state.tokens) {
      if (token.type !== 'html_block') continue
      const match = /^<!--\s*SC046_MEDIA:([A-Z][0-9]{2})\s*-->$/.exec(token.content.trim())
      if (!match) continue
      const entry = MEDIA_SLOTS[match[1]]
      if (!entry) throw new Error('Unknown SerialCraft media slot: ' + match[1])
      if (!existsSync(join(mediaRoot, entry.file))) {
        token.content = ''
        continue
      }
      const path = '/media/0.4.6/' + entry.file
      const media = entry.kind === 'image'
        ? `<img src="${safe(path)}" alt="${safe(entry.alt)}" loading="lazy" decoding="async" style="max-width:100%;height:auto;border-radius:8px">`
        : `<video controls preload="none" playsinline aria-label="${safe(entry.alt)}" style="max-width:100%;border-radius:8px"><source src="${safe(path)}" type="video/mp4"></video>`
      token.content = `<figure class="serialcraft-media">${media}<figcaption style="font-size:.85em;color:var(--vp-c-text-2)">${safe(entry.caption)}</figcaption></figure>\n`
    }
  })
}
