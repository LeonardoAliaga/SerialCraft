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
