---
title: SerialCraft tutorials
description: Learn Minecraft redstone and physical electronics with Arduino and ESP32, step by step.
---
# Tutorials: Minecraft meets electronics

SerialCraft connects real-world microcontrollers to Minecraft Java. These guides explain what each side of that connection does, with concrete examples for the development **0.4.6-beta (test branch)** release.

## Start here

| Topic | Learn |
| --- | --- |
| [I/O Module: directions and channels](/en/tutorials/io-block) | INPUT/OUTPUT between hardware and game versus redstone connector directions |
| [Arduino LED tutorial](/en/tutorials/arduino-led) | Drive an LED from a Minecraft redstone lever |
| [Potentiometer tutorial](/en/tutorials/potentiometer) | Send an analog reading to Minecraft |
| [Visualizer and signal generator](/en/tutorials/visualizer) | Inspect RX/TX and waveforms |
| [Game telemetry](/en/tutorials/telemetry) | Send game states and events |
| [Testing the mod](/en/tutorials/testing) | JUnit, GameTests and CI |

See also the detailed [official examples](/en/examples/) with Uno R3, ESP32 and Uno Q sketches, and the [protocol reference](/en/protocol) for baud rates, TCP port and signal conversions.

## How to learn effectively

Begin with USB and one channel. Observe messages of the form `channel:value`. Move to Wi-Fi only when the first circuit works; a local TCP connection adds IP addressing, firewalls and authentication.

The published documentation describes expected behavior. These pages are educational procedures, not claims that particular hardware was tested in this session. If you find a reproducible mismatch, [open an issue](https://github.com/LeonardoAliaga/SerialCraft/issues).

## Safety

Use low-voltage breadboard experiments and always place a current-limiting resistor in series with a standard LED. Do not directly control household mains equipment from a microcontroller pin. The Wi-Fi transport is plaintext and intended only for trusted local networks.
