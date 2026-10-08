---
title: Señales digitales y analógicas entre Minecraft y Arduino
description: Aprende a convertir redstone de 0 a 15 a valores de 0 a 255 y a distinguir una señal binaria de una proporcional.
---
# Digital y analógico: por qué Minecraft utiliza 0–15 y Arduino recibe 0–255

Uno de los aspectos más interesantes de SerialCraft es que sus dos mundos no representan las señales de la misma manera. **Redstone posee 16 niveles posibles (0–15)**, mientras que el protocolo de SerialCraft 0.4.6 utiliza valores **de 0 a 255**.

Entender la conversión ayuda a escribir sketches compatibles y a localizar comportamientos inesperados.

## Qué es una señal digital

En este contexto, digital significa que solo importa si la señal está apagada o encendida. El protocolo representa esos estados mediante 0 y 255.

Si una palanca da redstone 15, un Módulo de E/S configurado como OUTPUT y DIGITAL envía un mensaje como:

~~~text
led_verde:255
~~~

Si dejas de alimentar la entrada, envía:

~~~text
led_verde:0
~~~

En el sentido contrario, cualquier valor positivo recibido en modo DIGITAL se interpreta como redstone encendida al máximo. Es práctico para botones, interruptores y alarmas.

## Qué significa analógico aquí

El modo ANALOG conserva niveles intermedios mediante una **representación numérica**. No significa que Minecraft transmita una tensión analógica por USB: sigue enviando texto digital.

Para convertir redstone a valores del cable:

~~~text
valorCable = redstone × 255 / 15
~~~

Para convertir datos recibidos del dispositivo:

~~~text
redstone ≈ valorCable × 15 / 255
~~~

El redondeo exacto debe consultarse en la implementación de la versión usada.

| Redstone | Valor en el cable |
| ---: | ---: |
| 0 | 0 |
| 1 | 17 |
| 3 | 51 |
| 7 | 119 |
| 10 | 170 |
| 15 | 255 |

## PWM no es lo mismo que una salida analógica continua

En un Arduino Uno, `analogWrite(D9, 128)` normalmente genera **PWM**: una serie rápida de pulsos cuyo ciclo de trabajo aproxima un brillo del 50 %. No produce necesariamente una tensión continua estable de media escala.

Por eso PWM sirve para regular la intensidad aparente de muchos LEDs, pero no debe confundirse con un DAC ni conectarse directamente a actuadores de potencia.

Un potenciómetro, por otra parte, se lee con `analogRead()`, normalmente entre 0 y 1023 en el Uno R3. Debes mapear esos valores al intervalo 0–255 antes de enviarlos.

~~~cpp
int adc = analogRead(A0);
int wireValue = map(adc, 0, 1023, 0, 255);
Serial.print("pot_val:");
Serial.println(wireValue);
~~~

## Una experiencia sencilla

1. Configura un Módulo de E/S INPUT con canal `pot_val`.
2. Usa el sketch del [potenciómetro](/tutoriales/sensor-redstone).
3. Observa la intensidad de redstone en modo ANALOG.
4. Cambia solo el tipo a DIGITAL.
5. Compara el resultado: una escala gradual frente a encendido/apagado.

Ese contraste muestra la diferencia entre **cuantizar una medición** y **convertirla en un estado binario**.

## Errores frecuentes

**Mi LED se ve casi apagado:** un sketch antiguo puede estar interpretando el valor `1` como encendido, pero la versión actual envía `255` para ese estado.

**La señal salta varios niveles:** es normal que el sensor tenga más resolución que redstone; además, el ruido eléctrico puede causar variaciones. Puedes filtrar lecturas en el firmware.

**No cambia al elegir ANALOG:** comprueba que el circuito permita niveles intermedios. Una palanca solo entrega apagado o encendido.

Consulta el [protocolo oficial](/protocol) para detalles de la versión y la [guía del Módulo de E/S](/tutoriales/bloque-io) para configurar la dirección de los conectores.
