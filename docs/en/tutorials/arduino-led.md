---
title: Arduino LED controlled by Minecraft redstone
description: Follow the SerialCraft example to turn an Arduino LED on and off from Minecraft.
---
# Drive a physical LED from Minecraft

This beginner experiment demonstrates the route **redstone → SerialCraft I/O Module → USB serial → Arduino Uno R3 → LED**.

## Hardware

<!-- SC046_MEDIA:H02 -->

You need an Uno R3, one ordinary LED, a 220–330 Ω resistor, breadboard, jumper wires and a USB data cable.

Connect Uno **D9 → resistor → LED anode**, then the LED cathode to **GND**. Do not connect mains-powered devices or motor loads directly to D9.

## Configure the game

<!-- SC046_MEDIA:G03 -->

<!-- SC046_MEDIA:H03 -->

<!-- SC046_MEDIA:H04 -->

<!-- SC046_MEDIA:V01 -->

1. Install the version-compatible Minecraft **26.2**, **Java 25**, Fabric Loader, Fabric API and SerialCraft.
2. Connect the Uno at **115200 baud**.
3. Place an I/O Module, open **Boards → Configuration**, set the **Channel** to `led_verde`, and choose **Minecraft → Hardware**.
4. **In the world**, right-click one of the module's terminals to set it as a **green input** face and connect a lever or redstone line. The Diagnostics tab does not edit terminals.
5. Choose DIGITAL for on/off; ANALOG for values proportional to the received redstone strength.

With full redstone, the expected message is `led_verde:255`; with no redstone, `led_verde:0`. An Uno sketch can read complete lines and apply the value with `analogWrite(9, value)`.

For working firmware and the entire connection procedure, use the maintained [Uno R3 example](/en/examples/#_2-arduino-uno-r3-usb). Reading only part of a line or using a different baud rate causes confusing failures.

## Debug it in layers

First verify the serial connection, then the I/O Module channel and mode, then the redstone face, and finally LED wiring and polarity. Avoid opening Arduino IDE's serial monitor while SerialCraft is already holding the same USB port.

**Important:** an Arduino can retain the last LED level after a hard disconnect. Hardware-side watchdog logic is appropriate when an output must return to a safe state.
