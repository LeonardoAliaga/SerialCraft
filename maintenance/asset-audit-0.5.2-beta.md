# Revisión del rediseño de logos — SerialCraft 0.5.2-beta

Fecha: **1 de noviembre de 2026**. Rediseño revisado: commit [`0f8182872f8125cf4ea748d05b02ebb815b531df`](https://github.com/LeonardoAliaga/SerialCraft/commit/0f8182872f8125cf4ea748d05b02ebb815b531df), «Rediseñado de todos los logos», en `main`.

## Resultado

**El rediseño reduce la presencia explícita de marcas ajenas en los recursos activos del mod respecto de 0.5.1-beta. No permite declarar que el proyecto esté libre de reclamaciones.** Se sustituyeron las composiciones anteriores de la Laptop y la placa que mostraban los logotipos de Arduino y Minecraft. El infinito ahora está dibujado con jumpers, pero conserva esa silueta; el parecido de conjunto sigue requiriendo revisión.

Esta conclusión distingue evidencia visual de valoración de riesgo. No se han verificado registros de marcas por país, licencias privadas ni autorizaciones de los titulares. La reducción observada no tiene un plazo de protección temporal ni equivale a autorización.

## Sustituciones comprobadas

Rutas relativas a `src/main/resources/assets/serialcraft/`.

| Recurso | Estado comprobado y uso |
| --- | --- |
| `textures/gui/logo-txt.png` | PNG transparente de **1038 × 446**, con jumpers, placa, cuadrícula y texto SerialCraft. Lo usan `WelcomePage` y `NavBar`. Ya no muestra la composición anterior de logos oficiales. |
| `textures/block/serialcraft.png` | Composición propia con jumpers, SerialCraft y mascota. La usan los modelos de la Laptop apagada y encendida: `connector_block.json` y `connector_block_on.json`. La impresión anterior con los logotipos explícitos de Arduino y Minecraft fue sustituida. |
| `textures/block/io_block/placa.png` | Fondo de circuitos y nueva marca SerialCraft. Es referenciada tanto por `io_board.json` como por `io_board_base.json`, de modo que el cambio alcanza el modelo del ítem y los estados colocados. Ya no muestra los wordmarks anteriores. |
| `textures/block/arduino_redstone_block.png` | Icono Fabric cuadrado de **128 × 128**, ahora con el símbolo de jumpers. Se retiró la imagen anterior con el nombre `MyArduinoMc`. El nombre técnico del archivo permanece por compatibilidad de la referencia. |
| `textures/block/373737.png` | Detalle con las iniciales «SC», incluido en los modelos de la Laptop. |

El commit del autor también modifica `models/block/connector_block.json` y `models/block/io_block/io_board.json`. La versión 0.5.2-beta incorpora esos archivos tal como fueron entregados.

## Pendientes que conserva el proyecto

1. **Silueta de infinito:** los cables y conectores diferencian el dibujo, pero mantienen el motivo horizontal de dos bucles en un producto relacionado con electrónica. Las normas de Arduino restringen también las modificaciones de su logo. El rediseño no demuestra por sí solo autorización ni ausencia de confusión; tampoco se afirma que cualquier infinito pertenezca a Arduino.
2. **Identidad web:** `docs/public/brand/serialcraft-wordmark.svg` y `docs/public/favicon.svg` no cambiaron en el commit revisado y conservan la versión anterior del infinito. Las tarjetas sociales y los iconos móviles generados a partir de esas fuentes heredan su diseño.
3. **Capturas externas:** README y `MODRINTH.md` mantienen los cuatro enlaces CDN identificados en la [auditoría de 0.5.1-beta](asset-audit-0.5.1-beta.md). El commit no actualiza sus destinos ni la galería o el icono de la ficha de Modrinth. La publicación de un JAR nuevo no sustituye por sí sola esos recursos independientes.
4. **Textura histórica empaquetada:** `textures/item/connector_block.png` no cambió y sigue mostrando la miniatura antigua de la Laptop. La definición actual del ítem utiliza el modelo 3D, pero este PNG todavía pertenece a los recursos distribuidos. La mejora en las texturas activas no equivale a retirar toda copia antigua del JAR.
5. **Procedencia de recursos:** siguen pendientes las atribuciones y licencias por recurso de los gatos, atlas de iconos y fondos, señaladas en la auditoría anterior. Su aspecto por sí solo no permite afirmar una infracción.
6. **Versiones anteriores:** el historial Git, los JAR publicados y las imágenes históricas conservan su contenido.

Para reducir más la exposición, conviene actualizar las imágenes públicas y la identidad web, revisar la textura histórica y diferenciar más la silueta de marca o solicitar una autorización para el diseño concreto. Los avisos de independencia ayudan a explicar el origen del proyecto, pero no sustituyen los permisos aplicables.

## Corrección de la bienvenida

`WelcomePage` conservaba las dimensiones fuente **779 × 261**, incompatibles con el PNG de **1038 × 446**. Su altura se calculaba con esa proporción anterior y el logo aparecía comprimido. La corrección actualiza las dos constantes que comparten el cálculo de la distribución y el renderizado. `NavBar` ya utiliza las dimensiones correctas y no necesita otro cambio.

## Fuentes oficiales consultadas

- [Arduino: Arduino and community logos](https://www.arduino.cc/en/trademark/guides/arduino-and-community-logos/).
- [Arduino: Trademark guide for compatible products](https://www.arduino.cc/en/trademark/guides/trademark-guide-for-compatible-products/).
- [Minecraft: Usage Guidelines](https://www.minecraft.net/en-us/usage-guidelines).

Esta revisión visual y técnica documenta mejoras y pendientes; no es una certificación jurídica ni una revisión de todo el material publicado fuera del repositorio.
