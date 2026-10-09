---
title: SerialCraft Hardware I/O Module — modes, signals and diagnostics
description: Learn the channel and connector directions in SerialCraft 0.4.6-beta, including OR AND XOR and RX/TX.
---
# I/O Module: connecting redstone and physical hardware

In **0.4.6-beta (test branch)**, the old Arduino IO Block is renamed the **Hardware I/O Module**. Its registry ID remains `serialcraft:io_block` to preserve existing worlds.

A module has a display name and a Channel such as `pot_val` or `led_verde`. The channel must exactly match a firmware message.

## Two different kinds of direction

<!-- SC046_MEDIA:G01 -->

<!-- SC046_MEDIA:G02 -->

<!-- SC046_MEDIA:G04 -->

| Data direction | Green input face | Red output face |
| --- | --- | --- |
| **Hardware → Minecraft** | Optional gate condition | Emits redstone |
| **Minecraft → Hardware** | Reads and combines redstone | Inactive in this mode |

For a sensor controlling Minecraft, configure a **red output face**. For a Minecraft lever controlling a physical LED, configure a **green input face**.

Right-click the base to open the editor without connected hardware. It now has **Configuration** (name, channel, enable state, data direction, signal and logic) and **Diagnostics** (signal values and connection state) sections. The **?** icons open contextual help. **The five connectors are configured by interacting with their terminals in the world, not in the editor.** The lower terminal is selected on the board's upper surface near its south edge but connects to the block below. There is no top port.

Right-click a terminal to toggle green input/off; Shift + right-click toggles red output/off. The editor has **no connector cycling buttons** in this version. Unsaved form changes remain while switching sections; Save waits for server confirmation, rejected changes remain editable, and closing with pending changes prompts for confirmation.

## Hardware → Minecraft example

<!-- SC046_MEDIA:G07 -->

Configure channel `pot_val`, Analog, and a red output face. The received message `pot_val:128\n` corresponds to redstone **8**. Zero produces 0; 255 produces 15.

With no green inputs, the sample passes directly. With green inputs, the gate decides whether the sample is allowed to power the output; closing a gate does not erase the last RX value within the same session.

## Minecraft → Hardware example

<!-- SC046_MEDIA:G03 -->

Configure channel `led_verde`, digital and a green input face. An active lever sends `led_verde:255\n`, whereas off sends `led_verde:0\n`. In Analog mode, strength 7 sends 119. Without inputs, the value is zero. The transmitting module does **not** energize its red faces.

## Gate behavior

<!-- SC046_MEDIA:G08 -->

<!-- SC046_MEDIA:G09 -->

<!-- SC046_MEDIA:G10 -->

- **OR:** maximum input strength.
- **AND:** minimum, including zeros.
- **XOR:** maximum strength only if an odd number of inputs is nonzero; otherwise zero.

For strengths 2, 7, 12: OR=12, AND=2, XOR=12. These are not bitwise operations on analog values.

## Diagnostics and reconnection

<!-- SC046_MEDIA:G05 -->

<!-- SC046_MEDIA:G06 -->

<!-- SC046_MEDIA:V03 -->

**RX** is the last hardware sample; **TX** is the latest value forwarded to the owner's client, **not proof that hardware received or acted on it**. **Read** is combined redstone input, **Processed** is the evaluated value, and **Out** is emitted redstone. The panel also reports connection state and RX age. Missing RX/TX values are shown as no sample/no send.

Disconnecting clears RX and world output. Reloading a module invalidates its previous RX sample, so 0.4.6 example firmware sends an initial sample including zero, subsequent changes and periodic snapshots. USB also resends stable TX after the bootloader startup interval.

For channel rules, queue limits, world compatibility and feedback prevention, see the [technical reference](/en/io-module) and [testing guide](/en/tutorials/testing).
