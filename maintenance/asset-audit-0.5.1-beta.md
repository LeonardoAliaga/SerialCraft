# Revisión de imágenes y marcas — SerialCraft 0.5.1-beta

Fecha: **31 de octubre de 2026**. Base revisada: `b96419bd71a28a8d8c07f82d008cc17751af8170` (`main` y `test` coincidían al iniciar la revisión).

## Resultado

**Cambiar `logo-txt.png` elimina la composición anterior del encabezado, pero no elimina todas las marcas de terceros del proyecto.** Los logotipos de Arduino y Minecraft siguen visibles en texturas activas de la Laptop y del Módulo de E/S y en material promocional público.

El PNG nuevo, el wordmark web y el favicon utilizan un infinito visualmente próximo al símbolo de Arduino. Quitar `+` y `−` no permite dar por resuelta esa similitud. Esta es una observación visual y técnica, no una determinación jurídica ni una certificación de originalidad.

## Alcance y método

- Inspección visual de los **28 recursos gráficos versionados**: 21 PNG, 6 SVG y 1 WebP, incluidos los mapas de texturas ampliados.
- Inspección de referencias en Java, metadatos Fabric, modelos JSON, blockstates, definiciones de ítems, Markdown, Vue, CSS y generador de imágenes web.
- Lectura del historial de incorporación de las imágenes y de `LICENSE`.
- Inspección visual de las **cuatro imágenes remotas compartidas por README y MODRINTH.md**, además del logo de Modrinth enlazado desde el script de notificación.
- Consulta de la ficha pública de Modrinth `hLV9sPrg`: icono y **ocho entradas de galería**, descargadas para inspección en su resolución original cuando estaba disponible.
- Lectura de las políticas oficiales de Arduino y Minecraft enlazadas al final.

No se inspeccionaron archivos privados del autor, documentos de compra/licencia, archivos editables originales ni otras redes sociales. Las publicaciones históricas y los archivos de versiones anteriores conservan sus imágenes; esta actualización no reescribe el historial.

## Hallazgos prioritarios

Las rutas de texturas de esta tabla parten de `src/main/resources/assets/serialcraft/`.

| Prioridad | Recurso | Evidencia y uso real | Acción recomendada |
| --- | --- | --- | --- |
| Alta | `textures/block/serialcraft.png` | Composición azul con infinito `+`/`−`, wordmark de Minecraft y título SerialCraft. `models/block/connector_block.json` y `connector_block_on.json` la asignan a la textura `4`, utilizada en seis caras de elementos. Los ítems heredan el modelo de bloque. | Rediseñar la pantalla de la Laptop sin logotipos ajenos y actualizar después sus capturas. |
| Alta | `textures/block/io_block/placa.png` | Textura de placa con los mismos logotipos. La usan `io_board_base.json` en los estados del bloque e `io_board.json` como modelo del ítem. | Sustituir la impresión de la placa conservando sus coordenadas UV y comprobar bloque e inventario. |
| Alta | Galería de Modrinth: portada sin título, `Laptop`, `Placa`, `WelcomePage`, `Home`, `Lista de Placas`, `Events` | La portada incorpora ambos logos como parte de la identidad. Las otras seis entradas muestran las texturas o el encabezado antiguos. | Regenerar las capturas después de corregir las texturas; sustituir también la portada. |
| Alta | `README.md` y `MODRINTH.md`: imágenes de modelos y Módulo de E/S alojadas en el CDN | Los dos PNG grandes muestran claramente los logos impresos sobre la Laptop y la placa. Las recetas incluyen miniaturas de esos modelos. | Reemplazar los cuatro enlaces con capturas nuevas. Cambiar un archivo local no actualiza esos CDN. |
| Revisión alta | Nuevo `textures/gui/logo-txt.png` | Ya no contiene los dos wordmarks anteriores. Mantiene un infinito de trazado visualmente próximo al de Arduino, con una placa y una cuadrícula en los bucles. | Conservar la fuente y las licencias de sus elementos; valorar una silueta de marca claramente diferenciada o solicitar permiso. No asumir que suprimir los signos garantiza independencia. |
| Revisión alta | `docs/public/brand/serialcraft-wordmark.svg`, `docs/public/favicon.svg` | Reutilizan el infinito con placa y cuadrícula. El favicon mantiene un fondo turquesa. El generador usa el wordmark para las tarjetas sociales y el favicon para iconos móviles. | Resolver la forma de la marca de manera conjunta en mod, web, iconos y redes. Los SVG actuales no contienen enlaces a imágenes externas, pero eso no prueba su autoría. |
| Media | `textures/item/connector_block.png` | Raster antiguo de una Laptop con la identidad previa en la pantalla. El ítem actual usa el modelo 3D, no este PNG; el recurso sigue incluido al empaquetar `src/main/resources`. | Confirmar que ningún paquete externo lo necesita y sustituirlo o retirarlo en una limpieza posterior. |
| Procedencia pendiente | Gatos y mascota | `textures/gui/cats.png`, `gato.png`, el gato del monitor y las variantes web muestran un gato voxel muy parecido al tuxedo de Minecraft. No hay una ficha de procedencia por recurso que permita distinguir diseño propio, render de un modelo del juego o adaptación. | Documentar el origen. Una captura de una creación dentro del juego y un personaje usado como mascota de marca requieren análisis distintos. Si procede de recursos oficiales, valorar una mascota propia más diferenciada. |
| Procedencia pendiente | `textures/gui/icons.png` e `icons-w.png` | Se ven símbolos genéricos de campana, terminal, USB, Wi-Fi, etc. No identifiqué un logotipo de fabricante; tampoco una fuente o licencia específica del atlas. `SpriteIcon.java` consume la variante blanca. | Registrar autor, pack de origen y licencia, o confirmar que fueron dibujados por el autor. No atribuirlos a una biblioteca concreta por parecido. |
| Coherencia | `textures/block/arduino_redstone_block.png` | PNG blanco con el texto `MyArduinoMc`, seleccionado como icono en `fabric.mod.json`. No es el logo nuevo ni parece contener el logo oficial de Arduino; el nombre del archivo por sí solo no demuestra uso de una marca gráfica. | Actualizar el icono Fabric con un recurso cuadrado coherente con la marca elegida. Aclarar la procedencia de ese nombre antiguo. |

La octava captura de la galería, `Test to esp32`, muestra un panel de prueba y Minecraft en una ventana: tiene una Laptop pequeña con la textura previa. Su revisión también es recomendable, aunque la marca es mucho menos legible que en las otras entradas. El icono público de Modrinth ya usa el infinito con placa/cuadrícula y el gato, por lo que comparte los pendientes de esos recursos.

## Recursos web derivados

`docs/scripts/generate_brand_assets.py` genera estos archivos durante la construcción de la web:

| Fuente | Salidas |
| --- | --- |
| `docs/public/favicon.svg` | `apple-touch-icon.png`, `icon-192.png`, `icon-512.png` |
| `docs/public/brand/serialcraft-wordmark.svg` y `serialcraft-cat.svg` | `og/serialcraft-es-v2.png`, `og/serialcraft-en-v2.png` |

Aunque las cinco salidas no están versionadas en la base revisada, heredan el contenido de sus fuentes. Después de un rediseño, regenerarlas y cambiar las URL versionadas de las tarjetas sociales ayuda a renovar las cachés de Discord y otras redes. El favicon y el wordmark ya muestran la composición nueva de infinito, no los antiguos wordmarks explícitos.

## Imágenes remotas identificadas

| Uso en README y MODRINTH.md | URL |
| --- | --- |
| Modelos | <https://cdn.modrinth.com/data/cached_images/4aea9efd4686b3adf8ea97550df78b0240142c49.png> |
| Receta Laptop | <https://cdn.modrinth.com/data/cached_images/0796082c1617ad4cd6d1c4f3beb989fc89878192_0.webp> |
| Módulo E/S | <https://cdn.modrinth.com/data/cached_images/c20ed9d2c7fe7a3a5a28704394c6a64a6cc2839b.png> |
| Receta E/S | <https://cdn.modrinth.com/data/cached_images/07d93e2ac7612058dcfb25f2161efcc182ec78a8_0.webp> |

Galería e icono consultados: <https://api.modrinth.com/v2/project/serialcraft>. La portada de galería es `c9a5c658ba7cf3d4907e153521c3e58d7c84b80a.png`; el icono vigente es `25d894b03780bd99abb23523015b4aa8337b3de5_96.webp`. Son recursos independientes del JAR: publicar otra versión no los reemplaza.

El logo verde de Modrinth referenciado por `scripts/notify_discord.py` identifica la propia plataforma en una notificación; no está incorporado a la marca SerialCraft. Los badges de Shields usan texto descriptivo. No identifiqué en los recursos locales un logo independiente de Raspberry Pi, ESP32, Microsoft o Fabric.

## Qué permite concluir la procedencia disponible

La licencia MIT de la raíz indica cómo se ofrece el proyecto, pero no demuestra que cada elemento visual proceda de su autor ni concede por sí sola derechos sobre marcas de terceros. El historial registra quién incorporó un recurso, no necesariamente quién lo dibujó.

Ejemplos de trazabilidad: el logo anterior se incorporó en `be8489b` (25/12/2025), la textura `io_block/placa.png` en `db19841` (06/12/2025), el atlas blanco de iconos en `5202e4f` (05/01/2026), el atlas de gatos en `b92a06e` (17/10/2026) y las fuentes de marca web en `cf60102` (25/10/2026). No encontré un inventario de licencias o atribuciones específico para estas imágenes.

El aspecto voxel, una cuadrícula o una placa electrónica no prueban por sí solos una infracción. El parecido del infinito, la apariencia del gato y la procedencia del fondo de circuitos justifican revisión; los wordmarks explícitos en las dos texturas sí son evidencia visual directa de marcas ajenas integradas en el producto.

## Políticas oficiales y aplicación al proyecto

- [Arduino: logos y Community Logo](https://www.arduino.cc/en/trademark/guides/arduino-and-community-logos/) y [marcas y copyright](https://www.arduino.cc/en/trademark): diferencian referencias explicativas de la identificación de productos ajenos y también restringen modificaciones del logo. Por eso el infinito modificado sigue siendo un pendiente; no se declara que cualquier símbolo de infinito pertenezca a Arduino.
- [Minecraft: Usage Guidelines](https://www.minecraft.net/en-us/usage-guidelines): separan el uso descriptivo del nombre, las capturas y las marcas integradas en identidad propia. Mi recomendación para las dos texturas es retirar el wordmark de Minecraft. También conviene mostrar claramente la independencia del proyecto y revisar el uso del gato como mascota.
- [Arduino: cursos, talleres y tutoriales](https://www.arduino.cc/en/trademark/guides/trademark-guide-for-courses-workshops-and-tutorials/): útil para futuras fotografías, diagramas y guías. Mantener nombres como Arduino Uno Q o ESP32 para explicar compatibilidad no equivale a adoptar sus logotipos como marca propia.

Un aviso de no afiliación y los reconocimientos de marcas ayudan a aclarar el origen del proyecto, pero no sustituyen permisos cuando hagan falta. `docs/acerca-de.md` y `docs/en/about.md` ya declaran la independencia respecto de Mojang, Microsoft y los fabricantes. Conviene dar visibilidad al aviso en README, descripción de Modrinth y pie web, y mantener un registro específico de atribuciones gráficas.

## Cambio incluido en esta versión

Se copia exactamente el PNG entregado por Leonardo Aliaga a `textures/gui/logo-txt.png`, preservando sus bytes y transparencia. `NavBar.java` pasa de dimensiones fuente 779 × 261 a **1038 × 446**. La versión se incrementa a `0.5.1-beta`, con changelogs en español e inglés.

La sustitución no modifica las texturas de modelos ni los recursos remotos enumerados arriba. Esta revisión deja identificadas las siguientes tareas concretas: rediseñar las dos impresiones de modelos, resolver la forma del infinito, confirmar procedencia de mascota/iconos/fondo y regenerar material promocional e iconos con la identidad definitiva.

## Inventario visual local

Las dimensiones, hashes y rutas de los 28 recursos revisados se incluyen a continuación para permitir su identificación sin depender de miniaturas.

| Ruta | Dimensiones | SHA-256 (12 primeros caracteres) |
| --- | --- | --- |
| `docs/public/brand/serialcraft-cat.svg` | 1080 × 1080 (SVG) | `f2eaab6c0ceb` |
| `docs/public/brand/serialcraft-wordmark.svg` | 946 × 438 (SVG) | `d6be5c5ab7dc` |
| `docs/public/favicon.svg` | 1080 × 1080 (SVG) | `8e6a6d15cecd` |
| `docs/public/media/serialcraft-mascot-dark.svg` | 270 × 390 (SVG) | `98cd0b5557e6` |
| `docs/public/media/serialcraft-mascot-v2.svg` | 270 × 390 (SVG) | `05f42b555aef` |
| `docs/public/media/serialcraft-mascot.svg` | 270 × 390 (SVG) | `05f42b555aef` |
| `docs/public/media/serialcraft-mascot.webp` | 176 × 242 | `be7e1a8700b1` |
| `src/main/resources/assets/serialcraft/textures/block/272727.png` | 64 × 64 | `28938df4e753` |
| `src/main/resources/assets/serialcraft/textures/block/373737.png` | 64 × 64 | `797834fa03fa` |
| `src/main/resources/assets/serialcraft/textures/block/arduino_redstone_block.png` | 128 × 128 | `e8ae6ce0aafb` |
| `src/main/resources/assets/serialcraft/textures/block/gris-oscuro.png` | 64 × 64 | `0db5081833fe` |
| `src/main/resources/assets/serialcraft/textures/block/gris.png` | 64 × 64 | `8bb913c71c62` |
| `src/main/resources/assets/serialcraft/textures/block/io_block/default.png` | 64 × 64 | `5a6df45c4fde` |
| `src/main/resources/assets/serialcraft/textures/block/io_block/detalles.png` | 64 × 64 | `bc7ca5c571e3` |
| `src/main/resources/assets/serialcraft/textures/block/io_block/in.png` | 64 × 64 | `69c540efeef2` |
| `src/main/resources/assets/serialcraft/textures/block/io_block/out.png` | 64 × 64 | `2b6316166551` |
| `src/main/resources/assets/serialcraft/textures/block/io_block/placa.png` | 1080 × 1080 | `607452aa0503` |
| `src/main/resources/assets/serialcraft/textures/block/monitor.png` | 64 × 64 | `3901312a027f` |
| `src/main/resources/assets/serialcraft/textures/block/off.png` | 64 × 64 | `af9a7987c046` |
| `src/main/resources/assets/serialcraft/textures/block/on.png` | 64 × 64 | `fc34d8640174` |
| `src/main/resources/assets/serialcraft/textures/block/placa-texture.png` | 64 × 64 | `4a3ef689ee9a` |
| `src/main/resources/assets/serialcraft/textures/block/serialcraft.png` | 1080 × 1080 | `bc8754c77d47` |
| `src/main/resources/assets/serialcraft/textures/gui/cats.png` | 1254 × 1254 | `b6eb50b27da6` |
| `src/main/resources/assets/serialcraft/textures/gui/gato.png` | 434 × 434 | `c2daaade3ff5` |
| `src/main/resources/assets/serialcraft/textures/gui/icons-w.png` | 296 × 222 | `60d8f43660a8` |
| `src/main/resources/assets/serialcraft/textures/gui/icons.png` | 296 × 222 | `94a8143ff0c9` |
| `src/main/resources/assets/serialcraft/textures/gui/logo-txt.png` | 1038 × 446 | `26c29823ca3f` |
| `src/main/resources/assets/serialcraft/textures/item/connector_block.png` | 64 × 64 | `7c2b9ddfe475` |
