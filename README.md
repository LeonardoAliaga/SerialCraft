# 🔌 SerialCraft: el puente entre mundos

### Lleva tu hardware del mundo real a tu mundo Survival.

- [Documentación](https://github.com/leonardoaliaga/serialcraft)

[![Fabric](https://img.shields.io/badge/Loader-Fabric-bea67e?style=for-the-badge)](https://fabricmc.net/)
[![Minecraft](https://img.shields.io/badge/Minecraft-1.21.11-2d8528?style=for-the-badge)](https://www.minecraft.net/)
[![Versión](https://img.shields.io/badge/Versión-0.4.3--beta-c96f4a?style=for-the-badge)](RELEASE-0.4.3.md)
[![Licencia](https://img.shields.io/badge/Licencia-CC0_1.0-8a9a7b?style=for-the-badge)](LICENSE)

**SerialCraft** es un mod experimental y educativo que rompe la cuarta pared: permite comunicación bidireccional en tiempo real entre un **Arduino** (o cualquier dispositivo serial) y **Minecraft**, escrito en Java sobre Fabric.

No es solo una demostración técnica. Está diseñado para encajar de forma natural en una partida **Survival**, convirtiendo la electrónica externa en una parte funcional de tu progresión.

---

## 🛠️ Características principales

### 💻 La Laptop
El cerebro de la operación. En SerialCraft la conexión no aparece por arte de magia: hay que construirla.

* **Integración en Survival:** la Laptop es un bloque crafteable. Tienes que reunir los recursos, así que encaja en el equilibrio de una partida de supervivencia.
* **La interfaz:** al hacer clic derecho se abre una interfaz con cuatro páginas — Inicio, Conexión, Placas y Consola. El escaneo de puertos es automático: no necesitas saber qué es un puerto COM.

### ⚡ Físico ➔ digital (entrada)
Controla tu mundo con componentes reales.

* Conecta **botones, sensores o interruptores** físicos a tu placa.
* El mod lee esas señales y las traduce en redstone dentro del juego.
* *Ejemplo:* un potenciómetro real regula la intensidad de una línea de redstone; un interruptor de tu escritorio abre la puerta de hierro de tu base.

### 🔄 Digital ➔ físico (salida)
Y en el sentido contrario, ya funcionando:

* Un Bloque IO en modo salida detecta la redstone que recibe y la envía a tu placa.
* *Ejemplo:* la potencia de un comparador regula el brillo de un LED por PWM; una palanca en el juego enciende un zumbador en tu mesa.

### 📶 USB o Wi-Fi
* **USB Serial:** cable directo, la opción más simple y de menor latencia.
* **Wi-Fi (TCP):** sin cables, con emparejamiento por token. Ideal para ESP32 y para placas con Linux embebido como el Arduino Uno Q.

### 🌍 Cinco idiomas
Inglés y español, con localizaciones reales para España, México, Perú y Argentina (con voseo). El mod detecta tu región automáticamente.

---

## 📦 Bloques y recetas

![Modelos](https://cdn.modrinth.com/data/cached_images/4aea9efd4686b3adf8ea97550df78b0240142c49.png)

### 1. La Laptop (Bloque Conector)
El cerebro de la operación: gestiona la conexión con tu dispositivo del mundo real.

* **Uso:** clic derecho para abrir la interfaz. Elige tu **puerto** y los **baudios** (deben coincidir con los del sketch; por defecto 115200), o inicia el **servidor Wi-Fi** y copia la IP, el puerto y el token que aparecen en pantalla.
* **Ajustes:** puedes regular la velocidad de lectura para adaptarla a placas que envían mucho tráfico.

![Interfaz de la Laptop](https://cdn.modrinth.com/data/cached_images/2a06adbdf536890ecd23076067815f49d6f7f704_0.webp)

#### Receta
![Receta de la Laptop](https://cdn.modrinth.com/data/cached_images/0796082c1617ad4cd6d1c4f3beb989fc89878192_0.webp)

---

### 2. El Bloque IO (Arduino IO)
El puente real: es lo que traduce entre la redstone y tu placa.

* **Sistema de pines:**
    * **Clic derecho** sobre un conector lateral: lo pone en **IN (verde)**. Lee redstone.
    * **Shift + clic derecho** sobre un conector lateral: lo pone en **OUT (rojo)**. Emite redstone.
* **Configuración:** clic derecho sobre el bloque para definir el `Target Data` (por ejemplo `led_verde` o `sensor_a`), el modo (entrada o salida) y el tipo de señal (analógica o digital).
* **Compuertas lógicas:** cada bloque lleva OR, AND y XOR internas que deciden cuándo se considera activo si recibe energía por varios lados.

![Interfaz del Bloque IO](https://cdn.modrinth.com/data/cached_images/151ab59e0b022613135ae530b89378e60e3b8231_0.webp)
![Modelo del Bloque IO](https://cdn.modrinth.com/data/cached_images/c20ed9d2c7fe7a3a5a28704394c6a64a6cc2839b.png)

#### Receta
![Receta del Bloque IO](https://cdn.modrinth.com/data/cached_images/07d93e2ac7612058dcfb25f2161efcc182ec78a8_0.webp)

---

## 🧠 El concepto: lógica y circuitos

Este proyecto nació de un viaje personal: unir el juego que definió mi infancia con mi pasión por la electrónica.

Al desarrollar **SerialCraft** el objetivo fue demostrar que la lógica de la programación es sorprendentemente parecida al diseño de circuitos físicos:

* **Código como cableado:** la lógica condicional (`if/else`) escrita en Java se comporta igual que un interruptor o una compuerta lógica en una protoboard. Los Bloques IO llevan compuertas OR, AND y XOR reales.
* **Datos como corriente:** el flujo de información por el puerto serie imita el flujo de la corriente; si la lógica no está "cerrada", la señal no llega a su destino.

Este mod es un tributo a esa conexión: usar código para cerrar el circuito entre el mundo de bloques y el mundo físico.

---

## ⚙️ Instalación y uso

### Requisitos

* **Minecraft:** 1.21.11
* **Loader:** Fabric
* **Dependencia:** [Fabric API](https://modrinth.com/mod/fabric-api)
* **Hardware:** Arduino Uno R3, Uno Q, ESP32, o cualquier microcontrolador con comunicación serial o TCP.

### Primeros pasos

1. Instala el mod y la Fabric API en tu carpeta `mods`.
2. Conecta tu placa por USB (o ponla en la misma red Wi-Fi que tu PC).
3. Entra a tu mundo y **craftea la Laptop** (receta visible con REI/JEI).
4. Colócala, haz clic derecho y selecciona tu puerto en la pestaña Conexión.
5. Coloca un **Bloque IO**, dale un identificador en *Target Data* y elige su modo: entrada o salida.
6. Carga el sketch en tu placa. Hay [ejemplos listos para probar](docs/ejemplos/) para Uno R3, ESP32 y Uno Q.

### El protocolo en una línea

```text
IDENTIFICADOR:VALOR\n        →  pot_val:200
```

Valores de **0 a 255 en ambos sentidos**. El mod se encarga de convertir a la escala 0-15 de la redstone. Los detalles están en la [referencia de protocolo](docs/protocol.md).

---

## 🧩 Alcance y límites

Merece la pena decirlo antes de que lo descubras montando algo grande:

* **Multijugador:** funciona en un jugador, en LAN y en servidor dedicado. Cada jugador controla **sus propias placas desde su propio PC**, porque el puerto serie vive en el cliente. No existe un modo en el que el servidor sea dueño del hardware, y no es un olvido: es una decisión de arquitectura.
* **Escala:** entre 10 y 50 placas por jugador es razonable. El límite práctico lo pone la interfaz, no el servidor: la lista de placas todavía no tiene scroll.
* **Seguridad:** el canal Wi-Fi va en texto claro. El token evita el acceso casual dentro de tu red, pero no es cifrado. Sirve para una red doméstica o de aula; **no redirijas el puerto en tu router**.

---

## 📚 Documentación

* [Guía de instalación y uso](docs/guide.md)
* [Referencia de protocolo](docs/protocol.md)
* [Ejemplos y rutina de prueba](docs/ejemplos/)
* [Prompts para generar esquemas de conexión](docs/ejemplos/esquemas-ia.md)
* [Novedades de la v0.4.3](RELEASE-0.4.3.md)

---

## 🤝 Sobre el proyecto

SerialCraft lo desarrolla **Leonardo Aliaga** ([@aliaga1924](https://instagram.com/aliaga1924)) como proyecto de aprendizaje, explorando la intersección entre el modding en Java, el hardware físico y las telecomunicaciones.

En el desarrollo se usan herramientas de IA de forma estratégica. **El proyecto no depende de ellas para existir:** primero se establece la arquitectura y la lógica del sistema mediante razonamiento propio y el estudio de la API de Fabric; después la IA ayuda a ejecutar y refinar esas ideas más rápido.

¿Encontraste un error? [Repórtalo](https://github.com/leonardoaliaga/serialcraft/issues).

---

## 📜 Licencia

Proyecto de código abierto. Siéntete libre de estudiar el código para aprender cómo Java maneja la comunicación serial, modificarlo o incluirlo en tus modpacks.