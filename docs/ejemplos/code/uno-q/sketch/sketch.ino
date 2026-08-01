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
