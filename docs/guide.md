# Guía SerialCraft - v0.4.3 (Beta)

Bienvenido a la guía oficial de **SerialCraft**. La versión 0.4.3 introduce un salto generacional en la forma en que interactúas con tu hardware desde el juego, destacando una nueva interfaz de usuario inmersiva y soporte ampliado para **comunicación bidireccional real**.

## Sobre el Proyecto y Filosofía

SerialCraft es un proyecto de código abierto creado por **Leonardo Aliaga** (@aliaga1924). Nace con el firme propósito de ser un **proyecto de aprendizaje**, enfocado en explorar la sinergia entre el desarrollo de software (modding en Java), el diseño de hardware físico y las telecomunicaciones.

En este proyecto, el uso de herramientas de Inteligencia Artificial se realiza de forma estratégica. **El proyecto no depende de las IAs para existir.** Primero se establece la arquitectura, la estructura lógica y la ingeniería del sistema; posteriormente, la IA se usa como asistente para ejecutar, refinar y traducir esas ideas en código de forma más rápida.

---

## Novedades en v0.4.3

* **Nueva Interfaz Gráfica (UI) Inmersiva y Didáctica:** Hemos dejado atrás las configuraciones complejas. La nueva GUI es moderna y te guía visualmente paso a paso. Ya no necesitas tener conocimientos técnicos sobre qué es un puerto COM o los baudios; la interfaz escanea tu sistema y te permite conectarte a tu hardware con solo unos cuantos clics.
* **Comunicación Bidireccional Simultánea:** El Bloque IO ahora es capaz de leer (sensores físicos) y escribir (activar LEDs/Motores) en tiempo real usando un mismo código.
* **Soporte Inalámbrico (Wi-Fi) y USB:** Tú eliges. Conecta placas estándar por cable USB o utiliza microcontroladores con Wi-Fi (como el ESP32) para enlazarlos vía red local (Sockets TCP).

---

## Primeros Pasos: Instalación

1. Descarga e instala [Fabric Loader](https://fabricmc.net/).
2. Descarga el archivo `.jar` de SerialCraft v0.4.3.
3. Coloca el `.jar` en tu carpeta `mods` de Minecraft (junto con `Fabric API`).
4. Inicia el juego.

---

## Configuración de Conexión (La Nueva UI)

Interactuar con tu entorno es más fácil que nunca gracias al nuevo gestor visual. Solo sigue estos pasos según tu hardware:

### Opción A: Conexión mediante USB Serial (Recomendada)
Ideal para plataformas robustas y de rápida respuesta, como el **Arduino Uno Q de Qualcomm**.

1. Conecta tu placa al puerto USB de tu computadora.
2. Dentro de Minecraft, coloca un **Bloque Conector (Connector Block)** en el suelo y haz clic derecho sobre él.
3. Se abrirá la nueva y didáctica Interfaz de SerialCraft. Dirígete a la sección **Conexión Serial**.
4. ¡Olvida adivinar puertos! El sistema de escaneo automático detectará tu Arduino Uno Q y te lo mostrará en una lista desplegable amigable.
5. Selecciona tu placa y haz clic en **Conectar**. El mod configurará los baudios por defecto y un indicador verde confirmará el enlace.

### Opción B: Conexión Inalámbrica (Wi-Fi)
Perfecta para proyectos de domótica. En este modo, SerialCraft se conecta a tu placa mediante la IP de tu red local.

1. Asegúrate de que tu PC y tu placa física estén en la **misma red Wi-Fi**.
2. En la interfaz gráfica del Bloque Conector, selecciona la pestaña **Red Inalámbrica**.
3. Ingresa la Dirección IP local de tu placa (ej. `192.168.1.50`). La interfaz es didáctica y te avisará si el formato es incorrecto.
4. Presiona **Conectar**. Para más detalles técnicos y ejemplos de código sobre esta conexión, revisa la sección de **[Referencia de Protocolo](/protocol)**.

---

## Tu Primer Circuito Bidireccional

1. Con el **Bloque Conector** activo, coloca un **Bloque IO (Arduino IO Block)** a su lado (o conéctalos con polvo de Redstone).
2. Haz clic derecho sobre el Bloque IO para acceder a su menú.
3. En el campo **Target Data (ID del Bloque)**, escribe un nombre único para este componente, por ejemplo: `alarma` o `btn_1`.
4. Define su comportamiento:
    * **OUTPUT:** Para que Minecraft envíe señales a tu placa.
    * **INPUT:** Para que tu placa envíe señales a Minecraft.
5. Carga el código correspondiente en tu microcontrolador usando nuestro idioma `CLAVE:VALOR` detallado en la **[Referencia de Protocolo](/protocol)**.