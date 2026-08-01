---
# Configuración de la portada estilo VitePress
layout: home

hero:
  name: "SerialCraft"
  text: "El Puente entre Mundos"
  tagline: Conecta tu Arduino a Minecraft Java y lleva la electrónica al modo Survival.
  actions:
    - theme: brand
      text: Empezar Guía
      link: /guide
    - theme: alt
      text: Ver en GitHub
      link: https://github.com/leonardoaliaga/serialcraft

features:
  - title: Hardware Real en Survival
    details: Sin comandos mágicos. Craftea la Laptop, construye tus circuitos y conéctalos usando recursos del juego.
  - title: Plug & Play
    details: Compatible con Arduino Uno R3, Uno Q, ESP32 y cualquier placa Serial. Conexión por USB o por Wi-Fi con emparejamiento por token.
  - title: Código como Redstone
    details: Controla la Redstone con sensores reales o activa LEDs físicos con eventos del juego.
  - title: Multi-Idioma Nativo
    details: El mod detecta automáticamente tu región. Disponible en Inglés y Español (con localizaciones para España, Argentina, México y Perú).
---

# ¿Qué es SerialCraft?

**SerialCraft** es un mod para **Minecraft (Fabric)** que rompe la cuarta pared, permitiendo una comunicación bidireccional en tiempo real entre el juego y dispositivos electrónicos externos.

### Sobre la Autoría y Filosofía del Proyecto
SerialCraft es un proyecto creado por **Leonardo Aliaga** (Instagram: [@aliaga1924](https://instagram.com/aliaga1924)). Nace con el propósito principal de ser un **proyecto de aprendizaje**, enfocado en explorar la intersección y sinergia entre el desarrollo de software (modding en Java mediante Fabric), el diseño de hardware físico y las telecomunicaciones (como estudiante de la Universidad Nacional Mayor de San Marcos).

### El Rol de la Inteligencia Artificial
En el desarrollo de SerialCraft se están empleando herramientas de IA de forma estratégica para optimizar tiempos. **El proyecto no depende totalmente de las IAs para existir.** El proceso de creación es meticuloso:
1. Primero, se establece la **estructura arquitectónica y la lógica de programación** mediante el razonamiento personal y el estudio profundo de la API de Fabric y las librerías de hardware.
2. Posteriormente, se utilizan las herramientas de IA para **ejecutar con mayor rapidez y traducir** esas ideas estructuradas en código funcional, acelerando la producción y permitiendo centrar la energía en la creatividad y la innovación técnica.

## ¿Qué puedes hacer con SerialCraft?
* **De Minecraft al Mundo Real:** Haz que una palanca en el juego encienda una lámpara de escritorio real o active un motor.
* **Del Mundo Real a Minecraft:** Usa botones, sensores de luz o de temperatura físicos para abrir puertas, activar trampas o generar señales de Redstone dentro de tu partida.
* **Aprendizaje y Prototipado:** Es una herramienta excelente para estudiantes de ingeniería, robótica y entusiastas del hardware para probar lógicas de circuitos en un entorno sandbox inmersivo.

### ¿Cómo funciona?
El mod utiliza protocolos seriales (USB) y sockets TCP (Wi-Fi) para abrir un canal directo entre el juego y tu hardware.
1.  **Entrada:** Tu placa envía datos (ej. sensor de luz) -> Minecraft los recibe y activa bloques de Redstone.
2.  **Salida:** Minecraft detecta energía en bloques IO -> Envía estados al hardware para activar actuadores físicos.

[¡Empieza tu primer circuito ahora!](/guide) · [Ver ejemplos listos para probar](/ejemplos/)

---

## 🌍 Comunidad y Licencia

SerialCraft es un proyecto de **Código Abierto** (Open Source).
Eres libre de estudiar el código, modificarlo o usarlo en tus modpacks.

* **Idiomas Soportados:** English (US), Español (España, Argentina, México, Perú).
* **¿Encontraste un error?** [Repórtalo en GitHub](https://github.com/leonardoaliaga/serialcraft/issues).