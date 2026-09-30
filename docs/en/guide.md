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
* **Visualizer tab (Real-time oscilloscope monitor)**:
  The Laptop now includes a dedicated **Visualizer** tab offering real-time waveform graphing (oscilloscope) for IO Block inputs and outputs (analog and digital), making circuit diagnosis fast and intuitive.
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

## Known limits in this version

Worth knowing before you build something large:

* The Laptop's board list **does not scroll**. Beyond roughly 8 boards, the rest fall off the bottom of the screen.
* The interface is designed for standard resolutions; at maximum GUI scale on 854×480 the cards overflow the visible area.
* There is no mode where **the server** owns the hardware. The serial port lives on each player's computer, so the model is "every player controls their own boards from their PC". This is an architectural choice, not an oversight.
* The Wi-Fi channel is unencrypted.

---

## Multiplayer

Works in singleplayer, LAN and dedicated servers. Each player sees and controls **only their own boards**; operators with the `serialcraft.admin.bypass` permission can interact with boards belonging to others.

Reasonable numbers: 10 to 50 boards per player (the practical limit is the interface, not the server), several hundred per server, and around 2 KB/s of traffic per active board.
