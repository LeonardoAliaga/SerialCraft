# Módulo de E/S

El Módulo de E/S conecta un **canal de hardware** con un circuito de redstone. Su nombre ya no limita el dispositivo a Arduino. El nombre del módulo es una etiqueta; el **canal** debe coincidir exactamente con el del sketch, por ejemplo `pot_val` o `led_verde`.

## Configuración

Haz clic derecho en la base para abrir el editor. Puedes configurar el bloque sin conectar hardware. Escribe un nombre y un canal, elige dirección, señal y lógica, y abre **Conectores y diagnóstico** para asignar las cinco caras. Guardar espera la validación del servidor; un error conserva el borrador. El servidor comprueba propietario, dimensión, distancia de hasta 64 bloques, chunk cargado y valores válidos.

El canal admite 1–32 letras ASCII, dígitos, `_`, `.` y `-`. `mc_` está reservado para telemetría e identificación. No uses el mismo canal para varios emisores con valores distintos, ni para un generador y un módulo que controlen el mismo actuador. Varios receptores del mismo jugador sí pueden compartirlo.

| Dirección | Entrada verde | Salida roja | Desactivado |
| --- | --- | --- | --- |
| Hardware → Minecraft | Condición de habilitación | Emite redstone | No participa |
| Minecraft → Hardware | Lee y combina redstone | Inactiva | No participa |

En el mundo, clic derecho en un terminal alterna entrada/desactivado; Shift + clic derecho alterna salida/desactivado. El editor recorre desactivado → entrada → salida. El terminal **inferior está en la superficie de la placa**, cerca del borde sur; se selecciona desde arriba y comunica con el bloque de abajo. No hay puerto superior. Los colores muestran el rol configurado; los LEDs muestran habilitación y dirección, sin simular actividad física confirmada.

## Hardware → Minecraft

1. Configura `pot_val`, dirección Hardware → Minecraft, señal analógica.
2. Configura una cara como salida roja y conecta polvo, un repetidor o una lámpara.
3. La placa envía `pot_val:128\n`: el módulo emite redstone **8**. `0` apaga y `255` produce **15**.

Sin entradas verdes, el dato pasa directamente. Con entradas verdes, la lógica las utiliza como condición. Un dato recibido mientras la condición está cerrada se conserva y aparece cuando vuelve a abrirse. La señal hardware mantiene su magnitud analógica.

## Minecraft → Hardware

1. Configura `led_verde`, dirección Minecraft → Hardware.
2. Asigna una cara como entrada verde y coloca una palanca, botón o comparador.
3. Digital envía `led_verde:0\n` o `led_verde:255\n`. Analógico convierte los niveles 0, 1, 7 y 15 en **0, 17, 119 y 255**.

En este modo el módulo no emite redstone, aunque conserve caras de salida configuradas. Las salidas permanecen disponibles al cambiar la dirección. Sin entradas envía cero. Deshabilitar envía cero; cambiar de canal o abandonar transmisión envía cero al canal anterior si hay enlace. Descargar o destruir un módulo emisor también solicita cero.

## Lógica

| Compuerta | Regla | Ejemplo analógico |
| --- | --- | --- |
| OR | Máximo de las entradas | 2, 7, 12 → 12 |
| AND | Mínimo de todas, incluidos ceros | 2, 7, 12 → 2; 0, 15 → 0 |
| XOR | Máximo si hay un número impar de entradas mayores que cero | 2, 7 → 0; 2, 7, 12 → 12 |

Al transmitir, estas reglas calculan la magnitud enviada. Al recibir, un resultado positivo habilita la muestra del hardware. Digital convierte cualquier resultado positivo a 15/255. No son operaciones bit a bit sobre números analógicos.

## Diagnóstico y conexión

**RX** es el último dato recibido para ese módulo; **TX** es el último valor remitido al cliente del propietario; **Lectura** es la combinación de las entradas y **Salida** la redstone emitida. `-1` significa que no hay muestra/envío. El estado de conexión es reportado por el cliente que posee el hardware. No hay confirmación física en el protocolo, así que TX no prueba que un LED haya cambiado.

La desconexión observada invalida RX y apaga la redstone. El silencio por sí solo no implica desconexión: no existe un latido obligatorio. Al reiniciar o recargar una entidad, RX comienza sin muestra y requiere un dato nuevo. Al cambiar de dimensión se invalidan muestras; solamente los módulos de la dimensión actual del propietario intercambian datos. Reconectar reenvía los estados TX; USB hace otro reenvío a los dos segundos para cubrir el bootloader. Si el firmware reinicia internamente sin cerrar el transporte, el mod no puede detectar ese reinicio: reconecta o cambia la configuración para resincronizar.

Las entradas se procesan por turnos, hasta 40 líneas/s y 64 canales pendientes, con ocho transiciones por canal. Las salidas comparten un planificador de hasta 40 líneas/s, con memoria acotada. Bajo sobrecarga puede haber descartes: F7 muestra los contadores RX/TX. El visualizador registra RX antes del límite y TX al escribir en USB/TCP. Una transición más breve que un tick puede desaparecer al agrupar cambios del mundo; SerialCraft no es un osciloscopio ni un protocolo de pulsos garantizados.

| Problema | Revisión |
| --- | --- |
| RX llega, pero no hay redstone | Dirección, módulo habilitado, una salida roja y condición de entradas |
| Redstone llega, pero no hay TX | Cara verde, conexión y módulo en la dimensión del propietario |
| El LED conserva un estado antiguo | Reconecta; revisa baudios 115200 y que el sketch procese líneas completas |
| Una entrada se mantiene encendida | Entrada y salida comparten circuito; separa caminos con repetidores |
| Guardar es rechazado | Dueño, distancia, dimensión, canal válido y frecuencia de cambios |
| Mundo antiguo con canal inválido | El bloque se deshabilita y conserva el canal para revisarlo; no se enlaza silenciosamente a otro actuador |

USB, TCP, telemetría e identificación mantienen sus formatos de hardware. TCP sigue sin cifrado y requiere una red de confianza. Los paquetes internos de configuración/listado ahora usan IDs `v2`; cliente y servidor deben actualizarse juntos. Se conservan el bloque y la entidad `serialcraft:io_block`, recetas, modelos y nombres/ordinales NBT. Se ignora el dato temporal antiguo `redstoneOut`. AND/XOR ahora combinan entradas al transmitir, lo que cambia circuitos antiguos que dependían del máximo incondicional; revisa esos circuitos.

Los ejemplos actualizados para Uno R3, ESP32 y Uno Q envían siempre la primera muestra, incluido cero, y luego cambios e instantáneas cada segundo. Los sketches anteriores que solo envían cambios mantienen el formato, pero restaurar un módulo RX puede exigir mover el sensor o reenviar su dato. Los ceros de seguridad por cambio de canal/dirección o retirada cancelan órdenes pendientes del canal anterior y tienen prioridad acotada. Los flancos normales conservan su orden FIFO.

Ver también el [diseño técnico](./io-design), la [matriz de verificación](./io-test-matrix) y el [informe de auditoría](./audit-0.4.6). Las configuraciones antiguas con modos desconocidos se deshabilitan para revisión.
