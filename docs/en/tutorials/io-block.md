---
title: SerialCraft Hardware I/O Module — modes, signals and diagnostics
description: Learn the channel and connector directions in SerialCraft 0.4.6-beta, including OR AND XOR and RX/TX.
---
# I/O Module: connecting redstone and physical hardware

In **0.4.6-beta (test branch)**, the old Arduino IO Block is renamed the **Hardware I/O Module**. Its registry ID remains `serialcraft:io_block` to preserve existing worlds.

A module has a display name and a Channel such as `pot_val` or `led_verde`. The channel must exactly match a firmware message.

## Two different kinds of direction

| Data direction | Green input face | Red output face |
| --- | --- | --- |
| **Hardware → Minecraft** | Optional gate condition | Emits redstone |
| **Minecraft → Hardware** | Reads and combines redstone | Inactive in this mode |

For a sensor controlling Minecraft, configure a **red output face**. For a Minecraft lever controlling a physical LED, configure a **green input face**.

Right-click the base to edit without hardware connected. The **Connectors and diagnostics** screen shows five terminals: north, south, east, west, and down. The bottom terminal is selected near the south edge on the upper surface but communicates with the block below. There is no top port.

Right-click a terminal to toggle green input/off; Shift + right-click toggles red output/off. In the editor, cycle Off → Input → Output.

## Hardware → Minecraft example

Configure channel `pot_val`, Analog, and a red output face. The received message `pot_val:128\n` corresponds to redstone **8**. Zero produces 0; 255 produces 15.

With no green inputs, the sample passes directly. With green inputs, the gate decides whether the sample is allowed to power the output; closing a gate does not erase the last RX value within the same session.

## Minecraft → Hardware example

Configure channel `led_verde`, digital and a green input face. An active lever sends `led_verde:255\n`, whereas off sends `led_verde:0\n`. In Analog mode, strength 7 sends 119. Without inputs, the value is zero. The transmitting module does **not** energize its red faces.

## Gate behavior

- **OR:** maximum input strength.
- **AND:** minimum, including zeros.
- **XOR:** maximum strength only if an odd number of inputs is nonzero; otherwise zero.

For strengths 2, 7, 12: OR=12, AND=2, XOR=12. These are not bitwise operations on analog values.

## Diagnostics and reconnection

**RX** is last hardware sample. **TX** is the latest value forwarded to the owner's client, **not proof that hardware received or acted on it**. Read denotes combined inputs; Out denotes world redstone output. A missing value may display `-1`.

Disconnecting clears RX and world output. Reloading a module invalidates its previous RX sample, so 0.4.6 example firmware sends an initial sample including zero, subsequent changes and periodic snapshots. USB also resends stable TX after the bootloader startup interval.

For channel rules, queue limits, world compatibility and feedback prevention, see the [technical reference](/en/io-module) and [testing guide](/en/tutorials/testing).
