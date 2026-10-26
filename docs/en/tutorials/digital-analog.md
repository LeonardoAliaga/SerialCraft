---
title: Digital and analog signals between Minecraft and Arduino
description: Learn how SerialCraft converts redstone 0–15 to hardware values 0–255, and how digital signals differ from proportional readings and PWM.
---
# Digital and analog signals in SerialCraft

Minecraft redstone has **16 levels, from 0 to 15**. SerialCraft's hardware protocol uses **0–255 in both directions**. These ranges apply to **0.5.0-beta** on Minecraft 26.2.

Understanding the conversion helps you write compatible firmware and diagnose signals that appear stuck, dim or noisy.

## Digital: off or on

A module set to **Minecraft → Hardware** and **DIGITAL** sends zero when off and 255 when on. For a channel named `led_verde`, the messages are:

```text
led_verde:255
led_verde:0
```

Each message ends with a newline. In the other direction, any positive received value becomes full-strength redstone in DIGITAL mode. This is useful for buttons, switches and alarms.

## Analog: keep intermediate levels

<!-- SC046_MEDIA:I01 -->

ANALOG keeps intermediate numeric values. USB and TCP still carry digital text; Minecraft does not send an analog voltage through the cable.

```text
hardware value = redstone × 17
redstone ≈ hardware value / 17, rounded to the nearest level
```

| Redstone | Hardware value |
| ---: | ---: |
| 0 | 0 |
| 1 | 17 |
| 3 | 51 |
| 7 | 119 |
| 10 | 170 |
| 15 | 255 |

For example, an incoming value of 128 becomes redstone level 8 in ANALOG mode.

## PWM is not necessarily a continuous analog voltage

On an Arduino Uno R3, `analogWrite(9, 128)` normally produces **PWM**: rapid on/off pulses with approximately half the time spent on. This can control the apparent brightness of an LED, but it is not the same as a DAC output.

A potentiometer read with `analogRead(A0)` normally produces 0–1023 on an Uno R3. Map it to 0–255 before sending:

```cpp
int adc = analogRead(A0);
int wireValue = map(adc, 0, 1023, 0, 255);
Serial.print("pot_val:");
Serial.println(wireValue);
```

## Compare both modes

1. Configure an I/O Module as **Hardware → Minecraft**, channel `pot_val`.
2. Use the [potentiometer example](/en/tutorials/potentiometer).
3. Observe gradual redstone changes in ANALOG mode.
4. Change only the signal type to DIGITAL.
5. Compare the gradual reading with off/on output.

A lever has only two states; changing its module to ANALOG cannot create intermediate input levels.

## Common mistakes

- **A very dim LED:** old firmware may treat `1` as on; the current protocol sends `255` for digital on.
- **Readings jump between levels:** the sensor has more resolution than redstone and may also have electrical noise. Filter or add a small deadband in the firmware.
- **Direction and terminals are mixed up:** data direction and the green/red redstone connector roles are separate settings. See the [I/O Module guide](/en/tutorials/io-block).

Continue with the [protocol reference](/en/protocol) or [connection troubleshooting](/en/tutorials/troubleshooting).
