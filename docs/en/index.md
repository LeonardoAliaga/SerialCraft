---
# VitePress Home Page Configuration
layout: home

hero:
  name: "SerialCraft"
  text: "The Bridge Between Worlds"
  tagline: Connect your Arduino to Minecraft Java and bring electronics into Survival mode.
  actions:
    - theme: brand
      text: Start Guide
      link: /en/guide
    - theme: alt
      text: View on GitHub
      link: https://github.com/leonardoaliaga/serialcraft

features:
  - title: Real Hardware in Survival
    details: No magic commands. Craft the Laptop, build your circuits, and connect them using in-game resources.
  - title: Plug & Play
    details: Compatible with Arduino Uno R3, Uno Q, ESP32, and any Serial board. USB or Wi-Fi with token pairing.
  - title: Code as Redstone
    details: Control Redstone with real sensors or activate physical LEDs with in-game events.
  - title: Native Multi-Language
    details: The mod automatically detects your region. Available in English and Spanish (with localizations for Spain, Argentina, Mexico, and Peru).
---

# What is SerialCraft?

**SerialCraft** is a mod for **Minecraft (Fabric)** that breaks the fourth wall, allowing real-time bidirectional communication between the game and external electronic devices.

### About the Authorship and Project Philosophy
SerialCraft is a project created by **Leonardo Aliaga** (Instagram: [@aliaga1924](https://instagram.com/aliaga1924)). It was born with the main purpose of being a **learning project**, focused on exploring the intersection and synergy between software development (Java modding via Fabric), physical hardware design, and telecommunications (as a student at Universidad Nacional Mayor de San Marcos).

### The Role of Artificial Intelligence
In the development of SerialCraft, AI tools are employed strategically to optimize time. **The project does not rely entirely on AIs to exist.** The creation process is meticulous:
1. First, the **architectural structure and programming logic** are established through personal reasoning and deep study of the Fabric API and hardware libraries.
2. Subsequently, AI tools are used to **execute more quickly and translate** those structured ideas into functional code, accelerating production and allowing focus on creativity and technical innovation.

## What can you do with SerialCraft?
* **From Minecraft to the Real World:** Make an in-game lever turn on a real desk lamp or activate a motor.
* **From the Real World to Minecraft:** Use physical buttons, light sensors, or temperature sensors to open doors, trigger traps, or generate Redstone signals within your game.
* **Learning and Prototyping:** It is an excellent tool for engineering students, robotics enthusiasts, and hardware hobbyists to test circuit logic in an immersive sandbox environment.

### How does it work?
The mod uses serial protocols (USB) and TCP sockets (Wi-Fi) to open a direct channel between the game and your hardware.
1.  **Input:** Your board sends data (e.g., light sensor) -> Minecraft receives it and activates Redstone blocks.
2.  **Output:** Minecraft detects energy in IO Blocks -> Sends states to the hardware to activate physical actuators.

[Start your first circuit now!](/en/guide) · [See ready-to-run examples](/en/examples/)

---

## 🌍 Community and License

SerialCraft is an **Open Source** project.
You are free to study the code, modify it, or use it in your modpacks.

* **Supported Languages:** English (US), Spanish (Spain, Argentina, Mexico, Peru).
* **Found a bug?** [Report it on GitHub](https://github.com/leonardoaliaga/serialcraft/issues).