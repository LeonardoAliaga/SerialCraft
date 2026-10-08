# Matriz de verificación del Módulo de E/S

Fecha: 2026-10-08. Rama: `test`, versión `0.4.6-beta`.

**Automática** significa ejecutada sobre el código; **parcial** distingue la cobertura de lógica de la prueba con un cliente o hardware real. **Manual pendiente** nunca significa aprobada.

## Cobertura ejecutada

| Caso | Resultado esperado | Cobertura |
| --- | --- | --- |
| RX 0 / 255 / 128 | Analógico 0 / 15 / 8; digital 0 / 15 / 15 | Unitarias; GameTest RX 128 en cinco caras y RX 255 con condición |
| RX inválido, -1, 256, texto, canal `mc_` | Rechazo sin modificar señal | Unitarias de protocolo |
| TX redstone 0 / 1 / 7 / 15 | Analógico 0 / 17 / 119 / 255; digital 0 / 255 / 255 / 255 | Unitarias y GameTest con comparador/cofre; escritura física pendiente |
| DIGITAL ↔ ANALOG | Recalcular sin reutilizar la emisión como lectura | Unitarias; editor y firmware pendientes |
| Hardware → Minecraft ↔ Minecraft → Hardware | TX no alimenta ninguna salida; invalidar RX al cambiar dirección | Unitarias y GameTest de aislamiento; envío de cero al canal anterior pendiente con hardware |
| Habilitar/deshabilitar | Salida de mundo cero al deshabilitar; TX solicita cero | GameTest de lámpara tras bloque sólido; actuador físico pendiente |
| Cambiar canal | Invalidar RX, cancelar órdenes pendientes del canal anterior, solicitar cero anterior y estado nuevo | Codec y cola automática; recorrido completo con cliente/hardware pendiente |
| Cambiar compuerta con señal | OR máximo, AND mínimo, XOR máximo con paridad impar | Unitarias, incluidas todas las combinaciones binarias para 1–5 entradas |
| Sin conectores | RX sin condición; TX cero | Unitarias |
| Una / dos / tres o más entradas | Reglas lógicas y magnitud documentadas | Unitarias de lógica digital y analógica |
| Condición se cierra y abre sin otro RX | Conservar muestra y restaurar salida al abrir | GameTest con bloque de redstone |
| Norte / sur / este / oeste / abajo | Emitir débil y fuerte solamente en salidas; arriba cero | GameTest y 243 configuraciones empaquetadas |
| Selección de conectores | Región clicable coherente; terminal inferior alcanzable desde arriba | Unitarias de geometría y raycast inferior; interacción gráfica pendiente |
| Palanca, botón y repetidor | Detectar activación y apagado real, incluido final de pulsación | GameTests |
| Comparador/cofre | Recorrer 0→1→7→15→0 al cambiar inventario | GameTest |
| Polvo de redstone conectado por una derivación | Leer señal sin emitir en TX | GameTest con línea de polvo norte–sur y entrada lateral |
| Bloque sólido alimentado + lámpara | Encender y apagar sin energía residual | GameTest |
| Desconexión observada | Invalidar RX y apagar mundo | GameTest; detección USB/TCP física pendiente |
| Conexión TCP | Escuchar no significa conectado; token válido, escritura y cierre verificables | Tres pruebas de sockets locales / delimitación, incluida reinicialización |
| Handshake incompleto al detener servidor | Cerrar socket pendiente y liberar escucha | Prueba de socket local |
| Reconexión con valor TX estable | Invalidar deduplicación y reenviar; USB repite a los 2 s | Política implementada; bootloader/placa real pendiente |
| Guardar/cargar configuración | Conservar nombre, canal, propietario, señal, lógica, modo y habilitación; no persistir RX/TX/enlace | Unitarias sobre NBT real |
| Datos antiguos | Leer enums nombrados y ordinales; ignorar `redstoneOut`; conservar MODE=2 y caras; ignorar `blinking` antiguo | Unitarias con `NbtUtils` y codec real de paleta de chunks |
| Canal o modo antiguo inválido | Deshabilitar; conservar canal inválido saneado y bloquear activación sin corregirlo | Unitarias |
| Varios módulos, mismo canal | Distribuir muestra a receptores; retirar entidades del índice | GameTest funcional de 64 módulos |
| Permisos / distancia / chunk no cargado | Dueño permitido, otro jugador rechazado; no forzar carga | GameTest con jugadores de prueba |
| Colas RX/TX | Turnos entre canales, FIFO IO, memoria acotada, descartes observables, parada prioritaria | Unitarias |
| Telemetría | Estados coalescidos, eventos acotados, cancelación por canal | Unitarias; recorrido gráfico con eventos reales pendiente |
| Visualizador / generador | RX/TX separados, buffer circular, estadísticas, ondas y valor de reposo | Unitarias; renderizado y pérdida de enlace real pendientes |
| Autenticación | Vector HMAC-SHA256, desafío nuevo, rechazo de repetición, persistencia/revocación y fallo de escritura | Unitarias; persistencia de claves en ESP32/Uno Q pendiente |

## Pruebas manuales restantes

Usar copias de mundos anteriores. Instalar el mismo mod actualizado en cliente y servidor; los paquetes internos cambiaron a `v2`. Mantener 115200 en ambos extremos USB.

| Escenario | Pasos y criterio |
| --- | --- |
| Palanca y botón | TX digital con entrada en cada cara. Probar pulsos de botón y mantener palanca 60 s: un estado estable no debe crear tráfico continuo del bloque |
| Repetidor y comparador | TX analógico con niveles 0, 1, 7 y 15; RX hacia repetidor/lámpara. Verificar orientación, caída y ausencia de señal por caras desactivadas |
| Entrada y salida en el mismo circuito | RX condicionado con cable que devuelve su salida. Verificar apagado al deshabilitar y documentar el lazo real. Separar caminos con repetidores; una lectura local no identifica el origen de energía en todo el circuito |
| Cambios simultáneos | Cambiar varias entradas en el mismo tick, alternar lógica y desconectar una cara con señal. El resultado debe corresponder a los niveles finales del tick; no prometer capturar pulsos inferiores a un tick |
| Conector inferior | Colocar sobre piedra, redstone y componentes; elegir el terminal visible desde arriba y verificar el vecino inferior, nunca el superior |
| UI y permisos | Probar escalas GUI con anchuras 320/426/640 y alturas ≥240, los cuatro idiomas, nombre/canal al alternar opciones, scroll y errores de guardar. Guardar sin hardware debe funcionar; ningún éxito antes de respuesta del servidor |
| USB Uno R3 | Apertura ocupada/inexistente, conexión correcta, reset por DTR, desenchufar y reconectar con palanca estable. Verificar reenvío después del bootloader y primera muestra cero |
| Wi-Fi ESP32 / Uno Q | Token correcto/incorrecto, HMAC, olvidar placa, IP privada, cable/red caída, autenticación lenta, reconexión estable y cierre durante handshake |
| Reinicio / chunks | Guardar módulo RX alimentado, cerrar servidor y volver; no recuperar señal temporal. Descargar y recargar chunk; esperar la instantánea del sensor. TX debe solicitar cero al descargar y resincronizar al cargar |
| Dimensión y multijugador | Dos jugadores con el mismo canal en Overworld/Nether. Solo canales del dueño en su dimensión actual; cambio de dimensión invalida RX y solicita cero a emisores anteriores. Intentar modificar dueño ajeno, dimensión vieja y posiciones descargadas |
| Dos emisores, mismo canal | Mostrar que el actuador recibe el último comando: no hay arbitraje por dispositivo. Asignar canales distintos. Probar receptores compartidos, que sí están soportados |
| Telemetría + IO + ondas | Activar eventos, usar sensores y un generador en otro canal. Deshabilitar evento con datos pendientes, detener onda y confirmar reposo prioritario. No compartir canal entre generador y actuador controlado por IO |
| Mundo histórico | Copiar regiones reales anteriores, verificar receta, inventario, colocación, conectores, nombre, dueño y canales. Revisar AND/XOR antiguos y canales inválidos deshabilitados |

## Rendimiento

Se ejecutó una prueba funcional con **64 módulos**: recepción compartida 128→0, procesamiento y retirada de sus referencias del índice. Las colas y el recorder tienen pruebas de saturación. Esto no mide MSPT, uso de CPU, GC, paquetes por segundo ni rendimiento de renderizado.

Para el perfil pendiente, comparar 0/32/64/128 módulos durante cinco minutos de señal estable y cinco de cambios, con fuentes aisladas y después con circuitos conectados. Registrar MSPT, memoria/GC, actualizaciones de vecinos, bytes/paquetes, colas y descartes F7. Repetir tras descargar/cargar chunks. La condición buscada es que el estado estable no crezca en memoria ni genere actualizaciones continuas; el tráfico bajo cambios debe respetar el presupuesto compartido de 40 líneas/s y mostrar los descartes bajo sobrecarga. No se ha realizado este perfil.

## Reproducción

```powershell
$env:JAVA_HOME = 'C:/Program Files/Java/jdk-25.0.4.1'
./gradlew.bat build
./gradlew.bat -PioGameTests runGameTest
```

En Linux: `./gradlew build` y `./gradlew -PioGameTests runGameTest`. Los GameTests usan un mundo de prueba generado bajo `build/run/gameTest`; no abren mundos del jugador.
