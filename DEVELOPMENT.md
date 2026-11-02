# Desarrollo de SerialCraft con Fabric y NeoForge

Minecraft **26.2** y Java **25** se mantienen. Importa el `settings.gradle` de la raíz como un único proyecto Gradle en IntelliJ IDEA y usa el wrapper **9.7.1** con JDK 25 como Gradle JVM.

## Organización

| Módulo | Contenido |
| --- | --- |
| `common` | Bloques, ítems, block entities, protocolos, codecs, validación, configuración y recetas. `src/client` contiene toda la interfaz, señales, USB y Wi-Fi. `src/test` contiene las pruebas JUnit compartidas. |
| `fabric` | Inicializadores Fabric, eventos, red, permisos, metadatos, proveedores de servicios y entrada de datagen/GameTests. |
| `neoforge` | Inicializadores NeoForge, eventos de registro, red, permisos, metadatos, proveedores de servicios y entrada de datagen. |

Los loaders compilan directamente las fuentes comunes, siguiendo MultiLoader Template. Ambos JAR incorporan esas clases y recursos; el JAR de `common` es un artefacto de desarrollo y no se instala. La documentación web y los ejemplos de placas permanecen en sus directorios originales.

`common` compila contra Minecraft vanilla mediante Loom y conserva sus source sets `main`/`client`, sin dependencias de APIs Fabric/NeoForge. Esto detecta referencias de cliente desde el código de servidor durante la compilación. NeoForge compila ambos conjuntos en su source set principal; su inicializador `@Mod(dist = Dist.CLIENT)` y los servicios separados de cliente evitan su carga en un servidor dedicado. Los servicios comunes no referencian Minecraft cliente ni las conexiones físicas.

`Platform` proporciona directorio de configuración, versión, construcción de tipos de block entity y permisos. Los adaptadores de red usan los mismos identificadores y codecs y conservan los limitadores, la validación de propiedad/distancia/dimensión y la ejecución en el hilo de Minecraft. NeoForge registra bloques, ítems y block entities durante sus respectivos `RegisterEvent`, cuando los registros están abiertos.

El índice de placas recibe eventos Fabric de carga/descarga. En NeoForge, un mixin pequeño conecta `onLoad` y `setRemoved` con el mismo índice; `LevelChunk.clearAllBlockEntities` también llama a `setRemoved` al descargar chunks. El cierre del servidor conserva el comportamiento de seguridad existente. Los mixins de ejemplo anteriores no tienen funcionalidad y siguen sin activarse.

Se mantienen `serialcraft:connector_block`, `serialcraft:io_block`, sus ítems y block entities, canales, claves NBT, enums persistidos y nombres/formatos de los archivos de configuración. El permiso de administración sigue siendo `serialcraft.admin.bypass`, con nivel OP 2 por defecto: Fabric usa fabric-permissions-api y NeoForge su PermissionAPI y `COMMANDS_GAMEMASTER`. Un proveedor de permisos de cada plataforma puede conceder o denegar el nodo.

## Versiones fijadas

Todas están centralizadas en `gradle.properties`, excepto el wrapper en `gradle/wrapper/gradle-wrapper.properties`.

| Componente | Versión |
| --- | --- |
| Minecraft / Java / Gradle | 26.2 / 25 / 9.7.1 |
| SerialCraft | 0.5.2-beta |
| Fabric Loader / Fabric API | 0.19.3 / 0.157.0+26.2 |
| Fabric Loom | 1.17.21 (sin SNAPSHOT) |
| NeoForge / ModDevGradle | 26.2.0.93 / 2.0.148 |
| NeoForm utilizado por NeoForge | 26.2-2, fijado transitivamente por su userdev |
| jSerialComm / JmDNS | 2.11.4 / 3.5.8 |
| fabric-permissions-api | 0.7.0 |
| JUnit BOM para NeoForge | 5.14.3 |

Minecraft 26.2 no está ofuscado: no se añaden mappings ni pasos de reobfuscación/remapeo de las versiones antiguas. Los artefactos instalables salen de `jar`. Fabric usa `include` y NeoForge usa `jarJar`; ambos conservan los JAR originales de jSerialComm/JmDNS, incluidos los binarios nativos y sus metadatos. No se sombrea ni se relocaliza el código nativo.

## Comandos

Desde la raíz, usa `./gradlew` en Linux/macOS y `.\gradlew.bat` en Windows:

```powershell
.\gradlew.bat build
.\gradlew.bat :fabric:build
.\gradlew.bat :neoforge:build
.\gradlew.bat :fabric:test :neoforge:test
.\gradlew.bat -PioGameTests :fabric:runGameTest
.\gradlew.bat :fabric:runDatagen
.\gradlew.bat :neoforge:runData
python scripts/validate_locales.py
python -m unittest discover -s scripts -p 'test_*.py' -v
python scripts/publish_release.py --check
```

Los JUnit usan las mismas pruebas bajo cada loader: Fabric Loader JUnit y el soporte JUnit de ModDevGradle/FML. Los GameTests de Fabric conservan la implementación y configuración que ya existían. La generación de datos de ambos loaders escribe las mismas recetas y avances en `common/src/main/generated`; ejecútala por separado para evitar escrituras concurrentes.

`build` compila y ejecuta JUnit en ambos loaders y copia los dos JAR de distribución a `build/libs/`. Los originales están en:

```text
fabric/build/libs/serialcraft-fabric-0.5.2-beta.jar
neoforge/build/libs/serialcraft-neoforge-0.5.2-beta.jar
```

Instala exactamente el JAR correspondiente a tu loader. En Fabric instala también Fabric API para 26.2. En NeoForge no se necesita Fabric API. Cliente y servidor deben usar versiones compatibles de SerialCraft. Esta migración no garantiza conexión entre clientes y servidores que usan loaders diferentes.

## IntelliJ y ejecución

Después de recargar Gradle, las configuraciones compartidas de `.run/` aparecen en el selector de ejecución:

| Configuración | Tarea | Directorio del juego |
| --- | --- | --- |
| Fabric Client | `:fabric:runClient` | `fabric/runs/client` |
| Fabric Server | `:fabric:runServer` | `fabric/runs/server` |
| NeoForge Client | `:neoforge:runClient` | `neoforge/runs/client` |
| NeoForge Server | `:neoforge:runServer` | `neoforge/runs/server` |

También puedes ejecutar las tareas desde el panel Gradle. Loom/ModDevGradle proporcionan sus configuraciones nativas al sincronizar el IDE; las de `.run` usan tareas Gradle y no dependen de nombres internos de módulos de IntelliJ. El cliente Fabric y el servidor están en source sets independientes. Los dos loaders, y cliente/servidor dentro de cada loader, tienen carpetas separadas de configuración y mundos. Los directorios `run` anteriores quedan conservados.

Para un servidor Fabric normal, revisa y acepta personalmente la EULA en `fabric/runs/server/eula.txt` antes de arrancarlo. Las herramientas de desarrollo NeoForge/GameTest tienen condiciones de arranque distintas; no uses sus carpetas ni sus ajustes de red como configuración de producción. Los JUnit escriben su configuración en `fabric/build/test-run` y `neoforge/build/test-run`.

## CI y publicación

`build.yml` ejecuta la compilación y los JUnit de ambos loaders, los GameTests y los validadores de localización/publicación. El artefacto de release contiene ambos JAR instalables. Las automatizaciones de la web y notificaciones mantienen sus funciones.

`publish_release.py --check` no usa la red: comprueba ambos metadatos, la versión exacta de Minecraft, recursos compartidos, integridad ZIP, traducciones y bibliotecas incluidas, con comprobación de los nativos Windows/Linux de jSerialComm. Requiere Python 3.11 o posterior para leer TOML.

Cuando se autorice una publicación futura, `--publish` conserva una única release GitHub `v<versión>` y sube ambos JAR antes de publicar su borrador. Reintenta usando los mismos artefactos de la ejecución original: rechaza archivos distintos y tags con otro commit. Modrinth sigue requiriendo autorización explícita mediante `--modrinth`, el input del workflow o la marca `[publish-modrinth]`. Crea `<versión>-fabric` y `<versión>-neoforge`, cada una con su propio archivo, loader y Minecraft 26.2; solo la de Fabric requiere Fabric API. Usa una versión nueva para publicar esta migración si `0.5.2-beta` ya existe en producción. `MODRINTH.md` sigue siendo la descripción sincronizada del proyecto.

## Fuentes contrastadas

- [MultiLoader Template, revisión 178d1abb de la rama 26.2](https://github.com/jaredlll08/MultiLoader-Template/tree/178d1abb85e7d4ef7903bdd8fe9f4f9e0748c80c): estructura y compilación de fuentes comunes.
- [Fabric 26.2](https://docs.fabricmc.net/develop/), [Loom](https://docs.fabricmc.net/develop/loom/) y [portal de versiones](https://fabricmc.net/develop/). Se conservaron Loader/API del checkout y se sustituyó el SNAPSHOT de Loom por 1.17.21 publicado.
- [Versiones NeoForge](https://projects.neoforged.net/neoforged/neoforge), [metadatos Maven publicados](https://maven.neoforged.net/releases/net/neoforged/neoforge/maven-metadata.xml), [fuentes exactas 26.2.0.93](https://maven.neoforged.net/releases/net/neoforged/neoforge/26.2.0.93/neoforge-26.2.0.93-sources.jar) y [repositorio 26.2.x](https://github.com/neoforged/NeoForge/tree/26.2.x).
- [Inicializadores/metadatos](https://docs.neoforged.net/docs/gettingstarted/modfiles/), [registros](https://docs.neoforged.net/docs/concepts/registries/) y [payloads](https://docs.neoforged.net/docs/networking/payload/). El selector general consultado mostraba 26.1; las firmas se contrastaron con los sources y userdev 26.2.0.93, especialmente el registro separado de handlers cliente y el nuevo `iconFile`.
- [ModDevGradle](https://docs.neoforged.net/toolchain/docs/plugins/mdg/) y [versiones](https://projects.neoforged.net/neoforged/ModDevGradle), [Gradle multiproyecto](https://docs.gradle.org/current/userguide/multi_project_builds.html).
- [jSerialComm](https://github.com/Fazecast/jSerialComm) y [JmDNS](https://github.com/jmdns/jmdns): se conservan las versiones del proyecto y se empaquetan sin alterar sus bibliotecas.
- [GitHub Actions con Gradle](https://docs.github.com/en/actions/tutorials/build-and-test-code/java-with-gradle) y [Modrinth: crear una versión](https://docs.modrinth.com/api/operations/createversion/).
