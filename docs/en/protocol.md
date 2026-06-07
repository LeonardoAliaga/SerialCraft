# Bidirectional Protocol and Hardware (v0.4.3)

::: warning Beta Version 0.4.3
This technical documentation applies to the current version. The protocol supports USB Serial and Wireless (TCP) communication. It could evolve in future updates to include JSON or binary support.
:::

## 1. Communication Specifications

SerialCraft uses a synchronous plain text (ASCII) protocol. Communication is based on sending data packets strictly terminated by a newline character, applicable for both USB and Wi-Fi Sockets.

| Parameter | Value |
| :--- | :--- |
| **Supported Baud Rates (Serial)** | 9600, 14400, 19200, 38400, 57600, **115200 (Recommended)** |
| **Supported Port (Wi-Fi TCP)** | 8080 (Default, configurable in the code) |
| **Data Format** | `KEY:VALUE` |
| **Terminator** | `\n` (Line break / Newline) |
| **Encoding** | UTF-8 (ASCII compatible) |

::: danger Important
The mod ignores any message that does not end in `\n`. In the Arduino IDE, **ALWAYS** use `Serial.println()` or `client.println()`, and not just the `print()` function.
:::

---

## 2. Input (Hardware ➔ Minecraft)

For Minecraft to react to a physical event, your board must send a text string with the destination block ID and the desired value.

### Syntax
```text
<BLOCK_ID>:<INTEGER_VALUE>\n
```

* **`<BLOCK_ID>`**: The text string defined in the "Target Data" field within the IO Block interface. (Ex: `btn_1`, `light_sensor`).
* **`<INTEGER_VALUE>`**:
    * **0**: Turns off the Redstone signal.
    * **1 - 15**: Turns on the Redstone signal with that power level.
    * **> 15**: Will be interpreted as maximum signal (15).

```cpp
// Arduino Example: Send maximum signal to the "alarm" block
Serial.println("alarm:15");
```

---

## 3. Output (Minecraft ➔ Hardware)

When an IO Block in **Output Mode** detects a change in the Redstone signal it receives, it automatically sends a message.

### Syntax
```text
<BLOCK_ID>:<VALUE>\n
```

### Behavior according to Signal Type
The value sent depends on how you configured the block (Digital or Analog/PWM).

#### A. Digital Signal (Simple)
If the block is configured in **Digital** mode:
* **Receives Redstone > 0**: Sends `1`.
* **Receives Redstone = 0**: Sends `0`.

#### B. Analog Signal (PWM)
If the block is configured in **Analog** mode, the mod performs an internal mathematical conversion to translate the Redstone (0-15) to PWM (0-255).

**Internal formula:**
$$PWM = \frac{Redstone \times 255}{15}$$

| Redstone Level | Sent Value (PWM) | Description |
| :---: | :---: | :--- |
| 0 | **0** | Off |
| 1 | **17** | Minimum |
| 7 | **119** | Medium (~50%) |
| 15 | **255** | Maximum (100%) |

---

## 4. Bidirectional Implementation Examples

Here are the ready-to-use schematics to integrate simultaneous reading and writing, adapted to the platform chosen in the graphical interface.

### A. USB Connection (For Arduino Uno Q)
This code waits for instructions from the game to turn on an analog light (`ceiling_light:255`), and in parallel reads a physical button sending its state (`btn_1:15` or `btn_1:0`).

```cpp
const int pinLed = 13;
const int pinBtn = 2;
int lastBtnState = LOW;

void setup() {
  Serial.begin(115200); // Key speed for SerialCraft
  pinMode(pinLed, OUTPUT);
  pinMode(pinBtn, INPUT_PULLUP);
}

void loop() {
  // 1. READ FROM MINECRAFT (Game Output)
  if (Serial.available() > 0) {
    String message = Serial.readStringUntil('\n');
    message.trim();
    
    if (message.startsWith("ceiling_light:")) {
      int value = message.substring(message.indexOf(':') + 1).toInt();
      analogWrite(pinLed, value); // Works for Digital (0/1) and PWM (0-255)
    }
  }

  // 2. SEND TO MINECRAFT (Game Input)
  int currentBtn = digitalRead(pinBtn);
  if (currentBtn != lastBtnState) {
    if (currentBtn == LOW) { // Button pressed (inverse logic due to Pullup)
      Serial.println("btn_1:15");
    } else {
      Serial.println("btn_1:0");
    }
    lastBtnState = currentBtn;
    delay(50); // Safety debounce
  }
}
```

### B. Wireless Connection (Wi-Fi for ESP32)
Configuration as a local TCP Server. Minecraft (through the network interface) will act as the client connecting to the board's IP.

```cpp
#include <WiFi.h>

const char* ssid = "YOUR_WIFI_HERE";
const char* password = "YOUR_PASSWORD_HERE";
WiFiServer server(8080); // Default port
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
  
  Serial.print("Assigned Wi-Fi IP: ");
  Serial.println(WiFi.localIP()); // Place this IP in the Minecraft interface
  server.begin();
}

void loop() {
  if (!client.connected()) {
    client = server.available(); 
  }

  if (client.connected()) {
    // 1. READ FROM MINECRAFT (Wi-Fi RX)
    if (client.available()) {
      String message = client.readStringUntil('\n');
      message.trim();
      
      if (message.startsWith("ceiling_light:")) {
        int value = message.substring(message.indexOf(':') + 1).toInt();
        analogWrite(pinLed, value);
      }
    }

    // 2. SEND TO MINECRAFT (Wi-Fi TX)
    int currentBtn = digitalRead(pinBtn);
    if (currentBtn != lastBtnState) {
      String msg = (currentBtn == LOW) ? "btn_1:15\n" : "btn_1:0\n";
      client.print(msg); // Note: Here we use '\n' at the end of the string
      
      lastBtnState = currentBtn;
      delay(50); 
    }
  }
}
```

---

## 5. Logic Gates (Advanced)
IO Blocks have a hidden feature: **Logic Gates**. This defines when the block is activated if it receives power from multiple sides at once (North, South, East, West).

This is processed internally in the `updateLogicConditions()` method:

* **OR (Default):** Activates if *any* connected side receives power.
* **AND:** Activates only if *all* connected sides receive power simultaneously.
* **XOR:** Activates if an *odd* number of sides receive power.

*Note: Currently this configuration is only accessible through NBT editing or development versions, but the logic already exists in the engine's code.*