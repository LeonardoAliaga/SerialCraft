---
title: Visualize sensor data and generate test signals with SerialCraft
description: Learn how the SerialCraft 0.4.6-beta Visualize tab displays signal traces, sensor readings and test waveforms.
---
# Visualize: a signal test bench inside Minecraft

The Laptop's **Visualize** tab in **0.4.6-beta** lets you inspect channels over time, compare incoming sensor samples and send test waveforms to supported hardware.

It is **not a high-frequency electronic oscilloscope**. It records application-level protocol messages, so the view is useful for understanding channel values, debugging sensor mappings and checking telemetry.

## The three views

- **Timeline:** compare the history of multiple channels.
- **Sensor:** inspect raw values and their relation to redstone levels.
- **Generator:** send a ramp, triangle, square, sine or step test pattern to a channel.

## RX, TX and proof of delivery

An **RX** sample may be recorded before client-to-server throttling. Seeing it on a plot does not prove the corresponding I/O Module received it.

A **TX** point represents a write to the transport, not physical acknowledgement from the board. For an LED, verify the physical output separately.

## Practical exercise

1. Load the [Uno R3 potentiometer example](/en/tutorials/potentiometer).
2. Connect the board, configure the channel `pot_val` and open **Visualize**.
3. Turn the potentiometer and compare values on the 0–255 cable scale with redstone's 0–15 steps.
4. Optionally activate a telemetry channel such as `mc_hunger` and compare traces.

The plot should not be interpreted as direct knowledge of what happened between actual received samples.

## Generate with care

Use a unique test channel. Never run a generator and a separate I/O Module against the **same physical actuator channel** at once.

Stopping a waveform attempts to send a safe resting value, but a sudden unplug or crash prevents any guarantee that the command arrived. Implement a device-side timeout/watchdog when keeping an output active would be unsafe.

Read the [protocol guide](/en/protocol) and [troubleshooting advice](/en/guide) for transport constraints.
