# Ejemplos y pruebas (v0.4.6)

Tres montajes equivalentes del **mismo circuito** sobre plataformas distintas: un potenciómetro que controla un nivel de redstone dentro de Minecraft, y un LED físico cuyo brillo lo decide la redstone del juego.

El circuito es idéntico en los tres casos; lo que cambia es el transporte.

| Plataforma | Transporte | Dificultad |
| :--- | :--- | :--- |
| [Arduino Uno R3](#_2-arduino-uno-r3-usb) | USB Serial | La más simple, empieza aquí |
| [ESP32](#_3-esp32-wi-fi) | Wi-Fi (TCP) | Sin cables, requiere token |
| [Arduino Uno Q](#_4-arduino-uno-q-bridge-python) | Wi-Fi vía MPU + Python | La más compleja |

---

## 1. Preparación común en Minecraft

Los tres ejemplos necesitan exactamente los mismos dos bloques IO:

| Bloque | Target Data | Modo | Tipo de señal | Qué hace |
| :--- | :--- | :--- | :--- | :--- |
| Entrada | `pot_val` | **INPUT** | **Analógica** | El potenciómetro genera redstone 0-15 |
| Salida | `led_verde` | **OUTPUT** | **Analógica** | La redstone del juego regula el brillo del LED |

Montaje sugerido dentro del juego:

1. Coloca el bloque `pot_val` y conecta una lámpara de redstone a su lado, o una línea de polvo hacia un comparador para ver el nivel.
2. Coloca el bloque `led_verde` con una palanca o una línea de polvo de redstone entrando por uno de sus lados configurados como entrada.
3. Configura los lados de entrada en el menú de cada bloque.

::: tip Prueba rápida sin hardware
Antes de conectar nada, activa el HUD de depuración (tecla asignada en *Opciones → Controles → SerialCraft*). Muestra los mensajes que salen y entran, así distingues un problema del juego de uno del cableado.
:::

---

## 2. Arduino Uno R3 (USB)

### Conexión física

| Componente | Pin de la placa | Notas |
| :--- | :--- | :--- |
| Potenciómetro, patilla izquierda | `5V` | |
| Potenciómetro, patilla central | `A0` | Cursor: es la señal |
| Potenciómetro, patilla derecha | `GND` | |
| LED, ánodo (patilla larga) | `D9` mediante resistencia de 220 Ω | D9 es PWM |
| LED, cátodo (patilla corta) | `GND` | |

### Configuración en el juego

Bloque Conector → pestaña Conexión → selecciona el puerto del Uno → **115200 baudios**.

::: warning
El sketch usa `Serial.begin(115200)`. Si cambias uno de los dos valores, cambia el otro. Con baudios distintos no aparece ningún error: simplemente no llega nada.
:::

### Sketch

```cpp
/*
 * SerialCraft USB Bridge — Arduino Uno R3
 * ============================================================
 * Mod SerialCraft v0.4.6 (Minecraft Fabric 26.2, Java 25)
 * Comunicacion directa por cable USB (Serial).
 *
 * Hardware:
 *   - Potenciometro en A0 (pin central; extremos a 5V y GND)
 *   - LED verde en D9 (pin PWM) con resistencia de 220 ohm a GND
 *
 * Bloques IO que debes crear en el juego:
 *   - Target Data "pot_val"   -> modo INPUT   (placa -> Minecraft)
 *   - Target Data "led_verde" -> modo OUTPUT  (Minecraft -> placa)
 *
 * Protocolo y Canales:
 *   - Canal Bloques IO:
 *       Enviar al mod:   "pot_val:<0-255>\n"
 *       Recibir del mod: "led_verde:<0-255>\n"
 *   - Telemetria del juego:
 *       Prefijo reservado "mc_" (ej. "mc_health:20\n", "mc_damage:4\n").
 *       Permite descarte inmediato si la placa solo maneja pines.
 *
 * IMPORTANTE: los baudios de este sketch y los del Bloque Conector en el
 * juego deben coincidir. Aqui usamos 115200, que es el valor por defecto
 * del mod. Si los cambias en uno, cambialos en el otro.
 */

// ── Pines ────────────────────────────────────────────────────────────
const int POT_PIN = A0;
const int LED_PIN = 9;

// ── Identificadores de bloque (deben coincidir con Target Data) ──
const char* BLOCK_ID_POT = "pot_val";
const char* BLOCK_ID_LED = "led_verde";

// ── Protocolo ────────────────────────────────────────────────────────
const long  BAUD_RATE     = 115200;  // debe coincidir con el Bloque Conector
const int   ADC_MAX       = 1023;    // ADC de 10 bits del ATmega328P
const int   PWM_MAX       = 255;
const int   POT_HYSTERESIS = 2;      // ignora ruido electrico del pot
const int   BUFFER_LIMIT  = 48;      // el mod corta lineas de mas de 256
const unsigned long LOOP_DELAY_MS = 30;  // ~33 msg/s < limite de 40/s del mod

// ── Estado ───────────────────────────────────────────────────────────
int    lastPotValue = -1;
String inputBuffer  = "";

void setup() {
  Serial.begin(BAUD_RATE);

  pinMode(LED_PIN, OUTPUT);
  analogWrite(LED_PIN, 0);   // LED apagado al iniciar

  // Reservar memoria del buffer evita fragmentacion del heap en el Uno.
  inputBuffer.reserve(BUFFER_LIMIT + 8);
}

void loop() {
  readPotentiometer();
  readIncomingCommands();

  // Pausa que estabiliza la lectura del ADC y mantiene el ritmo de envio
  // por debajo del limitador de red del mod (40 paquetes/s sostenidos).
  delay(LOOP_DELAY_MS);
}

// ── 1. Potenciometro -> Minecraft ──────────────────────────
void readPotentiometer() {
  int raw      = analogRead(POT_PIN);
  int potValue = map(raw, 0, ADC_MAX, 0, PWM_MAX);

  // Enviar solo si cambio de verdad. Sin esta comprobacion el ruido del
  // potenciometro genera un paquete por vuelta de loop().
  if (abs(potValue - lastPotValue) >= POT_HYSTERESIS) {
    Serial.print(BLOCK_ID_POT);
    Serial.print(':');
    Serial.println(potValue);   // println: el mod ignora lineas sin '\n'
    lastPotValue = potValue;
  }
}

// ── 2. Minecraft -> LED ────────────────────────────────────
void readIncomingCommands() {
  while (Serial.available() > 0) {
    char c = Serial.read();

    if (c == '\n') {
      processCommand(inputBuffer);
      inputBuffer = "";
      continue;
    }
    if (c == '\r') continue;

    // Cortar ANTES de anadir: con 2 KB de RAM, una linea sin '\n' agota
    // la memoria del Uno y lo reinicia en bucle.
    if (inputBuffer.length() >= BUFFER_LIMIT) {
      inputBuffer = "";
      continue;
    }
    inputBuffer += c;
  }
}

void processCommand(String command) {
  command.trim();
  if (command.length() == 0) return;

  // 1. Discriminacion de telemetria:
  // Si comienza por "mc_", pertenece a la telemetria del juego (mc_time, mc_health...).
  // Al no procesar telemetria en este sketch, lo descartamos de inmediato sin procesar.
  if (command.startsWith("mc_")) return;

  // 2. Canal de bloques IO (<TARGET_DATA>:<VALOR>)
  int sep = command.indexOf(':');
  if (sep <= 0 || sep == command.length() - 1) return;  // "cmd", ":5" o "cmd:"

  String blockId  = command.substring(0, sep);
  String valueStr = command.substring(sep + 1);

  if (blockId == BLOCK_ID_LED) {
    // Desde v0.4.3 el mod envia 0-255 tanto en modo Analogico como en
    // Digital (digital = 0 o 255), asi que un unico analogWrite() sirve
    // para los dos casos sin saber como esta configurado el bloque.
    int pwm = constrain(valueStr.toInt(), 0, PWM_MAX);
    analogWrite(LED_PIN, pwm);
  }
}
```
### Qué esperar

* Al girar el potenciómetro, el nivel de redstone del bloque `pot_val` sube y baja de 0 a 15.
* Al alimentar el bloque `led_verde` con redstone, el LED cambia de brillo proporcionalmente.
* La consola de la Laptop muestra las líneas en ambos sentidos.

---

## 3. ESP32 (Wi-Fi)

::: danger 3,3 V
El ESP32 **no tolera 5 V** en sus pines. Alimenta el potenciómetro desde `3V3`.
:::

### Conexión física

| Componente | Pin de la placa | Notas |
| :--- | :--- | :--- |
| Potenciómetro, patilla izquierda | `3V3` | Nunca 5 V |
| Potenciómetro, patilla central | `GPIO34` | ADC1, solo entrada |
| Potenciómetro, patilla derecha | `GND` | |
| LED, ánodo | `GPIO16` mediante resistencia de 220 Ω | Canal LEDC |
| LED, cátodo | `GND` | |

::: tip Por qué GPIO34
El ADC2 del ESP32 queda inutilizable mientras el Wi-Fi está activo. Usa siempre pines de ADC1 (32-39) en proyectos conectados.
:::

### Configuración en el juego

Laptop → Inicio → **Iniciar servidor Wi-Fi**. Copia al sketch la IP, el puerto y el token que muestra la interfaz.

### Sketch

```cpp
/*
 * SerialCraft Wi-Fi Bridge — ESP32
 * ============================================================
 * Mod SerialCraft v0.4.6 (Minecraft Fabric 26.2, Java 25)
 *
 * DIRECCION DE LA CONEXION (cambio importante en v0.4.3):
 *   El MOD es el servidor TCP y la PLACA es el cliente.
 *   Antes se documentaba al reves (ESP32 como servidor en el 8080).
 *   Ahora: abre la Laptop en el juego -> pestana Inicio -> "Iniciar
 *   servidor Wi-Fi". La UI te muestra la IP, el puerto y un token.
 *   Copia esos tres valores aqui abajo.
 *
 * Handshake: la primera vez, la primera linea que envia la placa es el
 * token. El mod responde "OK" o cierra con "ERR TOKEN".
 *
 * PLACA RECORDADA (sin token): tras conectar, la placa se anuncia con
 *   mc_id:model=<chip>;uid=<id>
 * y, si en el juego pulsas "Recordar placa", el mod le entrega una clave
 * (mc_key:...) que se guarda en la memoria flash (NVS). Desde entonces la
 * placa entra con "TRUST <uid>" + reto-respuesta HMAC-SHA256 y ya NO necesita
 * el token. La clave no vuelve a viajar por la red. Si el mod la olvida
 * (ERR UNKNOWN / ERR TRUST) la placa borra su copia y vuelve a usar el token.
 *
 * Canales de comunicacion:
 *   - Canal de Bloques IO: "<id>:[0-255]\n"
 *   - Telemetria del juego: "mc_<clave>:[valor]\n"
 *
 * Hardware:
 *   - Potenciometro en GPIO34 (ADC1, solo entrada; extremos a 3V3 y GND)
 *   - LED verde en GPIO16 con resistencia de 220 ohm a GND
 *
 * ATENCION 3,3 V: el ESP32 NO tolera 5 V en sus pines. Alimenta el
 * potenciometro desde 3V3, nunca desde el pin de 5 V.
 *
 * Bloques IO que debes crear en el juego:
 *   - Target Data "pot_val"   -> modo INPUT
 *   - Target Data "led_verde" -> modo OUTPUT
 */

#include <WiFi.h>
#include <Preferences.h>
#include "mbedtls/md.h"

// ═════════════════════════════════════════════════════════════
//  CONFIGURACION  <- EDITA ESTO
// ═════════════════════════════════════════════════════════════
const char* WIFI_SSID     = "TU_WIFI";
const char* WIFI_PASSWORD = "TU_PASSWORD";

const char* MINECRAFT_IP   = "192.168.1.50";  // IP que muestra la Laptop
const uint16_t MINECRAFT_PORT = 25585;        // puerto por defecto del mod
const char* PAIRING_TOKEN  = "XXXXXX";        // token que muestra la Laptop
// ═════════════════════════════════════════════════════════════

const char* BLOCK_ID_POT = "pot_val";
const char* BLOCK_ID_LED = "led_verde";

const int POT_PIN = 34;
const int LED_PIN = 16;

const int ADC_MAX = 4095;   // ADC de 12 bits del ESP32
const int PWM_MAX = 255;
const int POT_HYSTERESIS = 3;          // el ADC del ESP32 es ruidoso
const unsigned long POT_INTERVAL_MS = 50;   // 20 lecturas/s < 40 msg/s
const unsigned long RECONNECT_DELAY_MS = 3000;
const size_t MAX_LINE = 256;   // el mod corta la sesion si se excede

WiFiClient client;
Preferences prefs;                // memoria flash: guarda la clave recordada
char boardUid[24];                // p. ej. ESP32-A1B2C3D4E5F6 (sale de la MAC)
int    lastPotValue = -1;
String rxBuffer = "";
unsigned long lastPotRead = 0;

// ── PWM: la API cambio entre el core 2.x y el 3.x del ESP32 ──
void ledSetup() {
#if ESP_ARDUINO_VERSION_MAJOR >= 3
  ledcAttach(LED_PIN, 5000, 8);        // pin, 5 kHz, 8 bits
#else
  ledcSetup(0, 5000, 8);
  ledcAttachPin(LED_PIN, 0);
#endif
}

void ledWrite(int value) {
#if ESP_ARDUINO_VERSION_MAJOR >= 3
  ledcWrite(LED_PIN, value);
#else
  ledcWrite(0, value);
#endif
}

void setup() {
  Serial.begin(115200);       // solo para depurar por el monitor serie
  prefs.begin("serialcraft", false);
  snprintf(boardUid, sizeof(boardUid), "ESP32-%012llX", (unsigned long long)ESP.getEfuseMac());
  ledSetup();
  ledWrite(0);
  rxBuffer.reserve(MAX_LINE + 8);

  WiFi.mode(WIFI_STA);
  WiFi.begin(WIFI_SSID, WIFI_PASSWORD);
  Serial.print("Conectando a Wi-Fi");
  while (WiFi.status() != WL_CONNECTED) {
    delay(500);
    Serial.print('.');
  }
  Serial.print("\nIP de la placa: ");
  Serial.println(WiFi.localIP());
}

void loop() {
  if (!client.connected()) {
    ledWrite(0);              // no dejar el actuador encendido sin enlace
    connectToMod();
    return;
  }

  readIncoming();
  sendPotentiometer();
}

// ── Identidad y clave ────────────────────────────────────────
// Dice al mod que placa es: "ESP32-S3", "ESP32-C3"... (ESP.getChipModel()).
void announce() {
  client.print(String("mc_id:model=") + ESP.getChipModel() + ";uid=" + boardUid + "\n");
}

String hmacSha256Hex(const String& key, const String& msg) {
  unsigned char out[32];
  mbedtls_md_context_t ctx;
  mbedtls_md_init(&ctx);
  mbedtls_md_setup(&ctx, mbedtls_md_info_from_type(MBEDTLS_MD_SHA256), 1);
  mbedtls_md_hmac_starts(&ctx, (const unsigned char*)key.c_str(), key.length());
  mbedtls_md_hmac_update(&ctx, (const unsigned char*)msg.c_str(), msg.length());
  mbedtls_md_hmac_finish(&ctx, out);
  mbedtls_md_free(&ctx);

  const char* hex = "0123456789abcdef";
  String result;
  result.reserve(64);
  for (int i = 0; i < 32; i++) {
    result += hex[out[i] >> 4];
    result += hex[out[i] & 0x0F];
  }
  return result;
}

// Lee una linea del mod con limite de tiempo; "" si no llega nada.
String readLineTimeout(unsigned long ms) {
  unsigned long deadline = millis() + ms;
  while (client.connected() && !client.available() && millis() < deadline) delay(10);
  String line = client.readStringUntil('\n');
  line.trim();
  return line;
}

// ── Conexion + handshake ─────────────────────────────────────
bool handshakeWithToken() {
  client.print(String(PAIRING_TOKEN) + "\n");
  String reply = readLineTimeout(3000);
  if (reply != "OK") {
    Serial.println("Handshake rechazado: " + reply + " (revisa el token)");
    return false;
  }
  return true;
}

bool handshakeWithKey(const String& key) {
  client.print(String("TRUST ") + boardUid + "\n");
  String chal = readLineTimeout(3000);

  if (!chal.startsWith("CHAL ")) {
    if (chal == "ERR UNKNOWN") {
      Serial.println("El mod ya no recuerda esta placa. Pon el token nuevo.");
      prefs.remove("key");
    }
    return false;
  }

  client.print(hmacSha256Hex(key, chal.substring(5)) + "\n");
  String reply = readLineTimeout(3000);
  if (reply == "OK") return true;

  if (reply == "ERR TRUST") {
    Serial.println("Clave rechazada: se borra. Pon el token nuevo.");
    prefs.remove("key");
  }
  return false;
}

void connectToMod() {
  Serial.printf("Conectando a %s:%u ...\n", MINECRAFT_IP, MINECRAFT_PORT);

  if (!client.connect(MINECRAFT_IP, MINECRAFT_PORT, 5000)) {
    Serial.println("Sin respuesta. ¿Entraste al mundo? (el servidor Wi-Fi arranca solo con placas recordadas)");
    delay(RECONNECT_DELAY_MS);
    return;
  }

  String key = prefs.getString("key", "");
  bool ok = key.length() >= 16 ? handshakeWithKey(key) : handshakeWithToken();
  if (!ok) {
    client.stop();
    delay(RECONNECT_DELAY_MS);
    return;
  }

  Serial.println(key.length() >= 16 ? "Enlazado con SerialCraft (placa recordada)."
                                    : "Enlazado con SerialCraft.");
  announce();                 // el mod muestra el modelo real de la placa
  rxBuffer = "";
  lastPotValue = -1;          // forzar el primer envio
}

// ── Minecraft -> LED ─────────────────────────────────────────
void readIncoming() {
  while (client.available()) {
    char c = client.read();

    if (c == '\n') {
      processCommand(rxBuffer);
      rxBuffer = "";
      continue;
    }
    if (c == '\r') continue;

    if (rxBuffer.length() >= MAX_LINE) {   // linea abusiva: descartar
      rxBuffer = "";
      continue;
    }
    rxBuffer += c;
  }
}

void processCommand(String command) {
  command.trim();
  if (command.length() == 0) return;

  // 0. Mensajes del propio mod sobre la identidad de esta placa.
  if (command.startsWith("mc_key:")) {            // clave para reconectar sin token
    String newKey = command.substring(7);
    if (newKey.length() >= 16 && newKey.length() <= 128) {
      prefs.putString("key", newKey);
      Serial.println("Clave guardada: esta placa ya no necesita token.");
    }
    return;
  }
  if (command.startsWith("mc_who:")) { announce(); return; }

  // 1. Discriminacion de telemetria:
  // Si comienza por "mc_", es telemetria del juego (mc_time, mc_health, etc.).
  // Si la placa solo controla pines fisicos de redstone, se descarta directamente.
  if (command.startsWith("mc_")) return;

  // 2. Canal de bloques IO (<id>:[0-255])
  int sep = command.indexOf(':');
  if (sep <= 0 || sep == command.length() - 1) return;

  String blockId  = command.substring(0, sep);
  String valueStr = command.substring(sep + 1);

  if (blockId == BLOCK_ID_LED) {
    // 0-255 siempre, tanto en Digital (0 o 255) como en Analogico.
    int pwm = constrain(valueStr.toInt(), 0, PWM_MAX);
    ledWrite(pwm);
  }
}

// ── Potenciometro -> Minecraft ───────────────────────────────
void sendPotentiometer() {
  if (millis() - lastPotRead < POT_INTERVAL_MS) return;
  lastPotRead = millis();

  int potValue = map(analogRead(POT_PIN), 0, ADC_MAX, 0, PWM_MAX);

  if (abs(potValue - lastPotValue) >= POT_HYSTERESIS) {
    client.print(String(BLOCK_ID_POT) + ":" + String(potValue) + "\n");
    lastPotValue = potValue;
  }
}
```
### Verificación

Abre el monitor serie a 115200: verás la IP asignada a la placa, el intento de conexión y el resultado del handshake. `Handshake rechazado` significa token incorrecto o que ya hay otra placa conectada.

---

## 4. Arduino Uno Q (Bridge + Python)

El Uno Q lleva dos procesadores: un microcontrolador STM32U585 que maneja los pines y una MPU Qualcomm con Linux que maneja la red. El sketch expone funciones; el Python las llama y habla con Minecraft.

```text
MCU (STM32U585) <-> Bridge <-> MPU (Python) <-> Wi-Fi TCP <-> Mod SerialCraft
```

::: danger 3,3 V
Las entradas del Uno Q trabajan a 3,3 V. Alimenta el potenciómetro desde `3V3`.
:::

### Conexión física

| Componente | Pin de la placa | Notas |
| :--- | :--- | :--- |
| Potenciómetro, patilla izquierda | `3V3` | |
| Potenciómetro, patilla central | `A0` | ADC de 12 bits |
| Potenciómetro, patilla derecha | `GND` | |
| LED, ánodo | `D9` mediante resistencia de 220 Ω | PWM |
| LED, cátodo | `GND` | |

### Sketch (lado MCU)

```cpp
/*
 * SerialCraft Wi-Fi Bridge — Arduino Uno Q (lado MCU)
 * ============================================================
 * Mod SerialCraft v0.4.3 (Minecraft Fabric 1.21.11)
 *
 * Arquitectura:
 *   MCU (STM32U585) <-> Bridge <-> MPU (QRB2210 / Python) <-> TCP <-> Mod
 *
 * Este sketch NO habla con Minecraft. Solo expone funciones al Python
 * que corre en la MPU; toda la logica de red vive alli (main.py).
 *
 * Hardware:
 *   - Potenciometro en A0 (extremos a 3V3 y GND)
 *   - LED verde en D9 (PWM) con resistencia de 220 ohm a GND
 *
 * ATENCION 3,3 V: las entradas del Uno Q trabajan a 3,3 V. Alimenta el
 * potenciometro desde 3V3, no desde 5 V.
 */

#include <Arduino_RouterBridge.h>

// ── Pines ──────────────────────────────────────────────────
const int POT_PIN = A0;
const int LED_PIN = 9;

// ── Escalas ────────────────────────────────────────────────
// El ADC del STM32U585 es de 12 bits. La resolucion se fija de forma
// EXPLICITA en setup() para que este valor sea cierto: por defecto el
// core de Arduino devuelve 10 bits, y mapear 0-1023 leyendo 0-4095 hacia
// que el potenciometro llegara al maximo en un cuarto de su recorrido.
const int ADC_BITS = 12;
const int ADC_MAX  = 4095;
const int PWM_MAX  = 255;

volatile int ledBrightness = 0;   // ultimo valor PWM aplicado (0-255)

// ── Funciones expuestas a Python ───────────────────────────

/** Valor crudo del ADC (0-4095). Util para depurar. */
int get_pot_raw() {
  return analogRead(POT_PIN);
}

/** Valor del potenciometro en la escala del mod (0-255). */
int get_pot_value() {
  return map(analogRead(POT_PIN), 0, ADC_MAX, 0, PWM_MAX);
}

/** Aplica al LED el PWM recibido del mod. */
void set_led_pwm(int pwmValue) {
  ledBrightness = constrain(pwmValue, 0, PWM_MAX);
  analogWrite(LED_PIN, ledBrightness);
}

/** Brillo actual del LED. */
int get_led_brightness() {
  return ledBrightness;
}

// ── Setup ──────────────────────────────────────────────────
void setup() {
  pinMode(LED_PIN, OUTPUT);
  analogWrite(LED_PIN, 0);

  analogReadResolution(ADC_BITS);   // sin esto ADC_MAX no seria 4095

  Bridge.begin();
  Bridge.provide("get_pot_raw",        get_pot_raw);
  Bridge.provide("get_pot_value",      get_pot_value);
  Bridge.provide("set_led_pwm",        set_led_pwm);
  Bridge.provide("get_led_brightness", get_led_brightness);
}

// ── Loop ───────────────────────────────────────────────────
void loop() {
  Bridge.update();   // toda la logica Wi-Fi vive en Python
  delay(10);
}
```

::: tip El detalle del ADC
`analogReadResolution(12)` es obligatorio. Sin esa línea el core devuelve 10 bits y el mapeo desde 4095 hace que el potenciómetro alcance el máximo a un cuarto de su recorrido.
:::

### Script (lado MPU, Python)

```python
"""
SerialCraft Wi-Fi Bridge — Arduino Uno Q (lado MPU / Python)
============================================================
Mod SerialCraft v0.4.3 (Minecraft Fabric 1.21.11)

Corre en el Qualcomm QRB2210 (Linux) del Arduino Uno Q y se conecta como
CLIENTE TCP al servidor Wi-Fi que levanta el mod desde la Laptop.

Flujo:
  1. Conecta y envia el token de emparejamiento (primera linea obligatoria).
  2. Espera "OK" del mod. Si llega "ERR TOKEN", el token es incorrecto.
  3. Lee el potenciometro via Bridge y envia  "pot_val:<0-255>\\n".
  4. Recibe  "led_verde:<0-255>\\n"  y aplica el PWM al LED.

Escala: desde v0.4.3 el cable usa 0-255 en AMBOS sentidos, tanto si el
Bloque IO esta en modo Analogico como en Digital (digital = 0 o 255).
"""

import socket
import threading
import time

from arduino.app_utils import Bridge

# ═══════════════════════════════════════════════════════════════
#  CONFIGURACION  <- EDITA ESTO ANTES DE EJECUTAR
# ═══════════════════════════════════════════════════════════════
MINECRAFT_IP   = "192.168.1.50"   # IP que muestra la Laptop en el juego
MINECRAFT_PORT = 25585            # puerto por defecto del mod
PAIRING_TOKEN  = "XXXXXX"         # token que muestra la Laptop

BLOCK_ID_POT = "pot_val"          # Bloque IO en modo INPUT
BLOCK_ID_LED = "led_verde"        # Bloque IO en modo OUTPUT

POT_POLL_INTERVAL = 0.05          # 20 lecturas/s (limite del mod: 40 msg/s)
POT_HYSTERESIS    = 2             # ignora el ruido del ADC
RECONNECT_DELAY   = 3.0
MAX_LINE_LENGTH   = 256           # el mod corta la sesion si se excede
RX_BUFFER_LIMIT   = 4096
# ═══════════════════════════════════════════════════════════════

_sock: socket.socket | None = None
_sock_lock = threading.Lock()
_stop = threading.Event()
_last_pot_value = -1


# ── Envio ──────────────────────────────────────────────────────
def send_to_mod(message: str) -> bool:
    """Envia una linea al mod. Devuelve False si el enlace ya no sirve."""
    with _sock_lock:
        sock = _sock
        if sock is None:
            return False
        try:
            sock.sendall((message.strip() + "\n").encode("utf-8"))
            return True
        except OSError as exc:
            print(f"[WiFi] Error enviando: {exc}")
            return False


# ── Recepcion ──────────────────────────────────────────────────
def receive_loop(connection: socket.socket) -> None:
    buf = ""
    while not _stop.is_set():
        try:
            data = connection.recv(1024)
        except OSError as exc:
            print(f"[WiFi] Error recibiendo: {exc}")
            break

        if not data:
            print("[WiFi] El mod cerro la conexion.")
            break

        buf += data.decode("utf-8", errors="replace")

        # Sin '\n' a la vista, el buffer crece sin limite: cortarlo.
        if len(buf) > RX_BUFFER_LIMIT:
            print("[WiFi] Buffer sin terminador. Descartando.")
            buf = ""
            continue

        while "\n" in buf:
            line, buf = buf.split("\n", 1)
            line = line.strip()
            if line and len(line) <= MAX_LINE_LENGTH:
                process_mod_message(line)


def process_mod_message(line: str) -> None:
    """Interpreta  BLOCK_ID:VALOR  y aplica la accion en el MCU."""
    block_id, sep, value_str = line.partition(":")
    if not sep:
        print(f"[Mod] Mensaje sin separador: {line!r}")
        return

    block_id = block_id.strip()
    value_str = value_str.strip()

    if block_id != BLOCK_ID_LED:
        print(f"[Mod] Bloque desconocido: {block_id!r}")
        return

    try:
        pwm = int(value_str)
    except ValueError:
        print(f"[Mod] Valor no numerico para el LED: {value_str!r}")
        return

    pwm = max(0, min(255, pwm))
    Bridge.call("set_led_pwm", pwm)
    print(f"[Bridge] LED -> PWM={pwm}")


# ── Potenciometro ──────────────────────────────────────────────
def potentiometer_loop() -> None:
    global _last_pot_value

    while not _stop.is_set():
        try:
            pot_level = int(Bridge.call("get_pot_value"))
        except Exception as exc:                      # noqa: BLE001
            print(f"[Bridge] Error leyendo el potenciometro: {exc}")
            time.sleep(POT_POLL_INTERVAL)
            continue

        # Enviar solo cambios reales: sin esto el ruido del ADC agota el
        # limitador de red del mod (40 paquetes/s sostenidos).
        if abs(pot_level - _last_pot_value) >= POT_HYSTERESIS:
            if send_to_mod(f"{BLOCK_ID_POT}:{pot_level}"):
                print(f"[Bridge->Mod] {BLOCK_ID_POT}:{pot_level}")
                _last_pot_value = pot_level
            else:
                _stop.set()                            # forzar reconexion
                return

        time.sleep(POT_POLL_INTERVAL)


# ── Sesion ─────────────────────────────────────────────────────
def handshake(connection: socket.socket) -> bool:
    """Envia el token y espera el 'OK' del mod."""
    connection.sendall((PAIRING_TOKEN + "\n").encode("utf-8"))

    connection.settimeout(5)
    try:
        reply = connection.recv(64).decode("utf-8", errors="replace").strip()
    except OSError:
        print("[WiFi] El mod no respondio al handshake.")
        return False
    finally:
        connection.settimeout(None)

    if reply.splitlines()[:1] != ["OK"]:
        print(f"[WiFi] Handshake rechazado ({reply!r}). Revisa el token.")
        return False
    return True


def connect_and_run() -> None:
    global _sock, _last_pot_value

    print(f"[WiFi] Conectando a {MINECRAFT_IP}:{MINECRAFT_PORT} ...")
    connection = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    connection.settimeout(10)

    try:
        connection.connect((MINECRAFT_IP, MINECRAFT_PORT))
        connection.settimeout(None)

        if not handshake(connection):
            return

        print("[WiFi] Enlazado con SerialCraft.")
        _stop.clear()
        _last_pot_value = -1

        with _sock_lock:
            _sock = connection

        pot_thread = threading.Thread(
            target=potentiometer_loop, daemon=True, name="SerialCraft-POT"
        )
        pot_thread.start()

        receive_loop(connection)          # bloquea hasta la desconexion

    except OSError as exc:
        print(f"[WiFi] No se pudo conectar: {exc}")

    finally:
        _stop.set()
        with _sock_lock:
            _sock = None
        try:
            connection.close()
        except OSError:
            pass
        # No dejar el actuador encendido sin enlace.
        try:
            Bridge.call("set_led_pwm", 0)
        except Exception:                              # noqa: BLE001
            pass
        print("[WiFi] Socket cerrado.")


def main() -> None:
    print("=" * 55)
    print("  SerialCraft Wi-Fi Bridge — Arduino Uno Q")
    print(f"  Mod en:      {MINECRAFT_IP}:{MINECRAFT_PORT}")
    print(f"  Bloque pot:  {BLOCK_ID_POT}")
    print(f"  Bloque LED:  {BLOCK_ID_LED}")
    print("=" * 55)

    try:
        Bridge.call("set_led_pwm", 0)
    except Exception:                                  # noqa: BLE001
        pass

    while True:
        connect_and_run()
        print(f"[WiFi] Reintentando en {RECONNECT_DELAY}s ...\n")
        time.sleep(RECONNECT_DELAY)


if __name__ == "__main__":
    main()
```
---

## 5. Rutina de prueba

Haz estos cinco pasos en orden. Cada uno aísla una capa distinta, así sabes exactamente dónde está el fallo.

| # | Prueba | Resultado esperado | Si falla |
| :---: | :--- | :--- | :--- |
| 1 | Sin Minecraft, monitor serie abierto: gira el potenciómetro | Aparecen líneas `pot_val:<n>` | Cableado del potenciómetro o pin incorrecto |
| 2 | Escribe a mano `led_verde:255` en el monitor serie | El LED enciende al máximo | Resistencia, polaridad del LED o pin sin PWM |
| 3 | Conecta desde el juego | La Laptop marca el enlace en verde | Baudios distintos (USB) o token incorrecto (Wi-Fi) |
| 4 | Gira el potenciómetro con el juego abierto | El bloque `pot_val` genera redstone | `Target Data` mal escrito o el bloque no está en INPUT |
| 5 | Alimenta `led_verde` con una palanca | El LED enciende | El bloque no está en OUTPUT, o está desactivado desde la Laptop |

### Atajo: probar el LED sin redstone

El paso 5 se puede hacer sin palanca: abre **Visualizar**, deja `led_verde` en la clave del generador y pulsa **Generar**. La rampa debe verse como un fundido de brillo en el LED, y la pista naranja `led_verde` de la línea de tiempo muestra exactamente lo que recibe la placa. Si el LED responde aquí pero no con redstone, el fallo está en el bloque (modo, `Target Data` o lados) y no en la placa. Detalles en el [banco de pruebas](/guide#banco-de-pruebas-pestana-visualizar).

### Prueba del modo digital

Cambia el bloque `led_verde` a **Digital** y vuelve a alimentarlo con la palanca. El LED debe encender **al máximo**, no tenuemente. Esa es la corrección de la v0.4.3: en modo digital el mod envía `255`, no `1`.

Si el LED enciende apenas perceptible, estás usando un sketch de la 0.3.x que compara `valor == 1`.

### Prueba del límite de ritmo

Quita temporalmente la histéresis del sketch y deja que envíe en cada vuelta de `loop()`. Verás que el juego deja de responder a algunos cambios: el limitador está descartando el exceso. Es el comportamiento correcto. Vuelve a poner la histéresis.

La vista **Sensor** de la pestaña Visualizar te dice cuántos **mensajes por segundo** envía tu sketch y qué **zona muerta** le conviene según el ruido real de tu potenciómetro.

---
