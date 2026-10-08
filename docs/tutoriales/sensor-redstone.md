---
title: Controlar la redstone con un potenciómetro y Arduino
description: Convierte una lectura analógica de Arduino en una señal de redstone dentro de Minecraft usando SerialCraft.
---
# Controlar la redstone desde un potenciómetro real

En el tutorial del LED, Minecraft enviaba órdenes al mundo físico. Ahora haremos lo contrario: **un potenciómetro conectado a Arduino regula la intensidad de una salida de redstone**.

La cadena de datos es **A0 de Arduino → USB → canal pot_val → Módulo de E/S → redstone**.

## Materiales y circuito

Necesitas Arduino Uno R3, potenciómetro (por ejemplo, de 10 kΩ), cable USB y cables de conexión.

Conecta los extremos del potenciómetro a **5 V** y **GND**, y la patilla central al pin **A0**. La posición del mando modifica la tensión en A0.

En Arduino Uno R3, `analogRead(A0)` normalmente devuelve un valor entre 0 y 1023. SerialCraft utiliza 0–255 en el cable, por lo que hay que convertirlo.

## Sketch de transmisión

~~~cpp
const int POT_PIN = A0;
int anterior = -1;
unsigned long ultimoEnvio = 0;

void setup() {
  Serial.begin(115200);
}

void loop() {
  int lectura = analogRead(POT_PIN);
  int valor = map(lectura, 0, 1023, 0, 255);
  valor = constrain(valor, 0, 255);

  // Evita saturar el puerto con lecturas equivalentes.
  if (anterior < 0 || abs(valor - anterior) >= 3 || millis() - ultimoEnvio >= 1000) {
    Serial.print("pot_val:");
    Serial.println(valor);
    anterior = valor;
    ultimoEnvio = millis();
  }
  delay(40);
}
~~~

La diferencia de 3 unidades reduce pequeñas oscilaciones. También hay una instantánea cada segundo que ayuda a reconstruir RX después de recargar el módulo. El retardo de 40 ms limita la transmisión a un máximo aproximado de 25 lecturas por segundo; no equivale a una garantía de entrega de todas las muestras.

## Configurar el Módulo de E/S

1. Conecta Arduino por USB a **115200 baudios**.
2. Coloca el Módulo de E/S y define el canal `pot_val`.
3. Selecciona dirección **Hardware → Minecraft**: los datos entran desde el hardware a Minecraft.
4. Selecciona tipo de señal **ANALOG**.
5. Configura una **cara roja de salida de redstone**, conectada al circuito que deseas alimentar.
6. Para observar el resultado, utiliza una línea de polvo de redstone o elementos que respondan a distinta intensidad.

En versiones antiguas, el modo global se llamaba INPUT. En el editor actual, **Hardware → Minecraft** describe ese mismo flujo. La cara roja OUTPUT indica una salida de redstone, no la dirección del mensaje.

## ¿Qué valor esperar?

La conversión teórica aproxima `valorRedstone = valorCable × 15 / 255` y se ajusta a la discretización del mod.

| Posición aproximada | ADC Arduino | Valor enviado | Intensidad esperada |
| --- | ---: | ---: | ---: |
| Mínima | 0 | 0 | 0 |
| Un cuarto | 256 | 64 | Alrededor de 4 |
| Mitad | 512 | 128 | Alrededor de 8 |
| Máxima | 1023 | 255 | 15 |

No necesitas que el potenciómetro marque exactamente la mitad: importan las lecturas reales y su conversión.

## Cómo comprobarlo

Gira lentamente el potenciómetro. Deberías observar que la intensidad cambia por escalones en el juego: Arduino tiene mucha más resolución en la lectura que Minecraft en la redstone.

Si solo tienes encendido/apagado, revisa que el bloque esté en **ANALOG** y no DIGITAL. Si el canal no reacciona, comprueba el nombre `pot_val`, el terminador de línea (`Serial.println`) y la cara roja de salida.

## Extensiones educativas

Prueba a sustituir el potenciómetro por un sensor analógico adecuado al voltaje de tu placa. En un ESP32 la entrada ADC trabaja con niveles distintos; **nunca apliques 5 V directamente a un GPIO de 3,3 V**.

Si aparecen oscilaciones, puedes aumentar ligeramente la diferencia mínima antes del envío, pero eso sacrifica sensibilidad. Esta elección muestra el compromiso entre precisión, ruido y frecuencia de actualización.

Lee también [Digital y analógico](/tutoriales/digital-analogico) y los [sketches completos](/ejemplos/).
