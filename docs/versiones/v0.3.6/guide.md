# Guía de Usuario - SerialCraft

::: warning Versión Beta 0.3.6
Esta guía corresponde a la versión **Beta 0.3.6** del mod. Algunas características pueden cambiar en futuras actualizaciones.
:::

## 1. Introducción y Filosofía
**SerialCraft** es un proyecto experimental de **aprendizaje** diseñado para tender un puente entre el mundo virtual de Minecraft y el mundo físico de la electrónica.

Nuestro objetivo es crear un mod **amigable tanto para novatos como para expertos**. A diferencia de otros mods técnicos que requieren configuraciones complejas fuera del juego, SerialCraft está diseñado para integrarse en tu experiencia **Survival**:
* Los bloques se craftean con recursos del juego.
* La conexión es plug-and-play.
* La lógica de Redstone se respeta y se extiende al mundo real.

---

## 🌐 Idiomas Disponibles
SerialCraft detecta automáticamente el idioma configurado en tu cliente de Minecraft. No necesitas configurar nada extra.

Actualmente soportamos traducciones nativas para:
* 🇺🇸 **English** (US)
* 🇪🇸 **Español** (España)
* 🇦🇷 **Español** (Argentina)
* 🇲🇽 **Español** (México)

*Si tu idioma no aparece en la lista, el mod se mostrará en Inglés por defecto.*

---

## 2. Hardware Compatible
Aunque el mod suele referirse a "Arduino", el sistema está diseñado para ser agnóstico al hardware. Puedes utilizar cualquier placa capaz de comunicarse por **Puerto Serie (USB)**:

* **Arduino:** Uno, Nano, Mega, Leonardo.
* **ESP:** ESP32, ESP8266 (NodeMCU).
* **Otros:** Raspberry Pi Pico, STM32, etc.

::: tip Nota sobre Drivers
Asegúrate de tener instalados los drivers de tu placa (como CH340 o CP2102) en tu sistema operativo para que Minecraft pueda detectar el puerto COM.
:::

---

## 3. Bloques y Funcionalidad

### El Bloque Conector (Laptop)
Es el cerebro de la operación. Al darle click derecho, verás la interfaz de conexión.

* **Puerto:** Selecciona el puerto COM de tu dispositivo.
* **Baud Rate (Velocidad):** Es crucial que este número coincida con el `Serial.begin(XXXX)` de tu código.
    * Soportamos: 9600, 14400, 19200, 38400, 57600, 115200.
    * *Recomendado:* **9600** para Arduino Uno/Nano, **115200** para ESP32.
* **Velocidad de Lectura (Vel/Speed):** Controla qué tan rápido procesa el juego los mensajes entrantes.
    * **Fast (Rápido):** Procesa todo instantáneamente (Por defecto). *Ideal si tu código Arduino es eficiente.*
    * **Norm (Normal):** Limita a 1 mensaje cada **50ms**.
    * **Low (Lento):** Limita a 1 mensaje cada **200ms**. *Úsalo si tu Arduino envía demasiados datos y causa lag.*

### El Bloque IO (Arduino IO)
Este bloque es el punto de enlace físico. Sus pequeños conectores laterales (pines) se pueden configurar individualmente para interactuar con la Redstone del mundo.

**Configuración de Pines (Lados):**
Interactúa con los pequeños botones en los costados del bloque:

* **🖱️ Click Derecho (Normal) ➔ Pin IN (Entrada de Redstone)**
    * **Color:** Verde 🟢
    * **Función:** El bloque **LEE** la energía de Redstone que le llega por este lado.
    * **Uso:** Sirve para enviar el estado de la Redstone al Arduino o para establecer condiciones lógicas (ver abajo).

* **⬆️ Shift + Click Derecho ➔ Pin OUT (Salida de Redstone)**
    * **Color:** Rojo 🔴
    * **Función:** El bloque **EMITE** energía de Redstone por este lado.
    * **Uso:** Por aquí saldrá la señal cuando el Arduino envíe una orden al juego.

---

### Compuertas Lógicas (Modo Seguridad)
El Bloque IO cuenta con un sistema de lógica interna que actúa como un "filtro de seguridad". Esto es útil si quieres que las órdenes de tu Arduino solo se ejecuten si se cumplen ciertas condiciones físicas en el juego (ej: una palanca de seguridad activada).

**¿Cómo funciona?**
Esta lógica solo se aplica a los **Pines IN (Verdes)**.
* **Sin Pines IN:** Si no configuras ningún pin como entrada, el bloque obedece al Arduino directamente (funciona normalmente).
* **Con Pines IN:** El bloque solo procesará la orden del Arduino si los pines de entrada cumplen la condición lógica configurada.

**Modos Lógicos:**
1.  **OR (O):** Se activa si **AL MENOS UNO** de los pines IN recibe energía.
2.  **AND (Y):** Se activa solo si **TODOS** los pines IN reciben energía simultáneamente.
3.  **XOR (O Exclusivo):** Se activa si una cantidad **IMPAR** de pines IN recibe energía.

---

## 4. Protocolo de Comunicación
Para que el mod "entienda" a tu placa (y viceversa), debes seguir estas reglas estrictas de formato.

### Criterios de Lectura y Escritura
1.  **Formato:** Siempre usamos pares `CLAVE:VALOR`.
2.  **Terminador de Línea (`\n`):** SerialCraft lee los datos línea por línea.
    * En Arduino, **SIEMPRE** debes usar `Serial.println()` al final de enviar un dato. Si usas solo `Serial.print()`, el mod se quedará esperando infinitamente el final del mensaje.
3.  **Sincronización:** Evita saturar el puerto serie. Enviar datos cada 50ms (`delay(50)`) es suficiente para una respuesta fluida sin causar lag.

---

## 5. Tu Primer Circuito
En este tutorial construiremos un sistema mixto: usaremos sensores físicos para controlar la Redstone en Minecraft y señales de Minecraft para encender un LED real.

### 1. El Circuito Físico
Necesitarás los siguientes componentes conectados a tu Arduino:
* **Potenciómetro:** Pin `A1`.
* **Sensor de Sonido (Micrófono):** Pin `A2`.
* **LED (o el integrado):** Pin `13`.

### 2. Configuración en Minecraft
Vamos a configurar 3 bloques: dos receptores (para el Potenciómetro y el Micro) y un emisor (para el LED).

#### A. Bloque 1: Potenciómetro (Arduino ➔ Minecraft)
Queremos que este bloque reciba un valor variable (0-15) y emita esa intensidad de Redstone.

1.  **Configuración Física (Pines):**
    * Apunta a un conector lateral y haz **Shift + Click Derecho**.
    * El pin cambiará a **Rojo (Pin OUT)**. (La energía *sale* hacia el mundo).
2.  **Configuración Lógica (UI):**
    * Haz **Click Derecho** en la base para abrir la interfaz.
    * **Modo:** ENTRADA (La placa recibe datos).
    * **Señal:** **ANÁLOGO** (Permite intensidad variable 0-15).
    * **Target Data:** Escribe `POT_1`.
    * *Formato esperado:* `POT_1:[0-15]`

#### B. Bloque 2: Micrófono (Arduino ➔ Minecraft)
Igual que el anterior, recibe datos variables.

1.  **Pines:** Haz **Shift + Click Derecho** en un conector para ponerlo en **Modo OUT** (Rojo).
2.  **UI:**
    * **Modo:** ENTRADA.
    * **Señal:** **ANÁLOGO**.
    * **Target Data:** `MIC_1`.
    * *Formato esperado:* `MIC_1:[0-15]`

#### C. Bloque 3: LED (Minecraft ➔ Arduino)
Queremos que este bloque lea la Redstone del juego (Encendido/Apagado) y mande la orden.

1.  **Configuración Física (Pines):**
    * Apunta a un conector lateral y haz **Click Derecho** (normal).
    * El pin cambiará a **Verde (Pin IN)**. (La placa *lee* la energía entrante).
2.  **Configuración Lógica (UI):**
    * Abre la interfaz.
    * **Modo:** SALIDA (La placa envía datos).
    * **Señal:** **DIGITAL** (Solo nos importa 0 o 1).
    * **Target Data:** Escribe `LED_1`.
    * *Formato enviado:* `LED_1:[0-1]` (0 = OFF, 1 = ON).

---

### 3. El Código (Sketch)
Copia y carga este código en tu Arduino. Asegúrate de que el **Baud Rate** del código (`9600`) coincida con el que seleccionaste en la Laptop del juego.

```cpp
// --- CONFIGURACIÓN DE PINES ---
const int PIN_POT = A1;      // Potenciómetro
const int PIN_MIC = A2;      // Sensor de Sonido
const int PIN_LED = 13;      // LED

// --- IDs (Deben coincidir con el "Target Data" de la UI) ---
String ID_POT = "POT_1";
String ID_MIC = "MIC_1";
String ID_LED = "LED_1";

// --- VARIABLES ---
unsigned long ultimoEnvio = 0;
const int INTERVALO = 50;
int lastValPot = -1;
int lastValMic = -1;

void setup() {
  Serial.begin(9600); // Misma velocidad que en el bloque Laptop
  pinMode(PIN_POT, INPUT);
  pinMode(PIN_MIC, INPUT);
  pinMode(PIN_LED, OUTPUT);
}

void loop() {
  // 1. --- ENVIAR DATOS (ARDUINO -> MINECRAFT) ---
  if (millis() - ultimoEnvio > INTERVALO) {
    
    // Potenciómetro (Analógico -> Redstone 0-15)
    int rawPot = analogRead(PIN_POT);
    int mcPot = map(rawPot, 0, 1023, 0, 15); 
    
    if (mcPot != lastValPot) {
      Serial.print(ID_POT);
      Serial.print(":");
      Serial.println(mcPot); // Importante: println para el \n
      lastValPot = mcPot;
    }

    // Micrófono (Analógico -> Redstone 0-15)
    int rawMic = analogRead(PIN_MIC);
    int intensidad = abs(rawMic - 512) * 10;
    int mcMic = constrain(map(intensidad, 0, 300, 0, 15), 0, 15);
    
    if (mcMic != lastValMic) {
      Serial.print(ID_MIC);
      Serial.print(":");
      Serial.println(mcMic); // Formato: MIC_1:8
      lastValMic = mcMic;
    }

    ultimoEnvio = millis();
  }

  // 2. --- RECIBIR DATOS (MINECRAFT -> ARDUINO) ---
  if (Serial.available() > 0) {
    String mensaje = Serial.readStringUntil('\n');
    mensaje.trim();

    // LED (Digital 0/1)
    if (mensaje.startsWith(ID_LED + ":")) {
      int separador = mensaje.indexOf(':');
      int valor = mensaje.substring(separador + 1).toInt();
      // Si llega "LED_1:1" enciende, si llega "LED_1:0" apaga
      digitalWrite(PIN_LED, valor > 0 ? HIGH : LOW);
    }
  }
}
```

---

## 6. Solución de Problemas

### Herramienta de Debug (HUD F7)
Si algo no funciona, SerialCraft incluye una herramienta de diagnóstico interna.
Presiona la tecla **F7** dentro del juego para desplegar el **Serial Debug HUD**.

* **TX (Verde):** Muestra los datos que Minecraft está *enviando* al puerto serie.
* **RX (Azul):** Muestra los datos que están *llegando* desde tu Arduino.

**Diagnóstico Común:**
1.  **¿RX está vacío?**
    * Verifica que tu código de Arduino use `Serial.println()` (con salto de línea) y no solo `Serial.print()`.
    * Comprueba que el **Baud Rate** en el bloque Conector sea el mismo que en tu `Serial.begin()`.
2.  **¿Error "Port Busy"?**
    * Asegúrate de no tener el monitor serie de Arduino IDE abierto al mismo tiempo. El puerto COM solo puede ser usado por un programa a la vez.