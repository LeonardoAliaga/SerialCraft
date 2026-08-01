# Protocolo Bidireccional y Hardware (v0.4.3)

::: warning Versión Beta 0.4.3
Esta versión **rompe compatibilidad** con los sketches escritos para la 0.3.x en dos puntos: la escala de los valores y la dirección de la conexión Wi-Fi. Lee la sección [Cambios respecto a 0.3.x](#_8-cambios-respecto-a-0-3-x) antes de reutilizar código antiguo.
:::

## 1. Especificaciones de comunicación

SerialCraft usa un protocolo de texto plano (UTF-8) orientado a líneas. Cada mensaje termina obligatoriamente en `\n`, tanto por USB como por TCP.

| Parámetro | Valor |
| :--- | :--- |
| **Baudios admitidos (Serial)** | 300, 1200, 2400, 4800, 9600, 19200, 38400, 57600, 74880, **115200 (por defecto)**, 230400, 250000 |
| **Puerto TCP (Wi-Fi)** | **25585** (por defecto) |
| **Formato** | `CLAVE:VALOR` |
| **Terminador** | `\n` |
| **Codificación** | UTF-8 |
| **Longitud máxima de línea** | 256 caracteres |
| **Longitud máxima del `Target Data`** | 32 caracteres |
| **Ritmo máximo entrante** | 40 mensajes/s sostenidos, ráfaga de 80 |

::: danger El terminador no es opcional
El mod ignora todo lo que no termine en `\n`. Usa siempre `Serial.println()` o `client.print("...\n")`, nunca `print()` a secas.
:::

::: tip Por qué existe el límite de 40 mensajes/s
Cada línea recibida se convierte en un paquete al servidor. Un `Serial.println()` dentro de `loop()` sin control genera miles por segundo y provoca lag real en partidas multijugador. El mod descarta en silencio lo que exceda el límite: no es un error, es contención. Envía solo cuando el valor **cambie**.
:::

---

## 2. Escala unificada 0-255

Desde la v0.4.3 **el cable habla siempre en 0-255**, en los dos sentidos y con los dos tipos de señal. Dentro de Minecraft la redstone sigue siendo 0-15; la conversión la hace el mod.

| Tipo de señal | Minecraft → placa | Placa → Minecraft |
| :--- | :--- | :--- |
| **Analógica (PWM)** | `redstone × 255 / 15` | `valor × 15 / 255` |
| **Digital** | 0 si redstone = 0, **255** si redstone ≥ 1 | 0 si valor = 0, **15** si valor ≥ 1 |

### Tabla de conversión analógica

| Redstone | Valor en el cable | Descripción |
| :---: | :---: | :--- |
| 0 | 0 | Apagado |
| 1 | 17 | Mínimo |
| 7 | 119 | Medio (~50 %) |
| 15 | 255 | Máximo |

::: tip Consecuencia práctica
Tu firmware **no necesita saber** si el bloque está en modo Digital o Analógico. Un único `analogWrite(pin, valor)` funciona en ambos casos. En 0.3.x el modo digital enviaba `1`, y un `analogWrite(pin, 1)` dejaba el LED apagado al 0,4 % de ciclo de trabajo: ese era el error clásico al montar el primer circuito.
:::

En modo digital, **cualquier** valor entrante mayor o igual que 1 se interpreta como encendido a potencia máxima (redstone 15). Puedes enviar `1`, `15` o `255` indistintamente.

---

## 3. Entrada (Hardware ➔ Minecraft)

La placa envía el ID del bloque destino y el valor:

```text
<TARGET_DATA>:<VALOR_ENTERO>\n
```

* **`<TARGET_DATA>`**: la cadena que escribiste en el campo *Target Data* del Bloque IO (ej. `btn_1`, `sensor_luz`). Máximo 32 caracteres. **Si está vacío, el bloque ignora todo**: no existe el comodín.
* **`<VALOR_ENTERO>`**: 0-255. Los valores fuera de rango se recortan; el texto no numérico se descarta sin lanzar error.

```cpp
Serial.println("sensor_luz:200");   // ~redstone 12 en modo analógico
Serial.println("alarma:255");       // encendido total
Serial.println("alarma:0");         // apagado
```

El Bloque IO debe estar en modo **INPUT** y tener el mismo `Target Data`. Solo reciben mensajes las placas del jugador que mantiene la conexión abierta.

---

## 4. Salida (Minecraft ➔ Hardware)

Un Bloque IO en modo **OUTPUT** emite automáticamente cuando cambia el nivel de redstone que recibe:

```text
<TARGET_DATA>:<VALOR>\n
```

Dos detalles de implementación que conviene conocer:

* **Deduplicación**: el bloque no reenvía un valor idéntico al anterior. Si mantienes una palanca encendida, el mensaje se manda una vez, no veinte veces por segundo.
* **Intervalo**: la comprobación de salida corre cada 2 ticks (10 Hz), salvo que un cambio la marque como urgente. Es el compromiso entre respuesta y coste en el servidor.

---

## 5. Wi-Fi: el mod es el servidor

::: warning Cambio de dirección respecto a 0.3.x
Antes la placa levantaba un servidor en el 8080 y Minecraft se conectaba a ella. **Ahora es al revés**: el cliente de Minecraft abre el servidor TCP y la placa se conecta como cliente.
:::

### Secuencia de conexión

1. En el juego: **Laptop → Inicio → Iniciar servidor Wi-Fi**. La interfaz muestra la IP local, el puerto (25585) y un **token de enlace**.
2. La placa abre una conexión TCP a esa IP y puerto.
3. La placa envía **el token como primera línea**, terminado en `\n`.
4. El mod responde `OK\n` si es correcto, o `ERR TOKEN\n` y cierra.
5. A partir de ahí el canal transporta mensajes `CLAVE:VALOR` normales.

```text
placa → mod:   YXTUEA\n
mod   → placa: OK\n
placa → mod:   pot_val:128\n
mod   → placa: led_verde:255\n
```

### Restricciones del servidor

| Regla | Motivo |
| :--- | :--- |
| Solo se acepta **una** placa a la vez | Evita que un tercero expulse a la tuya repetidamente |
| Solo direcciones **privadas** (LAN, loopback, link-local) | Evita la exposición accidental a Internet |
| Las líneas de más de 256 caracteres cortan la sesión | Un peer que no envíe `\n` agotaría la memoria del cliente |
| Token obligatorio | Sin él, cualquiera en la red podía accionar tu redstone con un telnet |

::: danger El canal va en claro
El token evita el acceso accidental o casual, pero **no es cifrado**. Es adecuado para una red doméstica o de aula. **No abras el puerto 25585 en tu router ni uses esto sobre Internet.**
:::

---

## 6. Lógica de compuertas

Cada Bloque IO tiene un modo lógico que decide cuándo se considera activo si recibe energía por varios lados configurados como entrada:

* **OR** (por defecto): se activa si *cualquier* lado recibe energía.
* **AND**: se activa solo si *todos* los lados de entrada reciben energía.
* **XOR**: se activa si un número *impar* de lados recibe energía.

Si la condición no se cumple, el bloque no emite ni acepta datos y deja su salida de redstone a 0. Lo mismo ocurre si está desactivado desde la Laptop.

---

## 7. Errores frecuentes

| Síntoma | Causa habitual |
| :--- | :--- |
| El LED no enciende, pero el juego dice que envía | Sketch antiguo esperando `1` en modo digital. Ahora llega `255`; usa `analogWrite` sin comparaciones |
| No llega nada por USB | Los baudios del sketch no coinciden con los del Bloque Conector |
| La placa se conecta y se cae al instante | Token incorrecto, o ya hay otra placa conectada |
| Llegan solo algunos mensajes | Superas los 40 mensajes/s. Añade histéresis y envía solo los cambios |
| El bloque ignora todo | `Target Data` vacío o distinto al del mensaje |
| El potenciómetro llega al máximo a mitad de recorrido | Resolución del ADC mal mapeada (10 bits frente a 12) |

---

## 8. Cambios respecto a 0.3.x

1. **Escala unificada**: la entrada era 0-15 y la salida 0-255. Un `200` enviado por la placa volvía como `15`. Ahora ambos extremos usan 0-255.
2. **El modo digital envía 255**, no `1`.
3. **Wi-Fi invertido**: el mod es el servidor, la placa el cliente.
4. **Puerto 25585** en lugar de 8080.
5. **Token de emparejamiento obligatorio.**
6. **Límite de ritmo** de 40 mensajes/s por jugador.
7. **Un `Target Data` vacío ya no acepta cualquier mensaje** que contenga `:`.

---

## 9. Ejemplos completos

Sketches listos para cargar en [Ejemplos y pruebas](/ejemplos/): Arduino Uno R3 (USB), ESP32 (Wi-Fi) y Arduino Uno Q (Bridge + Python), con sus esquemas de conexión.
