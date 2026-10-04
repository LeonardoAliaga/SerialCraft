# Protocolo Bidireccional y Hardware (v0.4.6)

::: warning Versión Beta 0.4.6
Esta especificación corresponde a **SerialCraft v0.4.6-beta**. Si vienes de la versión 0.4.3 o 0.3.x, consulta las secciones [Cambios respecto a 0.4.3](#_9-cambios-respecto-a-0-4-3) y [Cambios respecto a 0.3.x](#_8-cambios-respecto-a-0-3-x) para conocer las diferencias en el cable y la reserva del prefijo `mc_`.
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

## 9. Cambios respecto a 0.4.3

La versión 0.4.6 introduce el canal de telemetría y refuerza el aislamiento entre señales de bloques y datos del juego:

1. **Prefijo reservado `mc_`**:
   El prefijo `mc_` queda estrictamente reservado para la telemetría del juego (`mc_time`, `mc_health`, `mc_damage`, etc.).
   * **Rechazo en servidor:** el servidor rechaza cualquier intento de configurar un Bloque IO cuyo `Target Data` comience por `mc_`, notificando al jugador con el mensaje `message.serialcraft.reserved_key`.
   * **Descarte en entrada:** en `ArduinoIOBlockEntity`, cualquier paquete recibido desde el hardware que comience por `mc_` se descarta de inmediato en $O(1)$ sin procesar redstone ni modificar NBT.
2. **Telemetría del juego hacia el hardware**:
   Los eventos del jugador y del entorno se emiten hacia la placa mediante el formato `mc_<canal>:<entero>\n` activándolos desde la pestaña **Eventos** de la Laptop.
3. **Control de flujo y ritmo (Pacing)**:
   El envío de telemetría está estrictamente dosificado a un máximo de **1 línea por tick** (techo de 20 líneas/s) mediante `TelemetryOutbox`, con prioridad de sucesos frente a estados, evitando desbordar el búfer serie de 64 bytes en microcontroladores como el ATmega328P de Arduino Uno.
4. **Reenvío periódico cada 5 segundos**:
   Los canales periódicos activos se reenvían silenciosamente cada 100 ticks (5 s) sin emitir mensajes en la consola HUD, garantizando que microcontroladores que se reinician al abrir la conexión USB (DTR) adquieran el estado actual sin reconexión manual.
5. **Identificación de la placa y reconexión sin token**:
   Líneas opcionales `mc_who`, `mc_id` y `mc_key`, y un segundo camino de handshake (`TRUST` con reto-respuesta) para placas recordadas. Ver la [sección 12](#_12-identificacion-de-la-placa-y-reconexion-sin-token).
6. **Banco de pruebas (pestaña Visualizar)**:
   El mod registra con hora real los mensajes `clave:valor` en ambos sentidos y puede enviar ondas de prueba a la placa. No añade nada al formato del cable. Ver la [sección 13](#_13-probar-con-el-generador-pestana-visualizar).

---

## 10. Ejemplos completos

Sketches listos para cargar en [Ejemplos y pruebas](/ejemplos/): Arduino Uno R3 (USB), ESP32 (Wi-Fi) y Arduino Uno Q (Bridge + Python), con sus esquemas de conexión.

---

## 11. Telemetría del juego (Minecraft ➔ Hardware)

::: tip Novedad de la 0.4.6-beta
La pestaña **Eventos** del panel permite elegir qué datos del juego (hora, hambre, daño recibido...) se envían a la placa. Es el mismo canal USB o Wi-Fi de las secciones anteriores; solo cambia el formato de las líneas.
:::

### Formato

```text
mc_<canal>:<entero>\n
```

| Regla | Detalle |
| :--- | :--- |
| **Prefijo reservado `mc_`** | Ningún Bloque IO puede usarlo como `Target Data` (el servidor rechaza la configuración con un aviso). Así una placa nunca confunde un dato del juego con una orden de redstone. |
| **Clave** | `mc_[a-z0-9_]{1,29}`: minúsculas, dígitos y `_`, 32 caracteres como máximo. |
| **Valor** | Entero decimal con signo. Todos los canales caben en un `int` de 16 bits (el de un Arduino Uno). Cada canal promete además su propio rango, de la tabla siguiente. |
| **Desactivado por defecto** | No se envía ningún canal hasta que lo marcas en la pestaña. Un sketch antiguo no recibe líneas que no pidió. |
| **Claves desconocidas** | El sketch debe ignorarlas. Se añadirán canales nuevos sin romper los que ya funcionan. |

### Canales

| Clave | Tipo | Rango | Significado |
| :--- | :--- | :--- | :--- |
| `mc_time` | Estado | 0-23999 | Hora del día en ticks: `0` = 06:00, `6000` = 12:00, `12000` = 18:00, `18000` = 00:00. Un día dura 20 minutos reales (20 ticks/s). |
| `mc_isday` | Estado | 0-1 | `1` si `mc_time` es menor que 12000. |
| `mc_weather` | Estado | 0-2 | `0` despejado, `1` lluvia, `2` tormenta. |
| `mc_health` | Estado | 0-1024 | Salud en puntos, redondeada (2 puntos = 1 corazón). |
| `mc_hunger` | Estado | 0-20 | Nivel de hambre. |
| `mc_saturation` | Estado | 0-20 | Saturación, redondeada. |
| `mc_level` | Estado | 0-32767 | Nivel de experiencia. |
| `mc_air` | Estado | 0-100 | Aire restante, en porcentaje. |
| `mc_fire` | Estado | 0-1 | `1` si el jugador está en llamas. |
| `mc_damage` | Suceso | 1-32767 | Puntos de salud perdidos entre dos ticks consecutivos. Es una pérdida neta: no cuenta el daño absorbido por corazones dorados ni el recorte al terminar un efecto de vida extra. |
| `mc_death` | Suceso | 1 | El jugador ha muerto. La aparición de la línea es el suceso; el valor siempre es `1`. |

`mc_time` sigue el reloj de la dimensión donde está el jugador. En dimensiones sin ciclo de día y noche propio puede permanecer fijo; el canal está pensado para el Overworld.

### Garantías de entrega

| | **Estado** | **Suceso** |
| :--- | :--- | :--- |
| **Cuándo se envía** | Al cambiar, con el intervalo elegido en la pestaña (0,25 a 10 s), y **cada 5 s aunque no cambie** | Una vez por cada ocurrencia |
| **Se reenvía** | Sí | **Nunca**: repetirlo haría creer a la placa que hubo otro golpe |
| **Si se pierde** | Se recupera en el siguiente reenvío | Se pierde: no hay confirmación de recepción |

Consecuencias para tu sketch:

* **Trata los estados como idempotentes.** Recibir dos veces `mc_hunger:20` no debe cambiar nada.
* **El reenvío cada 5 s existe por el USB.** Un Arduino Uno se reinicia cuando el ordenador abre su puerto, y las primeras líneas caen dentro del arranque del bootloader. Sin reenvío, un valor estable no volvería a llegar nunca.
* **Ritmo.** Como máximo **una línea por tick** (20 líneas/s), con los sucesos por delante de los estados. Al conectar, la instantánea completa tarda unos cientos de milisegundos. La cola de sucesos guarda 16; si se llena se descarta el más antiguo.
* **Los datos del juego y las salidas de los Bloques IO comparten el mismo canal.** Distingue unos de otras por el prefijo `mc_`.

### Ejemplo: reloj con ESP32 y servo

La manecilla recorre 0° → 180° durante el día y vuelve a 0° durante la noche (el servo estándar no da la vuelta completa). Activa **Hora del día** en la pestaña Eventos. La placa interpola entre mensajes para que el movimiento sea suave y se detiene si pasan 15 s sin noticias, para no desfasarse con el juego en pausa.

```cpp
#include <WiFi.h>
#include <ESP32Servo.h>

// ── Configuración ────────────────────────────────────────────
const char*    WIFI_SSID = "TU_WIFI";
const char*    WIFI_PASS = "TU_CLAVE";
const char*    MOD_HOST  = "192.168.1.50";  // IP que muestra la Laptop del mod
const uint16_t MOD_PORT  = 25585;
const char*    MOD_TOKEN = "ABC123";        // token que muestra la Laptop del mod
const int      SERVO_PIN = 18;              // el servo se alimenta con 5 V externos

WiFiClient client;
Servo hand;

// Última hora recibida y el instante local en que llegó
long          baseTicks  = 0;
unsigned long baseMillis = 0;
bool          haveTime   = false;

int           currentAngle = 0;
unsigned long lastStep     = 0;
unsigned long lastTry      = 0;

// 20 ticks/s = 1 tick cada 50 ms. Sin noticias del mod durante 15 s (por encima
// del intervalo máximo de la pestaña, 10 s) la manecilla deja de avanzar sola:
// así no se desfasa si el juego está en pausa.
long estimatedTicks() {
  long elapsed = (long)((millis() - baseMillis) / 50UL);
  if (elapsed > 300) elapsed = 300;
  return (baseTicks + elapsed) % 24000L;
}

// Día (0-12000): 0° -> 180°.  Noche (12000-24000): 180° -> 0°.
int targetAngle(long t) {
  if (t < 12000L) return (int)(t * 180L / 12000L);
  return (int)((24000L - t) * 180L / 12000L);
}

void handleLine(String line) {
  line.trim();
  int sep = line.indexOf(':');
  if (sep <= 0) return;                        // línea sin formato CLAVE:VALOR
  String key  = line.substring(0, sep);
  long   value = line.substring(sep + 1).toInt();

  if (key == "mc_time") {
    baseTicks  = value;
    baseMillis = millis();
    haveTime   = true;
  }
  // Las claves desconocidas se ignoran: así el sketch sigue funcionando si
  // activas más datos en la pestaña Eventos.
}

bool connectToMod() {
  if (!client.connect(MOD_HOST, MOD_PORT)) return false;
  client.print(String(MOD_TOKEN) + "\n");      // el token es la primera línea
  String reply = client.readStringUntil('\n');
  reply.trim();
  return reply == "OK";
}

void moveHand() {
  if (millis() - lastStep < 15) return;        // como máximo 1° cada 15 ms
  lastStep = millis();
  int target = targetAngle(estimatedTicks());
  if (currentAngle == target) return;
  currentAngle += (currentAngle < target) ? 1 : -1;
  hand.write(currentAngle);
}

void setup() {
  hand.setPeriodHertz(50);
  hand.attach(SERVO_PIN, 500, 2400);
  hand.write(currentAngle);

  WiFi.begin(WIFI_SSID, WIFI_PASS);
  while (WiFi.status() != WL_CONNECTED) delay(250);
}

void loop() {
  if (!client.connected()) {
    if (millis() - lastTry > 2000) {           // reintenta cada 2 s
      lastTry = millis();
      connectToMod();
    }
    return;
  }

  while (client.available()) handleLine(client.readStringUntil('\n'));
  if (haveTime) moveHand();
}
```

::: warning El servo no se alimenta desde el ESP32
Usa una fuente de 5 V propia y une las tierras. El pin de 3,3 V del ESP32 no aguanta el pico de corriente de un servo al arrancar.
:::

## 12. Identificación de la placa y reconexión sin token

Todo es **opcional y compatible hacia atrás**: una placa que no sepa nada de esto sigue funcionando con el token de siempre. Las líneas usan el prefijo reservado `mc_` y las consume el cliente: **nunca llegan al servidor**.

### Identificación

| Dirección | Línea | Significado |
|---|---|---|
| mod ➔ placa | `mc_who:1` | «¿Quién eres?». Se envía 1,5 s, 4 s y 9 s después de conectar, hasta recibir respuesta. Las placas antiguas la ignoran. |
| placa ➔ mod | `mc_id:model=ESP32-S3;uid=ESP32-A1B2C3D4E5F6` | Modelo (≤ 32 caracteres) e identificador estable (`[A-Za-z0-9_-]`, 4–32; p. ej. derivado de la MAC). La forma corta `mc_id:esp32-s3` también vale. |

El mod combina varias fuentes y se queda con la más fiable: **anuncio de la placa** > **modelo exacto por USB (VID:PID)** o **banner de arranque del ESP** > **fabricante** > **chip puente**. Un CH340 o CP2102 no dice qué placa hay detrás (el mismo chip va en un Nano clon y en un ESP32), así que la interfaz muestra «Placa con CH340 (modelo sin identificar)» hasta que la placa se anuncia.

### Placa recordada (sin token)

1. **Primera vez:** la placa entra con el token y se anuncia con `mc_id`. En la pestaña Inicio aparece **Recordar placa**; al pulsarla el mod genera una clave de 128 bits, la guarda en `config/serialcraft-boards.properties` y se la envía con `mc_key:<clave>`. La placa la guarda en flash.
2. **Siguientes veces**, la primera línea es `TRUST <uid>` en lugar del token:

```
placa ➔ mod   TRUST ESP32-A1B2C3D4E5F6
mod   ➔ placa CHAL 9d1f00aa11bb22cc33dd44ee55ff6677      (reto aleatorio, distinto cada vez)
placa ➔ mod   <HMAC-SHA256(clave, reto) en hex minúscula>
mod   ➔ placa OK
```

La clave (en ASCII hex) es la clave HMAC y el reto (en ASCII hex) es el mensaje. La clave **no vuelve a viajar** por la red. Errores: `ERR UNKNOWN` (el mod no conoce esa placa) y `ERR TRUST` (respuesta incorrecta); en ambos casos la placa debe borrar su clave y volver al token.

Si hay placas recordadas, el servidor Wi-Fi **arranca solo al entrar a un mundo** (ajuste `settings.autoStartWifi`), así la placa se reconecta sin abrir la Laptop.

> El primer emparejamiento (token y `mc_key`) viaja en claro por la LAN, igual que el token siempre lo ha hecho: hazlo en una red de confianza. Para revocar una placa pulsa **Olvidar placa** o borra su bloque del fichero.

---

## 13. Probar con el generador (pestaña Visualizar)

El generador de la pestaña **Visualizar** envía a la placa líneas con el formato de siempre, `clave:valor`. **No añade nada al protocolo**: tu sketch las trata igual que las de un Bloque IO de salida.

| Propiedad | Valor |
| :--- | :--- |
| Formato | `<clave>:<0-255>\n`, entero |
| Ritmo | Como máximo 10 líneas/s y solo si el valor cambió (el mismo límite que los Bloques IO de salida, un envío cada 2 ticks) |
| Al detenerse | Envía `<clave>:0` una vez, para dejar el actuador en reposo |
| Clave | `[A-Za-z0-9_.-]`, hasta 32 caracteres |
| Consola | No escribe en la consola para no llenarla |

**Formas de onda** (periodo de 1, 2, 5 o 10 s; amplitud del 100, 50 o 25 % de 0-255):

| Forma | Valores |
| :--- | :--- |
| Rampa | Sube de 0 a 255 y vuelve a 0 de golpe |
| Triángulo | Sube y baja suavemente |
| Cuadrada | 0 la primera mitad del periodo y 255 la segunda |
| Seno | Senoidal, empieza en 0 |
| Escalera | 16 escalones: 0, 17, 34… 255, uno por cada nivel de redstone |

Con un LED en un pin PWM, la rampa debe verse como un fundido de brillo y la escalera como 16 niveles distintos. Si el LED responde al generador pero no a la redstone, el fallo está en el bloque (modo, `Target Data` o lados), no en la placa ni en el cableado.

### Qué registra el mod

Cada línea `clave:valor` recibida (**RX**) o enviada (**TX**) se guarda con la hora real en que ocurrió. La pestaña Visualizar dibuja ese registro.

* Se aceptan valores con signo y decimales (hasta 9 dígitos enteros y 6 decimales) **solo para mostrarlos**: los Bloques IO trabajan con enteros de 0 a 255.
* Se ignoran las líneas que no tengan la forma `clave:número`: banners de arranque, texto libre y las líneas de identificación `mc_id:`.
* Se guardan hasta **16 series** (cada pareja sentido + clave) y ~4000 muestras por serie; al llenarse se descarta lo más antiguo.
* Lo recibido se anota **antes** del limitador de ritmo: se ve lo que la placa envió, no lo que sobrevivió al filtro.
