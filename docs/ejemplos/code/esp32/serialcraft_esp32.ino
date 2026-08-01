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
