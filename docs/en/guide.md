# SerialCraft Guide - v0.4.3 (Beta)

Welcome to the official **SerialCraft** guide. Version 0.4.3 introduces a generational leap in how you interact with your hardware from the game, highlighting a new immersive user interface and expanded support for **real bidirectional communication**.

## About the Project and Philosophy

SerialCraft is an open-source project created by **Leonardo Aliaga** (Instagram: [@aliaga1924](https://instagram.com/aliaga1924)). It was born with the firm purpose of being a **learning project**, focused on exploring the synergy between software development (Java modding), physical hardware design, and telecommunications.

In this project, the use of Artificial Intelligence tools is done strategically. **The project does not depend entirely on AIs to exist.** The workflow consists of first establishing the pure structure and logic of the code; only once the architecture is defined are AI tools used to assist in executing and translating those ideas into functional code more quickly.

---

## What's New in v0.4.3

* **New Immersive and Didactic Graphical Interface (UI):** We have left behind complex configurations. The new GUI is modern and visually guides you step by step. You no longer need to have technical knowledge about what a COM port or baud rates are; the interface scans your system and allows you to connect to your hardware with just a few clicks.
* **Simultaneous Bidirectional Communication:** The IO Block is now capable of reading (physical sensors) and writing (activating LEDs/Motors) in real-time using a single channel.
* **Wireless (Wi-Fi) and USB Support:** You choose. Connect standard boards via USB cable or use microcontrollers with Wi-Fi (like the ESP32) to link them via the local network (TCP Sockets).

---

## Getting Started: Installation

1. Download and install [Fabric Loader](https://fabricmc.net/).
2. Download the `.jar` file for SerialCraft v0.4.3.
3. Place the `.jar` in your Minecraft `mods` folder (along with `Fabric API`).
4. Launch the game.

---

## Getting Started: Connection Setup

Interacting with your environment is easier than ever thanks to the new didactic UI. Just follow these steps according to your hardware:

### Option A: USB Serial Connection (Recommended)
Ideal for robust and fast-response platforms, like the **Qualcomm Arduino Uno Q**.

1. Connect your board to your computer's USB port.
2. Inside Minecraft, place a **Connector Block** on the ground and right-click on it.
3. The new immersive SerialCraft Interface will open. Go to the **Serial Connection** section.
4. Forget about guessing ports! The automatic scanning system will detect your hardware and show it to you in a friendly dropdown list.
5. Select your board and click **Connect**. The mod will automatically configure the default baud rate and a green indicator will confirm the link.

### Option B: Wireless Connection (Wi-Fi)
Perfect for home automation projects. In this mode, SerialCraft connects to your board using your local network IP.

1. Ensure your PC and your physical board are connected to the **same Wi-Fi network**.
2. In the Connector Block's graphical interface, select the **Wireless Network** tab.
3. Enter the local IP address of your board (e.g., `192.168.1.50`). The interface is didactic and will warn you if the format is incorrect.
4. Press **Connect**. For more technical details and code examples on how to configure your board to accept this connection, check the **[Protocol Reference](/en/protocol)** section.

---

## Your First Bidirectional Circuit

1. With the **Connector Block** active, place an **Arduino IO Block** next to it (or connect them with Redstone dust).
2. Right-click on the IO Block to access its menu.
3. In the **Target Data (Block ID)** field, write a unique name for this component, for example: `alarm` or `btn_1`.
4. Define its behavior:
    * **OUTPUT:** For Minecraft to send energy signals to your board.
    * **INPUT:** For your board to send sensor signals to Minecraft.
5. Upload the corresponding code to your microcontroller using our `KEY:VALUE` language detailed in the **[Protocol Reference](/en/protocol)**.