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
