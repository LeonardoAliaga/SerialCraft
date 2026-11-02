---
title: How to run SerialCraft tests with Gradle, JUnit and GameTest
description: Learn how to execute SerialCraft Java unit tests, Minecraft GameTests and GitHub Actions CI.
---
# Testing SerialCraft: JUnit, Minecraft GameTest and CI

Starting with **0.4.6-beta**, the `test` branch includes Java unit tests, Minecraft world tests and a GitHub Actions **Build and test** workflow.

## Prepare the environment

<!-- SC046_MEDIA:D01 -->

Use the `test` branch, a **Java 25 JDK**, and Gradle Wrapper from the repository. In a terminal run `java -version` and `git branch --show-current`.

| Goal | Windows | Linux / macOS |
| --- | --- | --- |
| Unit tests only | `.\gradlew.bat :fabric:test :neoforge:test` | `./gradlew :fabric:test :neoforge:test` |
| Full build and unit tests | `.\gradlew.bat build` | `./gradlew build` |
| Minecraft GameTests | `.\gradlew.bat -PioGameTests :fabric:runGameTest` | `./gradlew -PioGameTests :fabric:runGameTest` |

The optional `-PioGameTests` switch is a Gradle project property. `fabric/build.gradle` uses it to enable Fabric's test source set and GameTest environment.

JUnit runs the shared suite under **both loaders**. Minecraft GameTests currently use the **Fabric** test harness; they do not establish NeoForge world-test coverage.

## What does each test cover?

<!-- SC046_MEDIA:D02 -->

<!-- SC046_MEDIA:D03 -->

**JUnit** checks algorithms and code behavior without requiring a physical device: signal conversion, gates, bounded queues, telemetry, codec parsing, authentication, persistence and waveform helpers.

**GameTest** runs scenarios inside a temporary Minecraft testing world. The suite covers the five I/O faces, redstone input and output, gates, levers, buttons, comparators, repeaters, neighboring blocks, ownership rules, chunks and a functional scenario with 64 modules.

**Build** compiles and packages the mod in addition to configured unit tests. A passing build is not proof that every physical board or GUI layout has been checked.

## GitHub Actions

<!-- SC046_MEDIA:D04 -->

<!-- SC046_MEDIA:V07 -->

When you push to `test` or open a PR targeting `test`, [GitHub Actions](https://github.com/LeonardoAliaga/SerialCraft/actions) runs `./gradlew build --console=plain` followed by `./gradlew -PioGameTests :fabric:runGameTest --console=plain`. Reports and logs are uploaded as workflow artifacts.

Green means the configured commands succeeded; red requires opening the logs. Unit test reports are normally located at `fabric/build/reports/tests/test/index.html` or `neoforge/build/reports/tests/test/index.html`, with GameTest logs in `fabric/build/run/gameTest/logs`.

For a combined local run:

~~~powershell
.\gradlew.bat build -PioGameTests :fabric:runGameTest
~~~

## Test limitations

GameTests do not substitute for an actual Arduino/ESP32, real USB disconnects, Wi-Fi hardware, UI inspection or long-running performance measurements. The [test matrix](/io-test-matrix) and [audit report](/audit-0.4.6) document what was automated and what remains a manual check.

If a run fails, identify the first actionable error, verify the JDK and distinguish compilation/test failures from unavailable dependency repositories.
