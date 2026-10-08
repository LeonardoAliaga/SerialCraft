---
title: Cómo ejecutar las pruebas de SerialCraft con Gradle y GitHub Actions
description: Aprende a ejecutar las pruebas JUnit y Minecraft GameTest en Java 25, entender los comandos de Gradle y leer resultados de CI.
---
# Cómo ejecutar las pruebas automatizadas de SerialCraft

A partir del commit de estabilización de **SerialCraft 0.4.6-beta**, la rama `test` incorpora **JUnit**, **Minecraft GameTests** y el workflow **Build and test** de GitHub Actions. Esto es importante: ya no dependes solamente de entrar al juego y comprobar manualmente si el Módulo de E/S parece funcionar.

## Antes de ejecutar

Necesitas un clon local del repositorio, la rama `test`, un **JDK 25** y acceso a Internet para descargar dependencias la primera vez.

Comprueba la versión activa:

~~~powershell
java -version
git branch --show-current
~~~

Debe aparecer Java 25 y la rama `test`. En Windows, si tienes varios JDK instalados, revisa la variable `JAVA_HOME` y que el IDE use el mismo JDK para Gradle.

## Tres comandos, tres propósitos

| Comando | Cuándo utilizarlo | Qué hace |
| --- | --- | --- |
| `.\gradlew.bat test` | Cambias lógica pura | Ejecuta las pruebas unitarias JUnit |
| `.\gradlew.bat build` | Antes de guardar o publicar una revisión | Compila, prueba y empaqueta el mod |
| `.\gradlew.bat -PioGameTests runGameTest` | Modificas bloques, redstone o comportamiento del mundo | Levanta el entorno de prueba de Minecraft y ejecuta GameTests |

En Linux/macOS sustituye `.\gradlew.bat` por `./gradlew`.

### 1. Pruebas unitarias (JUnit)

~~~powershell
.\gradlew.bat test
~~~

Son pruebas Java que validan reglas aisladas, por ejemplo:

- Convertir `128` del protocolo a intensidad de redstone `8`.
- Comprobar OR, AND y XOR.
- Rechazar un mensaje con un canal o valor inválido.
- Verificar límites y prioridades de las colas de comunicación.
- Comprobar serialización, estados, autenticación y algoritmos del visualizador.

El resultado se guarda normalmente en `build/reports/tests/test/index.html`. Si una prueba falla, Gradle indica cuál es y genera un reporte que puedes abrir en el navegador.

### 2. Compilar y empaquetar

~~~powershell
.\gradlew.bat build
~~~

Este comando ejecuta las tareas necesarias para construir el mod, incluidas las pruebas unitarias configuradas. Úsalo antes de llevar cambios de `test` a `main`.

Si se rompe una clase o un recurso, puede fallar **antes** de crear el JAR. Que pase el build no demuestra por sí solo que Arduino, ESP32 o la interfaz gráfica funcionen correctamente.

### 3. GameTests: comportamiento dentro de Minecraft

~~~powershell
.\gradlew.bat -PioGameTests runGameTest
~~~

`-PioGameTests` es una **propiedad del proyecto Gradle**. En `build.gradle` hace que Fabric configure una fuente de pruebas de Minecraft. La tarea `runGameTest` inicia el entorno necesario y comprueba casos sobre un mundo temporal.

Los GameTests de 0.4.6 comprueban, entre otros:

- Emisión de redstone por las cinco caras configurables.
- Reacción de palancas, botones, repetidores y comparadores.
- Encendido y apagado de una lámpara a través de un bloque sólido.
- Condiciones lógicas con muestras recibidas del hardware.
- Separación entre leer redstone y emitirla.
- Propiedad, distancia, chunks no cargados y una escena con 64 módulos.

El entorno generado bajo `build/run/gameTest` **no necesita abrir ni modificar tus mundos personales**. Aun así, siempre respalda tus mundos antes de realizar otras pruebas manuales.

::: tip Ejecutar todo de una vez
~~~powershell
.\gradlew.bat build -PioGameTests runGameTest
~~~
Este es el patrón combinado usado en el informe técnico de 0.4.6.
:::

## ¿Qué es GitHub Actions «Build and test»?

El archivo `.github/workflows/build.yml` configura un ordenador temporal de GitHub con **Java 25** y Gradle. Se ejecuta automáticamente cuando haces un push a `test` o abres un Pull Request hacia `test`.

Su secuencia principal es:

1. Descargar el repositorio en el runner.
2. Preparar Java 25 y Gradle.
3. Ejecutar `./gradlew build --console=plain`.
4. Ejecutar `./gradlew -PioGameTests runGameTest --console=plain`.
5. Subir reportes de pruebas y logs como artefactos, incluso cuando una etapa falla.

Puedes revisar cada paso desde [GitHub Actions del repositorio](https://github.com/LeonardoAliaga/SerialCraft/actions).

| Estado | Qué significa |
| --- | --- |
| Verde / Success | Los comandos del workflow terminaron correctamente |
| Rojo / Failure | Una tarea falló: abre sus logs y el reporte |
| En progreso | Las pruebas todavía están ejecutándose |
| Cancelado | No significa ni aprobado ni rechazado |

## ¿Qué no garantizan estas pruebas?

No reemplazan la comprobación física con un Arduino conectado, la revisión visual de distintas resoluciones ni una medición de rendimiento sostenida. Un GameTest con 64 módulos comprueba un **comportamiento funcional**, no los FPS ni los MSPT durante una hora.

Consulta la [matriz de pruebas](/io-test-matrix) para distinguir cobertura automática de comprobaciones manuales pendientes y el [informe de auditoría](/audit-0.4.6) para conocer los resultados registrados en el commit. No supongas que esos resultados permanecerán válidos después de cualquier modificación posterior.

## Qué hacer si falla

Empieza leyendo el primer error real, no el último resumen `BUILD FAILED`:

- **No encuentra Java 25:** comprueba `JAVA_HOME` y la configuración Gradle del IDE.
- **No descarga dependencias:** comprueba conectividad y repositorios; un fallo de red no es necesariamente un bug Java.
- **JUnit falla:** abre `build/reports/tests/test/index.html` y busca la aserción.
- **GameTest falla:** consulta los logs en `build/run/gameTest/logs` y la escena de prueba indicada.
- **Falló solo en GitHub:** compara la versión de Java, el comando y los logs del runner con tu equipo.

Para seguir aprendiendo, combina estas pruebas con un ejercicio de [redstone y Arduino](/tutoriales/arduino-led-minecraft) y con la [referencia del Módulo de E/S](/io-module).
