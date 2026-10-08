---
title: Solución de errores de conexión en SerialCraft
description: Diagnostica conexiones USB y Wi-Fi, canales incorrectos, redstone y señales de Arduino o ESP32 paso a paso.
---
# Solucionar errores de SerialCraft sin cambiar todo a la vez

Cuando un circuito de SerialCraft no funciona, conviene evitar la tentación de reescribir todo el sketch. En un puente bidireccional hay varias capas: **hardware, transporte, protocolo, enrutamiento y redstone**. Comprueba cada una por separado.

Esta guía corresponde al protocolo de la versión en desarrollo **0.4.6-beta**.

## El método de cinco capas

| Capa | Pregunta que debes responder | Prueba sencilla |
| --- | --- | --- |
| Hardware | ¿La placa ejecuta el programa? | LED de inicio, salida serial de diagnóstico |
| Transporte | ¿Existe conexión USB o TCP? | Puerto correcto, estado de conexión |
| Protocolo | ¿Se envían líneas completas? | `canal:valor\n` |
| Enrutamiento | ¿El Módulo de E/S reconoce el canal? | Coinciden `Target Data` y modo |
| Redstone | ¿La cara correcta lee o emite? | Palanca o lámpara en la cara configurada |

Si mezclas varias modificaciones, dejas de saber qué cambio solucionó el fallo.

## Problema 1: el puerto USB aparece, pero no hay datos

Verifica que el cable USB tenga líneas de datos (algunos solo sirven para cargar). Cierra el monitor serial de Arduino IDE si mantiene ocupado el puerto. Revisa permisos del sistema operativo cuando corresponda.

Compara las velocidades: `Serial.begin(115200)` en el sketch y **115200 baudios** en SerialCraft. Una discrepancia puede producir caracteres ilegibles o ausencia de comunicación útil.

## Problema 2: un canal no responde

El formato esperado es:

~~~text
pot_val:128
~~~

Debe terminar en `\n`. Si el sketch usa `Serial.print()` sin añadir un salto de línea, el receptor podría seguir esperando el final del mensaje.

Comprueba exactamente las letras del canal. `pot_val`, `Pot_val` y `pot-val` no deben suponerse equivalentes.

Un bloque en dirección **Hardware → Minecraft** consume datos del hardware. Un bloque en dirección **Minecraft → Hardware** envía cambios de redstone hacia el hardware. Consulta [la guía del Módulo de E/S](/tutoriales/bloque-io).

## Problema 3: el valor llega, pero no se enciende la redstone

Comprueba que una cara del Módulo de E/S esté configurada como **salida roja de redstone**, orientada hacia el circuito. El modo global INPUT no configura automáticamente todas las caras.

Para probar niveles intermedios, selecciona ANALOG. Para un interruptor simple, DIGITAL.

## Problema 4: Wi-Fi desconecta inmediatamente

Comprueba que el ESP32 llegue realmente al servidor en la IP del ordenador y el puerto `25585`. Después revisa el token y el salto de línea del primer mensaje.

No publiques el token en una captura para pedir ayuda. Evita exponer ese puerto fuera de la red local.

## Problema 5: algunas muestras desaparecen

No envíes un `Serial.println` en cada iteración sin control. Agrupa cambios de estado, aplica un umbral pequeño a los sensores ruidosos y limita la frecuencia del firmware. La red y el servidor tienen mecanismos de protección que pueden descartar tráfico excesivo.

Si tu aplicación requiere registrar **cada** muestra a alta frecuencia, un protocolo de control de redstone no sustituye a un sistema dedicado de adquisición de datos.

## Antes de abrir un reporte

Prepara una descripción reproducible:

1. Versión de Minecraft, Fabric y SerialCraft.
2. Modelo exacto de placa y tipo de transporte.
3. Modo y `Target Data` del Módulo de E/S.
4. Mensaje esperado y mensaje observado, sin contraseñas ni tokens.
5. Qué etapas de esta guía funcionan y cuál falla.

Puedes enviar el reporte al [repositorio de incidencias](https://github.com/LeonardoAliaga/SerialCraft/issues) y revisar los [ejemplos oficiales](/ejemplos/) para contrastar tu sketch.
