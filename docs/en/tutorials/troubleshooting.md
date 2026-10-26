---
title: Troubleshoot SerialCraft USB, Wi-Fi and redstone connections
description: Diagnose SerialCraft connections, Arduino and ESP32 firmware, channel names, signal direction and redstone one layer at a time.
---
# Troubleshoot SerialCraft one layer at a time

This guide follows **0.5.0-beta**. When a circuit does not work, check **hardware, transport, protocol, routing and redstone** separately. Changing several layers at once makes it harder to identify the cause.

## Five checks before changing your sketch

| Layer | Question | Simple check |
| --- | --- | --- |
| Hardware | Is the board running the program? | Startup LED or serial diagnostics |
| Transport | Is USB or TCP connected? | Correct port and connection status |
| Protocol | Are complete lines being sent? | `channel:value\n` |
| Routing | Does the module use the same channel? | Exact **Channel** and data direction |
| Redstone | Is the correct face reading or emitting? | Lever or lamp next to the configured terminal |

## USB appears but no useful data arrives

Use a data-capable USB cable. Close Arduino IDE's Serial Monitor if it holds the port open, and check operating-system permissions where applicable.

The firmware's `Serial.begin(115200)` must match **115200 baud** in SerialCraft. A mismatch can produce unreadable characters or no useful communication. To change the speed, select it in **Home** and use **Apply and reconnect**.

## A channel does not respond

The expected message is a channel, a colon, an integer and a newline:

```text
pot_val:128
```

`Serial.print()` without a final newline can leave the receiver waiting. Use `Serial.println()` to finish the line.

Channel names are case-sensitive: `pot_val`, `Pot_val` and `pot-val` are different. The `mc_` prefix is reserved for game telemetry and identification, so use another name for an I/O Module.

- **Hardware → Minecraft** receives the board's values and emits redstone.
- **Minecraft → Hardware** reads redstone and sends values to the board.

The [I/O Module guide](/en/tutorials/io-block) explains data direction and connector roles.

## Values arrive but redstone stays off

Configure a **red output terminal** toward the target circuit. Selecting Hardware → Minecraft does not automatically configure every face.

Use ANALOG for intermediate levels and DIGITAL for an on/off signal. Inspect RX, the logic result and emitted redstone in the module's **Diagnostics** section.

## Wi-Fi disconnects immediately

Check that the board reaches the computer's local IP and TCP port **25585**. Then verify the pairing token and the newline after the first message.

Keep the token out of screenshots and issue reports. The TCP transport is intended for a trusted local network; do not expose it to the internet.

## Some sensor samples disappear

Do not send a line on every unrestricted loop iteration. Send changes, add a small deadband for noisy sensors, and limit the firmware's sending rate. Bounded queues and rate limits can discard excess traffic.

A redstone control protocol does not replace a dedicated high-frequency data-acquisition system when every sample must be recorded.

## Before reporting an issue

Include the Minecraft, Fabric and SerialCraft versions; board model and USB/Wi-Fi transport; module direction and channel; the expected and observed messages without secrets; and which checks above succeeded.

[Open a reproducible issue](https://github.com/LeonardoAliaga/SerialCraft/issues) and compare your sketch with the [official board examples](/en/examples/).
