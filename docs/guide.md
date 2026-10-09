# Guía SerialCraft — v0.4.6 (Beta)

Bienvenido a la guía oficial de **SerialCraft**. La 0.4.6 (Beta) incorpora telemetría del juego hacia el hardware, estandariza la comunicación por canales lógicos y optimiza el rendimiento tanto en el cliente de Minecraft como en microcontroladores con recursos reducidos (ej. Arduino Uno ATmega328P).

## Sobre el proyecto y su filosofía

SerialCraft es un proyecto de código abierto creado por **Leonardo Aliaga** (@aliaga1924). Nace con el propósito de ser un **proyecto de aprendizaje**, enfocado en la sinergia entre el desarrollo de software (modding en Java con Fabric), el diseño de hardware físico y las telecomunicaciones.

El uso de herramientas de inteligencia artificial es estratégico. **El proyecto no depende de las IAs para existir.** Primero se establece la arquitectura y la lógica del sistema; después la IA se usa como asistente para ejecutar, refinar y traducir esas ideas en código más rápido.

---

## Novedades en v0.4.6 (Beta)

* **Telemetría del juego (Minecraft ➔ Hardware)**:
  La nueva pestaña **Eventos** en la Laptop permite transmitir en tiempo real datos del juego (hora del día, hambre, vida, nivel, daño recibido, etc.) hacia la placa física mediante el formato `mc_<canal>:<valor>\n`.
* **Prefijo reservado `mc_`**:
  Se reserva el prefijo `mc_` exclusivamente para los canales de telemetría del juego. Los Bloques IO no pueden usar `Target Data` que empiece por `mc_` (el servidor lo rechaza con un aviso amigable), garantizando que las órdenes de redstone y la telemetría nunca colisionen.
* **Control de flujo y ritmo (TelemetryOutbox)**:
  La cola de telemetría dosifica los envíos a un máximo estricto de 1 línea por tick (20 líneas/s) priorizando sucesos frente a estados, evitando desbordar el buffer serial de 64 bytes del hardware (ATmega328P).
* **Reenvío periódico cada 5 segundos**:
  Para placas como Arduino Uno R3 que se reinician al abrir la conexión USB (DTR), el mod reenvía periódicamente los estados activos para que la placa siempre reciba el estado actual sin requerir intervención manual.
* **Valores en vivo en la interfaz**:
  La pestaña **Eventos** muestra al lado de cada interruptor el valor actual en el cable serial (`mc_hunger:20`), y la consola resalta los paquetes de telemetría (`TM:`).
* **Migración a Minecraft 26.2 y Java 25**:
  El mod corre sobre Fabric Loader para Minecraft 26.2 y requiere **Java 25** como entorno de ejecución estándar.
* **Pestaña Visualizar (banco de pruebas)**:
  La Laptop incorpora una pestaña **Visualizar** con tres herramientas: una **línea de tiempo** que muestra varias señales a la vez (sensores, valores que envía el juego y telemetría `mc_*`), una vista **Sensor** que compara el valor crudo con su conversión analógica a redstone y sugiere una zona muerta, y un **generador** de ondas para comprobar que la placa responde sin construir un circuito de redstone. Todo con la hora real de cada mensaje, uniendo solo puntos reales (nunca una curva inventada). Ver [Banco de pruebas](#banco-de-pruebas-pestana-visualizar).
* **Identificación de la placa**:
  Un chip puente (CH340, CP2102) no dice qué placa hay detrás, así que el mod ya no llama «Arduino» a cualquier placa con CH340. Reconoce por USB el Arduino UNO Q, UNO R3/R4, Mega, Nano ESP32 y los ESP32 con USB nativo; para el resto muestra «Placa con CH340 (modelo sin identificar)» hasta que la propia placa se anuncia con `mc_id`. Ver [protocolo, sección 12](/protocol#_12-identificacion-de-la-placa-y-reconexion-sin-token).
* **Placas recordadas (Wi-Fi sin token)**:
  Con **Recordar placa** (pestaña Inicio) una placa Wi-Fi guarda una clave y se reconecta sin teclear el token. Si hay placas recordadas, el servidor Wi-Fi arranca solo al entrar a un mundo. Ver [protocolo, sección 12](/protocol#_12-identificacion-de-la-placa-y-reconexion-sin-token).
* **La Laptop ya no pausa el juego**:
  En un mundo de un jugador, abrir la Laptop deja el mundo en marcha. Así la telemetría (`mc_*`: hora, clima, salud…) sigue llegando a la placa y se ve cambiar en Visualizar. Ten en cuenta que, con la Laptop abierta, el jugador sigue en el mundo y puede recibir daño.
* **Eliminación de ComputerCraft**:
  Se eliminaron las clases de integración periférica (`ArduinoPeripheral` y `CCIntegration`), simplificando el mod para centrarse exclusivamente en la interacción nativa de hardware real con redstone y telemetría.
* **Recetas integradas**:
  Las recetas de crafteo están completamente integradas en los assets para supervivencia y son compatibles con el libro de recetas vainilla y visores como JEI/REI.

---

## Cambios previos introducidos en v0.4.3

* **Escala unificada 0-255.** El cable habla en el mismo rango en ambos sentidos. Antes la salida enviaba 0-255 pero la entrada se recortaba a 0-15. En modo digital ahora se envía 255, no `1`.
* **Wi-Fi con emparejamiento.** El mod es servidor TCP y la placa cliente, en el puerto **25585**, con token obligatorio.
* **Preparado para servidor dedicado.** Las placas se indexan al cargarse del disco.
* **Límite de ritmo.** 40 mensajes/s por jugador, con ráfaga de 80.
* **Interfaz reorganizada** en páginas independientes, con recorte de texto correcto en cualquier idioma.
* **Cuatro localizaciones incluidas:** inglés, español de España, de México y de Argentina (con voseo).

::: warning Compatibilidad
Los sketches de la 0.3.x **no funcionan sin cambios**. Consulta la sección [Cambios respecto a 0.3.x](/protocol#_8-cambios-respecto-a-0-3-x) del protocolo.
:::

---

## Instalación

1. Instala [Fabric Loader](https://fabricmc.net/) para **Minecraft 26.2** (requiere **Java 25**).
2. Descarga `Fabric API` y el `.jar` de SerialCraft v0.4.6-beta.
3. Coloca ambos en tu carpeta `mods`.
4. Inicia el juego.

---

## Los tres elementos del mod

| Elemento | Para qué sirve |
| :--- | :--- |
| **Laptop** | Objeto de mano. Abre la interfaz: conexión, lista de placas, eventos, visualizar (osciloscopio) y consola. |
| **Bloque Conector** | Ancla la conexión por USB en el mundo y guarda los baudios. |
| **Módulo de E/S** | Puente entre redstone y hardware. Tiene nombre, **Canal**, dirección de datos, señal digital/analógica, lógica OR/AND/XOR y cinco terminales configurables en el mundo. |

---

## Configuración de la conexión

### Opción A: USB Serial

1. Conecta la placa al puerto USB del ordenador.
2. Coloca un **Bloque Conector** y haz clic derecho.
3. En la pantalla de bienvenida de conexión, el escaneo lista los puertos disponibles.
4. Selecciona la placa y pulsa **Conectar**.

::: tip Los baudios deben coincidir
El valor por defecto es **115200**. Si en el sketch pones `Serial.begin(9600)` y el bloque está a 115200, no llega nada legible. Es el fallo más habitual, y no da ningún error visible: simplemente no pasa nada.
:::

### Opción B: Wi-Fi

1. PC y placa en la **misma red local**.
2. Abre la Laptop → **Inicio** → **Iniciar servidor Wi-Fi**.
3. La interfaz muestra tres datos: **IP local**, **puerto** (25585) y **token de enlace**.
4. Copia esos tres valores al sketch o al script de la placa.
5. La placa se conecta y envía el token como primera línea; el mod responde `OK`.

::: danger Alcance de seguridad
El canal Wi-Fi va **en texto claro**. El token evita el acceso casual dentro de tu red, pero no es cifrado. Úsalo en una red doméstica o de aula; **no redirijas el puerto 25585 en el router**.
:::

---

## Tu primer circuito bidireccional

Consulta la [guía del Módulo de E/S](/io-module) para conectores, lógica, diagnóstico y cambios de compatibilidad.

1. Coloca un **Módulo de E/S** y haz clic derecho en su base. Desde **Placas → Configuración** puedes editar nombre, Canal, dirección, señal y lógica. Los botones **?** explican cada opción; **Diagnóstico** muestra RX/TX y el estado de la conexión.
2. En **Canal**, escribe un identificador único, por ejemplo `led_verde`.
3. Elige el modo:
   * **Minecraft → Hardware** (antiguo OUTPUT) — Minecraft envía a la placa (encender un LED, mover un motor).
   * **Hardware → Minecraft** (antiguo INPUT) — la placa envía a Minecraft (un botón, un sensor).
4. Elige el tipo de señal:
   * **Digital** — encendido o apagado (0 o 255 en el cable).
   * **Analógica** — proporcional a la redstone (0-255 en el cable).
5. **En el mundo**, haz clic derecho sobre el terminal para entrada verde, o Shift + clic derecho para salida roja. Usa una entrada verde para `led_verde` (Minecraft → Hardware) y una salida roja para `pot_val` (Hardware → Minecraft).
6. Carga en la placa el sketch correspondiente.

Con dos Módulos de E/S —uno en dirección **Hardware → Minecraft** con canal `pot_val` y otro en **Minecraft → Hardware** con canal `led_verde`— tienes el circuito bidireccional de los ejemplos. El botón **Guardar** requiere cambios pendientes y espera una respuesta del servidor; si cancelas con cambios sin guardar, se solicita confirmación.

👉 **[Ejemplos listos para cargar y probar](/ejemplos/)** — Arduino Uno R3, ESP32 y Arduino Uno Q, con esquemas de conexión.

---

## Banco de pruebas (pestaña Visualizar)

La pestaña **Visualizar** sirve para ver qué viaja entre el juego y la placa y para comprobar que la placa responde, sin construir circuitos de redstone.

::: info No es un osciloscopio de laboratorio
Por el cable van mensajes de texto con valores de 0 a 255, unas pocas decenas por segundo. Lo que ves es **qué mensajes llegaron y cuándo**; no hay nada que mostrar de lo que pase entre dos mensajes.
:::

### Qué hay en pantalla

| Fila | Controles |
| :---: | :--- |
| 1 | **Vista** (Línea de tiempo o Sensor), **Ventana** (5, 10, 30 o 60 s), **Pausar** y **Limpiar** |
| 2 | Campo de **canales** (línea de tiempo) o de **sensor** (vista Sensor), y el botón **Trazo** (Línea o Escalón) |
| 3 | **Generador**: Generar/Detener, forma, periodo, amplitud y la clave a enviar |

Bajo los controles, una línea de estado avisa si el generador está en marcha, si algo falló o si la gráfica está en pausa.

La Laptop **no pausa el juego** en un mundo de un jugador: el mundo sigue en marcha mientras la usas, así que vigila que no te ataquen.

### Cómo leer las gráficas

* **Verde (RX):** lo que **la placa envía** al juego, por ejemplo un sensor `pot_val`.
* **Naranja (TX):** lo que **el juego envía** a la placa: Bloques IO de salida, telemetría `mc_*` y el generador.
* El **trazo** tiene dos estilos, que se alternan con el botón **Trazo**. **Línea** (por defecto) une los mensajes reales con rectas, así una onda se ve como onda. **Escalón** mantiene el valor hasta el siguiente mensaje, tal como lo vio el juego. Ninguno es una curva suavizada, porque una curva dibujaría valores que nunca se enviaron. En modo Línea, si entre dos mensajes pasan más de 0,3 s no se unen (un sensor que estuvo quieto no cambió poco a poco): el valor se mantiene hasta el siguiente. Cuando hay pocos mensajes, cada uno se marca con un punto blanco.
* `mc_damage` y `mc_death` son **sucesos**, no estados: se dibujan como barras en el instante en que ocurrieron.
* La escala es 0-255. Se amplía a 1023, 4095 o 65535 si la señal los supera (un ADC de 10 o 12 bits), y los canales `mc_*` usan su rango propio.

Se registra lo que la placa envió de verdad, incluso los mensajes que el limitador de ritmo descarta antes de llegar al bloque.

### Línea de tiempo

Muestra varias señales en pistas que comparten el mismo eje de tiempo, para responder a «¿dónde se rompe la cadena?»: ¿el sensor envía?, ¿el juego recibe?, ¿la placa responde?

* Con el campo de canales **vacío** se muestran las señales con actividad en los últimos 2 minutos, las más recientes que quepan en pantalla; el resto se anuncia como «+N señales más».
* Para elegir cuáles ver, escribe nombres separados por coma o espacio. Cada uno puede ser exacto o terminar en `*`: `pot_val, led_verde` o `mc_*`.
* El orden de las pistas es fijo (primero RX, luego TX, por orden alfabético): no saltan de sitio cuando un sensor habla.

### Vista Sensor

Muestra **una** señal con detalle: el valor crudo del cable (verde) y su **conversión analógica a redstone** (naranja), dibujados en la misma escala. A la derecha, el eje `RS 0-15`.

| Fila | Qué significa |
| :--- | :--- |
| Valor | Último valor crudo y la redstone que le corresponde |
| Mensajes/s | Cuántos mensajes por segundo envía la señal (últimos 3 s) |
| Rango | Mínimo y máximo de la ventana y el promedio, ponderado por tiempo |
| Variación 3 s | Máximo menos mínimo en los últimos 3 s, y la **zona muerta sugerida** |
| Redstone | Cuántas veces cambió el nivel de redstone en los últimos 3 s |

**Cómo calibrar un sensor:**

1. Abre la vista **Sensor** y escribe la clave (por ejemplo `pot_val`). Vacío, muestra el último sensor activo.
2. Deja el sensor **quieto** unos segundos.
3. Mira **Variación 3 s**: con el sensor quieto, ese número es su ruido. Si es 0, la señal es limpia.
4. En el sketch, usa una zona muerta igual o mayor que el valor sugerido: `if (abs(valor - ultimo) >= 4) { enviar(); }`.
5. Si **Redstone** cambia varias veces con el sensor quieto, el valor está sobre el borde entre dos niveles de redstone (cada nivel son 17 unidades del cable) y parpadea. La zona muerta lo corrige.

::: warning La conversión a redstone es la de una señal Analógica
Un bloque **Digital** convierte cualquier valor mayor que 0 en 15. La vista solo muestra la conversión cuando la escala es 0-255 y la clave no empieza por `mc_`. No evalúa los conectores, la compuerta ni la habilitación de cada módulo: consulta su diagnóstico para conocer la redstone realmente emitida.
:::

### Generador

Envía una onda de prueba a una clave de la placa. Sirve para comprobar que un LED, un servo o un motor responden **sin construir un circuito de redstone**.

1. Conecta la placa.
2. Escribe la clave del actuador en el campo de la derecha (por defecto, `led_verde`).
3. Elige la **forma**: Rampa, Triángulo, Cuadrada, Seno o Escalera (16 escalones, uno por cada nivel de redstone).
4. Elige el **periodo** (1, 2, 5 o 10 s) y la **amplitud** (100, 50 o 25 % de 0-255).
5. Pulsa **Generar**. En la línea de tiempo verás la pista naranja con tu clave y, si la placa responde con un sensor (por ejemplo una LDR frente al LED), su pista verde justo debajo.

Reglas del generador:

* Envía enteros de 0 a 255, como máximo **20 mensajes por segundo** (uno por tick) y solo cuando el valor cambia: el mismo techo que la telemetría del juego. A ese ritmo un seno de 2 s tiene 40 puntos por ciclo, suficientes para que se vea como una onda.
* La clave admite letras, números, `_`, `.` y `-`, hasta 32 caracteres.
* Sigue funcionando aunque cambies de pestaña. **Se detiene** al pulsar Detener, al cerrar la Laptop o si se pierde la conexión.
* Al detenerse por tu orden o al cerrar la Laptop, deja la placa en reposo enviando `clave:0`.

::: warning Envía valores reales a tu hardware
Si hay un servo o un motor conectado, empieza con la amplitud al 25 %.
:::

---

## Límites conocidos de esta versión

Vale la pena conocerlos antes de montar algo grande:

* La lista de módulos y el editor usan una distribución adaptable y desplazamiento, pero conviene comprobar visualmente su legibilidad en tu resolución y escala GUI reales. El editor de v0.4.6-beta todavía **no incluye una vista gráfica interactiva de los cinco conectores**; se configuran en el propio mundo.
* No existe un modo en el que **el servidor** sea dueño del hardware. El puerto serie vive en el ordenador de cada jugador, así que el modelo es "cada jugador controla sus propias placas desde su PC". Esto es una decisión de arquitectura, no un olvido.
* El canal Wi-Fi no está cifrado.
* La pestaña Visualizar guarda las últimas ~4000 muestras de hasta 16 señales a la vez. Es un registro de **mensajes**, no un osciloscopio: no ve nada que ocurra entre dos mensajes.
* El generador envía como máximo 20 mensajes por segundo.

---

## Multijugador

Funciona en un jugador, en LAN y en servidor dedicado. Cada jugador ve y controla **solo sus propias placas**; los operadores con el permiso `serialcraft.admin.bypass` pueden operar las de otros.

Cifras razonables: entre 10 y 50 placas por jugador (el límite práctico lo pone la interfaz, no el servidor), varios cientos por servidor, y en torno a 2 KB/s de tráfico por placa activa.
