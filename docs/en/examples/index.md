# Examples and testing (v0.4.3)

Three equivalent builds of the **same circuit** on different platforms: a potentiometer that drives a redstone level inside Minecraft, and a physical LED whose brightness is decided by in-game redstone.

The circuit is identical in all three cases; only the transport changes.

| Platform | Transport | Difficulty |
| :--- | :--- | :--- |
| [Arduino Uno R3](#_2-arduino-uno-r3-usb) | USB Serial | Simplest, start here |
| [ESP32](#_3-esp32-wi-fi) | Wi-Fi (TCP) | Cable-free, needs a token |
| [Arduino Uno Q](#_4-arduino-uno-q-bridge-python) | Wi-Fi via MPU + Python | Most involved |

---

## 1. Shared setup in Minecraft

All three examples need exactly the same two IO Blocks:

| Block | Target Data | Mode | Signal type | What it does |
| :--- | :--- | :--- | :--- | :--- |
| Input | `pot_val` | **INPUT** | **Analog** | The potentiometer produces redstone 0-15 |
| Output | `led_verde` | **OUTPUT** | **Analog** | In-game redstone sets the LED brightness |

::: tip The block ID is not translated
The sketches use `led_verde` (Spanish for "green LED") as the literal key. `Target Data` is a raw string, not a localised name — if you rename it in the game, rename it in the sketch too.
:::

Suggested in-game build:

1. Place the `pot_val` block and put a redstone lamp beside it, or a dust line into a comparator to read the level.
2. Place the `led_verde` block with a lever or redstone dust feeding one of its configured input sides.
3. Configure the input sides in each block's menu.

::: tip Testing without hardware
Before wiring anything, enable the debug HUD (key bound under *Options → Controls → SerialCraft*). It shows outgoing and incoming messages, which separates a game-side problem from a wiring problem.
:::

---

## 2. Arduino Uno R3 (USB)

### Wiring

| Component | Board pin | Notes |
| :--- | :--- | :--- |
| Potentiometer, left leg | `5V` | |
| Potentiometer, center leg | `A0` | Wiper: this is the signal |
| Potentiometer, right leg | `GND` | |
| LED anode (long leg) | `D9` through a 220 Ω resistor | D9 is PWM-capable |
| LED cathode (short leg) | `GND` | |

### In-game setup

Connector Block → Connection tab → select the Uno's port → **115200 baud**.

::: warning
The sketch uses `Serial.begin(115200)`. Change one value and you must change the other. Mismatched baud rates raise no error — nothing simply arrives.
:::

### Sketch

```cpp
/*
 * SerialCraft USB Bridge — Arduino Uno R3
 * ============================================================
 * Mod SerialCraft v0.4.3 (Minecraft Fabric 1.21.11)
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
 * Protocolo (escala unificada 0-255 en AMBOS sentidos desde v0.4.3):
 *   - Enviar al mod:   "pot_val:<0-255>\n"
 *   - Recibir del mod: "led_verde:<0-255>\n"
 *
 * IMPORTANTE: los baudios de este sketch y los del Bloque Conector en el
 * juego deben coincidir. Aqui usamos 115200, que es el valor por defecto
 * del mod. Si los cambias en uno, cambialos en el otro.
 */

// ── Pines ──────────────────────────────────────────────────
const int POT_PIN = A0;
const int LED_PIN = 9;

// ── Identificadores de bloque (deben coincidir con Target Data) ──
const char* BLOCK_ID_POT = "pot_val";
const char* BLOCK_ID_LED = "led_verde";

// ── Protocolo ──────────────────────────────────────────────
const long  BAUD_RATE     = 115200;  // debe coincidir con el Bloque Conector
const int   ADC_MAX       = 1023;    // ADC de 10 bits del ATmega328P
const int   PWM_MAX       = 255;
const int   POT_HYSTERESIS = 2;      // ignora ruido electrico del pot
const int   BUFFER_LIMIT  = 48;      // el mod corta lineas de mas de 256
const unsigned long LOOP_DELAY_MS = 30;  // ~33 msg/s < limite de 40/s del mod

// ── Estado ─────────────────────────────────────────────────
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
### What to expect

* Turning the potentiometer moves the `pot_val` block's redstone level between 0 and 15.
* Feeding redstone into `led_verde` changes the LED brightness proportionally.
* The Laptop console shows lines in both directions.

---

## 3. ESP32 (Wi-Fi)

::: danger 3.3 V
The ESP32 is **not 5 V tolerant**. Power the potentiometer from `3V3`.
:::

### Wiring

| Component | Board pin | Notes |
| :--- | :--- | :--- |
| Potentiometer, left leg | `3V3` | Never 5 V |
| Potentiometer, center leg | `GPIO34` | ADC1, input only |
| Potentiometer, right leg | `GND` | |
| LED anode | `GPIO16` through a 220 Ω resistor | LEDC channel |
| LED cathode | `GND` | |

::: tip Why GPIO34
ADC2 is unusable while Wi-Fi is active on the ESP32. Always use ADC1 pins (32-39) in connected projects.
:::

### In-game setup

Laptop → Home → **Start Wi-Fi server**. Copy the IP, port and token shown in the UI into the sketch.

### Sketch

```cpp
/*
 * SerialCraft Wi-Fi Bridge — ESP32
 * ============================================================
 * Mod SerialCraft v0.4.3 (Minecraft Fabric 1.21.11)
 *
 * DIRECCION DE LA CONEXION (cambio importante en v0.4.3):
 *   El MOD es el servidor TCP y la PLACA es el cliente.
 *   Antes se documentaba al reves (ESP32 como servidor en el 8080).
 *   Ahora: abre la Laptop en el juego -> pestana Inicio -> "Iniciar
 *   servidor Wi-Fi". La UI te muestra la IP, el puerto y un token.
 *   Copia esos tres valores aqui abajo.
 *
 * Handshake obligatorio: la primera linea que envia la placa debe ser el
 * token. El mod responde "OK" o cierra con "ERR TOKEN".
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

// ═══════════════════════════════════════════════════════════
//  CONFIGURACION  <- EDITA ESTO
// ═══════════════════════════════════════════════════════════
const char* WIFI_SSID     = "TU_WIFI";
const char* WIFI_PASSWORD = "TU_PASSWORD";

const char* MINECRAFT_IP   = "192.168.1.50";  // IP que muestra la Laptop
const uint16_t MINECRAFT_PORT = 25585;        // puerto por defecto del mod
const char* PAIRING_TOKEN  = "XXXXXX";        // token que muestra la Laptop
// ═══════════════════════════════════════════════════════════

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

// ── Conexion + handshake ───────────────────────────────────
void connectToMod() {
  Serial.printf("Conectando a %s:%u ...\n", MINECRAFT_IP, MINECRAFT_PORT);

  if (!client.connect(MINECRAFT_IP, MINECRAFT_PORT, 5000)) {
    Serial.println("Sin respuesta. ¿Iniciaste el servidor Wi-Fi en la Laptop?");
    delay(RECONNECT_DELAY_MS);
    return;
  }

  // Primera linea: token de emparejamiento.
  client.print(String(PAIRING_TOKEN) + "\n");

  // Esperar la respuesta del handshake antes de enviar datos.
  unsigned long deadline = millis() + 3000;
  while (client.connected() && !client.available() && millis() < deadline) delay(10);

  String reply = client.readStringUntil('\n');
  reply.trim();

  if (reply != "OK") {
    Serial.println("Handshake rechazado: " + reply + " (revisa el token)");
    client.stop();
    delay(RECONNECT_DELAY_MS);
    return;
  }

  Serial.println("Enlazado con SerialCraft.");
  rxBuffer = "";
  lastPotValue = -1;          // forzar el primer envio
}

// ── Minecraft -> LED ───────────────────────────────────────
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

// ── Potenciometro -> Minecraft ─────────────────────────────
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
### Verification

Open the serial monitor at 115200: you will see the board's assigned IP, the connection attempt and the handshake result. `Handshake rechazado` means a wrong token, or another board is already connected.

---

## 4. Arduino Uno Q (Bridge + Python)

The Uno Q carries two processors: an STM32U585 microcontroller driving the pins, and a Qualcomm MPU running Linux handling the network. The sketch exposes functions; Python calls them and talks to Minecraft.

```text
MCU (STM32U585) <-> Bridge <-> MPU (Python) <-> Wi-Fi TCP <-> SerialCraft mod
```

::: danger 3.3 V
The Uno Q's inputs run at 3.3 V. Power the potentiometer from `3V3`.
:::

### Wiring

| Component | Board pin | Notes |
| :--- | :--- | :--- |
| Potentiometer, left leg | `3V3` | |
| Potentiometer, center leg | `A0` | 12-bit ADC |
| Potentiometer, right leg | `GND` | |
| LED anode | `D9` through a 220 Ω resistor | PWM |
| LED cathode | `GND` | |

### Sketch (MCU side)

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

::: tip The ADC detail
`analogReadResolution(12)` is mandatory. Without it the core returns 10 bits, and mapping from 4095 makes the potentiometer hit maximum a quarter of the way through its travel.
:::

### Script (MPU side, Python)

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

## 5. Test routine

Run these five steps in order. Each isolates a different layer, so you know exactly where a failure sits.

| # | Test | Expected result | If it fails |
| :---: | :--- | :--- | :--- |
| 1 | No Minecraft, serial monitor open: turn the potentiometer | `pot_val:<n>` lines appear | Potentiometer wiring or wrong pin |
| 2 | Type `led_verde:255` by hand in the serial monitor | LED lights fully | Resistor, LED polarity, or a non-PWM pin |
| 3 | Connect from the game | The Laptop shows a green link | Baud mismatch (USB) or wrong token (Wi-Fi) |
| 4 | Turn the potentiometer with the game open | The `pot_val` block emits redstone | `Target Data` typo, or the block is not in INPUT |
| 5 | Power `led_verde` with a lever | The LED lights | The block is not in OUTPUT, or is disabled from the Laptop |

### Testing digital mode

Switch the `led_verde` block to **Digital** and power it again with the lever. The LED must light **fully**, not faintly. That is the v0.4.3 fix: digital mode sends `255`, not `1`.

If the LED is barely visible, you are running a 0.3.x sketch that compares `value == 1`.

### Testing the rate limit

Temporarily remove the hysteresis so the sketch transmits on every `loop()` pass. The game will start ignoring some changes: the limiter is dropping the excess. That is correct behaviour. Put the hysteresis back.

---

## 6. Wiring diagrams

Descriptions for generating each diagram live in [Wiring diagram prompts](/en/examples/wiring-prompts).
