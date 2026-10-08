---
title: Conectar ESP32 a Minecraft por Wi-Fi con SerialCraft
description: Aprende la arquitectura TCP de SerialCraft, la conexión por token y cómo intercambiar señales con un ESP32 en tu red local.
---
# Conectar ESP32 a Minecraft por Wi-Fi

Con Arduino Uno R3 el cable USB une directamente el juego con la placa. Un **ESP32** permite estudiar una ruta diferente: el mod escucha conexiones TCP y el microcontrolador se conecta por Wi-Fi.

En SerialCraft **0.4.6-beta (rama test)**, Minecraft actúa como **servidor TCP** y el ESP32 como **cliente**. Este cambio de dirección es importante si conoces ejemplos anteriores de SerialCraft.

## Requisitos

<!-- SC046_MEDIA:H06 -->

- ESP32 con soporte de Wi-Fi.
- PC que ejecuta Minecraft y ESP32 en una red local compatible.
- SerialCraft y Fabric API de la versión en desarrollo.
- Arduino IDE u otro entorno capaz de cargar el firmware apropiado.

Primero confirma que ambos dispositivos pueden comunicarse dentro de la red local. Las redes de invitados o redes escolares con aislamiento de clientes pueden impedirlo.

## 1. Preparar Minecraft

<!-- SC046_MEDIA:G12 -->

Abre la Laptop del mod y localiza la función de servidor Wi-Fi. Anota:

- **IP local del PC**, no la IP pública del router.
- **Puerto**, normalmente `25585`.
- **Token de emparejamiento** mostrado por la interfaz.

El token es un secreto de acceso. No lo compartas en capturas, commits públicos ni logs.

## 2. Entender la secuencia

El ESP32 inicia la conexión TCP hacia el ordenador. Después, conforme al protocolo de la versión, envía el token como primera línea, terminado en salto de línea.

~~~text
ESP32  → Minecraft: TOKEN\n
Minecraft → ESP32: OK\n
ESP32  → Minecraft: pot_val:128\n
Minecraft → ESP32: led_verde:255\n
~~~

La conexión y el contenido del mensaje son dos fases diferentes: un token aceptado no garantiza que un canal esté correctamente configurado.

## 3. Usa el ejemplo mantenido junto al mod

El [ejemplo de ESP32](/ejemplos/#_3-esp32-wi-fi) incluye el sketch y el montaje de referencia. Antes de cargarlo, sustituye las credenciales de Wi-Fi, la IP de destino y el token por los valores de tu propia red.

No copies credenciales reales al repositorio. El archivo de ejemplo debe permanecer genérico.

En Minecraft, prepara un Módulo de E/S INPUT para el canal `pot_val` con una cara OUTPUT de redstone y, si deseas el camino inverso, otro Módulo de E/S OUTPUT para `led_verde` con una cara INPUT.

## 4. Diferenciar cuatro problemas posibles

<!-- SC046_MEDIA:V04 -->

| Síntoma | Qué revisar |
| --- | --- |
| El ESP32 no entra a la red Wi-Fi | Nombre de red, clave, señal y compatibilidad de banda |
| Se conecta al Wi-Fi, pero no al PC | IP privada, puerto, firewall, aislamiento de clientes |
| Minecraft rechaza la sesión | Token, terminador `\n`, otra conexión activa |
| El canal no produce redstone | Nombre de canal, modo del bloque, configuración de las caras |

En una red doméstica, es habitual que el firewall del PC solicite permiso para la aplicación. Autoriza solo en la red privada correspondiente; no abras el puerto al exterior.

## Emparejamiento y placas recordadas

En 0.4.6, además del token inicial, el firmware compatible puede implementar el mecanismo **Recordar placa**. Utiliza un identificador y una clave almacenada para responder a un desafío HMAC-SHA256 en futuras conexiones. Es opcional: consulta el [ejemplo ESP32 actualizado](/ejemplos/#_3-esp32-wi-fi) antes de implementarlo. Este mecanismo **no cifra** la red.

## Seguridad

::: danger No expongas el servidor TCP a Internet
Esta versión transmite datos de la sesión en **texto claro**. El token no cifra el tráfico. Utiliza la función únicamente en una red local de confianza y **no configures redirección del puerto 25585 en el router**.
:::

Con esto ya puedes comparar el coste práctico de USB y Wi-Fi: el primero elimina la configuración de red; el segundo añade libertad de movimiento, pero exige diagnosticar más capas.

Para una introducción al formato de mensajes consulta [Digital y analógico](/tutoriales/digital-analogico) y la [referencia de comunicación](/protocol#_5-wi-fi-el-mod-es-el-servidor).
