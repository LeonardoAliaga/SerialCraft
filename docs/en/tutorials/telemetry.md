---
title: Minecraft game telemetry to Arduino and ESP32
description: Understand how SerialCraft sends health, hunger, time and game events to hardware over reserved mc_ channels.
---
# Game telemetry: Minecraft data on physical hardware

**SerialCraft 0.4.6-beta** can send game state and events to a connected device using reserved channels beginning with `mc_`. They are independent of a specific I/O Module and can be switched on in the Laptop **Events** tab.

~~~text
mc_health:18
mc_hunger:14
mc_weather:1
~~~

These are illustrative protocol messages, not actual recorded measurements.

| Channel | Meaning | Range |
| --- | --- | --- |
| `mc_health` | Rounded health points | 0–1024 |
| `mc_hunger` | Current food level | 0–20 |
| `mc_time` | Day time in ticks | 0–23999 |
| `mc_weather` | Clear, rain or thunderstorm | 0–2 |
| `mc_damage` | Damage event | 1–32767 |
| `mc_death` | Death event | 1 |

The [protocol reference](/en/protocol) lists every supported channel, including air, saturation and experience level.

## Try it

<!-- SC046_MEDIA:G13 -->

<!-- SC046_MEDIA:G14 -->

<!-- SC046_MEDIA:V06 -->

1. Connect Arduino or ESP32 using USB or trusted local TCP.
2. Open the Laptop and enable a channel under **Events**.
3. Observe `mc_` messages in the console or Visualize tab.
4. Parse complete lines in your firmware and ignore unknown channel names.

Telemetry is **disabled by default** to avoid surprising older firmware.

## States versus events

States such as hunger can be safely replaced by the latest measurement and periodically resent. Events such as death represent a particular occurrence and should not be replayed as if they happened again.

There is **no guaranteed physical acknowledgement** for events. Lost connections can lose events, and sending multiple channels shares capacity with I/O commands and waveforms.

Use bounded firmware buffers and never use the reserved `mc_` prefix for your own I/O Module channel. Wi-Fi messages are not encrypted. See [Visualize](/en/tutorials/visualizer) for observing incoming and outgoing signals.
