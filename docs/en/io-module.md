# Hardware I/O Module

The module connects one hardware channel to redstone. The name is a label; **Channel** must match the sketch exactly. Right-click the base to edit without connecting hardware. The editor now has **Configuration** and **Diagnostics** tabs, contextual **?** help, and a draft preserved while switching tabs. Save is enabled for unsaved changes and waits for server approval; rejection retains the draft, while closing unsaved changes asks for confirmation. The server checks ownership, dimension, loaded chunks, distance (64 blocks) and valid values. Connector roles are edited directly on the block in the world.

| Direction | Green input | Red output | Disabled |
| --- | --- | --- | --- |
| Hardware → Minecraft | Enables the received sample | Emits redstone | Unused |
| Minecraft → Hardware | Reads and combines redstone | Inactive | Unused |

Five terminals face north, south, east, west and down. The bottom terminal is on the board's top surface near its south edge, so it remains selectable while placed on a block. It communicates with the block below. There is no top port. Right-click a terminal toggles input/off; Shift + right-click toggles output/off. The current editor does **not** provide a connector selector. Colors indicate configured roles; LEDs indicate direction and enabled state.

## Mod settings

The top-right gear opens **Settings**, including before connecting hardware. **On connection** and **on normal disconnection** are independent: send zero, send a custom integer from 0 to 255, or restore the last saved value on connection / send nothing on disconnection. These settings apply to Minecraft → hardware I/O channels.

Each active module initializes its channel when it first requests output; old channels without an active module are not replayed. USB boot resends retain the initial value until the world's input changes, then normal control resumes. Startup changes apply at the next connection.

Restore uses the last successful normal write, scoped by world/server, dimension, stable device identity and channel. Missing values or an unavailable stable ID fall back to zero. Final closure writes never replace saved normal values. COM numbers and IP addresses are not treated as stable device identities.

Normal panel disconnects, world exits and game shutdown cancel pending output and write final values before closing USB/Wi-Fi. Crashes and unplugged cables cannot guarantee delivery. Explicit generator stops and module removal/disable/reconfiguration retain their existing rules. Event telemetry (`mc_*`) and hardware → Minecraft input are unchanged.

Preferences live in `serialcraft.json`; bounded last-write history is saved atomically in `serialcraft-output-values.json`. Custom fields validate their range and leaving warns before discarding unsaved changes.

## Examples and logic

For **Hardware → Minecraft**, set `pot_val`, analog and one red output. `pot_val:128\n` emits level **8**; 0 and 255 produce 0 and 15. No green inputs means the sample passes directly. With inputs, their logic enables it. Closing the condition retains the received sample, so reopening does not require another message.

For **Minecraft → Hardware**, set `led_verde` and one green input. Digital sends 0 or 255. Analog redstone levels 0, 1, 7 and 15 send **0, 17, 119 and 255**. This direction never emits redstone. No inputs sends zero. Disabling sends zero; changing channel/direction, removing or unloading an output module requests zero on its previous channel when connected.

OR takes the maximum input. AND takes the minimum, including zero. XOR takes the maximum when an odd number of inputs are positive, otherwise zero. For example, 2/7/12 yields OR 12, AND 2, XOR 12; 2/7 yields XOR zero. Transmitting uses this magnitude; receiving uses it as a condition while retaining the hardware magnitude. Digital normalizes positive results to 15/255.

## Diagnostics and limits

RX is the last received value; TX is the last value forwarded to the owner's client, **without physical acknowledgement**. Read is the combined input; Processed is the evaluated value; Out is emitted redstone. The editor also displays the reported connection and RX sample age, with localized no-sample/no-send labels for missing data (internally `-1`). Connection state is reported by the hardware-owning client.

Disconnecting invalidates RX and clears redstone. Silence alone is not a disconnect because the protocol has no mandatory heartbeat. Reload/restart requires a fresh RX sample. Changing dimension invalidates samples; only the owner's current dimension exchanges signals. Reconnecting resends TX, with an extra USB resend after two seconds for the bootloader. A firmware reset that leaves the transport open requires manual reconnection/resynchronization.

Channels use 1–32 ASCII letters, digits, `_`, `.` or `-`; `mc_` is reserved. Multiple receivers may share a channel. Give independent actuators distinct channels, including the generator. Inputs use bounded round-robin delivery at 40 lines/s, 64 channels, eight transitions per channel; output producers share paced bounded delivery. Overload can drop data; F7 shows RX/TX drops. Visualizer records RX before throttling and TX at transport write. Pulses shorter than a world tick are not guaranteed. Separate feedback circuits with repeaters.

The registered block/entity IDs remain `serialcraft:io_block`, and recipes/resources/sketch formats remain compatible. Legacy string and integer enum data load correctly. Old `redstoneOut` is ignored; invalid legacy channels remain visible but disable the module for review. AND/XOR now apply while transmitting, so review circuits relying on the old unconditional maximum. Internal configuration/list payloads use `v2` IDs and require updating client and server together. TCP remains plaintext for trusted local networks.

The updated Uno R3, ESP32 and Uno Q examples always send the first sensor sample (including zero), then changes and a snapshot each second. Legacy firmware that only sends changes remains supported, but restoring an RX module may require changing the sensor or resending its value. Configuration/removal safety zeros cancel queued commands for the previous channel and receive bounded priority. Ordinary falling edges remain FIFO.

Unknown persisted modes disable the module for review. The [test matrix](../io-test-matrix) and [audit report](../audit-0.4.6) are available in Spanish.
