---
title: Control Minecraft redstone with an Arduino potentiometer
description: Read an Arduino analog input and translate it into Minecraft redstone strength using SerialCraft.
---
# Use a potentiometer to control redstone

<!-- SC046_MEDIA:H02 -->

<!-- SC046_MEDIA:G02 -->

<!-- SC046_MEDIA:G07 -->

<!-- SC046_MEDIA:H05 -->

<!-- SC046_MEDIA:V02 -->

This is the reverse of the LED experiment: **Arduino A0 → USB serial → SerialCraft → redstone**.

Wire a low-voltage potentiometer with its outer terminals to **5 V** and **GND** of an Arduino Uno R3 and its wiper to **A0**. The Uno's ADC normally reads 0–1023; map it to the SerialCraft protocol's 0–255 range.

~~~cpp
int reading = analogRead(A0);
int value = map(reading, 0, 1023, 0, 255);
Serial.print("pot_val:");
Serial.println(constrain(value, 0, 255));
~~~

This snippet illustrates the format only. In real firmware, **send the initial sample including zero, meaningful changes and a periodic snapshot** and limit the sample rate instead of transmitting as fast as `loop()` can run.

In Minecraft, open **Boards → Configuration**, set the **Channel** to `pot_val`, choose **Hardware → Minecraft** and **ANALOG**. Then **in the world** use Shift + right-click on a terminal to set a **red output** face. Connect redstone dust to that face. A value around 128 should give approximately 8 redstone strength; 255 should give 15.

If you see only on/off, check whether the block is DIGITAL. If nothing changes, verify the exact channel string, newline, serial baud rate and face direction.

Continue with the [official examples](/en/examples/) and [signal conversion reference](/en/protocol#_2-unified-0-255-scale).

An ESP32 typically uses 3.3 V GPIO levels; **never feed 5 V directly to an ESP32 GPIO**.
