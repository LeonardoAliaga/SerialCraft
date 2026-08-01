# Bidirectional Protocol and Hardware (v0.4.3)

::: warning Beta 0.4.3
This release **breaks compatibility** with 0.3.x sketches in two places: the value scale and the direction of the Wi-Fi connection. Read [Changes from 0.3.x](#_8-changes-from-0-3-x) before reusing older code.
:::

## 1. Communication specification

SerialCraft uses a plain-text (UTF-8), line-oriented protocol. Every message must end with `\n`, over USB and over TCP alike.

| Parameter | Value |
| :--- | :--- |
| **Supported baud rates (Serial)** | 300, 1200, 2400, 4800, 9600, 19200, 38400, 57600, 74880, **115200 (default)**, 230400, 250000 |
| **TCP port (Wi-Fi)** | **25585** (default) |
| **Format** | `KEY:VALUE` |
| **Terminator** | `\n` |
| **Encoding** | UTF-8 |
| **Maximum line length** | 256 characters |
| **Maximum `Target Data` length** | 32 characters |
| **Maximum inbound rate** | 40 messages/s sustained, burst of 80 |

::: danger The terminator is not optional
The mod ignores anything that does not end with `\n`. Always use `Serial.println()` or `client.print("...\n")`, never a bare `print()`.
:::

::: tip Why the 40 messages/s cap exists
Every received line becomes a packet to the server. An unthrottled `Serial.println()` inside `loop()` produces thousands per second and causes real lag on a multiplayer server. Excess packets are dropped silently — that is backpressure, not an error. Send only when the value **changes**.
:::

---

## 2. Unified 0-255 scale

Since v0.4.3 **the wire always speaks 0-255**, in both directions and for both signal types. Redstone inside Minecraft is still 0-15; the mod does the conversion.

| Signal type | Minecraft → board | Board → Minecraft |
| :--- | :--- | :--- |
| **Analog (PWM)** | `redstone × 255 / 15` | `value × 15 / 255` |
| **Digital** | 0 if redstone = 0, **255** if redstone ≥ 1 | 0 if value = 0, **15** if value ≥ 1 |

### Analog conversion table

| Redstone | Wire value | Description |
| :---: | :---: | :--- |
| 0 | 0 | Off |
| 1 | 17 | Minimum |
| 7 | 119 | Mid (~50%) |
| 15 | 255 | Maximum |

::: tip Practical consequence
Your firmware **does not need to know** whether the block is Digital or Analog. A single `analogWrite(pin, value)` covers both. In 0.3.x digital mode sent `1`, and `analogWrite(pin, 1)` left the LED effectively dark at a 0.4% duty cycle — the classic first-circuit failure.
:::

In digital mode, **any** incoming value of 1 or greater is treated as fully on (redstone 15). You may send `1`, `15` or `255` interchangeably.

---

## 3. Input (Hardware ➔ Minecraft)

The board sends the target block ID and a value:

```text
<TARGET_DATA>:<INTEGER>\n
```

* **`<TARGET_DATA>`**: the string you typed in the IO Block's *Target Data* field (e.g. `btn_1`, `light_sensor`). 32 characters max. **If it is empty the block ignores everything** — there is no wildcard.
* **`<INTEGER>`**: 0-255. Out-of-range values are clamped; non-numeric text is discarded without throwing.

```cpp
Serial.println("light_sensor:200");   // ~redstone 12 in analog mode
Serial.println("alarm:255");          // fully on
Serial.println("alarm:0");            // off
```

The IO Block must be in **INPUT** mode with a matching `Target Data`. Only the blocks of the player holding the connection receive messages.

---

## 4. Output (Minecraft ➔ Hardware)

An IO Block in **OUTPUT** mode emits automatically whenever the redstone level it receives changes:

```text
<TARGET_DATA>:<VALUE>\n
```

Two implementation details worth knowing:

* **Deduplication**: the block never resends an identical value. Holding a lever on sends one message, not twenty per second.
* **Interval**: the output check runs every 2 ticks (10 Hz) unless a change marks it urgent. That is the tradeoff between responsiveness and server cost.

---

## 5. Wi-Fi: the mod is the server

::: warning Direction changed from 0.3.x
Previously the board opened a server on port 8080 and Minecraft connected to it. **It is now the other way around**: the Minecraft client opens the TCP server and the board connects as a client.
:::

### Connection sequence

1. In game: **Laptop → Home → Start Wi-Fi server**. The UI shows the local IP, the port (25585) and a **pairing token**.
2. The board opens a TCP connection to that IP and port.
3. The board sends **the token as its first line**, terminated with `\n`.
4. The mod replies `OK\n` if correct, or `ERR TOKEN\n` and closes.
5. From then on the channel carries normal `KEY:VALUE` messages.

```text
board → mod: ABC123\n
mod → board: OK\n
board → mod: pot_val:128\n
mod → board: green_led:255\n
```

### Server restrictions

| Rule | Reason |
| :--- | :--- |
| Only **one** board at a time | Stops a third party from repeatedly kicking yours off |
| **Private** addresses only (LAN, loopback, link-local) | Prevents accidental exposure to the internet |
| Lines over 256 characters end the session | A peer that never sends `\n` would exhaust client memory |
| Token required | Without it, anyone on the network could drive your redstone over telnet |

::: danger The channel is plaintext
The token prevents casual or accidental access, but it is **not encryption**. Suitable for a home or classroom network. **Do not forward port 25585 on your router or use this over the internet.**
:::

---

## 6. Gate logic

Each IO Block has a logic mode that decides when it counts as active if several sides configured as inputs are powered:

* **OR** (default): active if *any* side is powered.
* **AND**: active only if *every* input side is powered.
* **XOR**: active if an *odd* number of sides is powered.

If the condition is not met the block neither emits nor accepts data, and drops its redstone output to 0. The same happens when it is disabled from the Laptop.

---

## 7. Common failures

| Symptom | Usual cause |
| :--- | :--- |
| LED stays dark although the game reports sending | Old sketch comparing against `1` in digital mode. It now receives `255`; use `analogWrite` with no comparison |
| Nothing arrives over USB | Sketch baud rate differs from the Connector Block's |
| The board connects and immediately drops | Wrong token, or another board is already connected |
| Only some messages arrive | You are above 40 messages/s. Add hysteresis and send changes only |
| The block ignores everything | `Target Data` empty or different from the message key |
| The pot maxes out halfway through its travel | ADC resolution mapped wrong (10-bit vs 12-bit) |

---

## 8. Changes from 0.3.x

1. **Unified scale**: input was 0-15 and output 0-255. A `200` sent by the board came back as `15`. Both ends now use 0-255.
2. **Digital sends 255**, not `1`.
3. **Wi-Fi inverted**: the mod is the server, the board is the client.
4. **Port 25585** instead of 8080.
5. **Pairing token is mandatory.**
6. **Rate limit** of 40 messages/s per player.
7. **An empty `Target Data` no longer accepts any message** containing `:`.

---

## 9. Full examples

Ready-to-flash sketches in [Examples and testing](/en/examples/): Arduino Uno R3 (USB), ESP32 (Wi-Fi) and Arduino Uno Q (Bridge + Python), with wiring diagrams.
