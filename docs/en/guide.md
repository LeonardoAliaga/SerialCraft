# SerialCraft Guide — v0.4.3 (Beta)

Welcome to the official **SerialCraft** guide. 0.4.3 is the release with the most internal work so far: a new interface, a unified protocol, and a full review of the networking layer with multiplayer servers in mind.

## About the project

SerialCraft is an open-source project created by **Leonardo Aliaga** (@aliaga1924). It exists as a **learning project**, exploring the overlap between software development (Java modding with Fabric), physical hardware design and telecommunications.

AI tools are used strategically. **The project does not depend on AI to exist.** The architecture and system logic come first; AI then helps execute, refine and translate those ideas into code faster.

---

## What's new in v0.4.3

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

1. Install [Fabric Loader](https://fabricmc.net/) for **Minecraft 1.21.11**.
2. Download `Fabric API` and the SerialCraft v0.4.3 `.jar`.
3. Drop both into your `mods` folder.
4. Launch the game.

---

## The three pieces of the mod

| Piece | Purpose |
| :--- | :--- |
| **Laptop** | Handheld item. Opens the interface: connection, board list and console. |
| **Connector Block** | Anchors the USB connection in the world and stores the baud rate. |
| **IO Block** | The actual bridge. Each has a `Target Data`, a mode (INPUT/OUTPUT), a signal type (Digital/Analog) and configurable sides. |

---

## Connection setup

### Option A: USB Serial

1. Plug the board into a USB port.
2. Place a **Connector Block** and right-click it.
3. Under **Connection**, the scan lists available ports.
4. Pick the board and press **Connect**.

::: tip Baud rates must match
The default is **115200**. If your sketch says `Serial.begin(9600)` while the block is at 115200, nothing readable arrives. It is the most common failure and it produces no visible error — nothing simply happens.
:::

### Option B: Wi-Fi

1. PC and board on the **same local network**.
2. Open the Laptop → **Home** → **Start Wi-Fi server**.
3. The UI shows three things: **local IP**, **port** (25585) and a **pairing token**.
4. Copy all three into your sketch or script.
5. The board connects and sends the token as its first line; the mod replies `OK`.

::: danger Security scope
The Wi-Fi channel is **plaintext**. The token prevents casual access inside your own network, but it is not encryption. Use it on a home or classroom network; **do not forward port 25585 on your router**.
:::

---

## Your first bidirectional circuit

1. Place an **IO Block** and right-click it.
2. In **Target Data**, type a unique identifier, e.g. `green_led`.
3. Pick the mode:
   * **OUTPUT** — Minecraft sends to the board (light an LED, drive a motor).
   * **INPUT** — the board sends to Minecraft (a button, a sensor).
4. Pick the signal type:
   * **Digital** — on or off (0 or 255 on the wire).
   * **Analog** — proportional to redstone (0-255 on the wire).
5. Configure which sides act as redstone inputs.
6. Flash the matching sketch to your board.

With two IO Blocks — one `INPUT` named `pot_val` and one `OUTPUT` named `green_led` — you already have the full circuit used in the examples.

👉 **[Ready-to-flash examples](/en/examples/)** — Arduino Uno R3, ESP32 and Arduino Uno Q, with wiring diagrams.

---

## Known limits in this version

Worth knowing before building something large:

* The Laptop's board list **has no scrolling**. Past roughly 8 boards, the rest fall off screen.
* The UI targets normal resolutions; at maximum GUI scale on 854×480 the cards overflow the visible area.
* There is no mode where **the server** owns the hardware. The serial port lives on each player's own computer, so the model is "every player drives their own boards from their own PC". That is an architectural decision, not an oversight.
* The Wi-Fi channel is not encrypted.

---

## Multiplayer

Works in singleplayer, on LAN and on a dedicated server. Each player sees and controls **only their own boards**; operators with the `serialcraft.admin.bypass` permission can operate other players' boards.

Reasonable figures: 10-50 boards per player (the practical ceiling is the UI, not the server), several hundred per server, and roughly 2 KB/s of traffic per active board.
