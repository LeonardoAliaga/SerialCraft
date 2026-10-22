# 🔌 SerialCraft — Arduino y ESP32 con Minecraft Fabric

### Lleva tu hardware del mundo real a tu mundo Survival.

- [Documentación](https://serialcraft.xyz) · [Descargar en Modrinth](https://modrinth.com/mod/serialcraft) · [Novedades de 0.4.6-beta](changelogs/0.4.6-beta.es.md)

[![Fabric](https://img.shields.io/badge/Loader-Fabric-bea67e?style=for-the-badge)](https://fabricmc.net/)
[![Minecraft](https://img.shields.io/badge/Minecraft-26.2-2d8528?style=for-the-badge)](https://www.minecraft.net/)
[![Java](https://img.shields.io/badge/Java-25-orange?style=for-the-badge)](https://adoptium.net/)
[![Versión](https://img.shields.io/badge/Versión-0.4.6--beta-c96f4a?style=for-the-badge)](https://github.com/LeonardoAliaga/serialcraft/releases)
[![Licencia](https://img.shields.io/badge/Licencia-MIT-8a9a7b?style=for-the-badge)](LICENSE)

**SerialCraft** es un mod experimental y educativo que rompe la cuarta pared: permite comunicación bidireccional en tiempo real entre un **Arduino** (o cualquier dispositivo serial) y **Minecraft**, escrito en Java sobre Fabric.

No es solo una demostración técnica. Está diseñado para encajar de forma natural en una partida **Survival**, convirtiendo la electrónica externa en una parte funcional de tu progresión.

---

## 🛠️ Características principales

### 💻 La Laptop
El cerebro de la operación. En SerialCraft la conexión no aparece por arte de magia: hay que construirla.

* **Integración en Survival:** la Laptop es un bloque crafteable. Tienes que reunir los recursos, así que encaja en el equilibrio de una partida de supervivencia.
* **La interfaz:** al hacer clic derecho se abre una interfaz con cuatro pestañas — Inicio, Placas, Eventos y Visualizar — más la bienvenida de conexión y la consola de Inicio. El escaneo de puertos es automático: no necesitas saber qué es un puerto COM.

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
* **Wi-Fi (TCP):** sin cables, con emparejamiento por token y opción de recordar la placa mediante desafío y respuesta HMAC. Ideal para ESP32 y para placas con Linux embebido como el Arduino Uno Q.
* **Identificación de placas:** reconoce descriptores USB y anuncios del firmware; un adaptador genérico no se confunde con un modelo confirmado.

### 📊 Eventos y Visualizar
* **Eventos:** envía vida, hambre, hora del mundo y sucesos del juego a tu placa mediante canales `mc_*`.
* **Visualizar:** observa mensajes RX/TX en una línea de tiempo, compara sensores con su conversión a redstone y prueba un actuador con el generador de ondas en un canal dedicado.
* La Laptop mantiene el mundo en marcha mientras usas su interfaz, incluso en un jugador.

### 🌍 Cuatro localizaciones
Inglés y español, con localizaciones reales para España, México y Argentina (con voseo). El juego utiliza la localización elegida por el jugador.

---

## 📦 Bloques y recetas

![Modelos](https://cdn.modrinth.com/data/cached_images/4aea9efd4686b3adf8ea97550df78b0240142c49.png)

### 1. La Laptop (Bloque Conector)
El cerebro de la operación: gestiona la conexión con tu dispositivo del mundo real.

* **Uso:** clic derecho para abrir la interfaz. En la bienvenida, conecta tu placa por **USB** (115200 baudios por defecto), o inicia el **servidor Wi-Fi** y copia la IP, el puerto y el token que aparecen en pantalla. Si el servidor no puede iniciarse, verás el error y podrás volver a intentarlo.
* **Ajustes USB:** desde **Inicio**, elige los **baudios** y pulsa **Aplicar y reconectar**. Deben coincidir con `Serial.begin(...)` o su equivalente en el firmware de la placa para que los datos se entiendan.

#### Receta
![Receta de la Laptop](https://cdn.modrinth.com/data/cached_images/0796082c1617ad4cd6d1c4f3beb989fc89878192_0.webp)

---

### 2. El Módulo de E/S
El puente real: es lo que traduce entre la redstone y tu placa.

* **Terminales en el mundo:** clic derecho alterna entrada verde/desactivado; Shift + clic derecho alterna salida roja/desactivado. Hay cinco terminales, incluido el inferior, que se selecciona desde arriba cerca del borde sur de la placa.
* **Configuración:** clic derecho sobre la base para definir el **Canal** (por ejemplo `led_verde` o `sensor_a`), la dirección **Minecraft → Hardware** o **Hardware → Minecraft**, y la señal analógica o digital. El canal debe coincidir con el sketch; `mc_` está reservado.
* **Diagnóstico:** consulta RX, TX, lecturas, salida y conexión. El editor ofrece ayuda con **?** y confirma el guardado con el servidor.
* **Compuertas lógicas:** OR combina por máximo, AND por mínimo y XOR por paridad. [Guía del Módulo de E/S](docs/io-module.md).

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

* **Minecraft:** 26.2
* **Java:** 25 o superior
* **Loader:** Fabric 0.19.3 o superior
* **Dependencia:** [Fabric API](https://modrinth.com/mod/fabric-api) para Minecraft 26.2
* **Hardware:** Arduino Uno R3, Uno Q, ESP32, o cualquier microcontrolador con comunicación serial o TCP.

### Primeros pasos

1. Instala el mod y la Fabric API en tu carpeta `mods`. En multijugador, actualiza SerialCraft en cliente y servidor juntos.
2. Conecta tu placa por USB (o ponla en la misma red Wi-Fi que tu PC).
3. Entra a tu mundo y **craftea la Laptop** y el **Bloque Conector** con las recetas incluidas.
4. Coloca el Conector y haz clic derecho, o abre la Laptop. Selecciona tu puerto USB o la tarjeta Wi-Fi en la bienvenida de conexión.
5. Coloca un **Módulo de E/S**, define su **Canal** y dirección, y configura los terminales sobre el bloque: entrada verde para leer redstone, salida roja para emitirla.
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
* **Escala:** hay una prueba funcional con 64 módulos. La capacidad sostenible requiere medir MSPT y tráfico; la lista tiene scroll y los presupuestos de comunicación se comparten por jugador. Ver la [matriz de verificación](docs/io-test-matrix.md).
* **Señales:** las colas son acotadas. TX indica una escritura al transporte, no una confirmación de que el actuador físico ejecutó la orden; no se garantizan todos los pulsos bajo sobrecarga.
* **Seguridad:** el canal Wi-Fi va en texto claro. El token evita el acceso casual dentro de tu red, pero no es cifrado. Sirve para una red doméstica o de aula; **no redirijas el puerto en tu router**.

---

## 📚 Documentación

* [Guía de instalación y uso](docs/guide.md)
* [Referencia de protocolo](docs/protocol.md)
* [Ejemplos y rutina de prueba](docs/ejemplos/)
* [Notas de versión en GitHub Releases](https://github.com/LeonardoAliaga/serialcraft/releases)

### Publicación automática

Al integrar una versión nueva en `main`, GitHub Actions compila, ejecuta las pruebas unitarias y los GameTests, y publica el mismo JAR en GitHub Releases y Modrinth. Para preparar una versión, actualiza `mod_version` en `gradle.properties` y añade `changelogs/<mod_version>.es.md` para GitHub y `changelogs/<mod_version>.en.md` para Modrinth.

La descripción inglesa de Modrinth se mantiene en [MODRINTH.md](MODRINTH.md) y se sincroniza al publicar desde `main`, también en cambios que conservan la versión. El secreto `MODRINTH_TOKEN` necesita **Read versions** y **Create versions**; para actualizar la descripción también necesita **Write projects** (`PROJECT_WRITE`). Si falla solo esa sincronización, el workflow muestra una advertencia y conserva la publicación.

Las versiones `alpha`/`beta` se marcan como prerelease en GitHub; una versión ya publicada se conserva sin duplicarla. Si una publicación queda a medias, usa **Re-run failed jobs** en la ejecución original para conservar el mismo JAR. El workflow también permite ejecución manual en `main`; si recompila un JAR distinto al de una publicación parcial, detiene el reintento para evitar mezclar archivos.

---

## 🤝 Sobre el proyecto

SerialCraft lo desarrolla **Leonardo Aliaga** ([@aliaga1924](https://instagram.com/aliaga1924)) como proyecto de aprendizaje, explorando la intersección entre el modding en Java, el hardware físico y las telecomunicaciones.

En el desarrollo se usan herramientas de IA de forma estratégica. **El proyecto no depende de ellas para existir:** primero se establece la arquitectura y la lógica del sistema mediante razonamiento propio y el estudio de la API de Fabric; después la IA ayuda a ejecutar y refinar esas ideas más rápido.

¿Encontraste un error? [Repórtalo](https://github.com/leonardoaliaga/serialcraft/issues).

---

## 📜 Licencia

Proyecto de código abierto. Siéntete libre de estudiar el código para aprender cómo Java maneja la comunicación serial, modificarlo o incluirlo en tus modpacks.
