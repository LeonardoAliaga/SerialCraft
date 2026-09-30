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

## 9. Changes from 0.4.3

Version 0.4.6 introduces the game telemetry channel and enforces isolation between block signals and game state:

1. **Reserved `mc_` prefix**:
   The `mc_` prefix is strictly reserved for in-game telemetry (`mc_time`, `mc_health`, `mc_damage`, etc.).
   * **Server-side rejection:** The server rejects any attempt to configure an IO Block whose `Target Data` starts with `mc_`, warning the player with a clear message (`message.serialcraft.reserved_key`).
   * **Input discard:** In `ArduinoIOBlockEntity`, any message received from the board that begins with `mc_` is discarded immediately in $O(1)$ without touching redstone or NBT.
2. **Game telemetry (Minecraft ➔ Hardware)**:
   Player and world events are streamed to the microcontroller using the format `mc_<channel>:<integer>\n` enabled from the Laptop's **Events** tab.
3. **Pacing and flow control**:
   Outbound telemetry is strictly throttled to at most **1 line per tick** (20 lines/s max) via `TelemetryOutbox`, giving priority to edge events over state snapshots to avoid overflowing the 64-byte serial buffer on boards like the ATmega328P.
4. **Periodic 5-second state resends**:
   Active periodic channels are quietly resent every 100 ticks (5 s) without console spam, ensuring microcontrollers that reset upon opening serial DTR lines acquire current state without manual intervention.

---

## 10. Full examples

Ready-to-flash sketches in [Examples and testing](/en/examples/): Arduino Uno R3 (USB), ESP32 (Wi-Fi) and Arduino Uno Q (Bridge + Python), with wiring diagrams.

---

## 11. Game telemetry (Minecraft ➔ Hardware)

::: tip New in 0.4.6-beta
The panel's **Events** tab lets you choose which game data (time of day, hunger, damage taken...) is sent to the board. It is the same USB or Wi-Fi channel as in the previous sections; only the line format differs.
:::

### Format

```text
mc_<channel>:<integer>\n
```

| Rule | Detail |
| :--- | :--- |
| **Reserved prefix `mc_`** | No IO Block may use it as `Target Data` (the server rejects the configuration with a notice). A board can therefore never mistake game data for a redstone command. |
| **Key** | `mc_[a-z0-9_]{1,29}`: lowercase letters, digits and `_`, 32 characters at most. |
| **Value** | Signed decimal integer. Every channel fits a 16-bit `int` (the size on an Arduino Uno). Each channel also promises its own, narrower range, listed below. |
| **Off by default** | No channel is sent until you tick it in the tab. An older sketch never receives lines it did not ask for. |
| **Unknown keys** | Your sketch must ignore them. New channels will be added without breaking existing ones. |

### Channels

| Key | Kind | Range | Meaning |
| :--- | :--- | :--- | :--- |
| `mc_time` | State | 0-23999 | Time of day in ticks: `0` = 06:00, `6000` = 12:00, `12000` = 18:00, `18000` = 00:00. A day lasts 20 real minutes (20 ticks/s). |
| `mc_isday` | State | 0-1 | `1` if `mc_time` is below 12000. |
| `mc_weather` | State | 0-2 | `0` clear, `1` rain, `2` thunderstorm. |
| `mc_health` | State | 0-1024 | Health in points, rounded (2 points = 1 heart). |
| `mc_hunger` | State | 0-20 | Hunger level. |
| `mc_saturation` | State | 0-20 | Saturation, rounded. |
| `mc_level` | State | 0-32767 | Experience level. |
| `mc_air` | State | 0-100 | Remaining air, as a percentage. |
| `mc_fire` | State | 0-1 | `1` while the player is on fire. |
| `mc_damage` | Event | 1-32767 | Health points lost between two consecutive ticks. It is a net loss: damage absorbed by golden hearts and the cut-back when a bonus-health effect ends do not count. |
| `mc_death` | Event | 1 | The player has died. The line appearing is the event; the value is always `1`. |

`mc_time` follows the clock of the dimension the player is in. In dimensions without their own day/night cycle it may stay fixed; the channel is meant for the Overworld.

### Delivery guarantees

| | **State** | **Event** |
| :--- | :--- | :--- |
| **When it is sent** | On change, at the interval chosen in the tab (0.25 to 10 s), and **every 5 s even if unchanged** | Once per occurrence |
| **Resent** | Yes | **Never**: repeating it would make the board think there was another hit |
| **If it is lost** | Recovered at the next resend | Lost: there is no acknowledgement |

What this means for your sketch:

* **Treat states as idempotent.** Receiving `mc_hunger:20` twice must change nothing.
* **The 5 s resend exists because of USB.** An Arduino Uno resets when the computer opens its port, and the first lines land inside the bootloader's startup. Without a resend, a stable value would never arrive.
* **Pacing.** At most **one line per tick** (20 lines/s), with events ahead of states. On connect, the full snapshot takes a few hundred milliseconds. The event queue holds 16; when full, the oldest is dropped.
* **Game data and IO Block outputs share the same channel.** Tell them apart by the `mc_` prefix.

### Example: ESP32 clock with a servo

The hand sweeps 0° → 180° during the day and back to 0° at night (a standard servo cannot turn a full circle). Enable **Time of day** in the Events tab. The board interpolates between messages so the motion is smooth, and stops after 15 s without news so it does not drift while the game is paused.

```cpp
#include <WiFi.h>
#include <ESP32Servo.h>

// ── Settings ─────────────────────────────────────────────────
const char*    WIFI_SSID = "TU_WIFI";
const char*    WIFI_PASS = "TU_CLAVE";
const char*    MOD_HOST  = "192.168.1.50";  // IP shown by the mod's Laptop
const uint16_t MOD_PORT  = 25585;
const char*    MOD_TOKEN = "ABC123";        // token shown by the mod's Laptop
const int      SERVO_PIN = 18;              // power the servo from an external 5 V supply

WiFiClient client;
Servo hand;

// Last time received and the local instant it arrived
long          baseTicks  = 0;
unsigned long baseMillis = 0;
bool          haveTime   = false;

int           currentAngle = 0;
unsigned long lastStep     = 0;
unsigned long lastTry      = 0;

// 20 ticks/s = 1 tick every 50 ms. With no news from the mod for 15 s (above the
// tab's maximum interval of 10 s) the hand stops advancing on its own, so it
// does not drift while the game is paused.
long estimatedTicks() {
  long elapsed = (long)((millis() - baseMillis) / 50UL);
  if (elapsed > 300) elapsed = 300;
  return (baseTicks + elapsed) % 24000L;
}

// Day (0-12000): 0° -> 180°.  Night (12000-24000): 180° -> 0°.
int targetAngle(long t) {
  if (t < 12000L) return (int)(t * 180L / 12000L);
  return (int)((24000L - t) * 180L / 12000L);
}

void handleLine(String line) {
  line.trim();
  int sep = line.indexOf(':');
  if (sep <= 0) return;                        // not a KEY:VALUE line
  String key  = line.substring(0, sep);
  long   value = line.substring(sep + 1).toInt();

  if (key == "mc_time") {
    baseTicks  = value;
    baseMillis = millis();
    haveTime   = true;
  }
  // Unknown keys are ignored: the sketch keeps working if you enable more
  // data in the Events tab.
}

bool connectToMod() {
  if (!client.connect(MOD_HOST, MOD_PORT)) return false;
  client.print(String(MOD_TOKEN) + "\n");      // the token is the first line
  String reply = client.readStringUntil('\n');
  reply.trim();
  return reply == "OK";
}

void moveHand() {
  if (millis() - lastStep < 15) return;        // at most 1° every 15 ms
  lastStep = millis();
  int target = targetAngle(estimatedTicks());
  if (currentAngle == target) return;
  currentAngle += (currentAngle < target) ? 1 : -1;
  hand.write(currentAngle);
}

void setup() {
  hand.setPeriodHertz(50);
  hand.attach(SERVO_PIN, 500, 2400);
  hand.write(currentAngle);

  WiFi.begin(WIFI_SSID, WIFI_PASS);
  while (WiFi.status() != WL_CONNECTED) delay(250);
}

void loop() {
  if (!client.connected()) {
    if (millis() - lastTry > 2000) {           // retry every 2 s
      lastTry = millis();
      connectToMod();
    }
    return;
  }

  while (client.available()) handleLine(client.readStringUntil('\n'));
  if (haveTime) moveHand();
}
```

::: warning Do not power the servo from the ESP32
Use its own 5 V supply and join the grounds. The ESP32's 3.3 V pin cannot supply a servo's startup current spike.
:::
