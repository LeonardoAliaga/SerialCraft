# Referencia Técnica del Protocolo

::: warning Versión Beta 0.3.6
Esta documentación técnica aplica a la versión actual. El protocolo podría evolucionar en futuras actualizaciones para incluir soporte JSON o binario.
:::

## 1. Especificaciones de Comunicación

SerialCraft utiliza un protocolo de texto plano (ASCII) síncrono. La comunicación se basa en el envío de paquetes de datos terminados por un carácter de nueva línea.

| Parámetro | Valor |
| :--- | :--- |
| **Baud Rates Soportados** | 9600, 14400, 19200, 38400, 57600, 115200 |
| **Formato de Datos** | `CLAVE:VALOR` |
| **Terminador** | `\n` (Salto de línea / Newline) |
| **Codificación** | UTF-8 (ASCII compatible) |

::: danger Importante
El mod ignora cualquier mensaje que no termine en `\n`. En Arduino, **SIEMPRE** usa `Serial.println()` y no solo `Serial.print()`.
:::

---

## 2. Entrada (Arduino ➔ Minecraft)

Para que Minecraft reaccione a un evento físico, el Arduino debe enviar una cadena de texto con el ID del bloque destino y el valor deseado.

### Sintaxis
```text
<ID_DEL_BLOQUE>:<VALOR_ENTERO>\n
```

* **`<ID_DEL_BLOQUE>`**: Es la cadena de texto definida en el campo "Target Data" dentro de la interfaz del Bloque IO. (Ej: `btn_1`, `sensor_luz`).
* **`<VALOR_ENTERO>`**:
    * **0**: Apaga la señal de Redstone.
    * **1 - 15**: Enciende la señal de Redstone con esa potencia.
    * **> 15**: Se interpretará como señal máxima (15).

### Lógica de Procesamiento
El mod procesa la entrada en `ArduinoIOBlockEntity.java`. Si el bloque está en **Modo Input**, leerá el valor y actualizará el nivel de Redstone adyacente.

```cpp
// Ejemplo Arduino: Enviar señal máxima al bloque "alarma"
Serial.println("alarma:15");
```

---

## 3. Salida (Minecraft ➔ Arduino)

Cuando un Bloque IO en **Modo Output** detecta un cambio en la señal de Redstone que recibe, envía automáticamente un mensaje por el puerto serie.

### Sintaxis
```text
<ID_DEL_BLOQUE>:<VALOR>\n
```

### Comportamiento según Tipo de Señal
El valor enviado depende de cómo configuraste el bloque (Digital o Analógico/PWM).

#### A. Señal Digital (Simple)
Si el bloque está configurado en modo **Digital**:
* **Recibe Redstone > 0**: Envía `1`.
* **Recibe Redstone = 0**: Envía `0`.

#### B. Señal Analógica (PWM)
Si el bloque está configurado en modo **Analógico**, el mod realiza una conversión matemática interna para traducir la Redstone (0-15) a PWM (0-255).

**Fórmula interna:**
$$ PWM = \frac{Redstone \times 255}{15} $$

| Nivel Redstone | Valor Enviado (PWM) | Descripción |
| :---: | :---: | :--- |
| 0 | **0** | Apagado |
| 1 | **17** | Mínimo |
| 7 | **119** | Medio (~50%) |
| 15 | **255** | Máximo (100%) |

### Ejemplo de Lectura en Arduino
```cpp
// Detectar si el mensaje es para el bloque "luz_techo"
if (mensaje.startsWith("luz_techo:")) {
    // Cortar la parte del ID y quedarse con el número
    int valor = mensaje.substring(mensaje.indexOf(':') + 1).toInt();
    
    // Escribir directamente al pin (funciona para Digital y PWM)
    analogWrite(PIN_LED, valor); 
}
```

---

## 4. Lógica de Compuertas (Avanzado)
Los Bloques IO tienen una característica oculta: **Compuertas Lógicas**. Esto define cuándo se activa el bloque si recibe energía por varios lados a la vez (Norte, Sur, Este, Oeste).

Esto se procesa internamente en el método `updateLogicConditions()`:

* **OR (Por defecto):** Se activa si *cualquier* lado conectado recibe energía.
* **AND:** Se activa solo si *todos* los lados conectados reciben energía simultáneamente.
* **XOR:** Se activa si una cantidad *impar* de lados recibe energía.

*Nota: Actualmente esta configuración solo es accesible mediante edición NBT o versiones de desarrollo, pero la lógica ya existe en el código.*