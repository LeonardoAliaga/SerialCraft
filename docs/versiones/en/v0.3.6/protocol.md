# Protocol Technical Reference

::: warning Beta Version 0.3.6
This technical documentation applies to the current version. The protocol may evolve in future updates to include JSON or binary support.
:::

## 1. Communication Specs

SerialCraft uses a synchronous plain text (ASCII) protocol. Communication is based on sending data packets terminated by a newline character.

| Parameter | Value |
| :--- | :--- |
| **Supported Baud Rates** | 9600, 14400, 19200, 38400, 57600, 115200 |
| **Data Format** | `KEY:VALUE` |
| **Terminator** | `\n` (Newline) |
| **Encoding** | UTF-8 (ASCII compatible) |

::: danger Important
The mod ignores any message not ending in `\n`. In Arduino, **ALWAYS** use `Serial.println()` and not just `Serial.print()`.
:::

---

## 2. Input (Arduino ➔ Minecraft)

For Minecraft to react to a physical event, the Arduino must send a text string with the target block ID and the desired value.

### Syntax
```text
<BLOCK_ID>:<INT_VALUE>\n
```

* **`<BLOCK_ID>`**: Text string defined in the "Target Data" field inside the IO Block interface (e.g., `btn_1`, `light_sensor`).
* **`<INT_VALUE>`**:
    * **0**: Turns off Redstone signal.
    * **1 - 15**: Turns on Redstone signal with that power level.
    * **> 15**: Interpreted as max signal (15).

### Processing Logic
The mod processes input in `ArduinoIOBlockEntity.java`. If the block is in **Input Mode**, it reads the value and updates adjacent Redstone levels via **OUT Pins (Red)**.

```cpp
// Arduino Example: Send max signal to block "alarm"
Serial.println("alarm:15");
```

---

## 3. Output (Minecraft ➔ Arduino)

When an IO Block in **Output Mode** detects a change in incoming Redstone signal (via **IN Pins / Green**), it automatically sends a message over serial.

### Syntax
```text
<BLOCK_ID>:<VALUE>\n
```

### Behavior by Signal Type
The sent value depends on how you configured the block (Digital or Analog/PWM).

#### A. Digital Signal (Simple)
If block is set to **Digital**:
* **Receives Redstone > 0**: Sends `1`.
* **Receives Redstone = 0**: Sends `0`.

#### B. Analog Signal (PWM)
If block is set to **Analog**, the mod performs an internal math conversion to translate Redstone (0-15) to PWM (0-255).

**Internal Formula:**
$$ PWM = \frac{Redstone \times 255}{15} $$

| Redstone Level | Sent Value (PWM) | Description |
| :---: | :---: | :--- |
| 0 | **0** | Off |
| 1 | **17** | Min |
| 7 | **119** | Half (~50%) |
| 15 | **255** | Max (100%) |

### Reading Example in Arduino
```cpp
// Detect if message is for block "ceiling_light"
if (message.startsWith("ceiling_light:")) {
    // Cut the ID part and parse the number
    int value = message.substring(message.indexOf(':') + 1).toInt();
    
    // Write directly to pin (works for Digital and PWM)
    analogWrite(PIN_LED, value); 
}
```

---

## 4. Logic Gates (Advanced)
IO Blocks feature a hidden characteristic: **Logic Gates**. This defines when the block activates if receiving power from multiple sides (North, South, East, West).

This is processed internally in `updateLogicConditions()`:

* **OR (Default):** Active if *any* connected side receives power.
* **AND:** Active only if *all* connected sides receive power simultaneously.
* **XOR:** Active if an *odd* number of sides receive power.