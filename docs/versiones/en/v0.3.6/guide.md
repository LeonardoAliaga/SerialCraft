# User Guide - SerialCraft

::: warning Beta Version 0.3.6
This guide corresponds to version **Beta 0.3.6**. Some features may change in future updates.
:::

## 1. Introduction & Philosophy
**SerialCraft** is an experimental **learning project** designed to bridge the gap between the virtual world of Minecraft and the physical world of electronics.

Our goal is to create a mod that is **friendly for both beginners and experts**. Unlike other technical mods that require complex out-of-game configurations, SerialCraft is designed to integrate into your **Survival** experience:
* Blocks are crafted with in-game resources.
* Connection is plug-and-play.
* Redstone logic is respected and extended to the real world.

---

## 🌐 Available Languages
SerialCraft automatically detects your Minecraft client language. No extra configuration needed.

We currently support native translations for:
* 🇺🇸 **English** (US)
* 🇪🇸 **Spanish** (Spain)
* 🇦🇷 **Spanish** (Argentina)
* 🇲🇽 **Spanish** (Mexico)

*If your language is not listed, the mod will default to English.*

---

## 2. Compatible Hardware
Although the mod often refers to "Arduino", the system is hardware-agnostic. You can use any board capable of **Serial Communication (USB)**:

* **Arduino:** Uno, Nano, Mega, Leonardo.
* **ESP:** ESP32, ESP8266 (NodeMCU).
* **Others:** Raspberry Pi Pico, STM32, etc.

::: tip Driver Note
Make sure you have installed the necessary drivers for your board (like CH340 or CP2102) on your OS so Minecraft can detect the COM port.
:::

---

## 3. Blocks & Functionality

### The Connector Block (Laptop)
This is the brain of the operation. Right-click it to open the connection interface.

* **Port:** Select your device's COM port.
* **Baud Rate:** This number MUST match the `Serial.begin(XXXX)` in your code.
    * Supported: 9600, 14400, 19200, 38400, 57600, 115200.
    * *Recommended:* **9600** for Arduino Uno/Nano, **115200** for ESP32.
* **Read Speed (Vel/Speed):** Controls how fast the game processes incoming messages.
    * **Fast:** Processes everything instantly (Default). *Ideal if your Arduino code is efficient.*
    * **Norm:** Limits to 1 message every **50ms**.
    * **Low:** Limits to 1 message every **200ms**. *Use this if your Arduino sends too much data and causes lag.*

### The IO Block (Arduino IO)
This block is the physical link. Its small side connectors (pins) can be individually configured to interact with the world's Redstone.

**Pin Configuration (Sides):**
Interact with the small buttons on the sides of the block:

* **🖱️ Right Click (Normal) ➔ Pin IN (Redstone Input)**
    * **Color:** Green 🟢
    * **Function:** The block **READS** the Redstone energy coming from this side.
    * **Usage:** Used to send Redstone status to Arduino OR to establish logic conditions (see below).

* **⬆️ Shift + Right Click ➔ Pin OUT (Redstone Output)**
    * **Color:** Red 🔴
    * **Function:** The block **EMITS** Redstone energy from this side.
    * **Usage:** Energy will come out here when Arduino sends a command to the game.

---

### Logic Gates (Security Mode)
The IO Block features an internal logic system acting as a "physical filter". This is useful if you want your Arduino commands to execute only if certain in-game physical conditions are met (e.g., a safety lever is on).

**How it works?**
This logic applies only to **IN Pins (Green)**.
* **No IN Pins:** If no pin is configured as IN, the block obeys Arduino directly (works normally).
* **With IN Pins:** The block will only process the Arduino command if the IN pins meet the configured logic condition.

**Logic Modes:**
1.  **OR:** Active if **AT LEAST ONE** IN pin receives power.
2.  **AND:** Active only if **ALL** IN pins receive power simultaneously.
3.  **XOR:** Active if an **ODD** number of IN pins receive power.

---

## 4. Communication Protocol
To ensure the mod "understands" your board (and vice-versa), you must follow strict formatting rules.

### Read/Write Criteria
1.  **Format:** We always use `KEY:VALUE` pairs.
2.  **Line Terminator (`\n`):** SerialCraft reads data line by line.
    * In Arduino, **ALWAYS** use `Serial.println()` when sending data. If you use just `Serial.print()`, the mod will wait infinitely for the message end.
3.  **Timing:** Avoid saturating the serial port. Sending data every 50ms (`delay(50)`) is enough for a smooth response without causing lag.

---

## 5. Your First Circuit
In this tutorial, we will build a mixed system: using physical sensors to control Minecraft Redstone and Minecraft signals to turn on a real LED.

### 1. Physical Circuit
You will need the following components connected to your Arduino:
* **Potentiometer:** Pin `A1`.
* **Sound Sensor (Mic):** Pin `A2`.
* **LED (or built-in):** Pin `13`.

### 2. Minecraft Configuration
We will set up 3 blocks: two receivers (for Pot and Mic) and one emitter (for LED).

#### A. Block 1: Potentiometer (Arduino ➔ Minecraft)
We want this block to receive a variable value (0-15) and emit that Redstone intensity.

1.  **Physical Config (Pins):**
    * Aim at a side connector and **Shift + Right Click**.
    * Pin turns **Red (Pin OUT)**. (Energy *exits* to the world).
2.  **Logic Config (UI):**
    * Right Click the base to open UI.
    * **Mode:** INPUT (Board receives data).
    * **Signal:** **ANALOG** (Allows variable intensity 0-15).
    * **Target Data:** Type `POT_1`.
    * *Expected format:* `POT_1:[0-15]`

#### B. Block 2: Microphone (Arduino ➔ Minecraft)
Same as above, receives variable data.

1.  **Pins:** **Shift + Right Click** on a connector to set **Mode OUT** (Red).
2.  **UI:**
    * **Mode:** INPUT.
    * **Signal:** **ANALOG**.
    * **Target Data:** `MIC_1`.
    * *Expected format:* `MIC_1:[0-15]`

#### C. Block 3: LED (Minecraft ➔ Arduino)
We want this block to read game Redstone (On/Off) and send the command.

1.  **Physical Config (Pins):**
    * Aim at a side connector and **Right Click** (normal).
    * Pin turns **Green (Pin IN)**. (Board *reads* incoming energy).
2.  **Logic Config (UI):**
    * Open UI.
    * **Mode:** OUTPUT (Board sends data).
    * **Signal:** **DIGITAL** (We only care about 0 or 1).
    * **Target Data:** Type `LED_1`.
    * *Sent format:* `LED_1:[0-1]` (0 = OFF, 1 = ON).

---

### 3. The Code (Sketch)
Copy and upload this code to your Arduino. Ensure the **Baud Rate** in code (`9600`) matches the one selected in the game Laptop.

```cpp
// --- PIN CONFIG ---
const int PIN_POT = A1;      // Potentiometer
const int PIN_MIC = A2;      // Sound Sensor
const int PIN_LED = 13;      // LED

// --- IDs (Must match UI "Target Data") ---
String ID_POT = "POT_1";
String ID_MIC = "MIC_1";
String ID_LED = "LED_1";

// --- VARIABLES ---
unsigned long lastSend = 0;
const int INTERVAL = 50;
int lastValPot = -1;
int lastValMic = -1;

void setup() {
  Serial.begin(9600); // Same speed as in-game Connector
  pinMode(PIN_POT, INPUT);
  pinMode(PIN_MIC, INPUT);
  pinMode(PIN_LED, OUTPUT);
}

void loop() {
  // 1. --- SEND DATA (ARDUINO -> MINECRAFT) ---
  if (millis() - lastSend > INTERVAL) {
    
    // Potentiometer (Analog -> Redstone 0-15)
    int rawPot = analogRead(PIN_POT);
    int mcPot = map(rawPot, 0, 1023, 0, 15); 
    
    if (mcPot != lastValPot) {
      Serial.print(ID_POT);
      Serial.print(":");
      Serial.println(mcPot); // Format: POT_1:15
      lastValPot = mcPot;
    }

    // Microphone (Analog -> Redstone 0-15)
    int rawMic = analogRead(PIN_MIC);
    int intensity = abs(rawMic - 512) * 10;
    int mcMic = constrain(map(intensity, 0, 300, 0, 15), 0, 15);
    
    if (mcMic != lastValMic) {
      Serial.print(ID_MIC);
      Serial.print(":");
      Serial.println(mcMic); // Format: MIC_1:8
      lastValMic = mcMic;
    }

    lastSend = millis();
  }

  // 2. --- RECEIVE DATA (MINECRAFT -> ARDUINO) ---
  if (Serial.available() > 0) {
    String message = Serial.readStringUntil('\n');
    message.trim();

    // LED (Digital 0/1)
    if (message.startsWith(ID_LED + ":")) {
      int separador = message.indexOf(':');
      int value = message.substring(separator + 1).toInt();
      // If "LED_1:1" arrives turn ON, if "LED_1:0" turn OFF
      digitalWrite(PIN_LED, value > 0 ? HIGH : LOW);
    }
  }
}
```

---

## 6. Troubleshooting

### Debug HUD (F7)
If something isn't working, SerialCraft includes an internal diagnostic tool.
Press **F7** in-game to toggle the **Serial Debug HUD**.

* **TX (Green):** Shows data Minecraft is *sending* to the serial port.
* **RX (Blue):** Shows data *arriving* from your Arduino.

**Common Diagnostics:**
1.  **Is RX empty?**
    * Check if your Arduino code uses `Serial.println()` (with newline) and not just `Serial.print()`.
    * Verify **Baud Rate** on Connector block matches `Serial.begin()`.
2.  **"Port Busy" Error?**
    * Ensure the Arduino IDE Serial Monitor is closed. The COM port can only be used by one program at a time.