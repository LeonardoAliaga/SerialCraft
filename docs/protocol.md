# Protocolo Bidireccional y Hardware (v0.4.3)

::: warning Versión Beta 0.4.3
Esta documentación técnica aplica a la versión actual. El protocolo soporta comunicación por USB Serial e Inalámbrica (TCP). Podría evolucionar en futuras actualizaciones para incluir soporte JSON o binario.
:::

## 1. Especificaciones de Comunicación

SerialCraft utiliza un protocolo de texto plano (ASCII) síncrono. La comunicación se basa en el envío de paquetes de datos terminados estrictamente por un carácter de nueva línea, aplicable tanto para USB como para Sockets Wi-Fi.

| Parámetro | Valor |
| :--- | :--- |
| **Baud Rates Soportados (Serial)** | 9600, 14400, 19200, 38400, 57600, **115200 (Recomendado)** |
| **Puerto Soportado (Wi-Fi TCP)** | 8080 (Por defecto, configurable en el código) |
| **Formato de Datos** | `CLAVE:VALOR` |
| **Terminador** | `\n` (Salto de línea / Newline) |
| **Codificación** | UTF-8 (ASCII compatible) |

::: danger Importante
El mod ignora cualquier mensaje que no termine en `\n`. En el IDE de Arduino, **SIEMPRE** usa `Serial.println()` o `client.println()`, y no solo la función `print()`.
:::

---

## 2. Entrada (Hardware ➔ Minecraft)

Para que Minecraft reaccione a un evento físico, tu placa debe enviar una cadena de texto con el ID del bloque destino y el valor deseado.

### Sintaxis
```text
<ID_DEL_BLOQUE>:<VALOR_ENTERO>\n
```

* **`<ID_DEL_BLOQUE>`**: Es la cadena de texto definida en el campo "Target Data" dentro de la interfaz del Bloque IO. (Ej: `btn_1`, `sensor_luz`).
* **`<VALOR_ENTERO>`**:
    * **0**: Apaga la señal de Redstone.
    * **1 - 15**: Enciende la señal de Redstone con esa potencia.
    * **> 15**: Se interpretará como señal máxima (15).

```cpp
// Ejemplo Arduino: Enviar señal máxima al bloque "alarma"
Serial.println("alarma:15");
```

---

## 3. Salida (Minecraft ➔ Hardware)

Cuando un Bloque IO en **Modo Output** detecta un cambio en la señal de Redstone que recibe, envía automáticamente un mensaje.

### Sintaxis
```text
<ID_DEL_BLOQUE>:<VALOR>\n
```

### Comportamiento según Tipo de Señal
El valor enviado depende de cómo configuraste el bloque (Digital o Analógico/PWM).

#### A. Señal Digital (Simple)
Si el bloque está configurado en modo **Digital**:
* **Recibe Redstone > 0**: Envía `1`.
* **Recibe Redstone = 0**: Envía `0`.

#### B. Señal Analógica (PWM)
Si el bloque está configurado en modo **Analógico**, el mod realiza una conversión matemática interna para traducir la Redstone (0-15) a PWM (0-255).

**Fórmula interna:**
$$PWM = \frac{Redstone \times 255}{15}$$

| Nivel Redstone | Valor Enviado (PWM) | Descripción |
| :---: | :---: | :--- |
| 0 | **0** | Apagado |
| 1 | **17** | Mínimo |
| 7 | **119** | Medio (~50%) |
| 15 | **255** | Máximo (100%) |

---

## 4. Ejemplos de Implementación Bidireccional

Aquí tienes los esquemas listos para integrar lectura y escritura simultáneas, adaptados a la plataforma elegida en la interfaz gráfica.

### A. Conexión USB (Para Arduino Uno Q)
Este código espera instrucciones del juego para encender una luz analógica (`luz_techo:255`), y en paralelo lee un botón físico enviando su estado (`btn_1:15` o `btn_1:0`).

```cpp
const int pinLed = 13;
const int pinBtn = 2;
int lastBtnState = LOW;

void setup() {
  Serial.begin(115200); // Velocidad clave para SerialCraft
  pinMode(pinLed, OUTPUT);
  pinMode(pinBtn, INPUT_PULLUP);
}

void loop() {
  // 1. LEER DESDE MINECRAFT (Output del juego)
  if (Serial.available() > 0) {
    String mensaje = Serial.readStringUntil('\n');
    mensaje.trim();
    
    if (mensaje.startsWith("luz_techo:")) {
      int valor = mensaje.substring(mensaje.indexOf(':') + 1).toInt();
      analogWrite(pinLed, valor); // Funciona para Digital (0/1) y PWM (0-255)
    }
  }

  // 2. ENVIAR A MINECRAFT (Input del juego)
  int currentBtn = digitalRead(pinBtn);
  if (currentBtn != lastBtnState) {
    if (currentBtn == LOW) { // Botón presionado (lógica inversa por Pullup)
      Serial.println("btn_1:15");
    } else {
      Serial.println("btn_1:0");
    }
    lastBtnState = currentBtn;
    delay(50); // Debounce de seguridad
  }
}
```

### B. Conexión Inalámbrica (Wi-Fi para ESP32)
Configuración como Servidor TCP local. Minecraft (mediante la interfaz de red) actuará como el cliente conectándose a la IP de la placa.

```cpp
#include <WiFi.h>

const char* ssid = "TU_WIFI_AQUI";
const char* password = "TU_PASSWORD_AQUI";
WiFiServer server(8080); // Puerto por defecto
WiFiClient client;

const int pinLed = 2;
const int pinBtn = 4;
int lastBtnState = LOW;

void setup() {
  Serial.begin(115200);
  pinMode(pinLed, OUTPUT);
  pinMode(pinBtn, INPUT_PULLUP);

  WiFi.begin(ssid, password);
  while (WiFi.status() != WL_CONNECTED) {
    delay(500);
  }
  
  Serial.print("IP Wi-Fi asignada: ");
  Serial.println(WiFi.localIP()); // Coloca esta IP en la interfaz de Minecraft
  server.begin();
}

void loop() {
  if (!client.connected()) {
    client = server.available(); 
  }

  if (client.connected()) {
    // 1. LEER DESDE MINECRAFT (Wi-Fi RX)
    if (client.available()) {
      String mensaje = client.readStringUntil('\n');
      mensaje.trim();
      
      if (mensaje.startsWith("luz_techo:")) {
        int valor = mensaje.substring(mensaje.indexOf(':') + 1).toInt();
        analogWrite(pinLed, valor);
      }
    }

    // 2. ENVIAR A MINECRAFT (Wi-Fi TX)
    int currentBtn = digitalRead(pinBtn);
    if (currentBtn != lastBtnState) {
      String msg = (currentBtn == LOW) ? "btn_1:15\n" : "btn_1:0\n";
      client.print(msg); // Ojo: Aquí usamos '\n' al final del string
      
      lastBtnState = currentBtn;
      delay(50); 
    }
  }
}
```

---

## 5. Lógica de Compuertas (Avanzado)
Los Bloques IO tienen una característica oculta: **Compuertas Lógicas**. Esto define cuándo se activa el bloque si recibe energía por varios lados a la vez (Norte, Sur, Este, Oeste).

Esto se procesa internamente en el método `updateLogicConditions()`:

* **OR (Por defecto):** Se activa si *cualquier* lado conectado recibe energía.
* **AND:** Se activa solo si *todos* los lados conectados reciben energía simultáneamente.
* **XOR:** Se activa si una cantidad *impar* de lados recibe energía.

*Nota: Actualmente esta configuración solo es accesible mediante edición NBT o versiones de desarrollo, pero la lógica ya existe en el código del motor.*