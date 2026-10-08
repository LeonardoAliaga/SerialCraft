---
title: Telemetría de Minecraft a Arduino o ESP32 con SerialCraft
description: Descubre cómo enviar salud, hambre, tiempo y eventos de Minecraft a un microcontrolador usando los canales mc_.
---
# Telemetría: enviar datos del juego a un dispositivo físico

Además de controlar redstone, **SerialCraft 0.4.6-beta** puede transmitir algunos estados del juego a Arduino o ESP32. Así puedes construir, por ejemplo, un indicador LED que reaccione a la salud del jugador o una pantalla que represente el nivel de hambre.

La diferencia clave con el Módulo de E/S es que la telemetría usa **canales reservados** que empiezan por `mc_` y se gestionan desde la pestaña **Eventos** de la Laptop.

## Un ejemplo de mensaje

~~~text
mc_health:18
mc_hunger:14
mc_weather:1
~~~

Cada línea representa un entero. Estos ejemplos ilustran el formato, no son lecturas de una partida específica.

| Canal | Qué significa | Rango documentado |
| --- | --- | --- |
| `mc_health` | Salud en puntos redondeados | 0–1024 |
| `mc_hunger` | Hambre actual | 0–20 |
| `mc_time` | Hora del día, en ticks | 0–23999 |
| `mc_weather` | Despejado, lluvia o tormenta | 0–2 |
| `mc_fire` | Si el jugador está en llamas | 0–1 |
| `mc_damage` | Suceso de daño | 1–32767 |
| `mc_death` | Suceso de muerte | 1 |

La [referencia completa de telemetría](/protocol#_11-telemetria-del-juego-minecraft-➔-hardware) incluye otros canales, como nivel de experiencia, aire y saturación.

## Cómo activarla

1. Conecta la placa mediante USB o TCP local.
2. Abre la Laptop y entra en **Eventos**.
3. Activa únicamente los canales que tu sketch esté preparado para recibir.
4. Abre **Visualizar** o la consola y observa el nombre y valor del canal.
5. Comprueba en el firmware que la línea se decodifique sin bloquear la lectura de otros mensajes.

La telemetría está **deshabilitada por defecto**. No necesitas crear un Módulo de E/S por cada `mc_`.

## Estados y sucesos no son lo mismo

Un **estado** como `mc_hunger:20` puede enviarse de nuevo sin significado adicional; tu firmware debería tratarlo como una actualización del valor actual.

Un **suceso** como `mc_death:1` representa que algo ocurrió. Repetirlo artificialmente podría disparar dos veces un efecto. Estos eventos no tienen confirmación de entrega a nivel del protocolo físico: pueden perderse si el enlace se interrumpe.

El sistema limita y prioriza su transmisión. La telemetría comparte el transporte con las órdenes del Módulo de E/S, por lo que enviar muchos datos no es gratis.

## Ejemplo de interpretación en firmware

~~~cpp
// fragmento ilustrativo, dentro de un lector de líneas
if (linea.startsWith("mc_hunger:")) {
  int hambre = linea.substring(10).toInt();
  hambre = constrain(hambre, 0, 20);
  // Convertir hambre a un indicador propio.
}
~~~

Para un Arduino Uno, recuerda que el hardware tiene recursos limitados. Evita grandes concatenaciones repetidas de `String`, usa un buffer acotado e ignora canales que el proyecto no implemente.

## Seguridad y compatibilidad

Nunca uses `mc_` como canal de tu propio Módulo de E/S. La conexión TCP funciona en texto claro y se limita a redes locales de confianza. Si construyes un actuador físico, incorpora en el firmware el comportamiento seguro que necesite ante desconexiones.

Sigue con [Visualizar señales](/tutoriales/visualizador) o los [ejemplos de placas](/ejemplos/) para crear un montaje completo.
