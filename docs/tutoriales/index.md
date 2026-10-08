---
title: Tutoriales de electrónica con Minecraft
description: Aprende paso a paso a conectar Arduino y ESP32 con Minecraft, leer sensores y controlar redstone con SerialCraft.
---
# Tutoriales: de la redstone a la electrónica real

Esta sección convierte la documentación de SerialCraft en ejercicios que puedes reproducir en casa o en un aula. Cada tutorial describe un objetivo, el montaje, la configuración en Minecraft, el intercambio de mensajes y cómo interpretar el resultado.

::: tip Antes de comenzar
Los tutoriales están escritos para la versión en desarrollo **0.4.6-beta (rama test)**, Minecraft Java **26.2**, Java **25**. Verifica la versión del mod y del sketch antes de probar. Son instrucciones y resultados esperados, no certificaciones de pruebas realizadas con tu equipo.
:::

## Ruta recomendada para principiantes

<!-- SC046_MEDIA:H01 -->

| Orden | Tutorial | Lo que aprendes |
| --- | --- | --- |
| 1 | [Entender el Módulo de E/S](/tutoriales/bloque-io) | Direcciones, canales y lados de redstone |
| 2 | [Encender un LED desde Minecraft](/tutoriales/arduino-led-minecraft) | Salida digital y comunicación USB |
| 3 | [Controlar redstone con un potenciómetro](/tutoriales/sensor-redstone) | Entrada analógica y conversión 0–255 |
| 4 | [Digital frente a analógico](/tutoriales/digital-analogico) | Por qué dos escalas representan una señal |
| 5 | [Conectar ESP32 por Wi-Fi](/tutoriales/esp32-wifi) | Transporte TCP y seguridad básica |
| 6 | [Diagnosticar una conexión](/tutoriales/solucionar-errores) | Localizar errores por capas |
| 7 | [Visualizador y generador](/tutoriales/visualizador) | Observar RX/TX y ondas |
| 8 | [Telemetría del juego](/tutoriales/telemetria) | Enviar estados y eventos |
| 9 | [Pruebas automatizadas](/tutoriales/pruebas-automatizadas) | Gradle, JUnit, GameTests y CI |

## ¿Qué necesito?

Minecraft Java con Fabric Loader, la versión compatible de SerialCraft y Fabric API; una placa compatible con comunicación USB serial o TCP; cables y componentes para el circuito elegido.

No es necesario comprar componentes para leer la [referencia del protocolo](/protocol) o comprender la [arquitectura de comunicación](/tutoriales/bloque-io). Si ya tienes Arduino Uno R3, empieza por USB: reduce las variables del experimento.

## Cómo se organiza cada práctica

1. **Predicción:** qué valor debería aparecer en Minecraft o en el dispositivo.
2. **Preparación:** componentes, pines y configuración del bloque.
3. **Intercambio:** qué mensaje viaja por el enlace y en qué sentido.
4. **Comprobación:** cómo verificar el resultado sin confundir redstone con comunicación.
5. **Diagnóstico:** qué observar si algo falla.

Para sketches más completos y otras placas consulta los [ejemplos oficiales](/ejemplos/). Si encuentras una diferencia entre una guía y el comportamiento de tu versión, [reporta el problema](https://github.com/LeonardoAliaga/SerialCraft/issues) indicando versión, placa y pasos para reproducirlo.

## ¿Y después?

Cuando logres una entrada y una salida, puedes diseñar experimentos: una alarma de proximidad, un indicador de vida en LEDs o una maqueta educativa. Empieza siempre con voltajes bajos y componentes de prueba; SerialCraft no es un sistema de control industrial.
