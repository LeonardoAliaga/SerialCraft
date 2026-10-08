# Hardware I/O Module

The module connects one hardware channel to redstone. The name is a label; **Channel** must match the sketch exactly. Right-click the base to edit without connecting hardware. Saving waits for server validation and keeps the draft on errors. The server checks ownership, dimension, loaded chunks, distance (64 blocks) and valid values.

| Direction | Green input | Red output | Disabled |
| --- | --- | --- | --- |
| Hardware → Minecraft | Enables the received sample | Emits redstone | Unused |
| Minecraft → Hardware | Reads and combines redstone | Inactive | Unused |

Five terminals face north, south, east, west and down. The bottom terminal is on the board's top surface near its south edge, so it remains selectable while placed on a block. It communicates with the block below. There is no top port. Right-click a terminal toggles input/off; Shift + right-click toggles output/off. The editor cycles Off → Input → Output. Colors indicate configured roles; LEDs indicate direction and enabled state.

## Examples and logic

For **Hardware → Minecraft**, set `pot_val`, analog and one red output. `pot_val:128\n` emits level **8**; 0 and 255 produce 0 and 15. No green inputs means the sample passes directly. With inputs, their logic enables it. Closing the condition retains the received sample, so reopening does not require another message.

For **Minecraft → Hardware**, set `led_verde` and one green input. Digital sends 0 or 255. Analog redstone levels 0, 1, 7 and 15 send **0, 17, 119 and 255**. This direction never emits redstone. No inputs sends zero. Disabling sends zero; changing channel/direction, removing or unloading an output module requests zero on its previous channel when connected.

OR takes the maximum input. AND takes the minimum, including zero. XOR takes the maximum when an odd number of inputs are positive, otherwise zero. For example, 2/7/12 yields OR 12, AND 2, XOR 12; 2/7 yields XOR zero. Transmitting uses this magnitude; receiving uses it as a condition while retaining the hardware magnitude. Digital normalizes positive results to 15/255.

## Diagnostics and limits

RX is the last received value; TX is the last value forwarded to the owner's client, **without physical acknowledgement**. Read is the input combination; Out is emitted redstone. `-1` means no sample/send. Connection state is reported by the hardware-owning client.

Disconnecting invalidates RX and clears redstone. Silence alone is not a disconnect because the protocol has no mandatory heartbeat. Reload/restart requires a fresh RX sample. Changing dimension invalidates samples; only the owner's current dimension exchanges signals. Reconnecting resends TX, with an extra USB resend after two seconds for the bootloader. A firmware reset that leaves the transport open requires manual reconnection/resynchronization.

Channels use 1–32 ASCII letters, digits, `_`, `.` or `-`; `mc_` is reserved. Multiple receivers may share a channel. Give independent actuators distinct channels, including the generator. Inputs use bounded round-robin delivery at 40 lines/s, 64 channels, eight transitions per channel; output producers share paced bounded delivery. Overload can drop data; F7 shows RX/TX drops. Visualizer records RX before throttling and TX at transport write. Pulses shorter than a world tick are not guaranteed. Separate feedback circuits with repeaters.

The registered block/entity IDs remain `serialcraft:io_block`, and recipes/resources/sketch formats remain compatible. Legacy string and integer enum data load correctly. Old `redstoneOut` is ignored; invalid legacy channels remain visible but disable the module for review. AND/XOR now apply while transmitting, so review circuits relying on the old unconditional maximum. Internal configuration/list payloads use `v2` IDs and require updating client and server together. TCP remains plaintext for trusted local networks.

The updated Uno R3, ESP32 and Uno Q examples always send the first sensor sample (including zero), then changes and a snapshot each second. Legacy firmware that only sends changes remains supported, but restoring an RX module may require changing the sensor or resending its value. Configuration/removal safety zeros cancel queued commands for the previous channel and receive bounded priority. Ordinary falling edges remain FIFO.

Unknown persisted modes disable the module for review. The [test matrix](../io-test-matrix) and [audit report](../audit-0.4.6) are available in Spanish.
