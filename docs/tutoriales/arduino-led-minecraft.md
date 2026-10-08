---
title: Encender un LED con redstone usando Arduino y SerialCraft
description: Monta un LED con resistencia en un Arduino Uno R3 y controla su brillo desde una palanca en Minecraft.
---
# Encender un LED físico desde Minecraft con Arduino Uno R3

Una palanca de Minecraft puede controlar un LED conectado a una placa Arduino. En este ejercicio recorrerás el camino completo: **redstone → Módulo de E/S → USB serial → Arduino → LED**.

El ejemplo es deliberadamente pequeño para que puedas diagnosticar cada etapa. La versión de desarrollo de referencia es SerialCraft **0.4.6-beta**.

## Materiales

- Arduino Uno R3 o placa equivalente con puerto serial USB.
- Un LED y una resistencia de **220 Ω a 330 Ω**.
- Protoboard, cables y cable USB de datos.
- Minecraft Java 26.2, Java 25, Fabric Loader, Fabric API y SerialCraft compatible.

Utiliza una resistencia con el LED: conectarlo directamente a un pin puede dañarlo.

## 1. Cableado

Conecta el pin **D9** a la resistencia; el otro extremo de la resistencia al ánodo (patilla larga) del LED y el cátodo (patilla corta) a **GND**.

D9 permite PWM en Arduino Uno R3, útil si luego deseas variar el brillo. No conectes cargas de alta corriente o una lámpara doméstica directamente.

## 2. Sketch mínimo

Carga el siguiente programa con Arduino IDE. Lee líneas completas con el formato `led_verde:VALOR` y aplica el valor 0–255 al PWM de D9.

~~~cpp
const int LED_PIN = 9;
String linea;

void setup() {
  pinMode(LED_PIN, OUTPUT);
  analogWrite(LED_PIN, 0);
  Serial.begin(115200);
  linea.reserve(48);
}

void loop() {
  while (Serial.available() > 0) {
    char c = (char)Serial.read();
    if (c == '\n') {
      linea.trim();
      if (linea.startsWith("led_verde:")) {
        String dato = linea.substring(10);
        bool valido = dato.length() > 0;
        for (unsigned int i = 0; i < dato.length(); i++) {
          if (dato[i] < '0' || dato[i] > '9') valido = false;
        }
        if (valido) analogWrite(LED_PIN, constrain(dato.toInt(), 0, 255));
      }
      linea = "";
    } else if (linea.length() < 47 && c != '\r') {
      linea += c;
    }
  }
}
~~~

Este es un ejemplo didáctico. El sketch completo de referencia, con más controles, está en [Ejemplos: Arduino Uno R3](/ejemplos/#_2-arduino-uno-r3-usb).

## 3. Configuración en Minecraft

1. Instala la versión del mod compatible y abre un mundo.
2. Coloca el Bloque Conector, prepara la conexión USB y selecciona el puerto de Arduino.
3. Asegúrate de usar **115200 baudios**, igual que `Serial.begin(115200)`.
4. Coloca un Módulo de E/S, abre el editor de la base y asigna el canal `led_verde`.
5. Selecciona **Minecraft → Hardware** como dirección del módulo (Minecraft envía al hardware).
6. Selecciona Digital o Analógico y configura una **cara verde de entrada de redstone**.
7. Conecta una palanca mediante el circuito de redstone a esa cara.

::: tip Si no encuentras el menú
La configuración del bloque y de la conexión puede distribuirse entre el Bloque Conector y la Laptop según la versión. Consulta [Instalación y configuración](/guide).
:::

## 4. Resultado esperado

| Redstone recibida | Digital, mensaje USB | Analógico, mensaje USB |
| ---: | --- | --- |
| 0 | `led_verde:0` | `led_verde:0` |
| 7 | `led_verde:255` | `led_verde:119` |
| 15 | `led_verde:255` | `led_verde:255` |

En DIGITAL el LED se comporta como interruptor. En ANALOG, una fuente de redstone variable permite experimentar con distintos brillos.

## 5. Diagnóstico por etapas

**No hay conexión USB:** comprueba que el cable transmita datos, el puerto sea correcto y Arduino IDE no mantenga abierto el monitor serial al mismo tiempo.

**Hay conexión, pero no llega el comando:** compara `led_verde` letra por letra, la dirección Minecraft → Hardware y la cara de entrada verde.

**Llega el comando, pero no se ilumina:** comprueba polaridad del LED, GND, resistencia y uso del pin D9. Un ejemplo que espera `1` en vez de `255` también fallará.

**El LED queda encendido tras desconectar:** el hardware puede conservar el último PWM. Para montajes donde importe el estado seguro, añade un temporizador de vigilancia en el firmware que apague la salida cuando deje de recibir mensajes.

El siguiente desafío es invertir el camino: [controlar redstone con un sensor](/tutoriales/sensor-redstone).
