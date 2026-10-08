---
title: Cómo usar Visualizar y el generador de ondas de SerialCraft
description: Aprende a explorar señales recibidas y enviadas, interpretar RX/TX y generar una señal de prueba en SerialCraft.
---
# Visualizar: el banco de pruebas de SerialCraft

Una de las novedades de **0.4.6-beta** es la pestaña **Visualizar** de la Laptop. Sirve para comprender qué información llega del dispositivo, qué información envía el mod y cómo cambian los valores con el tiempo.

No es un osciloscopio electrónico de alta velocidad: registra muestras del protocolo y representa su evolución. Esto es suficiente para diagnosticar sensores lentos, señales de redstone o telemetría del juego.

## Tres herramientas

<!-- SC046_MEDIA:G15 -->

| Herramienta | Para qué sirve | Un ejemplo |
| --- | --- | --- |
| Línea de tiempo | Comparar varios canales en un mismo intervalo | Potenciómetro y hambre del jugador |
| Sensor | Observar valores recibidos y conversión | Lecturas que oscilan cerca de un umbral |
| Generador | Enviar una onda o patrón a un canal del hardware | Comprobar un LED PWM |

## RX no significa que la redstone haya cambiado

**RX** indica que el transporte recibió una línea; la gráfica puede registrarla antes de aplicar límites de tráfico o pasar por el servidor. Por lo tanto, observar una muestra RX no garantiza que haya llegado al Módulo de E/S.

**TX** indica una escritura hacia el transporte, no la confirmación de que un actuador físico haya obedecido. Para comprobar el hardware necesitas observar el circuito real.

## Un primer experimento

<!-- SC046_MEDIA:G16 -->

1. Conecta Arduino y carga el ejemplo del potenciómetro.
2. Configura el canal `pot_val` como Hardware → Minecraft.
3. Abre **Visualizar** y selecciona el canal.
4. Gira el potenciómetro lentamente.
5. Compara el valor crudo (0–255) con los niveles discretos de redstone (0–15).

Las gráficas no deberían inventar puntos intermedios como si fueran muestras reales. Una línea entre muestras sirve para lectura visual, no para afirmar qué ocurrió entre ambos tiempos.

## Probar una salida con el generador

<!-- SC046_MEDIA:G17 -->

<!-- SC046_MEDIA:V05 -->

El generador puede enviar patrones como rampa, triángulo, cuadrada, seno o escalón. Resulta útil para probar un LED de PWM sin crear una redstone variable dentro de Minecraft.

**No uses el mismo canal simultáneamente** para el generador y un Módulo de E/S que controlen el mismo actuador. Dos fuentes distintas podrían producir órdenes contradictorias.

Al detener una onda, SerialCraft intenta solicitar un valor de reposo. **No puede garantizar que llegue si se corta el enlace**: usa un watchdog local en dispositivos donde dejar una salida energizada sea peligroso.

## Qué significa una señal incompleta

Puede deberse a cambios agrupados por ticks, colas de envío, descartes bajo sobrecarga, desconexiones o al propio firmware. Consulta los contadores del HUD F7 y sigue la [guía de errores](/tutoriales/solucionar-errores).

El [protocolo](/protocol#_13-probar-con-el-generador-pestana-visualizar) describe los límites vigentes. El siguiente ejercicio es [telemetría del juego](/tutoriales/telemetria), que añade canales `mc_` al mismo enlace.
