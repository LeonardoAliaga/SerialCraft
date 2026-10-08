---
title: Módulo de E/S de SerialCraft
description: Configura las direcciones, los cinco conectores, las compuertas y el diagnóstico RX/TX del Hardware I/O Module.
---
# Módulo de E/S: puente entre redstone y hardware

En **SerialCraft 0.4.6-beta (rama test)**, el antiguo Bloque IO evoluciona a **Módulo de E/S**. Sus clases Java ahora se llaman `HardwareIOBlock` y `HardwareIOBlockEntity`, pero su identificador registrado sigue siendo `serialcraft:io_block` para conservar mundos existentes.

El módulo tiene un nombre para identificarlo y un **canal** como `pot_val` o `led_verde`. El canal debe coincidir exactamente con el mensaje del firmware.

## Dos tipos de dirección

<!-- SC046_MEDIA:G01 -->

<!-- SC046_MEDIA:G02 -->

| Dirección del módulo | Entrada verde | Salida roja |
| --- | --- | --- |
| **Hardware → Minecraft** | Condición lógica opcional | Emite redstone al mundo |
| **Minecraft → Hardware** | Lee y combina redstone | Inactiva; conserva configuración |

Una señal física que entra al juego requiere una **cara roja** hacia el circuito de redstone. Una palanca que controla el dispositivo físico requiere una **cara verde** que lea redstone.

El editor se abre con clic derecho sobre la base y funciona sin hardware conectado. Configura dirección, canal, señal digital o analógica, compuerta y terminales desde **Conectores y diagnóstico**.

## Conectores

<!-- SC046_MEDIA:G04 -->

<!-- SC046_MEDIA:G05 -->

Existen cinco: norte, sur, este, oeste y **abajo**. No hay conector superior. El terminal de abajo se selecciona desde la superficie de la placa, cerca del borde sur, pero transmite hacia el vecino inferior.

- Clic derecho en un terminal: entrada verde o desactivado.
- Shift + clic derecho: salida roja o desactivado.
- Editor: recorre desactivado → entrada → salida.

El color identifica configuración, **no una confirmación física** de que se haya procesado un mensaje.

## Hardware → Minecraft: potenciómetro

1. Canal `pot_val`, dirección **Hardware → Minecraft**, Analógico.
2. Configura una salida roja hacia polvo de redstone.
3. Recibe `pot_val:128\n`; el módulo emite nivel **8** (0 → 0; 255 → 15).
4. Si añades entradas verdes, su compuerta actúa como condición para permitir o bloquear la señal, conservando la muestra recibida en esa sesión.

Con cero entradas verdes, el dato recibido se emite directamente.

## Minecraft → Hardware: LED

<!-- SC046_MEDIA:G03 -->

1. Canal `led_verde`, dirección **Minecraft → Hardware**.
2. Configura una cara de entrada verde frente a una palanca.
3. En Digital, encendido envía `led_verde:255\n` y apagado envía `led_verde:0\n`.
4. En Analógico, redstone 7 envía `led_verde:119\n`.

En este modo el módulo no energiza ninguna cara roja. Sin entradas verdes transmite cero.

## OR, AND y XOR

<!-- SC046_MEDIA:G08 -->

<!-- SC046_MEDIA:G09 -->

<!-- SC046_MEDIA:G10 -->

| Compuerta | Cómo combina entradas verdes | Con 2, 7 y 12 |
| --- | --- | --- |
| OR | Nivel máximo | 12 |
| AND | Nivel mínimo, incluidos ceros | 2 |
| XOR | Máximo si la cantidad de entradas mayores que cero es impar | 12 |

Con dos entradas activas, 2 y 7, XOR produce 0. **No son operaciones bit a bit.** Al transmitir al hardware las compuertas calculan el valor. Al recibir desde hardware funcionan como condición sin cambiar la magnitud del último valor recibido.

## Diagnóstico y sesiones

<!-- SC046_MEDIA:G06 -->

<!-- SC046_MEDIA:V03 -->

La pantalla distingue **RX** (último dato recibido), **TX** (último valor remitido al cliente), **Lectura** (redstone combinada) y **Salida** (redstone emitida). TX **no es una confirmación física del actuador**. `-1` indica ausencia de muestra o envío.

Desconectar invalida RX y apaga la salida. Reiniciar o descargar y cargar el módulo tampoco recupera una muestra RX antigua: el firmware debe enviar el valor actual de nuevo. Los sketches de 0.4.6 envían la primera muestra, incluidos ceros, además de cambios y una instantánea periódica.

El canal acepta 1–32 caracteres ASCII (letras, dígitos, `_`, `.`, `-`); el prefijo `mc_` está reservado. No mezcles un generador de ondas y un actuador independiente en un mismo canal.

Para restricciones, compatibilidad de mundos antiguos, lógica, desconexiones y colas visita la [referencia técnica del módulo](/io-module), la [matriz de pruebas](/io-test-matrix) y la [guía del protocolo](/protocol).

Continúa con el [ejemplo del LED](/tutoriales/arduino-led-minecraft) o el [potenciómetro](/tutoriales/sensor-redstone).
