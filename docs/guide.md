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
* **Pestaña Visualizar (Monitor y Osciloscopio en tiempo real)**:
  La Laptop incorpora una nueva pestaña **Visualizar**, que ofrece una gráfica interactiva en tiempo real del flujo de señales de los bloques IO (entradas y salidas analógicas y digitales) para diagnosticar circuitos rápidamente.
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
* **Cinco idiomas reales:** inglés, español de España, de México, de Perú y de Argentina (con voseo).

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
| **Bloque IO** | El puente real. Cada uno tiene un `Target Data`, un modo (INPUT/OUTPUT), un tipo de señal (Digital/Analógica) y lados configurables. |

---

## Configuración de la conexión

### Opción A: USB Serial

1. Conecta la placa al puerto USB del ordenador.
2. Coloca un **Bloque Conector** y haz clic derecho.
3. En la pestaña **Conexión**, el escaneo lista los puertos disponibles.
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

1. Coloca un **Bloque IO** y haz clic derecho.
2. En **Target Data**, escribe un identificador único, por ejemplo `led_verde`.
3. Elige el modo:
   * **OUTPUT** — Minecraft envía a la placa (encender un LED, mover un motor).
   * **INPUT** — la placa envía a Minecraft (un botón, un sensor).
4. Elige el tipo de señal:
   * **Digital** — encendido o apagado (0 o 255 en el cable).
   * **Analógica** — proporcional a la redstone (0-255 en el cable).
5. Configura los lados que actúan como entrada de redstone.
6. Carga en la placa el sketch correspondiente.

Con dos bloques IO —uno `INPUT` llamado `pot_val` y otro `OUTPUT` llamado `led_verde`— ya tienes el circuito completo de los ejemplos.

👉 **[Ejemplos listos para cargar y probar](/ejemplos/)** — Arduino Uno R3, ESP32 y Arduino Uno Q, con esquemas de conexión.

---

## Límites conocidos de esta versión

Vale la pena conocerlos antes de montar algo grande:

* La lista de placas de la Laptop **no tiene scroll**. A partir de unas 8 placas, las siguientes quedan fuera de la pantalla.
* La interfaz está pensada para resoluciones normales; con la escala de GUI al máximo en 854×480 las tarjetas se salen del área visible.
* No existe un modo en el que **el servidor** sea dueño del hardware. El puerto serie vive en el ordenador de cada jugador, así que el modelo es "cada jugador controla sus propias placas desde su PC". Esto es una decisión de arquitectura, no un olvido.
* El canal Wi-Fi no está cifrado.

---

## Multijugador

Funciona en un jugador, en LAN y en servidor dedicado. Cada jugador ve y controla **solo sus propias placas**; los operadores con el permiso `serialcraft.admin.bypass` pueden operar las de otros.

Cifras razonables: entre 10 y 50 placas por jugador (el límite práctico lo pone la interfaz, no el servidor), varios cientos por servidor, y en torno a 2 KB/s de tráfico por placa activa.
