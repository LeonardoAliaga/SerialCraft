# Diseño del Módulo de E/S — 0.4.6-beta

La revisión parte de `test`, sin cambios locales, y de una compilación inicial correcta sin pruebas automatizadas. Ya estaban corregidos el índice de entidades por propietario/dimensión, límites de paquetes, propiedad y comprobación de chunks cargados. Persistían la mezcla de lectura/emisión en `redstoneOutput`, la pérdida del dato al cerrar una compuerta, la lógica ignorada al enviar a hardware, consultas periódicas aunque no hubiera cambios, el conector inferior ausente de la forma, el éxito anticipado del editor y el descarte global de sensores cada 25 ms.

## Contrato

Se conserva un modo global y un canal por bloque. `input` en disco/protocolo significa **Hardware → Minecraft** y `output` significa **Minecraft → Hardware**. Las clases pasan a HardwareIOBlock/HardwareIOBlockEntity; los dos registros mantienen `serialcraft:io_block`. No hay transmisión y recepción simultáneas por un mismo bloque.

| Conector | Hardware → Minecraft | Minecraft → Hardware |
| --- | --- | --- |
| Desactivado | No lee ni emite | No lee ni emite |
| Entrada | Condición para el dato recibido | Combina redstone para enviarla |
| Salida | Emite el resultado, débil y fuerte | Inactivo, conserva su configuración |

Hay cinco conectores: norte, sur, este, oeste y abajo. El terminal marcado abajo está en la superficie de la placa para poder seleccionarlo mientras descansa en un bloque; se comunica con el vecino inferior. No hay conector superior. Se conserva clic derecho para entrada y Shift + clic derecho para salida; repetir desconecta. El editor ofrece los tres estados de forma explícita.

OR = máximo de los niveles; AND = mínimo de todos los niveles (incluidos ceros); XOR = máximo si el número de niveles mayores que cero es impar, cero en otro caso. Digital convierte el resultado positivo a 15/255. Analógico mantiene su magnitud. Sin entradas, Minecraft → Hardware envía cero y Hardware → Minecraft omite la condición. En recepción, la lógica no modifica la magnitud del dato: solamente lo habilita. La muestra recibida se conserva al cambiar la condición.

## Estado y comunicación

Configuración persistente: nombre, canal, modo, señal, lógica, habilitación, dueño y conectores (BlockState). Estado de sesión: muestra RX, entradas, resultado, emisión, último valor remitido al cliente y enlace reportado por el cliente. No existe ACK físico en `canal:valor`; un paquete enviado no significa que el actuador lo haya procesado.

El servidor es autoritativo. Agrupa cambios de vecinos por tick; no consulta entradas estables periódicamente. Al cambiar emisión o conectores notifica vecinos y los vecinos de los bloques alimentados fuertemente. Durante el muestreo su propia salida se oculta para impedir que un bloque conductor adyacente la devuelva. Un circuito externo que une físicamente entrada y salida sigue siendo una realimentación y debe separarse con repetidores; no es posible atribuir el origen de toda señal de polvo con una lectura local.

La desconexión real reportada invalida RX y elimina emisión. Sin latidos no se inventa una desconexión por silencio: el último dato sigue válido en esa sesión y se muestra su antigüedad. Reiniciar/recargar invalida RX; no se recupera energía desde `redstoneOut` antiguo. Reconectar fuerza TX aun sin cambios; USB requiere además reenvío tras el arranque del bootloader. La entrada del cliente utiliza una cola acotada por canal, conserva transiciones bajo su presupuesto y no elimina los límites del servidor.

Los niveles del protocolo son enteros 0..255; datos malformados o fuera de rango se rechazan. Las conversiones internas saturan. Analógico: redstone × 17, y hardware / 17 redondeado al entero más cercano (128 → 8). Canales ASCII de hasta 32 caracteres; `mc_` reservado. Varios receptores pueden compartir canal. Varios emisores con el mismo canal compiten por el mismo actuador y requieren canales distintos si sus valores difieren.
