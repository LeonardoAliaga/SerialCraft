# SerialCraft Guide — v0.4.6 (Beta)

Welcome to the official **SerialCraft** guide. 0.4.6 (Beta) introduces in-game telemetry streaming to hardware, standardizes communication channels, and optimizes performance across Minecraft clients, servers, and microcontrollers with limited resources (e.g. Arduino Uno ATmega328P).

## About the project

SerialCraft is an open source project created by **Leonardo Aliaga** (@aliaga1924). It was created as a **learning project** at the intersection of game modding (Fabric/Java), hardware design and telecommunications.

AI tools are used strategically. **The project does not depend on AI to exist.** Architecture and logic are designed first; AI is then used as an assistant to execute, refine and translate those ideas into code faster.

---

## What's new in v0.4.6 (Beta)

* **Game Telemetry (Minecraft ➔ Hardware)**:
  The new **Events** tab in the Laptop streams real-time game data (world time, hunger, health, level, damage taken, etc.) to the physical microcontroller using the line format `mc_<channel>:<integer>\n`.
* **Reserved prefix `mc_`**:
  The prefix `mc_` is strictly reserved for game telemetry channels. Physical IO Blocks cannot use a `Target Data` starting with `mc_` (rejected by the server with a helpful notice), ensuring redstone block signals and telemetry events never collide.
* **Flow control & pacing (TelemetryOutbox)**:
  Outbox queue paces outbound telemetry lines to at most 1 line per tick (20 lines/s) prioritizing edge events over state snapshots, preventing 64-byte serial buffer overflows on boards like the ATmega328P.
* **Periodic 5-second state resends**:
  Microcontrollers like Arduino Uno R3 reset when their USB serial connection opens (DTR signal). To avoid missing states, active channels are quietly resent every 5 seconds without flooding logs.
* **Live UI wire indicators**:
  The **Events** tab displays live values next to each toggle (`mc_hunger:20`), and the console highlights incoming and outgoing telemetry with the `TM:` prefix.
* **Migration to Minecraft 26.2 & Java 25**:
  The mod runs on Fabric Loader for Minecraft 26.2 and requires **Java 25** as its runtime environment.
* **Visualizer tab (test bench)**:
  The Laptop now has a **Visualizer** tab with three tools: a **timeline** that shows several signals at once (sensors, values the game sends, and `mc_*` telemetry), a **Sensor** view that compares the raw value with the redstone it produces and suggests a deadband, and a wave **generator** to check that your board responds without building a redstone circuit. Every message carries its real timestamp, and only real points are joined (never an invented curve). See [Test bench](#test-bench-visualizer-tab).
* **Board identification**:
  A USB-serial bridge chip (CH340, CP2102) does not say which board is behind it, so the mod no longer labels every CH340 board "Arduino". It recognises the Arduino UNO Q, UNO R3/R4, Mega, Nano ESP32 and native-USB ESP32s over USB; for the rest it shows "Board with CH340 (model not identified)" until the board announces itself with `mc_id`. See [protocol, section 12](/en/protocol#_12-board-identification-and-token-free-reconnection).
* **Remembered boards (Wi-Fi without a token)**:
  With **Remember board** (Home tab) a Wi-Fi board stores a key and reconnects without typing the token. When remembered boards exist, the Wi-Fi server starts by itself when you join a world. See [protocol, section 12](/en/protocol#_12-board-identification-and-token-free-reconnection).
* **The Laptop no longer pauses the game**:
  In a singleplayer world, opening the Laptop leaves the world running. That way telemetry (`mc_*`: time, weather, health…) keeps reaching the board and you can watch it change in Visualizer. Note that with the Laptop open the player is still in the world and can take damage.
* **Removal of ComputerCraft integration**:
  External peripheral classes (`ArduinoPeripheral` and `CCIntegration`) were removed to streamline the mod and focus purely on direct, native real-world hardware integration and game telemetry.
* **Integrated recipes**:
  Crafting recipes are built directly into mod assets for Survival mode, compatible with the vanilla recipe book and recipe viewers (JEI/REI).

---

## Changes previously introduced in v0.4.3

* **Unified 0-255 scale.** The wire uses the same range in both directions. Previously output sent 0-255 while input was clamped to 0-15, so a `200` sent by the board came back as `15`. **Digital mode now sends 255, not `1`**, so the same `analogWrite()` works for both signal types.
* **Wi-Fi with pairing.** The mod is now the TCP server and the board is the client, on port **25585**, with a mandatory token. In 0.3.x the port was open with no authentication: anyone on the same network could drive your redstone.
* **Dedicated-server ready.** Boards are now indexed when loaded from disk. In earlier versions, boards became invisible after a server restart until placed again by hand.
* **Rate limiting.** 40 messages/s per player with a burst of 80, protecting the world from a badly written sketch.
* **Reorganised interface** into independent pages, with correct text clipping in every language.
* **Five real locales:** English, plus Spanish for Spain, Mexico, Peru and Argentina (with *voseo*).

::: warning Compatibility
0.3.x sketches **will not work unchanged**. See [Changes from 0.3.x](/en/protocol#_8-changes-from-0-3-x).
:::

---

## Installation

1. Install [Fabric Loader](https://fabricmc.net/) for **Minecraft 26.2** (requires **Java 25**).
2. Download `Fabric API` and the SerialCraft v0.4.6-beta `.jar`.
3. Drop both into your `mods` folder.
4. Launch the game.

---

## The three pieces

| Piece | Purpose |
| :--- | :--- |
| **Laptop** | Handheld item. Opens the interface: connection, board list, events, visualizer (oscilloscope), and console. |
| **Connector Block** | Anchors the USB connection in the world and stores the baud rate. |
| **IO Block** | The actual bridge. Each has a `Target Data`, a mode (INPUT/OUTPUT), a signal type (Digital/Analog) and configurable sides. |

---

## Connection setup

### Option A: USB Serial

1. Plug the board into your computer's USB port.
2. Place a **Connector Block** and right-click it.
3. On the **Connection** tab, the port scan lists available boards.
4. Pick yours and hit **Connect**.

::: tip Baud rates must match
The default is **115200**. If your sketch uses `Serial.begin(9600)` while the block is set to 115200, nothing legible will come through. This is the single most common failure mode, and it produces no error message — simply silence.
:::

### Option B: Wi-Fi

1. Keep PC and board on the **same local network**.
2. Open the Laptop → **Home** → **Start Wi-Fi server**.
3. The interface displays three values: **local IP**, **port** (25585) and **pairing token**.
4. Copy all three into your sketch or Python script.
5. The board connects and sends the token as its first line; the mod replies `OK`.

::: danger Security scope
The Wi-Fi channel is **plaintext**. The token prevents casual access within your network, but it is not encryption. Safe for home or classroom use; **do not forward port 25585 on your router**.
:::

---

## Your first bidirectional circuit

1. Place an **IO Block** and right-click it.
2. In **Target Data**, enter a unique identifier, for instance `green_led`.
3. Pick the mode:
   * **OUTPUT** — Minecraft sends to the board (turn on an LED, move a motor).
   * **INPUT** — the board sends to Minecraft (a button, a sensor).
4. Pick the signal type:
   * **Digital** — on or off (0 or 255 on the wire).
   * **Analog** — proportional to redstone (0-255 on the wire).
5. Configure which sides accept redstone.
6. Flash the corresponding sketch to the board.

With two IO Blocks — one `INPUT` named `pot_val` and one `OUTPUT` named `green_led` — you have the complete setup used in all the examples.

👉 **[Ready-to-flash examples](/en/examples/)** — Arduino Uno R3, ESP32 and Arduino Uno Q, with wiring diagrams.

---

## Test bench (Visualizer tab)

The **Visualizer** tab shows what travels between the game and the board and lets you check that the board responds, without building redstone circuits.

::: info This is not a lab oscilloscope
The cable carries text messages with values from 0 to 255, a few dozen per second. What you see is **which messages arrived and when**; there is nothing to show about what happens between two messages.
:::

### What is on screen

| Row | Controls |
| :---: | :--- |
| 1 | **View** (Timeline or Sensor), **Window** (5, 10, 30 or 60 s), **Pause** and **Clear** |
| 2 | **Channels** field (timeline) or **sensor** field (Sensor view), and the **Trace** button (Line or Steps) |
| 3 | **Generator**: Generate/Stop, shape, period, amplitude and the key to send |

Below the controls, a status line tells you whether the generator is running, if something failed, or if the graph is paused.

The Laptop **does not pause the game** in a singleplayer world: the world keeps running while you use it, so watch out for attacks.

### How to read the graphs

* **Green (RX):** what **the board sends** to the game, for example a `pot_val` sensor.
* **Orange (TX):** what **the game sends** to the board: output IO Blocks, `mc_*` telemetry and the generator.
* The **trace** has two styles, switched with the **Trace** button. **Line** (default) joins the real messages with straight segments, so a wave looks like a wave. **Steps** holds the value until the next message, exactly as the game saw it. Neither is a smoothed curve, because a curve would draw values that were never sent. In Line mode, if more than 0.3 s pass between two messages they are not joined (a sensor that stayed still did not change gradually): the value is held until the next one. When there are few messages, each one is marked with a white dot.
* `mc_damage` and `mc_death` are **events**, not states: they are drawn as bars at the instant they happened.
* The scale is 0-255. It grows to 1023, 4095 or 65535 if the signal exceeds it (a 10 or 12-bit ADC), and `mc_*` channels use their own range.

What is recorded is what the board really sent, including messages the rate limiter drops before they reach the block.

### Timeline

Shows several signals in lanes that share one time axis, to answer "where does the chain break?": is the sensor sending, is the game receiving, is the board responding?

* With the channels field **empty**, it shows signals with activity in the last 2 minutes, the most recent ones that fit on screen; the rest is announced as "+N more signals".
* To choose which ones to see, type names separated by commas or spaces. Each can be exact or end in `*`: `pot_val, led_verde` or `mc_*`.
* Lane order is fixed (RX first, then TX, alphabetical): lanes do not jump around when a sensor talks.

### Sensor view

Shows **one** signal in detail: the raw wire value (green) and the **redstone** it produces (orange), drawn on the same scale. The `RS 0-15` axis is on the right.

| Row | Meaning |
| :--- | :--- |
| Value | Last raw value and its matching redstone level |
| Messages/s | How many messages per second the signal sends (last 3 s) |
| Range | Minimum and maximum of the window, and the time-weighted average |
| Variation 3 s | Maximum minus minimum over the last 3 s, and the **suggested deadband** |
| Redstone | How many times the redstone level changed in the last 3 s |

**How to calibrate a sensor:**

1. Open the **Sensor** view and type the key (for example `pot_val`). Empty shows the latest active sensor.
2. Keep the sensor **still** for a few seconds.
3. Read **Variation 3 s**: with the sensor still, that number is its noise. If it is 0, the signal is clean.
4. In the sketch, use a deadband equal to or larger than the suggested value: `if (abs(value - last) >= 4) { send(); }`.
5. If **Redstone** changes several times while the sensor is still, the value sits on the edge between two redstone levels (each level is 17 wire units) and flickers. The deadband fixes it.

::: warning The redstone conversion is the one for an Analog signal
A **Digital** block turns any value above 0 into 15. The view only shows the conversion when the scale is 0-255 and the key does not start with `mc_`.
:::

### Generator

Sends a test wave to a key on the board. Use it to check that an LED, servo or motor responds **without building a redstone circuit**.

1. Connect the board.
2. Type the actuator's key in the field on the right (`led_verde` by default).
3. Pick the **shape**: Ramp, Triangle, Square, Sine or Stairs (16 steps, one per redstone level).
4. Pick the **period** (1, 2, 5 or 10 s) and the **amplitude** (100, 50 or 25 % of 0-255).
5. Press **Generate**. On the timeline you will see the orange lane with your key and, if the board answers with a sensor (for example an LDR facing the LED), its green lane right below.

Generator rules:

* It sends integers from 0 to 255, at most **20 messages per second** (one per tick) and only when the value changes: the same ceiling as the game's telemetry. At that rate a 2 s sine has 40 points per cycle, enough for it to look like a wave.
* The key accepts letters, numbers, `_`, `.` and `-`, up to 32 characters.
* It keeps running if you switch tabs. It **stops** when you press Stop, when you close the Laptop, or if the connection is lost.
* When it stops on your command or when you close the Laptop, it leaves the board at rest by sending `key:0`.

::: warning It sends real values to your hardware
If a servo or motor is connected, start with the amplitude at 25 %.
:::

---

## Known limits in this version

Worth knowing before you build something large:

* The Laptop's board list **does not scroll**. Beyond roughly 8 boards, the rest fall off the bottom of the screen.
* The interface is designed for standard resolutions; at maximum GUI scale on 854×480 the cards overflow the visible area.
* There is no mode where **the server** owns the hardware. The serial port lives on each player's computer, so the model is "every player controls their own boards from their PC". This is an architectural choice, not an oversight.
* The Wi-Fi channel is unencrypted.
* The Visualizer tab keeps the last ~4000 samples of up to 16 signals at once. It is a log of **messages**, not an oscilloscope: it cannot see anything that happens between two messages.
* The generator sends at most 20 messages per second.

---

## Multiplayer

Works in singleplayer, LAN and dedicated servers. Each player sees and controls **only their own boards**; operators with the `serialcraft.admin.bypass` permission can interact with boards belonging to others.

Reasonable numbers: 10 to 50 boards per player (the practical limit is the interface, not the server), several hundred per server, and around 2 KB/s of traffic per active board.
