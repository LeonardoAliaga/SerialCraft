# 🔌 SerialCraft: the bridge between worlds

### Bring your real-world hardware into your Survival world.

[Documentation](https://serialcraft.xyz/en/) · [Source code](https://github.com/LeonardoAliaga/SerialCraft) · [Report an issue](https://github.com/LeonardoAliaga/SerialCraft/issues)

[![Fabric](https://img.shields.io/badge/Loader-Fabric-bea67e?style=for-the-badge)](https://fabricmc.net/)
[![Minecraft](https://img.shields.io/badge/Minecraft-26.2-2d8528?style=for-the-badge)](https://www.minecraft.net/)
[![Java](https://img.shields.io/badge/Java-25-orange?style=for-the-badge)](https://adoptium.net/)
[![Version](https://img.shields.io/badge/Version-0.5.0--beta-c96f4a?style=for-the-badge)](https://modrinth.com/mod/serialcraft/versions)
[![License](https://img.shields.io/badge/License-MIT-8a9a7b?style=for-the-badge)](https://github.com/LeonardoAliaga/SerialCraft/blob/main/LICENSE)

**SerialCraft** is an experimental, educational Fabric mod that connects **Arduino, ESP32, and other serial or TCP devices** to Minecraft. Send real sensor readings into redstone circuits and use your world to control physical LEDs, buzzers, and other components.

The Laptop, Connector, and Hardware I/O Module have crafting recipes, so your electronics can become part of a Survival playthrough.

## What's new in v0.5.0 Beta

**0.5.0-beta** brings the updated translucent interface and the community's Simplified Chinese translation together. It keeps the existing hardware protocol and Minecraft 26.2 / Java 25 compatibility.

- **Horizontal navigation:** Home and Boards sit to the left of the original centered SerialCraft logo; Events and Visualize sit to the right. The existing icons, page accents and visual identity are preserved.
- **Home:** connection properties scroll independently beside the bidirectional terminal, keeping the message history and command controls accessible. The connected-device card displays a randomly selected cat from the new sprite atlas.
- **Welcome:** vertically centered translucent panels, USB and Wi-Fi connection cards, clearer spacing, and a separate remembered-board notice. Pairing controls and connection help remain available.
- **Consistent panel styling:** glass headers, borders, button padding, margins and text alignment across Home, Boards, Events, Visualize and the IO module editor. Configuration, diagnostics and contextual help retain their existing functions.

### Simplified Chinese — 简体中文

Thanks to [@SK-Xiaohan](https://github.com/SK-Xiaohan) for contributing the Simplified Chinese translation in [PR #4](https://github.com/LeonardoAliaga/SerialCraft/pull/4) and suggesting the Chinese name **串连世界**!

The translation covers Welcome, Home, navigation, Boards, Events, Visualize, the IO editor, help dialogs, connection messages and dynamic device states. All five locale files have the same **402 translation keys**, with matching Minecraft format arguments. The original contribution and authorship are preserved in the repository history.

Select **简体中文（中国大陆）** in Minecraft's Language settings to use it.

> **Beta / pre-release:** `0.5.0-beta` is not a stable release. Back up worlds and update clients and servers together. Automated Java tests, Minecraft GameTests, localization and JAR checks pass; graphical UI inspection and tests with physical USB/Wi-Fi boards remain manual checks.

## Main features

- **Hardware → Minecraft:** physical buttons, switches, and sensors drive redstone. A potentiometer can control signal strength; a switch on your desk can open your base's door.
- **Minecraft → Hardware:** redstone controls your board. Use a lever to switch an LED or a comparator to adjust its brightness through PWM.
- **USB and Wi-Fi:** connect directly over USB serial, or pair a TCP board on your local network. Remembered Wi-Fi boards reconnect through HMAC challenge-response without retyping the token.
- **Events:** stream health, hunger, world time, and game events to hardware through dedicated `mc_*` channels.
- **Visualizer:** inspect RX/TX messages on a timeline, compare a sensor with its redstone conversion, and test an actuator with the waveform generator on a dedicated channel.
- **Board identification:** USB descriptors and firmware announcements identify supported boards. Generic USB adapters are shown without claiming an unconfirmed board model.
- **Configuration and diagnostics:** configure direction, signal, and OR/AND/XOR logic; inspect RX, TX, redstone, and connection state. Contextual help is available through **?**.
- **Five locales:** English, Spanish for Spain, Mexico and Argentina, and Simplified Chinese. SerialCraft follows your selected game language.

The Laptop has **Home**, **Boards**, **Events**, and **Visualizer** tabs, a connection welcome screen, and a console within Home. Opening it keeps your world running in singleplayer.

## Blocks and recipes

![SerialCraft blocks](https://cdn.modrinth.com/data/cached_images/4aea9efd4686b3adf8ea97550df78b0240142c49.png)

### Laptop and Connector

Open the Laptop or right-click a placed Connector to access the interface. Choose a USB board on the welcome screen, or start the Wi-Fi server to display its local IP, port, and pairing token. USB defaults to **115200 baud**; change it under **Home → Apply and reconnect** to match your firmware.

![Laptop recipe](https://cdn.modrinth.com/data/cached_images/0796082c1617ad4cd6d1c4f3beb989fc89878192_0.webp)

### Hardware I/O Module

Right-click the base to set a **Channel**, data direction, signal type, and logic. The channel must match your sketch, such as `pot_val` or `green_led`; `mc_` is reserved for game telemetry and identification.

Configure the five terminals directly on the placed block: right-click toggles a green input, and Shift + right-click toggles a red output. The bottom terminal is selected from above near the south edge of the board. Use green input for **Minecraft → Hardware** and red output for **Hardware → Minecraft**.

OR uses the maximum input, AND the minimum including zero, and XOR passes the maximum when an odd number of inputs are active. In Hardware → Minecraft mode, green inputs act as an enable condition for the received sample.

![Hardware I/O Module](https://cdn.modrinth.com/data/cached_images/c20ed9d2c7fe7a3a5a28704394c6a64a6cc2839b.png)
![Hardware I/O Module recipe](https://cdn.modrinth.com/data/cached_images/07d93e2ac7612058dcfb25f2161efcc182ec78a8_0.webp)

## Installation and first circuit

**Requirements:** Minecraft Java **26.2**, **Java 25 or newer**, **Fabric Loader 0.19.3 or newer**, and [Fabric API](https://modrinth.com/mod/fabric-api) for Minecraft 26.2.

1. Install SerialCraft and Fabric API in your `mods` folder. In multiplayer, update SerialCraft on clients and server together.
2. Flash a [ready-to-use example](https://serialcraft.xyz/en/examples/) for an Arduino Uno R3, ESP32, or Arduino Uno Q.
3. Plug the board into USB, or keep it on the same local network as your PC.
4. Craft the Laptop and Connector. Open the interface and connect through the USB or Wi-Fi welcome card.
5. Place a Hardware I/O Module. Set its Channel and direction, then configure its terminals in the world.
6. Use **Diagnostics**, the Home console, and **Visualizer** to follow the signal from your sketch to redstone or back to the board.

### Hardware protocol

Send a newline-terminated channel and integer, for example:

```text
pot_val:200
```

Hardware I/O values are **0–255 in both directions**, mapped to redstone's 0–15 range. Digital output sends 0 or 255. The firmware's baud rate must match the mod; the default is 115200. See the [protocol reference](https://serialcraft.xyz/en/protocol) for telemetry, pairing, and identification.

## Scope and limits

- **Multiplayer:** works in singleplayer, LAN, and dedicated servers. Each player connects hardware through their own PC; the dedicated server does not own a serial connection.
- **Scale:** a functional test covers 64 modules. Sustainable capacity depends on tick time and traffic; communication budgets are shared per player and the board list scrolls.
- **Signal delivery:** queues are bounded. TX records a transport write, not acknowledgement that an actuator executed it. Every pulse is not guaranteed under overload.
- **Wi-Fi:** TCP is unencrypted. Pairing is intended for a trusted home or classroom LAN; do not expose the port to the internet.

## Documentation and support

- [Installation and usage guide](https://serialcraft.xyz/en/guide)
- [Hardware I/O Module guide](https://serialcraft.xyz/en/io-module)
- [Protocol reference](https://serialcraft.xyz/en/protocol)
- [Board examples](https://serialcraft.xyz/en/examples/)
- [Tutorials](https://serialcraft.xyz/en/tutorials/)
- [Release notes](https://modrinth.com/mod/serialcraft/changelog)

SerialCraft is developed by **Leonardo Aliaga** as a learning project connecting Java modding, electronics, and telecommunications. It is open source under the [MIT license](https://github.com/LeonardoAliaga/SerialCraft/blob/main/LICENSE).
