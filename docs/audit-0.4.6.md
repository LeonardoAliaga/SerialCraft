# Auditoría y estabilización — SerialCraft 0.4.6-beta

Trabajo local en `test`, iniciado con el árbol limpio. Se conservaron versiones de Minecraft, Java, Fabric y dependencias de ejecución. No se realizaron commits, push, merge ni publicaciones.

## A. Diagnóstico del Bloque IO

La causa principal era la mezcla de responsabilidades en `redstoneOutput`: el mismo campo guardaba tanto redstone leída como energía emitida. Minecraft → Hardware podía alimentar conectores de salida con su propia lectura. Hardware → Minecraft descartaba muestras mientras su compuerta estaba cerrada y borraba el estado, por lo que abrirla otra vez no recuperaba la señal hasta recibir otro mensaje.

OR/AND/XOR condicionaban recepción, pero no procesaban transmisión. Los niveles analógicos se reducían siempre al máximo, incluso con AND/XOR. El muestreo periódico seguía ejecutándose aunque las entradas estuvieran estables. El bloque empezaba visualmente deshabilitado y su entidad habilitada; `BLINKING` no representaba actividad real. El terminal inferior tenía zona de interacción, pero no volumen seleccionable en la forma.

El editor anunciaba éxito y cambiaba datos locales antes de validación. Reconstruir controles podía restablecer nombre/canal. La lógica estaba desactivada al transmitir. Un filtro global de 25 ms descartaba mensajes de sensores diferentes. La reconexión no invalidaba deduplicación TX de forma fiable.

Ya existían antes de esta intervención potencia débil/fuerte, inversión correcta de la dirección consultada por Minecraft, registro por dueño/dimensión, eventos de carga/descarga y validaciones de paquetes. No se atribuyen esas correcciones a este trabajo. La lista de placas ya tenía scroll; se corrigió la documentación que decía lo contrario.

## B. Diseño final

Se mantienen **un modo global y un canal por módulo**. `IoMode.INPUT` persistido significa Hardware → Minecraft; `OUTPUT`, Minecraft → Hardware. `IOSide.INPUT/OUTPUT` describe exclusivamente recibir/emitir redstone. No hay bidireccionalidad simultánea en un módulo; se usan dos módulos y canales separados.

| Cara configurada | Hardware → Minecraft | Minecraft → Hardware |
| --- | --- | --- |
| Desactivada | No lee/emite | No lee/emite |
| Entrada | Condición opcional para la muestra RX | Entrada al cálculo enviado |
| Salida | Emisión débil y fuerte | Inactiva; conserva configuración |

Norte, sur, este, oeste y abajo son configurables; arriba no existe como terminal. La selección del vecino inferior se realiza sobre el terminal visible en la superficie. Se conserva la interacción directa: clic derecho alterna entrada/desactivado; Shift + clic derecho alterna salida/desactivado. El editor presenta un ciclo explícito desactivado→entrada→salida.

OR calcula máximo, AND mínimo incluyendo ceros, XOR máximo con una cantidad impar de entradas positivas. Digital normaliza a 0/15. En TX se convierte ese resultado a 0..255; en RX se usa como condición y se conserva la magnitud del hardware. Sin entradas, RX pasa directamente y TX calcula cero. Las conversiones analógicas son redstone×17 y hardware/17 redondeado; 128 produce 8.

`IoSignalState` separa muestra recibida, redstone agregada, resultado y emisión. La entidad mantiene aparte último valor remitido al cliente, antigüedad RX, configuración y sesión. **No existe ACK físico**: el servidor no afirma que el hardware haya procesado un paquete. La configuración se guarda; RX, TX y conexión nunca se recuperan como estados válidos tras reiniciar.

Los cambios de vecinos marcan entradas sucias y se agrupan por tick. Solo cambia la emisión cuando cambia el resultado. Se notifican también vecinos de conductores alimentados fuertemente y salidas anteriores al reconfigurar/retirar. Se oculta la emisión propia durante una lectura de conductores. Un circuito de polvo que une físicamente entrada y salida sigue siendo una realimentación externa: se documenta y requiere separar caminos; no se promete atribuir el origen de toda energía con una consulta local.

El cliente reporta sesiones físicas; cambiar conexión/dimensión invalida RX y fuerza reenvío TX. USB repite TX tras dos segundos para cubrir el arranque. Cambiar canal/dirección, descargar o retirar un emisor solicita cero al canal anterior; la cola cancela órdenes pendientes de ese canal y prioriza esa parada. El silencio no inventa una desconexión. Los ejemplos suministran muestras periódicas para recuperar RX tras carga de chunks.

Descargar posteriormente un módulo de una dimensión inactiva no solicita un segundo cero que pueda apagar otro módulo del mismo canal en la dimensión actual.

El editor conserva borradores, configura sin hardware, valida canal y conectores, espera respuesta correlacionada y muestra rechazos o respuesta no confirmada. Su vista secundaria muestra caras y RX/TX/lectura/emisión/conexión, sin presentar TX como confirmación física. Las listas se asocian a dimensión y tienen espera acotada.

## C. Cambios implementados

| Área | Archivos principales y efecto |
| --- | --- |
| IO | `ArduinoIOBlock`/`ArduinoIOBlockEntity` pasan a `HardwareIOBlock`/`HardwareIOBlockEntity`; flujo separado, geometría inferior, limpieza de energía y muestreo por cambios |
| Núcleo | Nuevos `IoLogic`, `IoSignalState`, `HardwareSessions`; `IOSide` empaqueta cinco caras; enums rechazan valores de red inválidos |
| Registro | `BoardRegistry`, `ModBlocks`, `ModBlockEntities`: carga/descarga y reindexación sin duplicados; limpieza y parada del canal |
| Red | `ConfigPayload`, `BoardInfo`, `IoSnapshot`, `ConfigResultPayload`, `HardwareLinkPayload`, listas y salida v2; validación autoritativa y respuestas correlacionadas |
| Transporte | `ChannelInbox`, `HardwareOutbox`, `ConnectionResult`, `ConnectionManager`, `SerialHandler`, `WifiHandler`: colas acotadas, conexión verificable, sesión y cierre seguros |
| Interfaz | `BoardsPage`, `PanelUI`, `WelcomePage`, `SerialCraftClient`, `SerialDebugHud`: borrador persistente, direcciones completas, diagnóstico, errores y contadores |
| Recursos | `blockstates/io_block.json`, cuatro locales y `fabric.mod.json`: habilitación/dirección/coherencia visual; IDs y modelos originales conservados |
| Resto | `GameEventsTracker`, `TelemetryOutbox`, `VisualizePage`, `TrustedBoardStore`, `ConnectorBlock/Entity`, `SerialConfig`: reconexión, cancelación, propiedad, sesiones y credenciales |
| Verificación | Pruebas JUnit, GameTests, `build.gradle`, `SerialCraftDataGenerator`, `ModRecipeGenerator`, `.github/workflows/build.yml` |
| Documentación | Guías/protocolos ES/EN, README, nuevo diseño/tutorial/matriz/informe y ejemplos Uno R3/ESP32/Uno Q |

Se eliminaron parpadeos de BlockState sin eventos reales. Se conserva MODE=2 para decodificar estados históricos y se normaliza al primer tick. Las combinaciones pasan de 2916 a 1458; los cinco conectores siguen almacenados en la paleta del chunk. Los colores indican configuración, y los LEDs habilitación/dirección; no se simula actividad física. No se añadieron umbrales, filtros, calibración ni otro motor de señales sin evidencia de necesidad.

## D. Compatibilidad

Bloque y BlockEntity conservan **`serialcraft:io_block`**. Se mantienen recetas, rutas de modelos/texturas y propiedades de las cinco caras. Las claves NBT de configuración se conservan, y se leen tanto nombres de enum como ordinales anteriores. El codec real de paleta acepta propiedades visuales antiguas desconocidas; se ignora `redstoneOut` temporal.

Los canales antiguos inválidos se conservan saneados y acotados para revisión y deshabilitan el módulo. Un toggle de encendido no puede saltarse esa validación, y tampoco se emiten ceros hacia canales reservados/inválidos. Enums desconocidos también deshabilitan para evitar convertir datos corruptos en comandos físicos. Datagen 26.2 omite `count: 1` por ser el valor predeterminado: se compararon ambas recetas con HEAD normalizando ese default; ingredientes, patrones, cantidad e IDs coinciden y ambos avances son idénticos.

Se cambió deliberadamente la semántica de AND/XOR al transmitir y se elimina emisión en Minecraft → Hardware; revisar circuitos que dependían de esos defectos anteriores.

El formato físico `canal:0..255\n`, USB 115200, token y HMAC siguen compatibles con sketches actuales válidos. No se admite el formato histórico 0.3.x como si fuera 0.4.6; su documentación permanece archivada. Los ejemplos ahora incluyen primera muestra cero e instantáneas cada segundo. Firmware anterior que solo envía cambios puede necesitar reenviar RX después de cargar un módulo.

**Cliente y servidor deben actualizarse juntos**: configuración, listado, toggle y salida interna usan payloads v2. No se cambian IDs persistidos del mundo. Integraciones Java externas que importen las clases renombradas deben recompilarse. No se verificaron copias de regiones de un mundo histórico real; las pruebas NBT/paleta no sustituyen esa comprobación.

## E. Pruebas

Se detectó una dependencia implícita de datagen/sourcesJar y se corrigió su orden. Las pruebas ampliadas encontraron una carrera entre publicar CONNECTED y actualizar la época Wi-Fi; se cambió la publicación a una operación coordinada. El cierre TCP con datos incompletos admite tanto EOF como RST. Un intento de compilar GameTests usó un método de posición inexistente en 26.2 y se corrigió antes de ejecutarlas. Estos fallos intermedios no se presentan como errores de descarga ni se omiten.

La comprobación final `build -PioGameTests runGameTest` terminó correctamente con **40 pruebas JUnit y 11 GameTests**, sin fallos ni omisiones, después de proteger la reactivación de canales antiguos inválidos. Diez GameTests pertenecen al mod y uno a Fabric. La ejecución previa de `build runDatagen` también aprobó las 40 pruebas y ejecutó el proveedor real de recetas. La [matriz](./io-test-matrix) distingue prueba automática, cobertura parcial y pendiente manual.

| Verificación ejecutada | Resultado |
| --- | --- |
| `./gradlew.bat build -PioGameTests runGameTest` | Compilación y empaquetado correctos; 40 JUnit y 11 GameTests aprobados |
| `./gradlew.bat build runDatagen` | Correcto; cuatro recursos generados conservados, sin recursos obsoletos eliminados |
| `npm ci` y `npm run docs:build` en `docs` | Instalación y generación de documentación correctas |
| JSON de recursos / sintaxis Python Uno Q | Válidos; no equivale a compilar firmware ni ejecutarlo en hardware |
| JAR final | Entrypoints y recetas presentes; clases GameTest excluidas |
| Revisión del diff | Sin errores de espacios; solo avisos de conversión CRLF de Git |

Las pruebas cubren conversión, lógica exhaustiva de 1–5 entradas digitales, lógica analógica, separación de estado, protocolo inválido, equidad/saturación de colas, prioridad de reposo, telemetría, buffer circular/estadísticas/envelopes/ondas, HMAC y confianza, rate limiter, configuración/red correlacionada, NBT y paletas históricas, cinco conectores y raycast inferior. Se ejecutaron sockets locales para autenticación, transmisión, reinicio y cierre de handshake incompleto.

Los diez GameTests propios cubren cinco caras débil/fuerte, gate sin nueva muestra, aislamiento TX, polvo en derivación, lámpara tras conductor, palanca/botón, repetidor, comparador/cofre con niveles 0/1/7/15, propiedad/distancia/chunk descargado y 64 módulos con retirada del índice. Fabric añade una prueba propia al total. Algunas ejecuciones anteriores emitieron avisos de atraso durante el arranque; la comprobación final no los emitió. Ninguna se usa como medición sostenida de TPS. JOML emitió una advertencia de `sun.misc.Unsafe`, y el helper de jugadores de GameTest está deprecado. No se cambiaron bibliotecas para ocultar advertencias.

## F. Mejoras adicionales de SerialCraft

La conexión USB devuelve un resultado verificado; el dashboard solo avanza con conexión real. Los transportes devuelven éxito de escritura, no lo infieren del estado. Se protegen lectores/salidas contra sesiones anteriores. Wi-Fi cierra sockets fallidos y handshakes pendientes, aplica un plazo absoluto de autenticación y evita que un hilo antiguo borre una sesión nueva. Un handshake rechazado no simula cerrar otra conexión. La identidad anunciada no puede sustituir el UID autenticado; se mantiene identificación de firmware antiguo sin UID al usar token.

RX usa turnos por canal, hasta 64 canales y ocho transiciones por canal; el envío al servidor sigue limitado a 40/s y el servidor conserva su limitador. IO/manual/telemetría/ondas comparten una cola y hasta 40 escrituras/s; IO mantiene FIFO, muestras continuas y estados se coalescen, y paradas tienen prioridad acotada. Bajo saturación hay descartes contados, no garantías de entrega ilimitada. Las sondas de identificación y entrega de clave son tráfico de control separado.

Telemetría reinicia estados por época de enlace y descarta pendientes al deshabilitar eventos, también en la cola compartida. Se mantienen el prefijo `mc_`, prioridades y ausencia de ACK para eventos. El generador rechaza canales reservados, cancela pendientes al detenerse y termina al cambiar de sesión. El visualizador conserva su recorder y motores existentes, registra RX antes del presupuesto y TX después de escribir, y se limpia al salir del mundo.

La confianza revierte cambios si falla el guardado, omite secretos en `toString`, aplica permisos POSIX antes de escribir y revoca cerrando la sesión. En Windows se heredan ACL del directorio. TCP sigue sin cifrar y la clave local no está cifrada; se mantienen advertencias y no se inventa una garantía de confidencialidad.

La laptop adquiere propietario, los paquetes comprueban permisos y su estado deriva de la sesión, nunca de una bandera persistida. Defaults de baudios se alinean a 115200, preservando configuraciones existentes. JSON nulo/malformado de configuración ya no impide obtener defaults. El error de formato de logger detectado por GameTest se corrigió.

Se recuperaron el entrypoint y el proveedor de recetas histórico, adaptados a la API 26.2. Una primera ejecución con un proveedor vacío detectó que datagen eliminaba recursos como obsoletos: se restauraron inmediatamente los archivos originales y se sustituyó el generador vacío por las recetas y avances reales, con los mismos IDs, ingredientes y desbloqueos. Se ordena datagen respecto de sourcesJar cuando ambas tareas se solicitan para evitar la dependencia implícita detectada por Gradle. Esta regla sigue la [validación oficial de Gradle](https://docs.gradle.org/current/userguide/validation_problems.html#implicit_dependencies_between_tasks). CI configura build y GameTests con Java 25 para `test` y PRs hacia `test`; no se ejecutó en GitHub porque no hubo push. MIT, cuatro idiomas, cuatro pestañas principales, scroll y documentación de canales/baudios quedan alineados con el repositorio. Se conserva el archivo histórico.

## G. Problemas pendientes

| Prioridad | Pendiente / límite | Validación requerida |
| --- | --- | --- |
| Antes de beta pública | Mundos históricos reales y ciclo guardar/reiniciar/descargar chunks | Copias de mundos de versiones anteriores; matriz de migración |
| Antes de beta pública | USB y Wi-Fi con Uno R3, ESP32 y Uno Q | Compilar firmware, bootloader, desenchufado, reconexión, confianza/revocación y LED real |
| Antes de beta pública | UI/modelos a diferentes escalas e idiomas | Cliente gráfico: raycast/interacción, texto, botones, scroll, errores y LEDs |
| Antes de beta pública | Multijugador completo y dimensiones | Dos clientes reales, permisos/admin, paquetes antiguos/retrasados y mismos canales |
| Antes de beta pública | Rendimiento sostenido | Perfil de MSPT/GC/actualizaciones/paquetes con 32–128 módulos; el test de 64 es funcional |
| Limitación de protocolo | No hay ACK, latido obligatorio ni detección de reinicio interno del firmware | Reconexión/reconfiguración para resincronizar; nueva versión de protocolo solo si se justifica |
| Limitación de circuitos | Realimentación externa y emisores/generadores con el mismo canal | Separar circuitos/canales; no hay arbitraje ni atribución global de fuentes |
| Limitación de transporte | USB y Wi-Fi simultáneos reciben broadcast; no hay asociación individual módulo→dispositivo | Probar uso simultáneo; emplear canales exclusivos en firmware |
| Limitación de carga | Colas acotadas pueden descartar transiciones; cambios del mundo inferiores a un tick pueden agruparse | Medir saturación; no anunciar pulsos garantizados |
| Seguridad | TCP/clave local sin cifrado; ACL Windows depende del directorio | Red de confianza y revisión de permisos locales; pruebas reales de autenticación |
| Mantenimiento | Loom sigue siendo el SNAPSHOT existente; helper GameTest y JOML tienen avisos | Seguimiento de upstream sin actualizar dependencias sin motivo |
| CI | Workflow local configurado, ejecución remota pendiente | Verificar cuando el usuario publique sus cambios |

La vista Sensor representa la conversión analógica general de una serie; el diagnóstico del módulo es la autoridad para su modo digital, compuerta y energía emitida. No se afirma que todas las curvas RX sean exactamente la salida de todos los módulos del mismo canal.

## H. Evaluación final

El núcleo IO tiene ahora un contrato explícito y pruebas de lógica, persistencia, redstone real y transporte local. La base está preparada para pruebas controladas de la beta. **La confiabilidad como beta pública con hardware, UI y mundos anteriores todavía requiere los pasos pendientes de la matriz**; no se declara certificada mediante pruebas unitarias ni una prueba breve de carga.

## Inventario de archivos

Rutas relativas al repositorio. M = modificado, A = añadido; las dos D corresponden a los nombres Java anteriores sustituidos por HardwareIO.

```text
M .gitignore
M README.md
M build.gradle
M docs/.vitepress/config.mts
M docs/ejemplos/code/esp32/serialcraft_esp32.ino
M docs/ejemplos/code/uno-q/main.py
M docs/ejemplos/code/uno-r3/serialcraft_uno_r3.ino
M docs/ejemplos/index.md
M docs/en/guide.md
M docs/en/protocol.md
M docs/guide.md
M docs/package-lock.json
M docs/package.json
M docs/protocol.md
M src/client/java/com/serialcraft/SerialCraftClient.java
M src/client/java/com/serialcraft/client/SerialDebugHud.java
M src/client/java/com/serialcraft/client/events/GameEventsTracker.java
M src/client/java/com/serialcraft/client/events/TelemetryOutbox.java
M src/client/java/com/serialcraft/client/ui/pages/BoardsPage.java
M src/client/java/com/serialcraft/client/ui/pages/VisualizePage.java
M src/client/java/com/serialcraft/client/ui/pages/WelcomePage.java
M src/client/java/com/serialcraft/connection/BoardLink.java
M src/client/java/com/serialcraft/connection/ConnectionManager.java
M src/client/java/com/serialcraft/connection/SerialHandler.java
M src/client/java/com/serialcraft/connection/WifiHandler.java
M src/client/java/com/serialcraft/identity/TrustedBoardStore.java
M src/client/java/com/serialcraft/screen/PanelUI.java
M src/main/generated/.cache/fd179bcf93fa6f91c06f2d4b9266be42dbecddf4
M src/main/generated/data/serialcraft/recipe/connector_block.json
M src/main/generated/data/serialcraft/recipe/io_block.json
D src/main/java/com/serialcraft/block/ArduinoIOBlock.java
M src/main/java/com/serialcraft/block/ConnectorBlock.java
M src/main/java/com/serialcraft/block/IOSide.java
M src/main/java/com/serialcraft/block/ModBlocks.java
D src/main/java/com/serialcraft/block/entity/ArduinoIOBlockEntity.java
M src/main/java/com/serialcraft/block/entity/ConnectorBlockEntity.java
M src/main/java/com/serialcraft/block/entity/ModBlockEntities.java
M src/main/java/com/serialcraft/board/BoardRegistry.java
M src/main/java/com/serialcraft/board/IoMode.java
M src/main/java/com/serialcraft/board/LogicMode.java
M src/main/java/com/serialcraft/board/SignalType.java
M src/main/java/com/serialcraft/config/SerialConfig.java
M src/main/java/com/serialcraft/network/BoardInfo.java
M src/main/java/com/serialcraft/network/BoardListRequestPayload.java
M src/main/java/com/serialcraft/network/BoardListResponsePayload.java
M src/main/java/com/serialcraft/network/ConfigPayload.java
M src/main/java/com/serialcraft/network/ModNetworking.java
M src/main/java/com/serialcraft/network/RemoteTogglePayload.java
M src/main/java/com/serialcraft/network/SerialOutputPayload.java
M src/main/java/com/serialcraft/network/guard/NetGuard.java
M src/main/resources/assets/serialcraft/blockstates/io_block.json
M src/main/resources/assets/serialcraft/lang/en_us.json
M src/main/resources/assets/serialcraft/lang/es_ar.json
M src/main/resources/assets/serialcraft/lang/es_es.json
M src/main/resources/assets/serialcraft/lang/es_mx.json
M src/main/resources/fabric.mod.json
A .github/workflows/build.yml
A docs/audit-0.4.6.md
A docs/en/io-module.md
A docs/io-design.md
A docs/io-module.md
A docs/io-test-matrix.md
A src/client/java/com/serialcraft/ModRecipeGenerator.java
A src/client/java/com/serialcraft/SerialCraftDataGenerator.java
A src/client/java/com/serialcraft/connection/ConnectionResult.java
A src/client/java/com/serialcraft/connection/HardwareOutbox.java
A src/gametest/java/com/serialcraft/HardwareIOGameTest.java
A src/gametest/resources/fabric.mod.json
A src/main/java/com/serialcraft/block/HardwareIOBlock.java
A src/main/java/com/serialcraft/block/entity/HardwareIOBlockEntity.java
A src/main/java/com/serialcraft/board/HardwareSessions.java
A src/main/java/com/serialcraft/board/IoLogic.java
A src/main/java/com/serialcraft/board/IoSignalState.java
A src/main/java/com/serialcraft/network/ChannelInbox.java
A src/main/java/com/serialcraft/network/ConfigResultPayload.java
A src/main/java/com/serialcraft/network/HardwareLinkPayload.java
A src/main/java/com/serialcraft/network/IoSnapshot.java
A src/main/java/com/serialcraft/network/SignalProtocol.java
A src/test/java/com/serialcraft/block/ConnectorGeometryTest.java
A src/test/java/com/serialcraft/block/entity/HardwareIOPersistenceTest.java
A src/test/java/com/serialcraft/board/IoSignalStateTest.java
A src/test/java/com/serialcraft/client/events/TelemetryOutboxTest.java
A src/test/java/com/serialcraft/connection/HardwareOutboxTest.java
A src/test/java/com/serialcraft/connection/WifiHandlerTest.java
A src/test/java/com/serialcraft/identity/AuthTest.java
A src/test/java/com/serialcraft/network/PayloadCodecTest.java
A src/test/java/com/serialcraft/network/ProtocolTest.java
A src/test/java/com/serialcraft/network/guard/PacketRateLimiterTest.java
A src/test/java/com/serialcraft/signal/SignalTest.java
A src/test/java/com/serialcraft/test/TestBootstrap.java
```
